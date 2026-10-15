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
import org.wso2.identity.integration.test.rest.api.user.common.RESTAPIUserTestBase;
import org.wso2.identity.integration.test.restclients.DeviceManagementRestClient;
import org.wso2.identity.integration.test.restclients.FlowExecutionClient;
import org.wso2.identity.integration.test.restclients.FlowManagementClient;
import org.wso2.identity.integration.test.restclients.SCIM2RestClient;
import org.wso2.identity.integration.test.utils.DeviceRegistrationFlowUtil;
import org.wso2.identity.integration.test.utils.UserUtil;

import java.io.IOException;

import static io.restassured.RestAssured.given;

/**
 * Base class for the self service device REST API (/me/devices) tests.
 * Two users are onboarded, each with one device, through a registration flow that runs the device registration
 * executor. The API is then invoked with each user's own credentials.
 */
public class UserMeDeviceTestBase extends RESTAPIUserTestBase {

    private static final String API_DEFINITION_NAME = "device.yaml";
    private static final String API_PACKAGE_NAME = "org.wso2.carbon.identity.rest.api.user.device.v1";
    protected static final String API_VERSION = "v1";
    protected static final String ME_DEVICES_PATH = "/me/devices";
    protected static final String USER_PASSWORD = "Wso2@test123";
    protected static final String STATUS_ACTIVE = "ACTIVE";
    protected static final String STATUS_INACTIVE = "INACTIVE";
    protected static final String DEVICE_NOT_FOUND = "DM-60001";

    protected static String swaggerDefinition;

    protected FlowManagementClient flowManagementClient;
    protected FlowExecutionClient flowExecutionClient;
    protected DeviceManagementRestClient deviceManagementRestClient;
    protected SCIM2RestClient scim2RestClient;

    protected TestUser firstUser;
    protected TestUser secondUser;

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

    protected void initUserMode(TestUserMode userMode) throws Exception {

        super.init(userMode);
        this.context = isServer;
        this.tenant = context.getContextTenant().getDomain();
    }

    /**
     * Initialize the API clients and onboard two users with one device each.
     *
     * @param firstUsername  Username of the first user.
     * @param secondUsername Username of the second user.
     * @throws Exception If the users or devices could not be registered.
     */
    protected void setupUsersWithDevices(String firstUsername, String secondUsername) throws Exception {

        super.testInit(API_VERSION, swaggerDefinition, tenant);
        flowManagementClient = new FlowManagementClient(serverURL, tenantInfo);
        flowExecutionClient = new FlowExecutionClient(serverURL, tenantInfo);
        deviceManagementRestClient = new DeviceManagementRestClient(serverURL, tenantInfo);
        scim2RestClient = new SCIM2RestClient(serverURL, tenantInfo);

        DeviceRegistrationFlowUtil.configureRegistrationFlow(flowManagementClient);
        firstUser = registerUserWithDevice(firstUsername, "Pixel 8");
        secondUser = registerUserWithDevice(secondUsername, "iPhone 15");
    }

    /**
     * Remove the onboarded users, disable the registration flow and release the API clients.
     *
     * @throws Exception If the cleanup failed.
     */
    protected void cleanup() throws Exception {

        try {
            for (TestUser user : new TestUser[]{firstUser, secondUser}) {
                if (user != null) {
                    scim2RestClient.deleteUser(user.userId);
                }
            }
            DeviceRegistrationFlowUtil.disableRegistrationFlow(flowManagementClient);
        } finally {
            flowManagementClient.closeHttpClient();
            flowExecutionClient.closeHttpClient();
            deviceManagementRestClient.closeHttpClient();
            scim2RestClient.closeHttpClient();
            super.conclude();
        }
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
     * Make the subsequent API calls with the credentials of the given user.
     *
     * @param user User to act as.
     */
    protected void actAs(TestUser user) {

        this.authenticatingUserName = user.username + "@" + tenant;
        this.authenticatingCredential = USER_PASSWORD;
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

    protected String myDevicePath(String deviceId) {

        return ME_DEVICES_PATH + "/" + deviceId;
    }

    private TestUser registerUserWithDevice(String username, String deviceModel) throws Exception {

        String deviceId = DeviceRegistrationFlowUtil.registerUserWithDevice(flowExecutionClient, username,
                USER_PASSWORD, username + "@wso2test.com", deviceModel);
        String userId = UserUtil.getUserId(username, context.getContextTenant());
        return new TestUser(username, userId, deviceId, deviceModel);
    }

    /**
     * A user onboarded for the test together with the device registered in the same flow.
     */
    protected static class TestUser {

        final String username;
        final String userId;
        final String deviceId;
        final String deviceModel;

        TestUser(String username, String userId, String deviceId, String deviceModel) {

            this.username = username;
            this.userId = userId;
            this.deviceId = deviceId;
            this.deviceModel = deviceModel;
        }

        String defaultDeviceName() {

            return username + "'s " + deviceModel;
        }
    }
}
