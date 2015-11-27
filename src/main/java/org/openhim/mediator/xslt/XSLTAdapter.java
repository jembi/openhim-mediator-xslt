/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.xslt;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XSLTAdapter extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final MediatorConfig config;
    private ActorRef requestHandler;
    private Map<String, String> transform;

    private static final String DEFAULT_MSG = "Welcome to the OpenHIM XSLT mediator! You can add your own transforms on the OpenHIM-console via the mediators page";


    public XSLTAdapter(MediatorConfig config) {
        this.config = config;
    }

    private String transformDocument(String xslt, String doc) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Source xsltSource = new StreamSource(IOUtils.toInputStream(xslt));
            Transformer transformer = factory.newTransformer(xsltSource);

            Source text = new StreamSource(IOUtils.toInputStream(doc));
            StringWriter sw = new StringWriter();
            transformer.transform(text, new StreamResult(sw));
            return sw.toString();

        } catch (TransformerException ex) {
            requestHandler.tell(new ExceptError(ex), getSelf());
            return null;
        }
    }

    private void lookupTransform(String path) {
        if (config.getDynamicConfig().containsKey("transforms")) {
            for (Map<String, String> tr : (List<Map<String, String>>)config.getDynamicConfig().get("transforms")) {
                if (path.equals(tr.get("endpoint"))) {
                    transform = tr;
                }
            }
        }

        if (transform==null) {
            FinishRequest finishRequest = new FinishRequest("No transforms bound to endpoint " + path, "text/plain", HttpStatus.SC_BAD_REQUEST);
            requestHandler.tell(finishRequest, getSelf());
        }
    }

    private Map<String, String> copyHeaders(Map<String, String> headers) {
        Map<String, String> copy = new HashMap<>();
        copy.put("content-type", headers.get("content-type"));
        copy.put("authorization", headers.get("authorization"));
        copy.put("x-openhim-transactionid", headers.get("x-openhim-transactionid"));
        copy.put("x-forwarded-for", headers.get("x-forwarded-for"));
        copy.put("x-forwarded-host", headers.get("x-forwarded-host"));
        return copy;
    }

    private void forwardRequestUpstream(MediatorHTTPRequest origRequest, String content) {
        log.info("Forwarding request to " + transform.get("upstream"));

        MediatorHTTPRequest request = new MediatorHTTPRequest(
                requestHandler,
                getSelf(),
                "XSLT Transform - Forward to destination",
                origRequest.getMethod(),
                transform.get("upstream"),
                content,
                copyHeaders(origRequest.getHeaders()),
                null
        );

        ActorSelection httpConnector = getContext().actorSelection(config.userPathFor("http-connector"));
        httpConnector.tell(request, getSelf());
    }

    private void processRequest(MediatorHTTPRequest request) {
        if (request.getPath().equals("/default")) {
            FinishRequest fr = new FinishRequest(DEFAULT_MSG, "text/plain", HttpStatus.SC_OK);
            requestHandler.tell(fr, getSelf());
            return;
        }

        lookupTransform(request.getPath());
        if (transform==null) {
            return;
        }

        String content = request.getBody();
        if (transform.containsKey("requestTransform") && !transform.get("requestTransform").trim().isEmpty()) {
            content = transformDocument(transform.get("requestTransform"), content);
            if (content==null) {
                return;
            }
        }

        forwardRequestUpstream(request, content);
    }


    private void processResponse(MediatorHTTPResponse response) {
        if (transform.containsKey("responseTransform") && !transform.get("responseTransform").trim().isEmpty()) {
            String content = transformDocument(transform.get("responseTransform"), response.getBody());
            if (content==null) {
                return;
            }

            FinishRequest fr = new FinishRequest(
                    content, response.getHeaders().get("content-type"), response.getStatusCode()
            );
            requestHandler.tell(fr, getSelf());
        } else {
            requestHandler.tell(response.toFinishRequest(), getSelf());
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) { //incoming request
            requestHandler = ((MediatorHTTPRequest) msg).getRequestHandler();
            processRequest((MediatorHTTPRequest) msg);

        } else if (msg instanceof MediatorHTTPResponse) { //response from upstream server
            processResponse((MediatorHTTPResponse) msg);

        } else {
            unhandled(msg);
        }
    }
}
