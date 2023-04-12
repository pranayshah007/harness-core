/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar.util;

import lombok.experimental.UtilityClass;

import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

@UtilityClass
public class SlackWebhookEncryptionUtil {
  private final String secretKey = "1d69b3e5f5d24a5a94a65bb5b719d209";
  private static final String ALGORITHM = "AES";
  private static final String CIPHER_TRANSFORMATION = "AES/ECB/PKCS5Padding";

  public String encrypt(String webhookUrl) throws Exception {
    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
    Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
    byte[] encryptedBytes = cipher.doFinal(webhookUrl.getBytes());
    return Base64.getEncoder().encodeToString(encryptedBytes);
  }

  public String decrypt(String encryptedWebhookUrl) throws Exception {
    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
    Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
    byte[] decodedBytes = Base64.getDecoder().decode(encryptedWebhookUrl);
    byte[] decryptedBytes = cipher.doFinal(decodedBytes);
    return new String(decryptedBytes);
  }
}
