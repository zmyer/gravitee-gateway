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
package io.gravitee.gateway.handlers.api.manager.endpoint;

import io.gravitee.node.management.http.endpoint.ManagementEndpointManager;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NodeApisEndpointInitializer {

    @Autowired
    private ManagementEndpointManager managementEndpointManager;

    @Autowired
    private ApisManagementEndpoint apisManagementEndpoint;
    @Autowired
    private ApiManagementEndpoint apiManagementEndpoint;

    @PostConstruct
    protected void init() {
        managementEndpointManager.register(apisManagementEndpoint);
        managementEndpointManager.register(apiManagementEndpoint);
    }
}
