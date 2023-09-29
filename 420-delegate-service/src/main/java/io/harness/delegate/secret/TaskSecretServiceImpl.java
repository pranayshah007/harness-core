/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.BaseNGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
public class TaskSecretServiceImpl implements TaskSecretService {
    @Inject
    public TaskSecretServiceImpl(@Named("PRIVILEGED") final SecretManagerClientService service) {
        this.ngSecretService = service;
    }

    private final SecretManagerClientService ngSecretService;

    @Override
    public List<EncryptedDataDetail> getEncryptionDetails(String secretId, Scope scope, String accountId, Optional<String> orgId, Optional<String> projectId) {
        SecretVariableDTO secretVariableDTO = getSecretVariableDTO(secretId, scope);
        return ngSecretService.getEncryptionDetails(
                BaseNGAccess.builder()
                        .accountIdentifier(accountId)
                        .orgIdentifier(orgId.orElseGet(null))
                        .projectIdentifier(projectId.orElseGet(null))
                        .build(),
                secretVariableDTO);
    }

    private SecretVariableDTO getSecretVariableDTO(String secretId, Scope scope) {
        return SecretVariableDTO.builder()
                .name(secretId)
                .secret(SecretRefData.builder()
                        .identifier(secretId)
                        .scope(scope)
                        .build())
                .type(SecretVariableDTO.Type.TEXT)
                .build();
    }
}
