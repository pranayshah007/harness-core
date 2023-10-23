/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.utils;

import io.harness.beans.IdentifierRef;
import io.harness.encryption.Scope;
import io.harness.utils.IdentifierRefHelper;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class CCMKubernetesConnectorHelper {
  public IdentifierRef getReferencedConnectorIdentifier(@NotNull String scopedConnectorIdentifier,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    final String sanitizedScopedConnectorIdentifier = sanitizeK8sConnectorScope(scopedConnectorIdentifier);

    return IdentifierRefHelper.getIdentifierRef(
        sanitizedScopedConnectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
  }

  /**
   * Should return Account scoped ConnectorDisconnectHandler identifier when the scope is not explicitly defined,
   * since by default an identifier without an explicit scope is assumed at Project level.
   */
  @NotNull
  private String sanitizeK8sConnectorScope(@NotNull String scopedConnectorIdentifier) {
    String[] scopedIdentifier = scopedConnectorIdentifier.split("\\.");
    if (scopedIdentifier.length == 1) {
      return String.format("%s.%s", Scope.ACCOUNT.getYamlRepresentation(), scopedConnectorIdentifier);
    }

    return scopedConnectorIdentifier;
  }
}
