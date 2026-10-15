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

import org.apache.http.HttpStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.identity.integration.test.restclients.FlowExecutionClient;
import org.wso2.identity.integration.test.restclients.FlowManagementClient;
import org.wso2.identity.integration.test.restclients.SCIM2RestClient;
import org.wso2.identity.integration.test.utils.DeviceRegistrationFlowUtil;
import org.wso2.identity.integration.test.utils.UserUtil;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Success path tests for the Device Management REST API.
 * A user and a device are onboarded through a registration flow that runs the device registration executor, and
 * the device is then administered through the API.
 */
public class DeviceManagementSuccessTest extends DeviceManagementTestBase {

    private static final String USERNAME = "DeviceMgtAdminApiUser";
    private static final String DEVICE_MODEL = "Pixel 8";
    private static final String UPDATED_DEVICE_NAME = "Admin renamed device";

    private FlowManagementClient flowManagementClient;
    private FlowExecutionClient flowExecutionClient;
    private SCIM2RestClient scim2RestClient;
    private String userId;
    private String deviceId;

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public DeviceManagementSuccessTest(TestUserMode userMode) throws Exception {

        super.init(userMode);
        this.context = isServer;
        this.authenticatingUserName = context.getContextTenant().getTenantAdmin().getUserName();
        this.authenticatingCredential = context.getContextTenant().getTenantAdmin().getPassword();
        this.tenant = context.getContextTenant().getDomain();
    }

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {

        super.testInit(API_VERSION, swaggerDefinition, tenant);
        flowManagementClient = new FlowManagementClient(serverURL, tenantInfo);
        flowExecutionClient = new FlowExecutionClient(serverURL, tenantInfo);
        scim2RestClient = new SCIM2RestClient(serverURL, tenantInfo);

        DeviceRegistrationFlowUtil.configureRegistrationFlow(flowManagementClient);
        deviceId = DeviceRegistrationFlowUtil.registerUserWithDevice(flowExecutionClient, USERNAME, USER_PASSWORD,
                USERNAME + "@wso2test.com", DEVICE_MODEL);
        userId = UserUtil.getUserId(USERNAME, context.getContextTenant());
    }

    @AfterClass(alwaysRun = true)
    public void concludeClass() throws Exception {

        try {
            if (userId != null) {
                scim2RestClient.deleteUser(userId);
            }
            DeviceRegistrationFlowUtil.disableRegistrationFlow(flowManagementClient);
        } finally {
            flowManagementClient.closeHttpClient();
            flowExecutionClient.closeHttpClient();
            scim2RestClient.closeHttpClient();
            super.conclude();
        }
    }

    @Test
    public void testListDevicesOfUser() {

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("userId", userId);
        getResponseOfGetWithQueryParams(DEVICES_PATH, queryParams)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("totalResults", equalTo(1))
                .body("count", equalTo(1))
                .body("startIndex", equalTo(1))
                .body("devices[0].id", equalTo(deviceId))
                .body("devices[0].deviceName", equalTo(USERNAME + "'s " + DEVICE_MODEL))
                .body("devices[0].deviceModel", equalTo(DEVICE_MODEL))
                .body("devices[0].status", equalTo(STATUS_ACTIVE))
                .body("devices[0].registeredAt", notNullValue());
    }

    @Test
    public void testListDevices() {

        getResponseOfGet(DEVICES_PATH)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("totalResults", greaterThanOrEqualTo(1))
                .body("devices.id", hasItem(deviceId));
    }

    @Test
    public void testListDevicesWithPagination() {

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("limit", 1);
        queryParams.put("offset", 0);
        getResponseOfGetWithQueryParams(DEVICES_PATH, queryParams)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("count", equalTo(1))
                .body("startIndex", equalTo(1))
                .body("devices.size()", equalTo(1));
    }

    @Test(dependsOnMethods = {"testListDevicesOfUser", "testListDevices", "testListDevicesWithPagination"})
    public void testGetDevice() {

        getResponseOfGet(devicePath(deviceId))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("id", equalTo(deviceId))
                .body("deviceName", equalTo(USERNAME + "'s " + DEVICE_MODEL))
                .body("deviceModel", equalTo(DEVICE_MODEL))
                .body("status", equalTo(STATUS_ACTIVE))
                .body("registeredAt", notNullValue());
    }

    @Test(dependsOnMethods = "testGetDevice")
    public void testUpdateDeviceName() {

        getResponseOfPatch(devicePath(deviceId), "{\"deviceName\": \"" + UPDATED_DEVICE_NAME + "\"}")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("id", equalTo(deviceId))
                .body("deviceName", equalTo(UPDATED_DEVICE_NAME))
                .body("deviceModel", equalTo(DEVICE_MODEL));

        getResponseOfGet(devicePath(deviceId))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("deviceName", equalTo(UPDATED_DEVICE_NAME));
    }

    @Test(dependsOnMethods = "testUpdateDeviceName")
    public void testDeleteDevice() {

        getResponseOfDelete(devicePath(deviceId))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_NO_CONTENT);

        getResponseOfGet(devicePath(deviceId))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND);

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("userId", userId);
        getResponseOfGetWithQueryParams(DEVICES_PATH, queryParams)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("totalResults", equalTo(0));
    }
}
