/*
 * Copyright (C) 2015 B3Partners B.V.
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
package nl.b3p.viewer.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.Persistence;

import nl.b3p.viewer.config.app.*;
import nl.b3p.viewer.config.metadata.Metadata;
import nl.b3p.viewer.config.services.FeatureSource;
import nl.b3p.viewer.config.services.GeoService;
import nl.b3p.viewer.config.services.Layer;
import nl.b3p.viewer.config.services.SimpleFeatureType;
import nl.b3p.viewer.config.services.WFSFeatureSource;
import nl.b3p.viewer.config.services.WMSService;
import nl.b3p.viewer.util.databaseupdate.ScriptRunner;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;

/**
 * utility methoden voor unit tests.
 *
 * @author Mark Prins mark@b3partners.nl
 * @author Meine Toonen meinetoonen@b3partners.nl
 */
public abstract class TestUtil extends LoggingTestUtil {

    protected EntityManager entityManager;

    protected static int TEST_VERSION_NUMBER = 666;
    
    public Long applicationId = 1L;
    public static String originalVersion = null;

    public ApplicationLayer testAppLayer;
    public Level testLevel;
    public StartLayer testStartLayer;
    public StartLevel testStartLevel;
    public ConfiguredComponent testComponent;
    public Application app;

    private static final Log log = LogFactory.getLog(TestUtil.class);

    protected String layerName = "Test_omgeving:unittest";
    protected String geometryAttribute = "geom";
    protected String url = "https://flamingo5.b3p.nl/geoserver/Test_omgeving/ows";
    protected List<ConfiguredAttribute> attributes = new ArrayList<ConfiguredAttribute>();

    /**
     * initialisatie van EntityManager {@link #entityManager} en starten
     * transactie.
     *
     * @throws Exception if any
     *
     * @see #entityManager
     */
    @Before
    public void setUp() throws Exception {
        final String persistenceUnit = System.getProperty("test.persistence.unit");
        Map config = new HashMap();
        String testname = testName.getMethodName();
        testname = testname.replaceAll(":", "-");
        testname = testname.replaceAll(" ", "");
        String randomizer = RandomStringUtils.randomAlphabetic(8);
        config.put("javax.persistence.jdbc.url", "jdbc:hsqldb:file:./target/unittest-hsqldb_"+ testname + "_" + randomizer + "/db;shutdown=true");
        entityManager = Persistence.createEntityManagerFactory(persistenceUnit,config).createEntityManager();
        if(!entityManager.getTransaction().isActive()){
            entityManager.getTransaction().begin();
        }
        loadTestData();

        if(!entityManager.getTransaction().isActive()){
            entityManager.getTransaction().begin();
        }
    }

    @After
    public void closeTransaction(){
         if(entityManager.getTransaction().isActive()){
            entityManager.getTransaction().commit();
        }
  
        if (entityManager.isOpen()) {
            entityManager.close();
        }
    }

    /**
     * Helper function for testing.
     * @param <T> Type of entity to persist
     * @param entity The entity to persist
     * @param clazz Class to persist
     */
    public <T> void persistEntityTest(T entity, Class<T> clazz){
        entityManager.persist(entity);
        entityManager.getTransaction().commit();
        entityManager.getTransaction().begin();
    }
    
    /**
     * Helper function for initializing data.
     * @throws java.net.URISyntaxException Thrown when the testdata cannot be found
     * @throws java.io.IOException Thrown when the testdata cannot be found
     * @throws java.sql.SQLException Thrown when the testdata cannot be loaded
     */
    public void loadTestData() throws URISyntaxException, IOException, SQLException {

        Application app = entityManager.find(Application.class, applicationId);
        if( app == null) {
            Reader f = new InputStreamReader(TestUtil.class.getResourceAsStream("testdata.sql"));
            executeScript(f);
        }
        Metadata version = entityManager.createQuery("From Metadata where configKey = :v", Metadata.class).setParameter("v", Metadata.DATABASE_VERSION_KEY).getSingleResult();
        originalVersion = version.getConfigValue();
    }
    /**
     * Helper function for initializing data.
     * @param f The reader containing the scripts to be executed
     * @throws java.io.IOException Thrown when the testdata cannot be found
     * @throws java.sql.SQLException Thrown when the testdata cannot be loaded
     */
    public void executeScript(Reader f) throws IOException, SQLException {
        Connection conn = null;

        try {
            Session session = (Session) entityManager.getDelegate();
            conn = (Connection) session.connection();
            ScriptRunner sr = new ScriptRunner(conn, true, true);
            sr.runScript(f, false);
            conn.commit();
            entityManager.flush();
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }
    /**
     * Helper function for initializing data.
     * @param addToStartmap Do the layers/levels have to be added to the startmap?
     */
    public void initData( boolean addToStartmap) {
        app = new Application();
        app.setName("testapp");
        app.setVersion("154");
        app.getReaders().add("pietje");
        app.getReaders().add("puk");
        
        persistEntityTest(app, Application.class);
        
        Level root = new Level();
        root.setName("root");
        app.setRoot(root);
        entityManager.persist(app);
        persistEntityTest(root, Level.class);

        testLevel = new Level();
        testLevel.setName("testLevel");
        testLevel.setParent(root);
        root.getChildren().add(testLevel);
        persistEntityTest(testLevel, Level.class);

        testStartLevel = new StartLevel();
        testStartLevel.setApplication(app);
        testStartLevel.setLevel(testLevel);
        testLevel.getStartLevels().put(app, testStartLevel);
        app.getStartLevels().add(testStartLevel);
        persistEntityTest(testStartLevel, StartLevel.class);

        if (addToStartmap) {
            
            FeatureSource fs = new WFSFeatureSource();
            fs.setName("pietje");

            fs.setUrl(url);
            persistEntityTest(fs, FeatureSource.class);
            
            SimpleFeatureType sft = new SimpleFeatureType();
            sft.setTypeName(layerName);
            sft.setGeometryAttribute(geometryAttribute);
            sft.setFeatureSource(fs);
            
            persistEntityTest(sft, SimpleFeatureType.class);
            fs.getFeatureTypes().add(sft);
            persistEntityTest(fs, FeatureSource.class);
            
            Layer rlayer = new Layer();
            rlayer.setName("root");
            persistEntityTest(rlayer, Layer.class);
            
            Layer l = new Layer();
            l.setName(layerName);
            l.setFeatureType(sft);
            l.setParent(rlayer);
            persistEntityTest(l, Layer.class);
            
            rlayer.getChildren().add(l);
            persistEntityTest(rlayer, Layer.class);
            
            
            GeoService gs = new WMSService();
            gs.setName("gsname");
            gs.setUrl(url);
            gs.setTopLayer(rlayer);
            
            persistEntityTest(gs, GeoService.class);
            
            rlayer.setService(gs);
            l.setService(gs);
            
            testAppLayer = new ApplicationLayer();
            testAppLayer.setLayerName(layerName);
            testAppLayer.setService(gs);
            testAppLayer.setAttributes(attributes);

            testLevel.getLayers().add(testAppLayer);

            testStartLayer = new StartLayer();
            testStartLayer.setApplicationLayer(testAppLayer);
            testStartLayer.setApplication(app);
            testStartLayer.setSelectedIndex(16);
            app.getStartLayers().add(testStartLayer);

            testAppLayer.getStartLayers().put(app, testStartLayer);
            persistEntityTest(testAppLayer, ApplicationLayer.class);

            testStartLevel.setSelectedIndex(9);
            entityManager.persist(testStartLevel);
            
            entityManager.persist(testAppLayer);
            entityManager.persist(app);

            persistEntityTest(testStartLayer, StartLayer.class);
        }

        testComponent = new ConfiguredComponent();
        testComponent.setApplication(app);
        testComponent.setClassName("viewer.components.Bookmark");
        testComponent.setConfig("{value: 'aapnootmies'}");
        testComponent.setName("testClassName1");
        app.getComponents().add(testComponent);
        persistEntityTest(testComponent, ConfiguredComponent.class);

        entityManager.getTransaction().commit();
        entityManager.getTransaction().begin();
    }
}
