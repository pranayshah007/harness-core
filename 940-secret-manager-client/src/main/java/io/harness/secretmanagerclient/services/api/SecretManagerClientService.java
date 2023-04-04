/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagerclient.services.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DecryptedSecretValue;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;

@OwnedBy(PL)
public interface SecretManagerClientService {
  List<EncryptedDataDetail> getEncryptionDetails(NGAccess ngAccess, DecryptableEntity consumer);
  SecretManagerConfigDTO getSecretManager(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, boolean maskSecrets);
  SecretResponseWrapper getSecret(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);
  DecryptedSecretValue getDecryptedSecretValue(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);
}
