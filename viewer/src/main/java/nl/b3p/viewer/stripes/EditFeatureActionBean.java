/*
 * Copyright (C) 2012-2013 B3Partners B.V.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.b3p.viewer.stripes;

import net.sourceforge.stripes.controller.LifecycleStage;
import nl.b3p.viewer.util.AuditTrailLogger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.viewer.audit.AuditMessageObject;
import nl.b3p.viewer.audit.Auditable;
import nl.b3p.viewer.config.app.Application;
import nl.b3p.viewer.config.app.ApplicationLayer;
import nl.b3p.viewer.config.security.Authorizations;
import nl.b3p.viewer.config.services.FeatureTypeRelation;
import nl.b3p.viewer.config.services.Layer;
import nl.b3p.viewer.config.services.SimpleFeatureType;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureSource;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.filter.text.cql2.CQL;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.FeatureId;
import org.stripesstuff.stripersist.Stripersist;

/**
 *
 * @author Matthijs Laan
 */
@UrlBinding("/action/feature/edit")
@StrictBinding
public class EditFeatureActionBean extends LocalizableApplicationActionBean implements Auditable {
    private static final Log log = LogFactory.getLog(EditFeatureActionBean.class);

    private static final String FID = FeatureInfoActionBean.FID;

    private ActionBeanContext context;

    @Validate
    private Application application;

    @Validate
    private String feature;

    @Validate
    private ApplicationLayer appLayer;

    protected Layer layer;

    protected SimpleFeatureStore store;

    protected JSONObject jsonFeature;

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

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
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

    public JSONObject getJsonFeature() {
        return jsonFeature;
    }
    
    public void setJsonFeature(JSONObject jsonFeature){
        this.jsonFeature = jsonFeature;
    }

    public Layer getLayer() {
        return layer;
    }

    public String getFID() {
        return FID;
    }

    public AuditMessageObject getAuditMessageObject() {
        return this.auditMessageObject;
    }
    //</editor-fold>


    @Before(stages = LifecycleStage.EventHandling)
    public void initAudit(){
        auditMessageObject = new AuditMessageObject();
    }

    @DefaultHandler
    public Resolution edit() throws JSONException {
        JSONObject response = editResponse();
        return new StreamingResolution("application/json", new StringReader(response.toString(4)));
    }

    public JSONObject editResponse() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("success", Boolean.FALSE);
        String error = null;

        FeatureSource fs = null;
        EntityManager em = getEntityManager();
        try {
            do {
                if(appLayer == null) {
                    error = getBundle().getString("viewer.editfeatureactionbean.1");
                    break;
                }
                if(!Authorizations.isAppLayerWriteAuthorized(application, appLayer, context.getRequest(), em)) {
                    error = getBundle().getString("viewer.editfeatureactionbean.2");
                    break;
                }

                layer = appLayer.getService().getLayer(appLayer.getLayerName(), em);

                if(layer == null) {
                    error = getBundle().getString("viewer.editfeatureactionbean.3");
                    break;
                }

                if(layer.getFeatureType() == null) {
                    error =getBundle().getString("viewer.editfeatureactionbean.4");
                    break;
                }

                fs = layer.getFeatureType().openGeoToolsFeatureSource();

                if(!(fs instanceof SimpleFeatureStore)) {
                    error = getBundle().getString("viewer.editfeatureactionbean.5");
                    break;
                }
                store = (SimpleFeatureStore)fs;
                addAuditTrailLog();
                jsonFeature = getJsonFeature(feature);
                if (!this.isFeatureWriteAuthorized(appLayer,jsonFeature,context.getRequest())){
                     error = getBundle().getString("viewer.editfeatureactionbean.6");
                     break;
                }
                String fid = jsonFeature.optString(FID, null);

                if(fid == null) {
                    json.put(FID, addNewFeature());
                } else {
                    editFeature(fid);
                    json.put(FID, fid);
                }

                json.put("success", Boolean.TRUE);
            } while(false);
        } catch(Exception e) {
            log.error("Exception editing feature",e);

            error = e.toString();
            if(e.getCause() != null) {
                error += "; cause: " + e.getCause().toString();
            }
        } finally {
            if(fs != null) {
                fs.getDataStore().dispose();
            }
        }

        if(error != null) {
            json.put("error", error);
            log.error("Returned error message editing feature: " + error);
        }

        this.auditMessageObject.addMessage(json);

        return json;
    }

    protected EntityManager getEntityManager() {
        return Stripersist.getEntityManager();
    }

    public Resolution saveRelatedFeatures() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("success", Boolean.FALSE);
        String error = null;

        FeatureSource fs = null;
        EntityManager em = getEntityManager();
        if (appLayer == null) {
            error = getBundle().getString("viewer.editfeatureactionbean.7");

        }
        if (!Authorizations.isAppLayerWriteAuthorized(application, appLayer, context.getRequest(), em)) {
            error = getBundle().getString("viewer.editfeatureactionbean.8");

        }

        layer = appLayer.getService().getLayer(appLayer.getLayerName(), em);

        if (layer.getFeatureType().hasRelations()) {
            String label;
            for (FeatureTypeRelation rel : layer.getFeatureType().getRelations()) {
                if (rel.getType().equals(FeatureTypeRelation.RELATE)) {
                    try {
                        SimpleFeatureType fType = rel.getForeignFeatureType();
                        label = fType.getDescription() == null ? fType.getTypeName() : fType.getDescription();

                        fs = fType.openGeoToolsFeatureSource(5000);
                        store = (SimpleFeatureStore) fs;
                        jsonFeature = new JSONObject(feature);
                        String fid = jsonFeature.optString(FID, null);
                        if (fid == null || fid.equals("")) {
                            json.put(FID, addNewFeature());
                        } else {
                            jsonFeature.remove("rel_id");
                            //editFeature(fid);
                            Transaction transaction = new DefaultTransaction("edit");
                            store.setTransaction(transaction);

                            FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
                            Filter filter = ff.id(new FeatureIdImpl(fid));

                            List<String> attributes = new ArrayList<String>();
                            List values = new ArrayList();
                            for (Iterator<String> it = jsonFeature.keys(); it.hasNext();) {
                                String attribute = it.next();
                                if (!FID.equals(attribute)) {

                                    AttributeDescriptor ad = store.getSchema().getDescriptor(attribute);

                                    if (ad != null) {
                                        attributes.add(attribute);
                                        //System.out.println(attribute);
                                        String v = jsonFeature.getString(attribute);
                                        //System.out.println(v);
                                        values.add(StringUtils.defaultIfBlank(v, null));
                                    }
                                }
                            }

                            log.debug(String.format("Modifying feature source #%d fid=%s, attributes=%s, values=%s",
                                    layer.getFeatureType().getId(),
                                    fid,
                                    attributes.toString(),
                                    values.toString()));

                            try {
                                store.modifyFeatures(attributes.toArray(new String[]{}), values.toArray(), filter);

                                transaction.commit();
                            } catch (Exception e) {
                                transaction.rollback();
                                throw e;
                            } finally {
                                transaction.close();
                            }

                            json.put(FID, fid);
                        }
                        json.put("success", Boolean.TRUE);
                    } catch (Exception ex) {
                        log.error("cannot save relatedFeature Exception: ",ex);
                    }finally{
                        if(fs != null){
                            fs.getDataStore().dispose();
                        }
                    }
                }
            }
        }
        this.auditMessageObject.addMessage(json);

        return new StreamingResolution("application/json", new StringReader(json.toString(4)));
    }   
    
    public Resolution delete() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("success", Boolean.FALSE);
        String error = null;

        FeatureSource fs = null;

        EntityManager em = getEntityManager();
        try {
            do {
                if(appLayer == null) {
                    error = getBundle().getString("viewer.editfeatureactionbean.9");
                    break;
                }
                if(!Authorizations.isAppLayerWriteAuthorized(application, appLayer, context.getRequest(), em)) {
                    error = getBundle().getString("viewer.editfeatureactionbean.10");
                    break;
                }

                layer = appLayer.getService().getLayer(appLayer.getLayerName(), em);

                if(layer == null) {
                    error = getBundle().getString("viewer.editfeatureactionbean.11");
                    break;
                }
                if (!Authorizations.isLayerGeomWriteAuthorized(layer, context.getRequest(), em)) {
                    error = getBundle().getString("viewer.editfeatureactionbean.12");
                    break;
                }

                if(layer.getFeatureType() == null) {
                    error ="No feature type";
                    break;
                }

                fs = layer.getFeatureType().openGeoToolsFeatureSource();

                if(!(fs instanceof SimpleFeatureStore)) {
                    error = getBundle().getString("viewer.editfeatureactionbean.13");
                    break;
                }
                store = (SimpleFeatureStore)fs;

                jsonFeature = new JSONObject(feature);
                if (!this.isFeatureWriteAuthorized(appLayer,jsonFeature,context.getRequest())){
                     error = getBundle().getString("viewer.editfeatureactionbean.14");
                     break;
                }
                String fid = jsonFeature.optString(FID, null);

                if(fid == null) {
                    error = getBundle().getString("viewer.editfeatureactionbean.15");
                    break;
                } else {
                    deleteFeature(fid);
                }

                json.put("success", Boolean.TRUE);
            } while(false);
        } catch(Exception e) {
            log.error(String.format("Exception editing feature", e));

            error = e.toString();
            if(e.getCause() != null) {
                error += "; cause: " + e.getCause().toString();
            }
        } finally {
            if(fs != null) {
                fs.getDataStore().dispose();
            }
        }

        if(error != null) {
            json.put("error", error);
            log.error("Returned error message editing feature: " + error);
        }

        this.auditMessageObject.addMessage(json);
        return new StreamingResolution("application/json", new StringReader(json.toString(4)));
    }
    
    protected JSONObject getJsonFeature(String feature){
        return new JSONObject(feature);        
    }

    public Resolution removeRelatedFeatures() throws JSONException, Exception {
        JSONObject json = new JSONObject();
        json.put("success", Boolean.FALSE);
        String error = null;
        FeatureSource fs = null;
        EntityManager em = getEntityManager();
        
        if (appLayer == null) {
            error = getBundle().getString("viewer.editfeatureactionbean.16");
        }
        if (!Authorizations.isAppLayerWriteAuthorized(application, appLayer, context.getRequest(), em)) {
            error = getBundle().getString("viewer.editfeatureactionbean.17");
        }

        layer = appLayer.getService().getLayer(appLayer.getLayerName(), em);
        if (layer.getFeatureType().hasRelations()) {
            String label;
            for (FeatureTypeRelation rel : layer.getFeatureType().getRelations()) {
                if (rel.getType().equals(FeatureTypeRelation.RELATE)) {
                    SimpleFeatureType fType = rel.getForeignFeatureType();
                    label = fType.getDescription() == null ? fType.getTypeName() : fType.getDescription();
                    fs = fType.openGeoToolsFeatureSource(5000);
                    store = (SimpleFeatureStore) fs;
                    jsonFeature = new JSONObject(feature);
                    String fid = jsonFeature.optString(FID, null);
                    if (fid == null || fid.equals("")) {
                        error = getBundle().getString("viewer.editfeatureactionbean.18");
                        break;
                    } else {
                        deleteFeature(fid);
                    }
                    json.put("success", Boolean.TRUE);
                }
            }
            fs.getDataStore().dispose();
        }
        this.auditMessageObject.addMessage(json);
        return new StreamingResolution("application/json", new StringReader(json.toString(4)));
    }
    
    protected String addNewFeature() throws Exception {

        SimpleFeature f = DataUtilities.template(store.getSchema());

        Transaction transaction = new DefaultTransaction("create");
        store.setTransaction(transaction);

        for(AttributeDescriptor ad: store.getSchema().getAttributeDescriptors()) {
            if(ad.getType() instanceof GeometryType) {
                String wkt = jsonFeature.optString(ad.getLocalName(), null);
                Geometry g = null;
                if(wkt != null) {
                    g = new WKTReader().read(wkt);
                }
                f.setDefaultGeometry(g);
            } else {
                String v = jsonFeature.optString(ad.getLocalName());
                f.setAttribute(ad.getLocalName(), StringUtils.defaultIfBlank(v, null));
            }
        }

        log.debug(String.format("Creating new feature in feature source source #%d: %s",
                layer.getFeatureType().getId(),
                f.toString()));

        try {
            List<FeatureId> ids = store.addFeatures(DataUtilities.collection(f));

            transaction.commit();
            return ids.get(0).getID();
        } catch (Exception e) {
            transaction.rollback();
            throw e;
        } finally {
            transaction.close();
        }
    }

    protected void deleteFeature(String fid) throws IOException, Exception {
        Transaction transaction = new DefaultTransaction("edit");
        store.setTransaction(transaction);

        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Filter filter = ff.id(new FeatureIdImpl(fid));

        try {
            store.removeFeatures(filter);
            transaction.commit();
        } catch (Exception e) {
            transaction.rollback();
            throw e;
        } finally {
            transaction.close();
        }
    }

    protected void editFeature(String fid) throws Exception {
        Transaction transaction = new DefaultTransaction("edit");
        store.setTransaction(transaction);

        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Filter filter = ff.id(new FeatureIdImpl(fid));

        List<String> attributes = new ArrayList<String>();
        List values = new ArrayList();
        for(Iterator<String> it = jsonFeature.keys(); it.hasNext();) {
            String attribute = it.next();
            if(!FID.equals(attribute)) {

                AttributeDescriptor ad = store.getSchema().getDescriptor(attribute);

                if (ad != null) {
                    if (!isAttributeUserEditingDisabled(attribute)) {
                        attributes.add(attribute);

                        if (ad.getType() instanceof GeometryType) {
                            String wkt = jsonFeature.getString(ad.getLocalName());
                            Geometry g = null;
                            if (wkt != null) {
                                g = new WKTReader().read(wkt);
                            }
                            values.add(g);
                        } else if(ad.getType().getBinding().getCanonicalName().equals("byte[]")){
                            Object ba = jsonFeature.get(attribute);
                            values.add(ba);
                        } else {
                            String v = jsonFeature.optString(attribute);
                            values.add(StringUtils.defaultIfBlank(v, null));
                        }
                    } else {
                        log.info(String.format("Attribute \"%s\" not user editable; ignoring", attribute));
                    }
                } else {
                    log.warn(String.format("Attribute \"%s\" not in feature type; ignoring", attribute));
                }
            }
        }

        log.debug(String.format("Modifying feature source #%d fid=%s, attributes=%s, values=%s",
                layer.getFeatureType().getId(),
                fid,
                attributes.toString(),
                values.toString()));

        try {
            store.modifyFeatures(attributes.toArray(new String[] {}), values.toArray(), filter);

            transaction.commit();
        } catch (Exception e) {
            transaction.rollback();
            throw e;
       } finally {
            transaction.close();
        }
    }

    /**
     * Check that if {@code disableUserEdit} flag is set on the attribute.
     *
     * @param attrName attribute to check
     * @return {@code true} when the configured attribute is flagged as
     * "readOnly"
     */
    protected boolean isAttributeUserEditingDisabled(String attrName) {
        return this.getAppLayer().getAttribute(this.getLayer().getFeatureType(), attrName).isDisableUserEdit();
    }

    private boolean isFeatureWriteAuthorized(ApplicationLayer appLayer, JSONObject jsonFeature, HttpServletRequest request) {
        if (appLayer.getDetails()!=null && appLayer.getDetails().containsKey("editfeature.usernameAttribute")){
            String attr=appLayer.getDetails().get("editfeature.usernameAttribute").getValue();

            String featureUsername=jsonFeature.optString(attr);
            if (featureUsername!=null && featureUsername.equals(request.getRemoteUser())){
                return true;
            }else{
                return false;
            }
        }
        return true;
    }

    private void addAuditTrailLog() {
        AuditTrailLogger logger = new AuditTrailLogger();
        logger.setContext(context);
        logger.setStore(store);
        logger.setLog(log);
        logger.addAuditTrailLog();
    }
}
