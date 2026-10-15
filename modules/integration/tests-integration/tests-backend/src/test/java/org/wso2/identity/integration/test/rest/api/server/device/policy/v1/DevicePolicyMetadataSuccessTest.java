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

package org.wso2.identity.integration.test.rest.api.server.device.policy.v1;

import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.TestUserMode;

import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.isOneOf;

/**
 * Success path tests for the Device Policy metadata REST API.
 */
public class DevicePolicyMetadataSuccessTest extends DevicePolicyMetadataTestBase {

    private static final String FIELD_PLATFORM = "platform";
    private static final String FIELD_ANDROID_OS_VERSION = "androidOsVersion";
    private static final String FIELD_IS_ROOTED = "isRooted";
    private static final String FIELD_IOS_OS_VERSION = "iosOsVersion";
    private static final String FIELD_JAILBREAK = "jailbreak";

    @Factory(dataProvider = "restAPIUserConfigProvider")
    public DevicePolicyMetadataSuccessTest(TestUserMode userMode) throws Exception {

        initUser(userMode);
    }

    @Test
    public void testGetAndroidMetadata() {

        getMetadata("android")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("field.name", hasItems(FIELD_PLATFORM, FIELD_ANDROID_OS_VERSION, FIELD_IS_ROOTED))
                .body("field.name", not(hasItem(FIELD_IOS_OS_VERSION)))
                .body("field.name", not(hasItem(FIELD_JAILBREAK)))
                .body("operators", everyItem(notNullValue()))
                .body("value", everyItem(notNullValue()));
    }

    @Test
    public void testGetIosMetadata() {

        getMetadata("ios")
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("field.name", hasItems(FIELD_PLATFORM, FIELD_IOS_OS_VERSION, FIELD_JAILBREAK))
                .body("field.name", not(hasItem(FIELD_ANDROID_OS_VERSION)))
                .body("field.name", not(hasItem(FIELD_IS_ROOTED)));
    }

    @Test
    public void testGetMetadataOfAllPlatforms() {

        getMetadata(null)
                .then()
                .log().ifValidationFails()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("field.name", hasItems(FIELD_PLATFORM, FIELD_ANDROID_OS_VERSION, FIELD_IS_ROOTED,
                        FIELD_IOS_OS_VERSION, FIELD_JAILBREAK))
                .body("value.inputType", everyItem(isOneOf("input", "options")))
                .body("value.valueType", everyItem(isOneOf("string", "number", "boolean", "date")));
    }

    private Response getMetadata(String platform) {

        return getResponseOfGet(platform == null ? METADATA_PATH : METADATA_PATH + "?platform=" + platform);
    }
}
