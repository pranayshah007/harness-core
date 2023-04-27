/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.service.spring;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.service.entity.ServiceSequence;
import io.harness.repositories.service.custom.ServiceSequenceRepositoryCustom;

import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(HarnessTeam.CDP)
@HarnessRepo
public interface ServiceSequenceRepository
    extends PagingAndSortingRepository<ServiceSequence, String>, ServiceSequenceRepositoryCustom {
  Optional<ServiceSequence> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndServiceIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier);
}
