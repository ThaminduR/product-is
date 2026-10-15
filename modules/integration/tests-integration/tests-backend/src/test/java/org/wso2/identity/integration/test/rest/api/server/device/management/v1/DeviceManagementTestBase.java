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

package org.wso2.identity.integration.test.rest.api.server.device.management.v1;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.identity.integration.test.rest.api.server.common.RESTAPIServerTestBase;

import java.io.IOException;

import static io.restassured.RestAssured.given;

/**
 * Base class for the Device Management REST API tests.
 */
public class DeviceManagementTestBase extends RESTAPIServerTestBase {

    private static final String API_DEFINITION_NAME = "device-mgt.yaml";
    private static final String API_PACKAGE_NAME = "org.wso2.carbon.identity.api.server.device.mgt.v1";
    protected static final String API_VERSION = "v1";
    protected static final String DEVICES_PATH = "/devices";
    protected static final String NON_EXISTING_DEVICE_ID = "00000000-0000-0000-0000-000000000000";
    protected static final String USER_PASSWORD = "Wso2@test123";
    protected static final String STATUS_ACTIVE = "ACTIVE";

    protected static String swaggerDefinition;

    static {
        try {
            swaggerDefinition = getAPISwaggerDefinition(API_PACKAGE_NAME, API_DEFINITION_NAME);
        } catch (IOException e) {
            Assert.fail(String.format("Unable to read the swagger definition %s from %s", API_DEFINITION_NAME,
                    API_PACKAGE_NAME), e);
        }
    }

    @DataProvider(name = "restAPIUserConfigProvider")
    public static Object[][] restAPIUserConfigProvider() {

        return new Object[][]{
                {TestUserMode.SUPER_TENANT_ADMIN},
                {TestUserMode.TENANT_ADMIN}
        };
    }

    @BeforeMethod(alwaysRun = true)
    public void setBasePath() {

        RestAssured.basePath = basePath;
    }

    @AfterMethod(alwaysRun = true)
    public void clearBasePath() {

        RestAssured.basePath = StringUtils.EMPTY;
    }

    /**
     * Invoke PATCH without the OpenAPI request validation, to send requests the API definition does not allow.
     *
     * @param endpointUri Endpoint to be invoked.
     * @param body        Request body.
     * @return Response.
     */
    protected Response getResponseOfPatchWithoutValidation(String endpointUri, String body) {

        return given().auth().preemptive().basic(authenticatingUserName, authenticatingCredential)
                .contentType(ContentType.JSON)
                .header(HttpHeaders.ACCEPT, ContentType.JSON)
                .body(body)
                .log().ifValidationFails()
                .when()
                .patch(endpointUri);
    }

    /**
     * Get the device path of the given device.
     *
     * @param deviceId ID of the device.
     * @return Device path.
     */
    protected String devicePath(String deviceId) {

        return DEVICES_PATH + "/" + deviceId;
    }
}
