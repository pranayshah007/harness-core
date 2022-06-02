/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.serviceoverride.spring;

import io.harness.annotation.HarnessRepo;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.repositories.serviceoverride.custom.ServiceOverrideRepositoryCustom;

import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
public interface ServiceOverrideRepository
    extends PagingAndSortingRepository<NGServiceOverridesEntity, String>, ServiceOverrideRepositoryCustom {
  Optional<NGServiceOverridesEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvironmentRefAndServiceRef(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef);
}
