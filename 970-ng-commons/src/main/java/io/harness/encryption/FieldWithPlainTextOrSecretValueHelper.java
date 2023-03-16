/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryption;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FieldWithPlainTextOrSecretValueHelper {
  public char[] getValueFromPlainTextOrSecretRef(String plainText, SecretRefData secretRefData) {
    if (isNotBlank(plainText) && !isNullSecretRefData(secretRefData)) {
      throw new InvalidArgumentsException("Both plain text and secret value specified for the field");
    }
    if (isBlank(plainText) && isNullSecretRefData(secretRefData)) {
      throw new InvalidArgumentsException("Both plain text and secret value cannot be null for the field");
    }
    if (isNotBlank(plainText)) {
      return plainText.toCharArray();
    }
    return secretRefData.getDecryptedValue();
  }

  private static boolean isNullSecretRefData(SecretRefData secretRefData) {
    return secretRefData == null || secretRefData.isNull();
  }

  public String getSecretAsStringFromPlainTextOrSecretRef(String plainText, SecretRefData secretRefData) {
    char[] secretValue = getValueFromPlainTextOrSecretRef(plainText, secretRefData);
    return EmptyPredicate.isNotEmpty(secretValue) ? String.valueOf(secretValue) : null;
  }
}
