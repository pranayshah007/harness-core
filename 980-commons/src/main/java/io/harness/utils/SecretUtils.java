/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.exception.ExplanationException.SECRET_DECRYPTED_VALUE_INVALID;
import static io.harness.exception.HintException.HINT_DECRYPTED_SECRET_VALUE;
import static io.harness.exception.NestedExceptionUtils.hintWithExplanationException;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class SecretUtils {
  public static void validateDecryptedValue(char[] decryptedValue, String identifier) {
    if (decryptedValue == null) {
      throw hintWithExplanationException(String.format(HINT_DECRYPTED_SECRET_VALUE, identifier),
          SECRET_DECRYPTED_VALUE_INVALID, new InvalidRequestException("Decrypted value of the secret is null."));
    }
  }
}
