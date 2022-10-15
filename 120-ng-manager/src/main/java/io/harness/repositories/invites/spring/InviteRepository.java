/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.invites.spring;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.repositories.invites.custom.InviteRepositoryCustom;

import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
@OwnedBy(PL)
public interface InviteRepository extends PagingAndSortingRepository<Invite, String>, InviteRepositoryCustom {
  Optional<Invite> findByIdAndDeleted(String id, Boolean deleted);

  Optional<Invite> findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String email);
}
