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

package org.wso2.identity.integration.test.rest.api.server.flow.execution.v1;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.FlowExecutionResponse;
import org.wso2.identity.integration.test.restclients.DeviceManagementRestClient;
import org.wso2.identity.integration.test.restclients.FlowExecutionClient;
import org.wso2.identity.integration.test.restclients.FlowManagementClient;
import org.wso2.identity.integration.test.restclients.SCIM2RestClient;
import org.wso2.identity.integration.test.utils.DeviceKeyUtil;
import org.wso2.identity.integration.test.utils.DeviceRegistrationFlowUtil;
import org.wso2.identity.integration.test.utils.UserUtil;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.wso2.identity.integration.test.utils.DeviceRegistrationFlowUtil.DATA_CHALLENGE;
import static org.wso2.identity.integration.test.utils.DeviceRegistrationFlowUtil.DATA_REGISTRATION_ID;
import static org.wso2.identity.integration.test.utils.DeviceRegistrationFlowUtil.PARAM_PUBLIC_KEY;
import static org.wso2.identity.integration.test.utils.DeviceRegistrationFlowUtil.PARAM_SIGNATURE;
import static org.wso2.identity.integration.test.utils.DeviceRegistrationFlowUtil.STATUS_COMPLETE;

/**
 * Tests registering a device through flow execution: a REGISTRATION flow runs the DeviceRegistrationExecutor, which
 * challenges the device to prove possession of its private key. The device is persisted and bound to the newly
 * onboarded user when the flow completes.
 */
public class DeviceRegistrationFlowExecutionTest extends FlowExecutionTestBase {

    private static final String USER_PASSWORD = "Wso2@test123";
    private static final String DEVICE_MODEL = "Galaxy S24";

    private FlowManagementClient flowManagementClient;
    private FlowExecutionClient flowExecutionClient;
    private DeviceManagementRestClient deviceManagementRestClient;
    private SCIM2RestClient scim2RestClient;
    private final List<String> registeredUsers = new ArrayList<>();

    @DataProvider(name = "restAPIUserConfigProvider")
    public static Object[][] restAPIUserConfigProvider() {

        return new Object[][]{
                {TestUserMode.SUPER_TENANT_ADMIN},
                {TestUserMode.TENANT_ADMIN}
        };
    }

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public DeviceRegistrationFlowExecutionTest(TestUserMode userMode) throws Exception {

        super.init(userMode);
        this.context = isServer;
        this.authenticatingUserName = context.getContextTenant().getTenantAdmin().getUserName();
        this.authenticatingCredential = context.getContextTenant().getTenantAdmin().getPassword();
        this.tenant = context.getContextTenant().getDomain();
    }

    @BeforeClass(alwaysRun = true)
    public void setupClass() throws Exception {

        super.testInit(API_VERSION, swaggerDefinition, tenantInfo.getDomain());
        flowManagementClient = new FlowManagementClient(serverURL, tenantInfo);
        flowExecutionClient = new FlowExecutionClient(serverURL, tenantInfo);
        deviceManagementRestClient = new DeviceManagementRestClient(serverURL, tenantInfo);
        scim2RestClient = new SCIM2RestClient(serverURL, tenantInfo);
        DeviceRegistrationFlowUtil.configureRegistrationFlow(flowManagementClient);
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() throws Exception {

        try {
            for (String username : registeredUsers) {
                String userId = findUserId(username);
                if (userId != null) {
                    scim2RestClient.deleteUser(userId);
                }
            }
            DeviceRegistrationFlowUtil.disableRegistrationFlow(flowManagementClient);
        } finally {
            flowManagementClient.closeHttpClient();
            flowExecutionClient.closeHttpClient();
            deviceManagementRestClient.closeHttpClient();
            scim2RestClient.closeHttpClient();
        }
    }

    @Test
    public void testDeviceRegistrationChallenge() throws Exception {

        FlowExecutionResponse prompt = startRegistration("DeviceFlowChallengeUser");
        List<String> requiredParams = prompt.getData().getRequiredParams();
        Assert.assertTrue(requiredParams.contains(PARAM_PUBLIC_KEY), "publicKey is not requested: " + requiredParams);
        Assert.assertTrue(requiredParams.contains(PARAM_SIGNATURE), "signature is not requested: " + requiredParams);

        Map<String, Object> additionalData = prompt.getData().getAdditionalData();
        Assert.assertNotNull(additionalData.get(DATA_REGISTRATION_ID), "Registration ID is not returned.");
        String challenge = (String) additionalData.get(DATA_CHALLENGE);
        Assert.assertNotNull(challenge, "Challenge is not returned.");
        Assert.assertTrue(Base64.getUrlDecoder().decode(challenge).length > 0, "Challenge is empty.");
    }

    @Test
    public void testDeviceRegistrationWithInvalidSignature() throws Exception {

        String username = "DeviceFlowInvalidSignatureUser";
        FlowExecutionResponse prompt = startRegistration(username);
        String challenge = (String) prompt.getData().getAdditionalData().get(DATA_CHALLENGE);
        String registrationId = (String) prompt.getData().getAdditionalData().get(DATA_REGISTRATION_ID);

        // Sign something other than the issued challenge.
        KeyPair keyPair = DeviceKeyUtil.generateKeyPair();
        String forgedChallenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("not-" + challenge).getBytes(StandardCharsets.UTF_8));
        Object response = DeviceRegistrationFlowUtil.submitDeviceProof(flowExecutionClient, prompt.getFlowId(),
                DeviceKeyUtil.encodePublicKey(keyPair), DeviceKeyUtil.signChallenge(keyPair.getPrivate(),
                        forgedChallenge), DEVICE_MODEL);

        if (response instanceof FlowExecutionResponse) {
            Assert.assertNotEquals(((FlowExecutionResponse) response).getFlowStatus(), STATUS_COMPLETE,
                    "Flow completed with an invalid device signature.");
        }
        Assert.assertNull(findUserId(username), "User was onboarded although the device proof was invalid.");
        Assert.assertNull(deviceManagementRestClient.getDevice(registrationId),
                "Device was registered although the device proof was invalid.");
    }

    @Test
    public void testRegisterUserWithDevice() throws Exception {

        String username = "DeviceFlowRegisteredUser";
        registeredUsers.add(username);
        String deviceId = DeviceRegistrationFlowUtil.registerUserWithDevice(flowExecutionClient, username,
                USER_PASSWORD, username + "@wso2test.com", DEVICE_MODEL);

        JSONObject device = getOnlyDeviceOfUser(username);
        Assert.assertEquals(device.get("id"), deviceId, "Device ID should be the registration ID.");
        Assert.assertEquals(device.get("deviceName"), username + "'s " + DEVICE_MODEL);
        Assert.assertEquals(device.get("deviceModel"), DEVICE_MODEL);
        Assert.assertEquals(device.get("status"), "ACTIVE");
    }

    @Test
    public void testRegisterUserWithDeviceWithoutModel() throws Exception {

        String username = "DeviceFlowNoModelUser";
        registeredUsers.add(username);
        String deviceId = DeviceRegistrationFlowUtil.registerUserWithDevice(flowExecutionClient, username,
                USER_PASSWORD, username + "@wso2test.com", null);

        JSONObject device = getOnlyDeviceOfUser(username);
        Assert.assertEquals(device.get("id"), deviceId);
        Assert.assertEquals(device.get("deviceName"), username + "'s Device");
    }

    private FlowExecutionResponse startRegistration(String username) throws Exception {

        registeredUsers.add(username);
        return DeviceRegistrationFlowUtil.startRegistration(flowExecutionClient, username, USER_PASSWORD,
                username + "@wso2test.com");
    }

    private JSONObject getOnlyDeviceOfUser(String username) throws Exception {

        String userId = findUserId(username);
        Assert.assertNotNull(userId, "User " + username + " was not onboarded.");
        JSONObject devices = deviceManagementRestClient.listDevicesOfUser(userId);
        Assert.assertEquals(((Number) devices.get("totalResults")).intValue(), 1,
                "Unexpected number of devices for user " + username + ": " + devices);
        return (JSONObject) ((JSONArray) devices.get("devices")).get(0);
    }

    private String findUserId(String username) {

        try {
            return UserUtil.getUserId(username, context.getContextTenant());
        } catch (Exception | AssertionError e) {
            return null;
        }
    }
}
