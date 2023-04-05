/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.services;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DecryptedSecretValue;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.remote.client.NGRestClientExecutor;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;

@OwnedBy(PL)
@Singleton
public class PrivilegedSecretNGManagerClientServiceImpl implements SecretManagerClientService {
  @Inject @Named("PRIVILEGED") private SecretNGManagerClient secretManagerClient;
  @Inject NGRestClientExecutor restClientExecutor;
  @Override
  public List<EncryptedDataDetail> getEncryptionDetails(NGAccess ngAccess, DecryptableEntity consumer) {
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(ngAccess.getAccountIdentifier())
                                    .orgIdentifier(ngAccess.getOrgIdentifier())
                                    .projectIdentifier(ngAccess.getProjectIdentifier())
                                    .identifier(ngAccess.getIdentifier())
                                    .build();
    return restClientExecutor.getResponse(secretManagerClient.getEncryptionDetails(ngAccess.getAccountIdentifier(),
        NGAccessWithEncryptionConsumer.builder().ngAccess(baseNGAccess).decryptableEntity(consumer).build()));
  }

  @Override
  public SecretManagerConfigDTO getSecretManager(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, boolean maskSecrets) {
    return getResponse(secretManagerClient.getSecretManager(
        identifier, accountIdentifier, orgIdentifier, projectIdentifier, maskSecrets));
  }

  @Override
  public SecretResponseWrapper getSecret(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return getResponse(secretManagerClient.getSecret(identifier, accountIdentifier, orgIdentifier, projectIdentifier));
  }

  @Override
  public DecryptedSecretValue getDecryptedSecretValue(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return getResponse(
        secretManagerClient.getDecryptedSecretValue(identifier, accountIdentifier, orgIdentifier, projectIdentifier));
  }
}
