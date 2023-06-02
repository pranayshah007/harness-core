/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.serviceaccounts.persistence;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(PL)
@HarnessRepo
public interface ServiceAccountRepository
    extends PagingAndSortingRepository<ServiceAccountDBO, String>, ServiceAccountCustomRepository {
  Optional<ServiceAccountDBO> findByIdentifierAndScopeIdentifier(String identifier, String scopeIdentifier);

  Page<ServiceAccountDBO> findByScopeIdentifier(String scopeIdentifier, Pageable pageable);

  Optional<ServiceAccountDBO> deleteByIdentifierAndScopeIdentifier(String identifier, String scopeIdentifier);
}
