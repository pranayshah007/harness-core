/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.encryption;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@UtilityClass
public class EncryptionUtils {
  private static final String AES_ALGORITHM = "AES";
  private static final String AES_CIPHER = "AES/CBC/PKCS5Padding";

  public static String encryptString(String unencrypted, String sharedKey) {
    try {
      // Generate a random IV (Initialization Vector)
      byte[] ivBytes = generateRandomIV();

      // Create a SecretKey using the shared key
      SecretKeySpec secretKeySpec = generateSecretKey(sharedKey);

      // Initialize the Cipher with AES algorithm and CBC mode
      Cipher cipher = Cipher.getInstance(AES_CIPHER);
      cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(ivBytes));

      // Encrypt the API response
      byte[] encryptedBytes = cipher.doFinal(unencrypted.getBytes(StandardCharsets.UTF_8));

      // Combine IV and encrypted data and encode them as Base64
      byte[] combinedBytes = new byte[ivBytes.length + encryptedBytes.length];
      System.arraycopy(ivBytes, 0, combinedBytes, 0, ivBytes.length);
      System.arraycopy(encryptedBytes, 0, combinedBytes, ivBytes.length, encryptedBytes.length);
      return Base64.getEncoder().encodeToString(combinedBytes);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private static byte[] generateRandomIV() {
    byte[] iv = new byte[16];
    new SecureRandom().nextBytes(iv);
    return iv;
  }

  private static SecretKeySpec generateSecretKey(String sharedKey) throws NoSuchAlgorithmException {
    // Use a secure hash function to generate a 128-bit key from the shared key
    MessageDigest sha = MessageDigest.getInstance("SHA-256");
    byte[] keyBytes = sha.digest(sharedKey.getBytes(StandardCharsets.UTF_8));
    return new SecretKeySpec(keyBytes, AES_ALGORITHM);
  }
}
