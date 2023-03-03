/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.persistence.repositories;

import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(HarnessTeam.PL)
@HarnessRepo
public interface RoleAssignmentRepository
    extends PagingAndSortingRepository<RoleAssignmentDBO, String>, RoleAssignmentCustomRepository {
  Optional<RoleAssignmentDBO> findByIdentifierAndScopeIdentifier(String identifier, String parentIdentifier);

  Optional<RoleAssignmentDBO> deleteByIdentifierAndScopeIdentifier(String identifier, String parentIdentifier);

  List<RoleAssignmentDBO> findByPrincipalIdentifierAndPrincipalType(String principal, PrincipalType principalType);
}
