/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.ExplanationException.SECRET_DECRYPTED_VALUE_INVALID;
import static io.harness.exception.HintException.HINT_DECRYPTED_SECRET_VALUE;
import static io.harness.exception.NestedExceptionUtils.hintWithExplanationException;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class SecretUtils {
  public static final String BASE_64_SECRET_IDENTIFIER_PATTERN = "//base64:/%s";
  public static final String BASE_64_SECRET_IDENTIFIER_PREFIX = "//base64:/";
  public static final Pattern SECRET_PATTERN = Pattern.compile("\\$\\{ngSecretManager\\.obtain[^}]*}");

  public static void validateDecryptedValue(char[] decryptedValue, String identifier) {
    if (decryptedValue == null) {
      throw hintWithExplanationException(String.format(HINT_DECRYPTED_SECRET_VALUE, identifier),
          SECRET_DECRYPTED_VALUE_INVALID, new InvalidRequestException("Decrypted value of the secret is null."));
    }
  }

  public static String getBase64SecretIdentifier(final String secretIdentifier) {
    return format(BASE_64_SECRET_IDENTIFIER_PATTERN, secretIdentifier);
  }

  public static boolean isBase64SecretIdentifier(final String secretIdentifier) {
    return secretIdentifier.startsWith(BASE_64_SECRET_IDENTIFIER_PREFIX);
  }

  public static boolean containsSecret(String content) {
    if (isEmpty(content)) {
      return false;
    }

    return SECRET_PATTERN.matcher(content).find();
  }
}
