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

package org.wso2.identity.integration.test.restclients;

import io.restassured.http.ContentType;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.wso2.carbon.automation.engine.context.beans.Tenant;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.identity.integration.test.utils.OAuth2Constant;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Rest client which provides methods to interact with the Device Management REST API as the tenant admin.
 */
public class DeviceManagementRestClient extends RestBaseClient {

    private static final String DEVICES_API_PATH = "devices";
    private final String username;
    private final String password;
    private final String devicesBasePath;

    public DeviceManagementRestClient(String serverUrl, Tenant tenantInfo) {

        String tenantDomain = tenantInfo.getContextUser().getUserDomain();
        this.username = tenantInfo.getContextUser().getUserName();
        this.password = tenantInfo.getContextUser().getPassword();
        if (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
            this.devicesBasePath = serverUrl + API_SERVER_PATH + PATH_SEPARATOR + DEVICES_API_PATH;
        } else {
            this.devicesBasePath = serverUrl + TENANT_PATH + tenantDomain + PATH_SEPARATOR + API_SERVER_PATH +
                    PATH_SEPARATOR + DEVICES_API_PATH;
        }
    }

    /**
     * List the devices registered by the given user.
     *
     * @param userId ID of the user.
     * @return Device list response.
     * @throws Exception If the devices could not be listed.
     */
    public JSONObject listDevicesOfUser(String userId) throws Exception {

        String url = devicesBasePath + "?userId=" + URLEncoder.encode(userId, StandardCharsets.UTF_8.name());
        try (CloseableHttpResponse response = getResponseOfHttpGet(url, getHeaders())) {
            String responseBody = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() != HTTP_OK) {
                throw new Exception("Error code " + response.getStatusLine().getStatusCode() +
                        " occurred while listing the devices of user " + userId + ": " + responseBody);
            }
            return getJSONObject(responseBody);
        }
    }

    /**
     * Get a device by its ID.
     *
     * @param deviceId ID of the device.
     * @return Device response, or null if the device does not exist.
     * @throws Exception If the device could not be retrieved.
     */
    public JSONObject getDevice(String deviceId) throws Exception {

        try (CloseableHttpResponse response = getResponseOfHttpGet(devicesBasePath + PATH_SEPARATOR + deviceId,
                getHeaders())) {
            String responseBody = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404) {
                return null;
            }
            if (statusCode != HTTP_OK) {
                throw new Exception("Error code " + statusCode + " occurred while retrieving the device " +
                        deviceId + ": " + responseBody);
            }
            return getJSONObject(responseBody);
        }
    }

    /**
     * Close the HTTP client.
     *
     * @throws IOException If an error occurred while closing the Http Client.
     */
    public void closeHttpClient() throws IOException {

        client.close();
    }

    private Header[] getHeaders() {

        Header[] headerList = new Header[3];
        headerList[0] = new BasicHeader(USER_AGENT_ATTRIBUTE, OAuth2Constant.USER_AGENT);
        headerList[1] = new BasicHeader(AUTHORIZATION_ATTRIBUTE, BASIC_AUTHORIZATION_ATTRIBUTE +
                Base64.encodeBase64String((username + ":" + password).getBytes()).trim());
        headerList[2] = new BasicHeader(CONTENT_TYPE_ATTRIBUTE, String.valueOf(ContentType.JSON));
        return headerList;
    }
}
