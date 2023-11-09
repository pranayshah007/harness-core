/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import io.harness.beans.IdentifierRef;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.EncryptDecryptException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskSecretServiceImpl implements TaskSecretService {
  @Inject
  public TaskSecretServiceImpl(@Named("PRIVILEGED") final SecretManagerClientService service) {
    this.ngSecretService = service;
  }

  private final SecretManagerClientService ngSecretService;

  @Override
  public Optional<EncryptedDataDetail> getEncryptionDetail(@NotNull IdentifierRef secretIdentifierRef) {
    SecretVariableDTO secretVariableDTO = SecretVariableDTO.builder()
                                              .name(secretIdentifierRef.getIdentifier())
                                              .secret(SecretRefData.builder()
                                                          .identifier(secretIdentifierRef.getIdentifier())
                                                          .scope(secretIdentifierRef.getScope())
                                                          .build())
                                              .type(SecretVariableDTO.Type.TEXT)
                                              .build();
    var dataDetails =
        ngSecretService.getEncryptionDetails(BaseNGAccess.builder()
                                                 .accountIdentifier(secretIdentifierRef.getAccountIdentifier())
                                                 .orgIdentifier(secretIdentifierRef.getOrgIdentifier())
                                                 .projectIdentifier(secretIdentifierRef.getProjectIdentifier())
                                                 .build(),
            secretVariableDTO);
    if (dataDetails.size() == 1) {
      return Optional.of(dataDetails.get(0));
    } else {
      // Should not reach here
      final String message =
          String.format("Fetching encryption details returned unexpected number of results for secret %s",
              secretIdentifierRef.getFullyQualifiedName());
      log.error(message);
      throw new EncryptDecryptException(message);
    }
  }

  @Override
  public List<Optional<EncryptedDataDetail>> getEncryptionDetails(@NotNull List<IdentifierRef> secretIds) {
    return secretIds.stream().map(this::getEncryptionDetail).collect(Collectors.toList());
  }
}
