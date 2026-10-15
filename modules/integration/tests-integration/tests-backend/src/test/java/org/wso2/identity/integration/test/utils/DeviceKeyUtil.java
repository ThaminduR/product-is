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

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Plays the device side of the device registration protocol used by the DeviceRegistrationExecutor.
 * The device holds an EC P-256 key pair, sends its public key as the Base64 encoded X.509 SubjectPublicKeyInfo,
 * and proves possession of the private key by signing the server issued challenge.
 */
public class DeviceKeyUtil {

    private static final String KEY_ALGORITHM = "EC";
    private static final String CURVE = "secp256r1";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private DeviceKeyUtil() {

    }

    /**
     * Generate a new EC P-256 device key pair.
     *
     * @return Generated key pair.
     * @throws GeneralSecurityException If the key pair could not be generated.
     */
    public static KeyPair generateKeyPair() throws GeneralSecurityException {

        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        generator.initialize(new ECGenParameterSpec(CURVE));
        return generator.generateKeyPair();
    }

    /**
     * Encode the public key of the given key pair in the format expected by the server.
     *
     * @param keyPair Device key pair.
     * @return Standard Base64 encoded X.509 SubjectPublicKeyInfo of the public key.
     */
    public static String encodePublicKey(KeyPair keyPair) {

        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * Sign a registration challenge issued by the server.
     *
     * @param privateKey Device private key.
     * @param challenge  Base64URL encoded challenge as returned by the server.
     * @return Standard Base64 encoded DER SHA256withECDSA signature over the decoded challenge bytes.
     * @throws GeneralSecurityException If the challenge could not be signed.
     */
    public static String signChallenge(PrivateKey privateKey, String challenge) throws GeneralSecurityException {

        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(Base64.getUrlDecoder().decode(challenge));
        return Base64.getEncoder().encodeToString(signature.sign());
    }
}
