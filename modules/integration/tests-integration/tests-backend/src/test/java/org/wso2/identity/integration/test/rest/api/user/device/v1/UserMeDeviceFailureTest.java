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

package org.wso2.identity.integration.test.rest.api.user.device.v1;

import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.json.simple.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

/**
 * Failure path tests for the self service device REST API (/me/devices), including attempts to access a device
 * owned by another user.
 */
public class UserMeDeviceFailureTest extends UserMeDeviceTestBase {

    private static final String NON_EXISTING_DEVICE_ID = "00000000-0000-0000-0000-000000000000";
    private static final String EMPTY_UPDATE_REQUEST = "DM-60203";

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public UserMeDeviceFailureTest(TestUserMode userMode) throws Exception {

        initUserMode(userMode);
    }

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        setupUsersWithDevices("MeDeviceFailureUserOne", "MeDeviceFailureUserTwo");
    }

    @AfterClass(alwaysRun = true)
    public void concludeClass() throws Exception {

        cleanup();
    }

    @Test
    public void testGetAnotherUsersDevice() {

        actAs(secondUser);
        getResponseOfGet(myDevicePath(firstUser.deviceId))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .body("code", equalTo(DEVICE_NOT_FOUND));
    }

    @Test
    public void testUpdateAnotherUsersDevice() {

        actAs(secondUser);
        getResponseOfPatch(myDevicePath(firstUser.deviceId), "{\"deviceName\": \"Hijacked\", \"status\": \"" +
                STATUS_INACTIVE + "\"}")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .body("code", equalTo(DEVICE_NOT_FOUND));
    }

    @Test
    public void testDeleteAnotherUsersDevice() {

        actAs(secondUser);
        getResponseOfDelete(myDevicePath(firstUser.deviceId))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .body("code", equalTo(DEVICE_NOT_FOUND));
    }

    @Test(dependsOnMethods = {"testGetAnotherUsersDevice", "testUpdateAnotherUsersDevice",
            "testDeleteAnotherUsersDevice"})
    public void testAnotherUsersDeviceIsUnchanged() throws Exception {

        JSONObject device = deviceManagementRestClient.getDevice(firstUser.deviceId);
        Assert.assertNotNull(device, "The device was removed by another user.");
        Assert.assertEquals(device.get("deviceName"), firstUser.defaultDeviceName());
        Assert.assertEquals(device.get("status"), STATUS_ACTIVE);

        actAs(firstUser);
        getResponseOfGet(ME_DEVICES_PATH)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("totalResults", equalTo(1))
                .body("devices[0].id", equalTo(firstUser.deviceId));
    }

    @Test
    public void testGetNonExistingDevice() {

        actAs(firstUser);
        getResponseOfGet(myDevicePath(NON_EXISTING_DEVICE_ID))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .body("code", equalTo(DEVICE_NOT_FOUND));
    }

    @Test
    public void testUpdateDeviceWithEmptyRequest() {

        actAs(firstUser);
        getResponseOfPatch(myDevicePath(firstUser.deviceId), "{}")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body("code", equalTo(EMPTY_UPDATE_REQUEST));
    }

    @Test
    public void testUpdateDeviceWithInvalidStatus() {

        // The status is not one the API definition allows, so the request is sent without the OpenAPI validation.
        actAs(firstUser);
        getResponseOfPatchWithoutValidation(myDevicePath(firstUser.deviceId), "{\"status\": \"BLOCKED\"}")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void testListDevicesWithoutAuthentication() {

        given().contentType(ContentType.JSON)
                .when()
                .get(ME_DEVICES_PATH)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_UNAUTHORIZED);
    }
}
