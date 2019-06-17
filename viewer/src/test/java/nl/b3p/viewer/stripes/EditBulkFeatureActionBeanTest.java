package nl.b3p.viewer.stripes;

import net.sourceforge.stripes.action.Resolution;
import nl.b3p.viewer.util.TestActionBeanContext;
import nl.b3p.viewer.util.TestUtil;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import static nl.b3p.viewer.stripes.FeatureInfoActionBean.FID;


@RunWith(Enclosed.class)
public class EditBulkFeatureActionBeanTest {
    public static class Scaffolding {
        private static final String FIXTURE_JSON_ARRAY_THREE_FEATURES = "[" +
                "{\"" + FID + "\": 1,\"attr1\": \"val1\"}," +
                "{\"" + FID + "\": 2,\"attr1\": \"val2\"}," +
                "{\"" + FID + "\": 3,\"attr1\": \"val3\"}" +
                "]";
        private EditBulkFeatureActionBean instance;

        @Before
        public void init() {
            instance = new EditBulkFeatureActionBean();
        }

        @Test
        public void testCorrectNumberOfFeaturesGetParsed() {
            instance.setFeatures(FIXTURE_JSON_ARRAY_THREE_FEATURES);
            Assert.assertEquals(3, instance.getFeaturesArray().length);
        }

        @Test
        public void testFirstFeatureIsCorrect() {
            instance.setFeatures(FIXTURE_JSON_ARRAY_THREE_FEATURES);
            JSONObject feature = instance.getFeaturesArray()[0];
            Assert.assertEquals(1, feature.get("__fid"));
            Assert.assertEquals("val1", feature.get("attr1"));
        }
    }

    public static class WithGeoserver extends TestUtil {

        private EditBulkFeatureActionBean instance;

        public WithGeoserver() {
            url = "http://localhost:8600/geoserver/ows";
            layerName = "flamingo:meaningless_unittest_table";
            geometryAttribute = "geom";
        }

        @Before
        public void setUp() throws Exception {
            super.setUp();
            initData(true);
            buildInstance();
            simulateBeforeEventHandling();
        }

        private void buildInstance() {
            instance = new EditBulkFeatureActionBean();
            instance.setContext(new TestActionBeanContext());
            instance.setEntityManager(entityManager);
            instance.setApplication(app);
            instance.setAppLayer(testAppLayer);
            instance.setFeatures("[" +
                    "{\"__fid\": \"meaningless_unittest_table.1\", \"codeword\": \"ALPHA!\"}," +
                    "{\"__fid\": \"meaningless_unittest_table.2\", \"codeword\": \"BRAVO!\"}," +
                    "{\"__fid\": \"meaningless_unittest_table.3\", \"codeword\": \"CHARLIE!\"}" +
                    "]");
        }

        private void simulateBeforeEventHandling() {
            instance.initAudit();
        }

        @Test
        public void testSuccessTrueInJsonResponse() {
            JSONObject jsonResponse = instance.editbulkJsonResponse();
            Assert.assertTrue(jsonResponse.getBoolean("success"));
        }

        @Test
        public void testStreamingResolution() {
            Resolution resolution = instance.editbulk();
            Assert.assertNotNull(resolution);
        }
    }
}
