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

import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Failure path tests for the Device Management REST API.
 */
public class DeviceManagementFailureTest extends DeviceManagementTestBase {

    private static final String DEVICE_NOT_FOUND = "DM-60001";

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public DeviceManagementFailureTest(TestUserMode userMode) throws Exception {

        super.init(userMode);
        this.context = isServer;
        this.authenticatingUserName = context.getContextTenant().getTenantAdmin().getUserName();
        this.authenticatingCredential = context.getContextTenant().getTenantAdmin().getPassword();
        this.tenant = context.getContextTenant().getDomain();
    }

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        super.testInit(API_VERSION, swaggerDefinition, tenant);
    }

    @AfterClass(alwaysRun = true)
    public void concludeClass() {

        super.conclude();
    }

    @Test
    public void testGetNonExistingDevice() {

        getResponseOfGet(devicePath(NON_EXISTING_DEVICE_ID))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .body("code", equalTo(DEVICE_NOT_FOUND));
    }

    @Test
    public void testUpdateNonExistingDevice() {

        getResponseOfPatch(devicePath(NON_EXISTING_DEVICE_ID), "{\"deviceName\": \"New name\"}")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .body("code", equalTo(DEVICE_NOT_FOUND));
    }

    @Test
    public void testDeleteNonExistingDevice() {

        // Device deletion through the admin API is idempotent.
        getResponseOfDelete(devicePath(NON_EXISTING_DEVICE_ID))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_NO_CONTENT);
    }

    @Test
    public void testUpdateDeviceWithoutName() {

        getResponseOfPatchWithoutValidation(devicePath(NON_EXISTING_DEVICE_ID), "{}")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void testListDevicesOfNonExistingUser() {

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("userId", NON_EXISTING_DEVICE_ID);
        getResponseOfGetWithQueryParams(DEVICES_PATH, queryParams)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("totalResults", equalTo(0));
    }

    @Test
    public void testListDevicesWithInvalidLimit() {

        getResponseOfGetNoFilter(DEVICES_PATH + "?limit=0")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void testListDevicesWithoutAuthentication() {

        given().contentType(ContentType.JSON)
                .when()
                .get(DEVICES_PATH)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_UNAUTHORIZED);
    }
}
