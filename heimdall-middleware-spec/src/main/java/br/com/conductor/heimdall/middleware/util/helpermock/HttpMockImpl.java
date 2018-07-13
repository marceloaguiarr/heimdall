
package br.com.conductor.heimdall.middleware.util.helpermock;

/*-
 * =========================LICENSE_START==================================
 * heimdall-middleware-spec
 * ========================================================================
 * Copyright (C) 2018 Conductor Tecnologia SA
 * ========================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ==========================LICENSE_END===================================
 */

import br.com.conductor.heimdall.middleware.spec.ApiResponse;
import br.com.conductor.heimdall.middleware.spec.Http;

import java.util.Map;

/**
 * Mock class created to help unit test the root request class of a middleware.
 *
 * @author Marcelo Aguiar
 */
public class HttpMockImpl implements Http {
    @Override
    public Http header(String name, String value) {
        return null;
    }

    @Override
    public Http header(Map<String, String> params) {
        return null;
    }

    @Override
    public Http url(String url) {
        return null;
    }

    @Override
    public Http queryParam(String name, String value) {
        return null;
    }

    @Override
    public Http body(Map<String, Object> params) {
        return null;
    }

    @Override
    public Http body(String params) {
        return null;
    }

    @Override
    public ApiResponse sendGet() {
        return null;
    }

    @Override
    public ApiResponse sendPost() {
        return null;
    }

    @Override
    public ApiResponse sendPut() {
        return null;
    }

    @Override
    public ApiResponse sendDelete() {
        return null;
    }
}
