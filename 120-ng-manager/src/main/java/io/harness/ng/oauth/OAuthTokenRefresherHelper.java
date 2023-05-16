/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.models.Secret;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class OAuthTokenRefresherHelper {
  @Inject private SecretManagerClientService ngSecretService;
  @Inject private SecretCrudService ngSecretCrudService;

  List<EncryptedDataDetail> getEncryptionDetails(
      DecryptableEntity decryptableEntity, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    List<EncryptedDataDetail> authenticationEncryptedDataDetails =
        ngSecretService.getEncryptionDetails(ngAccess, decryptableEntity);
    if (isNotEmpty(authenticationEncryptedDataDetails)) {
      encryptedDataDetails.addAll(authenticationEncryptedDataDetails);
    }

    return encryptedDataDetails;
  }

  void updateContext() {
    Principal principal = SecurityContextBuilder.getPrincipal();
    if (principal == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
      SecurityContextBuilder.setContext(principal);
    }
    final GitEntityInfo emptyInfo = GitEntityInfo.builder().build();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(emptyInfo).build());
    }
  }

  public SecretDTOV2 getSecretSecretValue(Scope scope, SecretRefData token) {
    SecretResponseWrapper tokenWrapper = ngSecretCrudService
                                             .get(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
                                                 scope.getProjectIdentifier(), token.getIdentifier())
                                             .orElse(null);

    if (tokenWrapper == null) {
      log.error("Error in secret with identifier: {}", token.getIdentifier());
      return null;
    }

    return tokenWrapper.getSecret();
  }

  public void updateSecretSecretValue(Scope scope, SecretDTOV2 secretDTOV2, String newSecret) {
    SecretTextSpecDTO secretSpecDTO = (SecretTextSpecDTO) secretDTOV2.getSpec();
    secretSpecDTO.setValue(newSecret);
    secretDTOV2.setSpec(secretSpecDTO);

    Secret secret = Secret.fromDTO(secretDTOV2);
    try {
      ngSecretCrudService.update(scope.getAccountIdentifier(), secret.getOrgIdentifier(), secret.getProjectIdentifier(),
          secretDTOV2.getIdentifier(), secretDTOV2);
    } catch (Exception ex) {
      log.error("Failed to update token in DB, secretDTO: {}", secretDTOV2, ex);
    }
  }
}
