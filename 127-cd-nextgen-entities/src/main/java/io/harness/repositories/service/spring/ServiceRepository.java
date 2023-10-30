/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.service.spring;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.repositories.service.custom.ServiceRepositoryCustom;

import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@HarnessRepo
public interface ServiceRepository extends PagingAndSortingRepository<ServiceEntity, String>, ServiceRepositoryCustom {
  Optional<ServiceEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      boolean notDeleted, boolean loadFromCache, boolean loadFromFallbackBranch, boolean getMetadataOnly);

  Optional<ServiceEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier);
}
