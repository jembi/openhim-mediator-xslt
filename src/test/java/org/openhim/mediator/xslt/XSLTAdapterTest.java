/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.xslt;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.*;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.testing.MockHTTPConnector;
import org.openhim.mediator.engine.testing.TestingUtils;
import org.xml.sax.SAXException;
import scala.concurrent.duration.FiniteDuration;

import static org.junit.Assert.*;

public class XSLTAdapterTest {
    private static class MockUpstreamServer extends MockHTTPConnector {
        @Override
        public String getResponse() {
            return responseXML;
        }

        @Override
        public Integer getStatus() {
            return HttpStatus.SC_OK;
        }

        @Override
        public Map<String, String> getHeaders() {
            return Collections.singletonMap("content-type", "application/xml");
        }

        @Override
        public void executeOnReceive(MediatorHTTPRequest req) {
            assertEquals("http://localhost:4000/mock", req.getUri());

            try {
                Diff diff = new Diff(requestXMLTransformed, req.getBody());
                assertTrue(diff.toString(), diff.identical());
            } catch (SAXException|IOException ex) {
                ex.printStackTrace();
                fail();
            }
        }
    }

    final MediatorConfig testConfig = new MediatorConfig("mediator-xslt", "localhost", 9758);

    private static String xslt;
    private static String requestXML;
    private static String requestXMLTransformed;
    private static String responseXML;
    private static String responseXMLTransformed;

    static ActorSystem system;


    @BeforeClass
    public static void setup() throws IOException {
        system = ActorSystem.create();

        xslt = IOUtils.toString(XSLTAdapterTest.class.getClassLoader().getResourceAsStream("transform.xslt"));
        requestXML = IOUtils.toString(XSLTAdapterTest.class.getClassLoader().getResourceAsStream("request.xml"));
        requestXMLTransformed = IOUtils.toString(XSLTAdapterTest.class.getClassLoader().getResourceAsStream("request_transformed.xml"));
        responseXML = IOUtils.toString(XSLTAdapterTest.class.getClassLoader().getResourceAsStream("response.xml"));
        responseXMLTransformed = IOUtils.toString(XSLTAdapterTest.class.getClassLoader().getResourceAsStream("response_transformed.xml"));
        XMLUnit.setIgnoreWhitespace(true);
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    @Before
    public void before() throws Exception {
        Map<String, String> transformation = new HashMap<>();
        transformation.put("endpoint", "/test");
        transformation.put("upstream", "http://localhost:4000/mock");
        transformation.put("requestTransform", xslt);
        transformation.put("responseTransform", xslt);
        testConfig.getDynamicConfig().put("transforms", Collections.singletonList(transformation));
    }

    @After
    public void after() throws Exception {
    }

    @Test
    public void testMediatorHTTPRequest() throws Exception {
        new JavaTestKit(system) {{
            TestingUtils.launchMockHTTPConnector(system, testConfig.getName(), MockUpstreamServer.class);

            try {
                final ActorRef orchestrator = system.actorOf(Props.create(XSLTAdapter.class, testConfig));

                MediatorHTTPRequest POST_Request = new MediatorHTTPRequest(
                        getRef(),
                        getRef(),
                        "unit-test",
                        "POST",
                        "http",
                        null,
                        null,
                        "/test",
                        requestXML,
                        Collections.singletonMap("Content-Type", "application/xml"),
                        Collections.<String, String>emptyMap()
                );

                orchestrator.tell(POST_Request, getRef());

                FinishRequest response = expectMsgClass(
                        FiniteDuration.apply(60, TimeUnit.SECONDS),
                        FinishRequest.class
                );

                assertNotNull(response);
                assertEquals(new Integer(200), response.getResponseStatus());

                Diff diff = new Diff(responseXMLTransformed, response.getResponse());
                assertTrue(diff.toString(), diff.identical());
            } finally {
                TestingUtils.clearRootContext(system, testConfig.getName());
            }
        }};
    }
}
