package nl.b3p.viewer.stripes;

import javafx.util.Pair;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.controller.LifecycleStage;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.viewer.audit.AuditMessageObject;
import nl.b3p.viewer.audit.Auditable;
import nl.b3p.viewer.config.app.Application;
import nl.b3p.viewer.config.app.ApplicationLayer;
import nl.b3p.viewer.config.app.ConfiguredAttribute;
import nl.b3p.viewer.config.security.Authorizations;
import nl.b3p.viewer.config.services.Layer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureSource;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.filter.text.cql2.CQL;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.stripesstuff.stripersist.Stripersist;

import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static nl.b3p.viewer.stripes.FeatureInfoActionBean.FID;

@UrlBinding("/action/feature/editbulk")
@StrictBinding
public class EditBulkFeatureActionBean extends LocalizableApplicationActionBean implements Auditable {
    private static final Log log = LogFactory.getLog(EditBulkFeatureActionBean.class);
    protected Layer layer;
    protected SimpleFeatureStore store;
    private JSONObject currentFeature;
    @Validate
    private Application application;
    @Validate
    private String features;
    @Validate
    private ApplicationLayer appLayer;
    private ActionBeanContext context;
    private String editbulkError;
    private JSONObject editbulkResponse;
    private FeatureSource editbulkFeatureSource;
    protected EntityManager entityManager;
    private AuditMessageObject auditMessageObject;

    //<editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public String getFeatures() {
        return features;
    }

    public void setFeatures(String features) {
        this.features = features;
    }

    public ApplicationLayer getAppLayer() {
        return appLayer;
    }

    public void setAppLayer(ApplicationLayer appLayer) {
        this.appLayer = appLayer;
    }

    public SimpleFeatureStore getStore() {
        return store;
    }

    public JSONObject getCurrentFeature() {
        return currentFeature;
    }

    public void setCurrentFeature(JSONObject currentFeature) {
        this.currentFeature = currentFeature;
    }

    public void setEntityManager(EntityManager entityManager) { this.entityManager = entityManager; }

    public EntityManager getEntityManager() { return this.entityManager; }

    public Layer getLayer() {
        return layer;
    }

    public AuditMessageObject getAuditMessageObject() {
        return this.auditMessageObject;
    }
    //</editor-fold>

    @Before(stages = LifecycleStage.EventHandling)
    public void initAudit() {
        auditMessageObject = new AuditMessageObject();
    }

    @DefaultHandler
    public Resolution editbulk() throws JSONException {
        JSONObject response = editbulkJsonResponse();
        return new StreamingResolution("application/json", new StringReader(response.toString(4)));
    }

    public JSONObject editbulkJsonResponse() throws JSONException {
        setInitialValuesForInstanceFields();

        editbulkResponse.put("success", false);

        try {
            saveFeaturesAndBuildResponseAndCommitOrRollback();
        } catch (Exception e) {
            log.error("Exception editing features", e);

            editbulkError = e.toString();
            if (e.getCause() != null) {
                editbulkError += "; cause: " + e.getCause().toString();
            }
            editbulkError += org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(e);
        } finally {
            if (editbulkFeatureSource != null) {
                editbulkFeatureSource.getDataStore().dispose();
            }
        }

        if (editbulkError != null) {
            editbulkResponse.put("editbulkError", editbulkError);
            log.error("Returned editbulkError message editing features: " + editbulkError);
        }

        this.auditMessageObject.addMessage(editbulkResponse);

        return editbulkResponse;
    }

    private void setInitialValuesForInstanceFields() {
        editbulkResponse = new JSONObject();
        editbulkError = null;
        editbulkFeatureSource = null;

        if (entityManager == null) {
            entityManager = Stripersist.getEntityManager();
        }
    }

    private void saveFeaturesAndBuildResponseAndCommitOrRollback() throws Exception {
        Transaction transaction = new DefaultTransaction("edit");

        try {
            validateAndPrepareStore();
            store.setTransaction(transaction);
            saveAllFeaturesAndBuildResponse();
            addAuditTrailLog();
            transaction.commit();
        } catch (IOException e) {
            transaction.rollback();
            throw e;
        } finally {
            transaction.close();
        }

    }

    private void saveAllFeaturesAndBuildResponse() throws Exception {
        for (JSONObject feature : getFeaturesArray()) {
            currentFeature = feature;
            saveCurrentFeatureAndBuildResponse();
        }
    }

    private void validateAndPrepareStore() throws Exception {
        do {
            if (appLayer == null) {
                editbulkError = getBundle().getString("viewer.editfeatureactionbean.1");
                break;
            }
            if (!Authorizations.isAppLayerWriteAuthorized(application, appLayer, context.getRequest(), entityManager)) {
                editbulkError = getBundle().getString("viewer.editfeatureactionbean.2");
                break;
            }

            layer = appLayer.getService().getLayer(appLayer.getLayerName(), entityManager);

            if (layer == null) {
                editbulkError = getBundle().getString("viewer.editfeatureactionbean.3");
                break;
            }

            if (layer.getFeatureType() == null) {
                editbulkError = getBundle().getString("viewer.editfeatureactionbean.4");
                break;
            }

            editbulkFeatureSource = layer.getFeatureType().openGeoToolsFeatureSource();

            if (!(editbulkFeatureSource instanceof SimpleFeatureStore)) {
                editbulkError = getBundle().getString("viewer.editfeatureactionbean.5");
                break;
            }
            store = (SimpleFeatureStore) editbulkFeatureSource;
        } while (false);
    }

    private void saveCurrentFeatureAndBuildResponse() throws IOException, ParseException {
        do {
            if (!this.isFeatureWriteAuthorized(appLayer, currentFeature, context.getRequest())) {
                editbulkError = getBundle().getString("viewer.editfeatureactionbean.6");
                break;
            }
            String fid = currentFeature.optString(FID, null);

            if (fid == null) {
                editbulkError = getBundle().getString("viewer.editbulkfeatureactionbean.1");
                break;
            } else {
                saveFeatureToStore(fid);
                editbulkResponse.put(FID, fid);
            }

            editbulkResponse.put("success", Boolean.TRUE);
        } while (false);
    }

    private JSONObject getFirstFeature() {
        return this.getFeaturesArray()[0];
    }

    private void saveFeatureToStore(String fid) throws IOException, ParseException {
        Pair<String[], Object[]> attributesAndValues = buildAttributesAndValues();

        String[] attributes = attributesAndValues.getKey();
        Object[] values = attributesAndValues.getValue();

        log.debug(String.format("Modifying features source #%d fid=%s, attributes=%s, values=%s",
                layer.getFeatureType().getId(),
                fid,
                attributes.toString(),
                values.toString()));

        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Filter filter = ff.id(new FeatureIdImpl(fid));

        store.modifyFeatures(attributes, values, filter);
    }

    private Pair<String[], Object[]> buildAttributesAndValues() throws ParseException {
        List<String> attributes = new ArrayList<String>();
        List values = new ArrayList();
        for (Iterator<String> it = currentFeature.keys(); it.hasNext(); ) {
            String attribute = it.next();
            if (!FID.equals(attribute)) {

                SimpleFeatureType schema = store.getSchema();
                AttributeDescriptor ad = schema.getDescriptor(attribute);

                if (ad != null) {
                    if (!isAttributeUserEditingDisabled(attribute)) {
                        attributes.add(attribute);

                        if (ad.getType() instanceof GeometryType) {
                            String wkt = currentFeature.getString(ad.getLocalName());
                            Geometry g = null;
                            if (wkt != null) {
                                g = new WKTReader().read(wkt);
                            }
                            values.add(g);
                        } else if (ad.getType().getBinding().getCanonicalName().equals("byte[]")) {
                            Object ba = currentFeature.get(attribute);
                            values.add(ba);
                        } else {
                            String v = currentFeature.optString(attribute);
                            values.add(StringUtils.defaultIfBlank(v, null));
                        }
                    } else {
                        log.info(String.format("Attribute \"%s\" not user editable; ignoring", attribute));
                    }
                } else {
                    log.warn(String.format("Attribute \"%s\" not in features type; ignoring", attribute));
                }
            }
        }

        String[] arrAttributes = attributes.toArray(new String[]{});
        Object[] arrValues = values.toArray(new String[]{});
        return new Pair<String[], Object[]>(arrAttributes, arrValues);
    }

    /**
     * Check that if {@code disableUserEdit} flag is set on the attribute.
     *
     * @param attrName attribute to check
     * @return {@code true} when the configured attribute is flagged as
     * "readOnly"
     */
    protected boolean isAttributeUserEditingDisabled(String attrName) {
        ConfiguredAttribute attribute = this.getAppLayer().getAttribute(this.getLayer().getFeatureType(), attrName);
        return (attribute == null) ? false : attribute.isDisableUserEdit();
    }

    private boolean isFeatureWriteAuthorized(ApplicationLayer appLayer, JSONObject jsonFeature, HttpServletRequest request) {
        if (appLayer.getDetails() != null && appLayer.getDetails().containsKey("editfeature.usernameAttribute")) {
            String attr = appLayer.getDetails().get("editfeature.usernameAttribute").getValue();

            String featureUsername = jsonFeature.optString(attr);
            return featureUsername != null && featureUsername.equals(request.getRemoteUser());
        }
        return true;
    }

    /**
     * Method to query the datastore with a dummy query, containing the username. This is used for an audittrail.
     * A query is composed using the
     * first attribute from the type, and constructing a Query with it:
     * {@code <firstattribute> = 'username is <username>'}.
     */
    private void addAuditTrailLog() {
        try {
            List<AttributeDescriptor> attributeDescriptors = store.getSchema().getAttributeDescriptors();
            String typeName = null;
            for (AttributeDescriptor ad : attributeDescriptors) {
                // Get an attribute of type string. This because the username is almost always a string, and passing it to a Integer/Double will result in a invalid
                // query which will not log the passed values (possibly because the use of geotools).
                if (ad.getType().getBinding() == String.class) {
                    typeName = ad.getLocalName();
                    break;
                }
            }

            if (typeName == null) {
                typeName = store.getSchema().getAttributeDescriptors().get(0).getLocalName();
                log.warn("Audittrail: cannot find attribute of type double/integer or string. Take the first attribute.");
            }
            String username = context.getRequest().getRemoteUser();
            String[] dummyValues = new String[]{"a", "b"}; // use these values for creating a statement which will always fail: attribute1 = a AND attribute1 = b.
            String valueToInsert = "username = " + username;
            store.modifyFeatures(typeName, valueToInsert, CQL.toFilter(typeName + " = '" + dummyValues[0] + "' and " + typeName + " = '" + dummyValues[1] + "'"));

        } catch (Exception ex) {
            // Swallow all exceptions, because this inherently fails. It's only use is to log the application username, so it can be matched (via the database process id
            // to the following insert/update/delete statement.
        }
    }

    JSONObject[] getFeaturesArray() {
        JSONArray jsonFeatures = new JSONArray(this.features);
        JSONObject[] list = new JSONObject[jsonFeatures.length()];
        for (int i = 0; i < jsonFeatures.length(); i++) {
            list[i] = jsonFeatures.getJSONObject(i);
        }
        return list;
    }
}
