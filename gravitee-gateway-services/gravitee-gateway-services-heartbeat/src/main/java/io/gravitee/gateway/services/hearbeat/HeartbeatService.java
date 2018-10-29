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
package io.gravitee.gateway.services.hearbeat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.common.service.AbstractService;
import io.gravitee.common.util.Version;
import io.gravitee.common.utils.UUID;
import io.gravitee.gateway.env.GatewayConfiguration;
import io.gravitee.gateway.services.hearbeat.event.InstanceEventPayload;
import io.gravitee.gateway.services.hearbeat.event.Plugin;
import io.gravitee.node.api.Node;
import io.gravitee.plugin.core.api.PluginRegistry;
import io.gravitee.repository.management.api.EventRepository;
import io.gravitee.repository.management.model.Event;
import io.gravitee.repository.management.model.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HeartbeatService extends AbstractService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatService.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${services.heartbeat.enabled:true}")
    private boolean enabled;

    @Value("${services.heartbeat.delay:5000}")
    private int delay;

    @Value("${services.heartbeat.unit:MILLISECONDS}")
    private TimeUnit unit;

    @Value("${services.heartbeat.storeSystemProperties:true}")
    private boolean storeSystemProperties;

    @Value("${http.port:8082}")
    private String port;

    @Autowired
    private Node node;

    @Autowired
    private PluginRegistry pluginRegistry;

    @Autowired
    private EventRepository eventRepository;

    private ExecutorService executorService;

    private Event heartbeatEvent;

    @Autowired
    private GatewayConfiguration gatewayConfiguration;

    @Override
    protected void doStart() throws Exception {
        if (enabled) {
            super.doStart();
            LOGGER.info("Start gateway monitor");

            Event evt = prepareEvent();
            LOGGER.debug("Sending a {} event", evt.getType());
            heartbeatEvent = eventRepository.create(evt);

            executorService = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "gateway-monitor"));

            HeartbeatThread monitorThread = new HeartbeatThread(heartbeatEvent);
            this.applicationContext.getAutowireCapableBeanFactory().autowireBean(monitorThread);

            LOGGER.info("Monitoring scheduled with fixed delay {} {} ", delay, unit.name());

            ((ScheduledExecutorService) executorService).scheduleWithFixedDelay(
                    monitorThread, 0, delay, unit);

            LOGGER.info("Start gateway monitor : DONE");
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (enabled) {
            if (! executorService.isShutdown()) {
                LOGGER.info("Stop gateway monitor");
                executorService.shutdownNow();
            } else {
                LOGGER.info("Gateway monitor already shut-downed");
            }

            heartbeatEvent.setType(EventType.GATEWAY_STOPPED);
            heartbeatEvent.getProperties().put("stopped_at", Long.toString(new Date().getTime()));
            LOGGER.debug("Sending a {} event", heartbeatEvent.getType());
            eventRepository.update(heartbeatEvent);

            super.doStop();
            LOGGER.info("Stop gateway monitor : DONE");
        }
    }

    private Event prepareEvent() {
        Event event = new Event();
        event.setId(UUID.toString(UUID.random()));
        event.setType(EventType.GATEWAY_STARTED);
        event.setCreatedAt(new Date());
        event.setUpdatedAt(event.getCreatedAt());
        Map<String, String> properties = new HashMap<>();
        properties.put("id", node.id());
        properties.put("started_at", Long.toString(event.getCreatedAt().getTime()));
        properties.put("last_heartbeat_at", Long.toString(event.getCreatedAt().getTime()));
        event.setProperties(properties);

        InstanceEventPayload instance = createInstanceInfo();

        try {
            String payload = objectMapper.writeValueAsString(instance);
            event.setPayload(payload);
        } catch (JsonProcessingException jsex) {
            LOGGER.error("An error occurs while transforming instance information into JSON", jsex);
        }
        return event;
    }

    private InstanceEventPayload createInstanceInfo() {
        InstanceEventPayload instanceInfo = new InstanceEventPayload();

        instanceInfo.setId(node.id());
        instanceInfo.setVersion(Version.RUNTIME_VERSION.toString());

        Optional<List<String>> shardingTags = gatewayConfiguration.shardingTags();
        instanceInfo.setTags(shardingTags.orElse(null));

        instanceInfo.setPlugins(plugins());
        instanceInfo.setSystemProperties(getSystemProperties());
        instanceInfo.setPort(port);

        Optional<String> tenant = gatewayConfiguration.tenant();
        instanceInfo.setTenant(tenant.orElse(null));

        try {
            instanceInfo.setHostname(InetAddress.getLocalHost().getHostName());
            instanceInfo.setIp(InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException uhe) {
            LOGGER.warn("Could not get hostname / IP", uhe);
        }

        return instanceInfo;
    }

    public Set<Plugin> plugins() {
        return pluginRegistry.plugins().stream().map(regPlugin -> {
            Plugin plugin = new Plugin();
            plugin.setId(regPlugin.id());
            plugin.setName(regPlugin.manifest().name());
            plugin.setDescription(regPlugin.manifest().description());
            plugin.setVersion(regPlugin.manifest().version());
            plugin.setType(regPlugin.type().name().toLowerCase());
            plugin.setPlugin(regPlugin.clazz());
            return plugin;
        }).collect(Collectors.toSet());
    }

    @Override
    protected String name() {
        return "Gateway Heartbeat";
    }

    private Map getSystemProperties() {
        if (storeSystemProperties) {
            return System.getProperties()
                    .entrySet()
                    .stream()
                    .filter(entry -> !entry.getKey().toString().toUpperCase().startsWith("GRAVITEE"))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return Collections.emptyMap();
    }
}
