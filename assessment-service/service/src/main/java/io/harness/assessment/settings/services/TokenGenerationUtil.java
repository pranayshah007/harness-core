/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SEI)
@UtilityClass
public class TokenGenerationUtil {
  public static final String INVITE_SALT_STRING = "salt";
  public static final String RESULT_SALT_STRING = "salt";

  static String generateInviteFromEmail(String userEmail, String assessmentId) throws NoSuchAlgorithmException {
    String encoded;
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    String text = userEmail + assessmentId + INVITE_SALT_STRING;
    byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
    encoded = Base64.getUrlEncoder().encodeToString(hash);
    return encoded;
  }

  static String generateResultLinkFromEmail(String userId, String assessmentResponseId)
      throws NoSuchAlgorithmException {
    String encoded;
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    String text = userId + assessmentResponseId + RESULT_SALT_STRING;
    byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
    encoded = Base64.getUrlEncoder().encodeToString(hash);
    return encoded;
  }
}
