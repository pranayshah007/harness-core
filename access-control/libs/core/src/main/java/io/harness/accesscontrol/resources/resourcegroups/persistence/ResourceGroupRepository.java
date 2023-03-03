/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups.persistence;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(HarnessTeam.PL)
@HarnessRepo
public interface ResourceGroupRepository
    extends PagingAndSortingRepository<ResourceGroupDBO, String>, ResourceGroupCustomRepository {
  Optional<ResourceGroupDBO> findByIdentifierAndScopeIdentifier(String identifier, String scopeIdentifier);

  Page<ResourceGroupDBO> findByScopeIdentifier(String scopeIdentifier, Pageable pageable);

  Optional<ResourceGroupDBO> deleteByIdentifierAndScopeIdentifier(String identifier, String scopeIdentifier);
}
