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
package io.gravitee.gateway.security.oauth2.policy;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.policy.AbstractPolicy;
import io.gravitee.gateway.policy.PolicyException;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.SubscriptionRepository;
import io.gravitee.repository.management.api.search.SubscriptionCriteria;
import io.gravitee.repository.management.model.Subscription;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CheckSubscriptionPolicy extends AbstractPolicy {

    static final String CONTEXT_ATTRIBUTE_CLIENT_ID = "oauth.client_id";
    static final String BEARER_AUTHORIZATION_TYPE = "Bearer";

    private final static String OAUTH2_ERROR_ACCESS_DENIED = "access_denied";
    private final static String OAUTH2_ERROR_SERVER_ERROR = "server_error";

    @Override
    protected void onRequest(Request request, Response response, PolicyChain policyChain, ExecutionContext executionContext) throws PolicyException {
        SubscriptionRepository subscriptionRepository = executionContext.getComponent(SubscriptionRepository.class);

        // Get plan and client_id from execution context
        String clientId = (String) executionContext.getAttribute(CONTEXT_ATTRIBUTE_CLIENT_ID);
        if (clientId == null || clientId.trim().isEmpty()) {
            sendError(response, policyChain, "invalid_client", "No client_id was supplied");
            return;
        }

        String api = (String) executionContext.getAttribute(ExecutionContext.ATTR_API);

        try {
            List<Subscription> subscriptions = subscriptionRepository.search(
                    new SubscriptionCriteria.Builder()
                            .apis(Collections.singleton(api))
                            .clientId(clientId)
                            .status(Subscription.Status.ACCEPTED)
                            .build());

            if (subscriptions != null && !subscriptions.isEmpty()) {
                Subscription subscription = subscriptions.get(0);
                if (subscription.getClientId().equals(clientId) &&
                        (
                                subscription.getEndingAt() == null ||
                                        subscription.getEndingAt().after(new Date(request.timestamp())))) {

                    executionContext.setAttribute(ExecutionContext.ATTR_APPLICATION, subscription.getApplication());
                    executionContext.setAttribute(ExecutionContext.ATTR_USER_ID, subscription.getId());
                    executionContext.setAttribute(ExecutionContext.ATTR_PLAN, subscription.getPlan());

                    policyChain.doNext(request, response);
                    return;
                }
            }

            // As per https://tools.ietf.org/html/rfc6749#section-4.1.2.1
            sendUnauthorized(policyChain, OAUTH2_ERROR_ACCESS_DENIED);
        } catch (TechnicalException te) {
            // As per https://tools.ietf.org/html/rfc6749#section-4.1.2.1
            sendUnauthorized(policyChain, OAUTH2_ERROR_SERVER_ERROR);
        }
    }

    private void sendUnauthorized(PolicyChain policyChain, String description) {
        policyChain.failWith(PolicyResult.failure(
                HttpStatusCode.UNAUTHORIZED_401, description));
    }

    /**
     * As per https://tools.ietf.org/html/rfc6750#page-7:
     *
     *      HTTP/1.1 401 Unauthorized
     *      WWW-Authenticate: Bearer realm="example",
     *      error="invalid_token",
     *      error_description="The access token expired"
     */
    private void sendError(Response response, PolicyChain policyChain, String error, String description) {
        String headerValue = BEARER_AUTHORIZATION_TYPE +
                " realm=\"gravitee.io\"," +
                " error=\"" + error + "\"," +
                " error_description=\"" + description + "\"";
        response.headers().add(HttpHeaders.WWW_AUTHENTICATE, headerValue);
        policyChain.failWith(PolicyResult.failure(HttpStatusCode.UNAUTHORIZED_401, null));
    }

    @Override
    public String id() {
        return "check-subscription";
    }
}
