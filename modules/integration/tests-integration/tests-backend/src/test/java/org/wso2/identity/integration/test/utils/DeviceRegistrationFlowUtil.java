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

package org.wso2.identity.integration.test.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.Assert;
import org.wso2.identity.integration.test.rest.api.common.RESTTestBase;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.FlowConfig;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.FlowExecutionRequest;
import org.wso2.identity.integration.test.rest.api.server.flow.execution.v1.model.FlowExecutionResponse;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.FlowRequest;
import org.wso2.identity.integration.test.restclients.FlowExecutionClient;
import org.wso2.identity.integration.test.restclients.FlowManagementClient;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers users together with a device through a REGISTRATION (self sign-up) flow that runs the
 * DeviceRegistrationExecutor, playing the device side of the protocol with {@link DeviceKeyUtil}.
 * The flow collects the user's details in a view, runs the device registration step and provisions the user;
 * the device is persisted and bound to the new user when the flow completes.
 */
public class DeviceRegistrationFlowUtil {

    public static final String REGISTRATION_FLOW_TYPE = "REGISTRATION";
    public static final String STATUS_INCOMPLETE = "INCOMPLETE";
    public static final String STATUS_COMPLETE = "COMPLETE";
    public static final String TYPE_VIEW = "VIEW";
    public static final String TYPE_INTERNAL_PROMPT = "INTERNAL_PROMPT";
    public static final String PARAM_PUBLIC_KEY = "publicKey";
    public static final String PARAM_SIGNATURE = "signature";
    public static final String PARAM_DEVICE_MODEL = "deviceModel";
    public static final String DATA_REGISTRATION_ID = "registrationId";
    public static final String DATA_CHALLENGE = "challenge";

    private static final String REGISTRATION_FLOW_WITH_DEVICE = "registration-flow-with-device-registration.json";
    private static final String SUBMIT_ACTION_ID = "button_5zqc";
    private static final String USERNAME_CLAIM = "http://wso2.org/claims/username";
    private static final String EMAIL_CLAIM = "http://wso2.org/claims/emailaddress";
    private static final String PASSWORD = "password";

    private DeviceRegistrationFlowUtil() {

    }

    /**
     * Configure and enable a REGISTRATION flow that registers a device for the user being onboarded.
     * Email verification and account locking on creation are disabled so that the registered users can
     * immediately call the APIs with their own credentials.
     *
     * @param flowManagementClient Flow management client of the tenant.
     * @throws Exception If the flow could not be configured.
     */
    public static void configureRegistrationFlow(FlowManagementClient flowManagementClient) throws Exception {

        String flowJson = RESTTestBase.readResource(REGISTRATION_FLOW_WITH_DEVICE, DeviceRegistrationFlowUtil.class);
        flowManagementClient.putFlow(new ObjectMapper().readValue(flowJson, FlowRequest.class));

        FlowConfig flowConfig = new FlowConfig();
        flowConfig.setFlowType(REGISTRATION_FLOW_TYPE);
        flowConfig.setIsEnabled(true);
        Map<String, String> flowCompletionConfigs = new HashMap<>();
        flowCompletionConfigs.put("isEmailVerificationEnabled", "false");
        flowCompletionConfigs.put("isAccountLockOnCreationEnabled", "false");
        flowConfig.setFlowCompletionConfigs(flowCompletionConfigs);
        flowManagementClient.updateFlowConfig(flowConfig);
    }

    /**
     * Disable the REGISTRATION flow.
     *
     * @param flowManagementClient Flow management client of the tenant.
     * @throws Exception If the flow could not be disabled.
     */
    public static void disableRegistrationFlow(FlowManagementClient flowManagementClient) throws Exception {

        FlowConfig flowConfig = new FlowConfig();
        flowConfig.setFlowType(REGISTRATION_FLOW_TYPE);
        flowConfig.setIsEnabled(false);
        flowManagementClient.updateFlowConfig(flowConfig);
    }

    /**
     * Start the registration flow and submit the user's details, which triggers the device registration step.
     *
     * @param flowExecutionClient Flow execution client of the tenant.
     * @param username            Username of the user to register.
     * @param password            Password of the user to register.
     * @param email               Email address of the user to register.
     * @return Response carrying the device registration challenge.
     * @throws Exception If the flow could not be executed.
     */
    public static FlowExecutionResponse startRegistration(FlowExecutionClient flowExecutionClient, String username,
                                                          String password, String email) throws Exception {

        FlowExecutionResponse initiation = asFlowResponse(
                flowExecutionClient.initiateFlowExecution(REGISTRATION_FLOW_TYPE));
        Assert.assertEquals(initiation.getFlowStatus(), STATUS_INCOMPLETE);
        Assert.assertEquals(initiation.getType().toString(), TYPE_VIEW);

        FlowExecutionRequest request = new FlowExecutionRequest();
        request.setFlowType(REGISTRATION_FLOW_TYPE);
        request.setFlowId(initiation.getFlowId());
        request.setActionId(SUBMIT_ACTION_ID);
        Map<String, String> inputs = new HashMap<>();
        inputs.put(USERNAME_CLAIM, username);
        inputs.put(PASSWORD, password);
        inputs.put(EMAIL_CLAIM, email);
        request.setInputs(inputs);

        FlowExecutionResponse prompt = asFlowResponse(flowExecutionClient.executeFlow(request));
        Assert.assertEquals(prompt.getFlowStatus(), STATUS_INCOMPLETE);
        Assert.assertEquals(prompt.getType().toString(), TYPE_INTERNAL_PROMPT,
                "The device registration step should prompt the device for its key and signature.");
        return prompt;
    }

    /**
     * Answer a device registration prompt.
     *
     * @param flowExecutionClient Flow execution client of the tenant.
     * @param flowId              Flow ID.
     * @param publicKey           Public key input.
     * @param signature           Signature input.
     * @param deviceModel         Device model input, or null to omit it.
     * @return Response of the flow execution, which may be an error.
     * @throws Exception If the flow could not be executed.
     */
    public static Object submitDeviceProof(FlowExecutionClient flowExecutionClient, String flowId, String publicKey,
                                           String signature, String deviceModel) throws Exception {

        FlowExecutionRequest request = new FlowExecutionRequest();
        request.setFlowType(REGISTRATION_FLOW_TYPE);
        request.setFlowId(flowId);
        Map<String, String> inputs = new HashMap<>();
        inputs.put(PARAM_PUBLIC_KEY, publicKey);
        inputs.put(PARAM_SIGNATURE, signature);
        if (deviceModel != null) {
            inputs.put(PARAM_DEVICE_MODEL, deviceModel);
        }
        request.setInputs(inputs);
        return flowExecutionClient.executeFlow(request);
    }

    /**
     * Register a user together with a device generated for the test.
     *
     * @param flowExecutionClient Flow execution client of the tenant.
     * @param username            Username of the user to register.
     * @param password            Password of the user to register.
     * @param email               Email address of the user to register.
     * @param deviceModel         Model of the device.
     * @return ID of the registered device.
     * @throws Exception If the registration failed.
     */
    public static String registerUserWithDevice(FlowExecutionClient flowExecutionClient, String username,
                                                String password, String email, String deviceModel)
            throws Exception {

        FlowExecutionResponse prompt = startRegistration(flowExecutionClient, username, password, email);
        List<String> requiredParams = prompt.getData().getRequiredParams();
        Assert.assertTrue(requiredParams.contains(PARAM_PUBLIC_KEY) && requiredParams.contains(PARAM_SIGNATURE),
                "Unexpected required params: " + requiredParams);
        String registrationId = (String) prompt.getData().getAdditionalData().get(DATA_REGISTRATION_ID);
        String challenge = (String) prompt.getData().getAdditionalData().get(DATA_CHALLENGE);
        Assert.assertNotNull(registrationId, "Registration ID is not returned.");
        Assert.assertNotNull(challenge, "Challenge is not returned.");

        KeyPair keyPair = DeviceKeyUtil.generateKeyPair();
        FlowExecutionResponse completion = asFlowResponse(submitDeviceProof(flowExecutionClient, prompt.getFlowId(),
                DeviceKeyUtil.encodePublicKey(keyPair), DeviceKeyUtil.signChallenge(keyPair.getPrivate(), challenge),
                deviceModel));
        Assert.assertEquals(completion.getFlowStatus(), STATUS_COMPLETE);
        return registrationId;
    }

    /**
     * Cast a flow execution client result to a successful flow response, failing with the error otherwise.
     *
     * @param response Result of a flow execution client call.
     * @return Flow execution response.
     */
    public static FlowExecutionResponse asFlowResponse(Object response) {

        Assert.assertTrue(response instanceof FlowExecutionResponse, "Flow execution failed: " + response);
        return (FlowExecutionResponse) response;
    }
}
