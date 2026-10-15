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

package org.wso2.identity.integration.test.rest.api.server.flow.management.v1;

import org.apache.http.HttpStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;

/**
 * Tests for managing device registration flows through the Flow Management API.
 * A dedicated DEVICE_REGISTRATION flow binds a device to an existing user, so it must verify the user with an OTP
 * before the device registration step; flows that only collect a username are rejected.
 */
public class DeviceRegistrationFlowManagementTest extends FlowManagementTestBase {

    private static final String DEVICE_REGISTRATION = "DEVICE_REGISTRATION";
    private static final String DEVICE_REGISTRATION_EXECUTOR = "DeviceRegistrationExecutor";
    private static final String EMAIL_OTP_EXECUTOR = "EmailOTPExecutor";
    private static final String FLOW_PATH = "/flow";
    private static final String FLOW_META_PATH = "/flow/meta";
    private static final String DEVICE_FLOW_WITH_OTP = "device-registration-flow-with-email-otp.json";
    private static final String DEVICE_FLOW_WITHOUT_OTP = "device-registration-flow-without-otp.json";
    private static final String INVALID_DEVICE_REGISTRATION_FLOW = "FM-10013";

    @DataProvider(name = "restAPIUserConfigProvider")
    public static Object[][] restAPIUserConfigProvider() {

        return new Object[][]{
                {TestUserMode.SUPER_TENANT_ADMIN},
                {TestUserMode.TENANT_ADMIN}
        };
    }

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public DeviceRegistrationFlowManagementTest(TestUserMode userMode) throws Exception {

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
    public void cleanup() throws Exception {

        super.testConclude();
    }

    @Test
    public void testDeviceRegistrationFlowMeta() {

        getResponseOfGet(FLOW_META_PATH, Collections.<String, Object>singletonMap("flowType", DEVICE_REGISTRATION))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("flowType", equalTo(DEVICE_REGISTRATION))
                .body("supportedExecutors", hasItem(DEVICE_REGISTRATION_EXECUTOR));
    }

    @Test
    public void testRegistrationFlowMetaListsDeviceRegistrationExecutor() {

        getResponseOfGet(FLOW_META_PATH, Collections.<String, Object>singletonMap("flowType", "REGISTRATION"))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("supportedExecutors", hasItem(DEVICE_REGISTRATION_EXECUTOR));
    }

    @Test
    public void testUpdateDeviceRegistrationFlowWithoutOtpVerification() throws Exception {

        getResponseOfPut(FLOW_PATH, readResource(DEVICE_FLOW_WITHOUT_OTP))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body("code", equalTo(INVALID_DEVICE_REGISTRATION_FLOW));
    }

    @Test(dependsOnMethods = "testUpdateDeviceRegistrationFlowWithoutOtpVerification")
    public void testUpdateDeviceRegistrationFlowWithOtpVerification() throws Exception {

        getResponseOfPut(FLOW_PATH, readResource(DEVICE_FLOW_WITH_OTP))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK);

        getResponseOfGet(FLOW_PATH, Collections.<String, Object>singletonMap("flowType", DEVICE_REGISTRATION))
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body(containsString(EMAIL_OTP_EXECUTOR))
                .body(containsString(DEVICE_REGISTRATION_EXECUTOR));
    }
}
