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

import org.apache.http.HttpStatus;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Success path tests for the self service device REST API (/me/devices).
 */
public class UserMeDeviceSuccessTest extends UserMeDeviceTestBase {

    private static final String RENAMED_DEVICE_NAME = "My work phone";

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public UserMeDeviceSuccessTest(TestUserMode userMode) throws Exception {

        initUserMode(userMode);
    }

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        setupUsersWithDevices("MeDeviceSuccessUserOne", "MeDeviceSuccessUserTwo");
    }

    @AfterClass(alwaysRun = true)
    public void concludeClass() throws Exception {

        cleanup();
    }

    @Test
    public void testListMyDevices() {

        for (TestUser user : new TestUser[]{firstUser, secondUser}) {
            actAs(user);
            getResponseOfGet(ME_DEVICES_PATH)
                    .then()
                    .log().ifValidationFails()
                    .assertThat()
                    .statusCode(HttpStatus.SC_OK)
                    .body("totalResults", equalTo(1))
                    .body("count", equalTo(1))
                    .body("devices.size()", equalTo(1))
                    .body("devices[0].id", equalTo(user.deviceId))
                    .body("devices[0].deviceName", equalTo(user.defaultDeviceName()))
                    .body("devices[0].deviceModel", equalTo(user.deviceModel))
                    .body("devices[0].status", equalTo(STATUS_ACTIVE))
                    .body("devices[0].registeredAt", notNullValue());
        }
    }

    @Test
    public void testListMyDevicesWithPagination() {

        actAs(firstUser);
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("limit", 1);
        queryParams.put("offset", 0);
        getResponseOfGetWithQueryParams(ME_DEVICES_PATH, queryParams)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("totalResults", equalTo(1))
                .body("devices[0].id", equalTo(firstUser.deviceId));

        queryParams.put("offset", 1);
        getResponseOfGetWithQueryParams(ME_DEVICES_PATH, queryParams)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("totalResults", equalTo(1))
                .body("count", equalTo(0));
    }

    @Test
    public void testDevicesAreBoundToTheirOwners() throws Exception {

        for (TestUser user : new TestUser[]{firstUser, secondUser}) {
            JSONObject devices = deviceManagementRestClient.listDevicesOfUser(user.userId);
            Assert.assertEquals(((Number) devices.get("totalResults")).intValue(), 1,
                    "Unexpected number of devices for user " + user.username);
            JSONObject device = (JSONObject) ((JSONArray) devices.get("devices")).get(0);
            Assert.assertEquals(device.get("id"), user.deviceId);
        }
    }

    @Test(dependsOnMethods = {"testListMyDevices", "testListMyDevicesWithPagination",
            "testDevicesAreBoundToTheirOwners"})
    public void testGetMyDevice() {

        actAs(firstUser);
        getResponseOfGet(myDevicePath(firstUser.deviceId))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("id", equalTo(firstUser.deviceId))
                .body("deviceName", equalTo(firstUser.defaultDeviceName()))
                .body("deviceModel", equalTo(firstUser.deviceModel))
                .body("status", equalTo(STATUS_ACTIVE));
    }

    @Test(dependsOnMethods = "testGetMyDevice")
    public void testRenameMyDevice() {

        actAs(firstUser);
        getResponseOfPatch(myDevicePath(firstUser.deviceId), "{\"deviceName\": \"" + RENAMED_DEVICE_NAME + "\"}")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("id", equalTo(firstUser.deviceId))
                .body("deviceName", equalTo(RENAMED_DEVICE_NAME))
                .body("status", equalTo(STATUS_ACTIVE));
    }

    @Test(dependsOnMethods = "testRenameMyDevice")
    public void testDeactivateMyDevice() {

        actAs(firstUser);
        getResponseOfPatch(myDevicePath(firstUser.deviceId), "{\"status\": \"" + STATUS_INACTIVE + "\"}")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("deviceName", equalTo(RENAMED_DEVICE_NAME))
                .body("status", equalTo(STATUS_INACTIVE));

        // Inactive devices are still listed for their owner.
        getResponseOfGet(ME_DEVICES_PATH)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("totalResults", equalTo(1))
                .body("devices[0].status", equalTo(STATUS_INACTIVE));
    }

    @Test(dependsOnMethods = "testDeactivateMyDevice")
    public void testActivateAndRenameMyDevice() {

        actAs(firstUser);
        String body = "{\"deviceName\": \"" + firstUser.defaultDeviceName() + "\", \"status\": \"" + STATUS_ACTIVE +
                "\"}";
        getResponseOfPatch(myDevicePath(firstUser.deviceId), body)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("deviceName", equalTo(firstUser.defaultDeviceName()))
                .body("status", equalTo(STATUS_ACTIVE));
    }

    @Test(dependsOnMethods = "testActivateAndRenameMyDevice")
    public void testDeleteMyDevice() throws Exception {

        actAs(firstUser);
        getResponseOfDelete(myDevicePath(firstUser.deviceId))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_NO_CONTENT);

        getResponseOfGet(myDevicePath(firstUser.deviceId))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .body("code", equalTo(DEVICE_NOT_FOUND));

        getResponseOfGet(ME_DEVICES_PATH)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("totalResults", equalTo(0));

        Assert.assertNull(deviceManagementRestClient.getDevice(firstUser.deviceId),
                "The deleted device is still returned by the admin API.");
        Assert.assertNotNull(deviceManagementRestClient.getDevice(secondUser.deviceId),
                "Deleting a device removed another user's device.");
    }
}
