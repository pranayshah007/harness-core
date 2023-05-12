/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ipallowlist.spring;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ipallowlist.entity.IPAllowlistEntity;
import io.harness.repositories.ipallowlist.custom.IPAllowlistRepositoryCustom;

import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(PL)
@HarnessRepo
public interface IPAllowlistRepository
    extends PagingAndSortingRepository<IPAllowlistEntity, String>, IPAllowlistRepositoryCustom {
  Optional<IPAllowlistEntity> findByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);
  long deleteByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);
}
