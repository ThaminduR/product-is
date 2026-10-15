/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.integration.test.rest.api.server.device.policy.v1;

import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Failure path tests for the Device Policy metadata REST API.
 */
public class DevicePolicyMetadataFailureTest extends DevicePolicyMetadataTestBase {

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public DevicePolicyMetadataFailureTest(TestUserMode userMode) throws Exception {

        initUser(userMode);
    }

    @Test
    public void testGetMetadataOfInvalidPlatform() {

        // The platform is not one the API definition allows, so the request is sent without the OpenAPI validation.
        getResponseOfGetNoFilter(METADATA_PATH + "?platform=symbian")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body("code", equalTo("DPM-60001"));
    }

    @Test
    public void testGetMetadataWithoutAuthentication() {

        given().contentType(ContentType.JSON)
                .when()
                .get(METADATA_PATH)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_UNAUTHORIZED);
    }
}
