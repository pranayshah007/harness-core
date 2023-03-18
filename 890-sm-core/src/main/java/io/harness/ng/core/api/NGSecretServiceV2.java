/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.models.Secret;
import io.harness.ng.core.remote.SecretValidationMetaData;
import io.harness.ng.core.remote.SecretValidationResultDTO;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public interface NGSecretServiceV2 {
  boolean validateTheIdentifierIsUnique(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);

  Optional<Secret> get(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);

  Optional<Secret> get(IdentifierRef identifierRef);

  boolean delete(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, boolean forceDelete);

  Secret create(String accountIdentifier, SecretDTOV2 dto, boolean draft);

  Secret update(String accountIdentifier, SecretDTOV2 dto, boolean draft);

  SecretValidationResultDTO validateSecret(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, SecretValidationMetaData metadata);

  long count(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  Page<Secret> list(Criteria criteria, Pageable pageable);

  List<Secret> getPermitted(Collection<Secret> secrets);

  Page<Secret> getPaginatedResult(List<Secret> unpagedSecrets, int page, int size);

  Long countSecrets(String accountIdentifier);
}
