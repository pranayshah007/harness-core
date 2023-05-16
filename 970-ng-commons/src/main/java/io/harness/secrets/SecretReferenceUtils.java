/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import io.harness.beans.IdentifierRef;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.NGAccess;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.IdentifierRefHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SecretReferenceUtils {
  public static List<String> getAllSecretFQNs(Map<String, SecretRefData> secrets, NGAccess baseNGAccess) {
    List<IdentifierRef> secretIdentifiers = getAllSecretIdentifiers(secrets, baseNGAccess);
    List<String> secretFQNs = new ArrayList<>();
    for (IdentifierRef secretIdentifier : secretIdentifiers) {
      if (secretIdentifier != null) {
        secretFQNs.add(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(baseNGAccess.getAccountIdentifier(),
            secretIdentifier.getOrgIdentifier(), secretIdentifier.getProjectIdentifier(),
            secretIdentifier.getIdentifier()));
      }
    }
    return secretFQNs;
  }

  private List<IdentifierRef> getAllSecretIdentifiers(Map<String, SecretRefData> secrets, NGAccess baseNGAccess) {
    List<IdentifierRef> secretIdentifierRef = new ArrayList<>();
    for (Map.Entry<String, SecretRefData> secret : secrets.entrySet()) {
      if (secret != null && secret.getValue() != null && !secret.getValue().isNull()) {
        secretIdentifierRef.add(IdentifierRefHelper.getIdentifierRef(secret.getValue().toSecretRefStringValue(),
            baseNGAccess.getAccountIdentifier(), baseNGAccess.getOrgIdentifier(), baseNGAccess.getProjectIdentifier()));
      }
    }
    return secretIdentifierRef;
  }
}
