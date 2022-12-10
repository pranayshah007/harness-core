/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryption;

import io.harness.ng.core.BaseNGAccess;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecretRefHelper {
  public SecretRefData createSecretRef(String secretConfigString) {
    return new SecretRefData(secretConfigString);
  }

  public SecretRefData createSecretRef(String secretConfigString, Scope scope, char[] decryptedValue) {
    return new SecretRefData(secretConfigString, scope, decryptedValue);
  }

  public String getSecretConfigString(SecretRefData secretRefData) {
    if (secretRefData == null) {
      return null;
    }
    return secretRefData.toSecretRefStringValue();
  }

  public BaseNGAccess getScopeIdentifierForSecretRef(
      SecretRefData secretRefData, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Scope secretRefScope = secretRefData.getScope();
    String orgIdBasedOnScope = secretRefScope.equals(Scope.ACCOUNT) ? null : orgIdentifier;
    String projectIdBasedOnScope = secretRefScope.equals(Scope.PROJECT) ? projectIdentifier : null;
    return BaseNGAccess.builder()
        .identifier(secretRefData.getIdentifier())
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdBasedOnScope)
        .projectIdentifier(projectIdBasedOnScope)
        .build();
  }
}
