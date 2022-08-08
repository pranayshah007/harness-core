/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.infrastructure.spring;

import io.harness.annotation.HarnessRepo;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.repositories.infrastructure.custom.InfrastructureRepositoryCustom;

import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
public interface InfrastructureRepository
    extends PagingAndSortingRepository<InfrastructureEntity, String>, InfrastructureRepositoryCustom {
  Optional<InfrastructureEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String envIdentifier,
      String infraIdentifier);
}
