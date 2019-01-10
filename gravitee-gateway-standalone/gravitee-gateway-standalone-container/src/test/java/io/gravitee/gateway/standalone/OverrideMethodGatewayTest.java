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
package io.gravitee.gateway.standalone;

import io.gravitee.common.http.HttpMethod;
import io.gravitee.gateway.standalone.junit.annotation.ApiConfiguration;
import io.gravitee.gateway.standalone.junit.annotation.ApiDescriptor;
import io.gravitee.gateway.standalone.policy.OverrideMethodPolicy;
import io.gravitee.gateway.standalone.policy.PolicyBuilder;
import io.gravitee.gateway.standalone.servlet.EchoServlet;
import io.gravitee.plugin.core.api.ConfigurablePluginManager;
import io.gravitee.plugin.policy.PolicyPlugin;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ApiDescriptor(value = "/io/gravitee/gateway/standalone/override-method.json")
@ApiConfiguration(
        servlet = EchoServlet.class,
        contextPath = "/echo")
public class OverrideMethodGatewayTest extends AbstractGatewayTest {

    @Test
    public void call_override_method() throws Exception {
        org.apache.http.client.fluent.Request request = org.apache.http.client.fluent.Request.Get("http://localhost:8082/echo/helloworld");

        org.apache.http.client.fluent.Response response = request.execute();
        HttpResponse returnResponse = response.returnResponse();

        assertEquals(HttpStatus.SC_OK, returnResponse.getStatusLine().getStatusCode());
        assertEquals(HttpMethod.POST.name(), returnResponse.getFirstHeader("method").getValue());
    }

    @Override
    public void register(ConfigurablePluginManager<PolicyPlugin> policyPluginManager) {
        super.register(policyPluginManager);

        PolicyPlugin dynamicRoutingPolicy = PolicyBuilder.build("override-method", OverrideMethodPolicy.class);
        policyPluginManager.register(dynamicRoutingPolicy);
    }
}
