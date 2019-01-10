/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.gateway.services.endpoint.discovery.verticle;

import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.definition.model.Endpoint;
import io.gravitee.definition.model.EndpointGroup;
import io.gravitee.definition.model.HttpClientOptions;
import io.gravitee.definition.model.services.discovery.EndpointDiscoveryService;
import io.gravitee.discovery.api.ServiceDiscovery;
import io.gravitee.discovery.api.event.Handler;
import io.gravitee.discovery.api.service.Service;
import io.gravitee.gateway.handlers.api.definition.Api;
import io.gravitee.gateway.reactor.Reactable;
import io.gravitee.gateway.reactor.ReactorEvent;
import io.gravitee.gateway.services.endpoint.discovery.endpoint.DiscoveredEndpoint;
import io.gravitee.gateway.services.endpoint.discovery.factory.ServiceDiscoveryFactory;
import io.gravitee.plugin.core.api.ConfigurablePluginManager;
import io.gravitee.plugin.discovery.ServiceDiscoveryPlugin;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EndpointDiscoveryVerticle extends AbstractVerticle implements
        EventListener<ReactorEvent, Reactable> {

    private final static Logger LOGGER = LoggerFactory.getLogger(EndpointDiscoveryVerticle.class);

    @Autowired
    private EventManager eventManager;

    @Autowired
    private ConfigurablePluginManager<ServiceDiscoveryPlugin> serviceDiscoveryPluginManager;

    @Autowired
    private ServiceDiscoveryFactory serviceDiscoveryFactory;

    @Autowired
    private ApplicationContext applicationContext;

    private final Map<Api, List<ServiceDiscovery>> apiServiceDiscoveries = new HashMap<>();

    @Override
    public void start(final Future<Void> startedResult) {
        eventManager.subscribeForEvents(this, ReactorEvent.class);
    }

    @Override
    public void onEvent(Event<ReactorEvent, Reactable> event) {
        final Api api = (Api) event.content().item();

        switch (event.type()) {
            case DEPLOY:
                lookupForServiceDiscovery(api);
                break;
            case UNDEPLOY:
                stopServiceDiscovery(api);
                break;
            case UPDATE:
                stopServiceDiscovery(api);
                lookupForServiceDiscovery(api);
                break;
        }
    }

    private void stopServiceDiscovery(Api api) {
        List<ServiceDiscovery> discoveries = apiServiceDiscoveries.remove(api);
        if (discoveries != null) {
            LOGGER.info("Stop service discovery for API id[{}] name[{}]", api.getId(), api.getName());
            discoveries.forEach(serviceDiscovery -> {
                try {
                    serviceDiscovery.stop();
                } catch (Exception ex) {
                    LOGGER.error("Unexpected error while stopping service discovery", ex);
                }
            });
        }
    }

    private void lookupForServiceDiscovery(Api api) {
        if (api.isEnabled()) {
            for (EndpointGroup group : api.getProxy().getGroups()) {
                EndpointDiscoveryService discoveryService = group.getServices().get(EndpointDiscoveryService.class);
                if (discoveryService != null && discoveryService.isEnabled()) {
                    startServiceDiscovery(api, group, discoveryService);
                }
            }
        }
    }

    private void startServiceDiscovery(Api api, EndpointGroup group, EndpointDiscoveryService discoveryService) {
        LOGGER.info("A discovery service is defined for API id[{}] name[{}] group[{}] type[{}]", api.getId(), api.getName(), group.getName(), discoveryService.getProvider());
        ServiceDiscoveryPlugin serviceDiscoveryPlugin = serviceDiscoveryPluginManager.get(discoveryService.getProvider());
        if (serviceDiscoveryPlugin != null) {
            ServiceDiscovery serviceDiscovery = serviceDiscoveryFactory.create(
                    serviceDiscoveryPlugin, discoveryService.getConfiguration());

            // Autowire fetcher
            applicationContext.getAutowireCapableBeanFactory().autowireBean(serviceDiscovery);

            List<ServiceDiscovery> discoveries = apiServiceDiscoveries.getOrDefault(api, new ArrayList<>());
            discoveries.add(serviceDiscovery);
            apiServiceDiscoveries.put(api, discoveries);

            try {
                serviceDiscovery.listen(new Handler<io.gravitee.discovery.api.event.Event>() {
                    @Override
                    public void handle(io.gravitee.discovery.api.event.Event event) {
                        LOGGER.info("Receiving a service discovery event id[{}] type[{}]", event.service().id(), event.type());
                        Set<Endpoint> endpoints = group.getEndpoints();
                        DiscoveredEndpoint endpoint = createEndpoint(event.service());

                        switch (event.type()) {
                            case REGISTER:
                                endpoints.add(endpoint);
                                break;
                            case UNREGISTER:
                                endpoints.remove(endpoint);
                                break;
                        }
                    }
                });
            } catch (Exception ex) {
                LOGGER.error("An errors occurs while starting to listen from service discovery provider", ex);
            }
        } else {
            LOGGER.error("No Service Discovery plugin found for type[{}] api[{}] group[{}]",
                    discoveryService.getProvider(), api.getId(), group.getName());
        }
    }

    private DiscoveredEndpoint createEndpoint(Service service) {
        final String scheme = (service.port() == 443) ? "https" : "http";
        DiscoveredEndpoint endpoint = new DiscoveredEndpoint(
                "sd:" + service.id(),
                scheme + "://" + service.host() + ':' + service.port());
        endpoint.setHttpClientOptions(new HttpClientOptions());

        return endpoint;
    }
}
