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
package io.gravitee.gateway.standalone.vertx;

import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.reactor.Reactor;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxReactorHandler implements Handler<HttpServerRequest> {

    private final Reactor reactor;

    VertxReactorHandler(Reactor reactor) {
        this.reactor = reactor;
    }

    @Override
    public void handle(HttpServerRequest httpServerRequest) {
        final Request request = new VertxHttpServerRequest(httpServerRequest);
        final Response response = new VertxHttpServerResponse(httpServerRequest.response(), request.metrics());

        reactor.route(request, response, __ -> {});
    }
}
