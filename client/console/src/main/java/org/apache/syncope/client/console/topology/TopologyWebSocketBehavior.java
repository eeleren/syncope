/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.client.console.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.apache.syncope.client.console.SyncopeConsoleSession;
import org.apache.syncope.client.console.rest.ConnectorRestClient;
import org.apache.syncope.client.console.rest.ResourceRestClient;
import org.apache.syncope.common.lib.to.ConnInstanceTO;
import org.apache.syncope.common.lib.to.ResourceTO;
import org.apache.wicket.Application;
import org.apache.wicket.Component;
import org.apache.wicket.Session;
import org.apache.wicket.ThreadContext;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.ws.api.WebSocketBehavior;
import org.apache.wicket.protocol.ws.api.WebSocketRequestHandler;
import org.apache.wicket.protocol.ws.api.message.TextMessage;
import org.apache.wicket.util.cookies.CookieUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyWebSocketBehavior extends WebSocketBehavior {

    private static final long serialVersionUID = -1653665542635275551L;

    private static final Logger LOG = LoggerFactory.getLogger(TopologyWebSocketBehavior.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, String> resources =
            Collections.<String, String>synchronizedMap(new HashMap<>());

    private final Set<String> runningResCheck = Collections.synchronizedSet(new HashSet<>());

    private final Map<String, String> connectors =
            Collections.<String, String>synchronizedMap(new HashMap<>());

    private final Set<String> runningConnCheck = Collections.synchronizedSet(new HashSet<>());

    private final ConnectorRestClient connectorRestClient = new ConnectorRestClient();

    private final ResourceRestClient resourceRestClient = new ResourceRestClient();

    @Override
    protected CharSequence getSessionId(final Component component) {
        String sessionId = "";
        WebApplication application = (WebApplication) component.getApplication();
        Set<SessionTrackingMode> effectiveSessionTrackingModes =
                application.getServletContext().getEffectiveSessionTrackingModes();
        Object containerRequest = component.getRequest().getContainerRequest();
        if (effectiveSessionTrackingModes.size() == 1
                && SessionTrackingMode.URL.equals(effectiveSessionTrackingModes.iterator().next())) {

            sessionId = component.getSession().getId();
        } else if (containerRequest instanceof HttpServletRequest) {
            CookieUtils cookieUtils = new CookieUtils();
            String jsessionCookieName = application.getServletContext().getSessionCookieConfig().getName();
            if (jsessionCookieName == null) {
                jsessionCookieName = "JSESSIONID";
            }
            Cookie jsessionid = cookieUtils.getCookie(jsessionCookieName);
            HttpServletRequest httpServletRequest = (HttpServletRequest) containerRequest;
            if (jsessionid == null || !httpServletRequest.isRequestedSessionIdValid()) {
                sessionId = component.getSession().getId();
            }
        }
        return sessionId;
    }

    @Override
    protected void onMessage(final WebSocketRequestHandler handler, final TextMessage message) {
        try {
            JsonNode obj = OBJECT_MAPPER.readTree(message.getText());

            switch (Topology.SupportedOperation.valueOf(obj.get("kind").asText())) {
                case CHECK_CONNECTOR:
                    final String ckey = obj.get("target").asText();

                    if (connectors.containsKey(ckey)) {
                        handler.push(connectors.get(ckey));
                    } else {
                        handler.push(String.format(
                                "{ \"status\": \"%s\", \"target\": \"%s\"}", TopologyNode.Status.UNKNOWN, ckey));
                    }

                    if (runningConnCheck.contains(ckey)) {
                        LOG.debug("Running connection check for connector {}", ckey);
                    } else {
                        runningConnCheck.add(ckey);
                    }

                    SyncopeConsoleSession.get().execute(new ConnCheck(ckey));

                    break;
                case CHECK_RESOURCE:
                    final String rkey = obj.get("target").asText();

                    if (resources.containsKey(rkey)) {
                        handler.push(resources.get(rkey));
                    } else {
                        handler.push(String.format(
                                "{ \"status\": \"%s\", \"target\": \"%s\"}", TopologyNode.Status.UNKNOWN, rkey));
                    }

                    if (runningResCheck.contains(rkey)) {
                        LOG.debug("Running connection check for resource {}", rkey);
                    } else {
                        runningResCheck.add(rkey);
                    }

                    SyncopeConsoleSession.get().execute(new ResCheck(rkey));

                    break;
                case ADD_ENDPOINT:
                    handler.appendJavaScript(String.format("addEndpoint('%s', '%s', '%s');",
                            obj.get("source").asText(),
                            obj.get("target").asText(),
                            obj.get("scope").asText()));
                    break;
                default:
            }
        } catch (IOException e) {
            LOG.error("Eror managing websocket message", e);
        }
    }

    public boolean connCheckDone(final Collection<String> connectors) {
        return this.connectors.keySet().containsAll(connectors);
    }

    public boolean resCheckDone(final Collection<String> resources) {
        return this.resources.keySet().containsAll(resources);
    }

    class ConnCheck implements Runnable {

        private final String key;

        private final Application application;

        private final Session session;

        ConnCheck(final String key) {
            this.key = key;
            this.application = Application.get();
            this.session = Session.exists() ? Session.get() : null;
        }

        @Override
        public void run() {
            try {
                ThreadContext.setApplication(application);
                ThreadContext.setSession(session);

                String res;
                try {
                    final ConnInstanceTO connector = connectorRestClient.read(key);
                    res = String.format("{ \"status\": \"%s\", \"target\": \"%s\"}",
                            connectorRestClient.check(connector).getLeft()
                            ? TopologyNode.Status.REACHABLE : TopologyNode.Status.UNREACHABLE, key);
                } catch (Exception e) {
                    LOG.warn("Error checking connection for {}", key, e);
                    res = String.format("{ \"status\": \"%s\", \"target\": \"%s\"}", TopologyNode.Status.FAILURE, key);
                }

                connectors.put(key, res);
                runningConnCheck.remove(key);
            } finally {
                ThreadContext.detach();
            }
        }
    }

    class ResCheck implements Runnable {

        private final String key;

        private final Application application;

        private final Session session;

        ResCheck(final String key) {
            this.key = key;
            this.application = Application.get();
            this.session = Session.exists() ? Session.get() : null;
        }

        @Override
        public void run() {
            try {
                ThreadContext.setApplication(application);
                ThreadContext.setSession(session);

                String res;
                try {
                    final ResourceTO resource = resourceRestClient.read(key);
                    res = String.format("{ \"status\": \"%s\", \"target\": \"%s\"}",
                            resourceRestClient.check(resource).getLeft()
                            ? TopologyNode.Status.REACHABLE : TopologyNode.Status.UNREACHABLE, key);
                } catch (Exception e) {
                    LOG.warn("Error checking connection for {}", key, e);
                    res = String.format("{ \"status\": \"%s\", \"target\": \"%s\"}", TopologyNode.Status.FAILURE, key);
                }

                resources.put(key, res);
                runningResCheck.remove(key);
            } finally {
                ThreadContext.detach();
            }
        }
    }
}
