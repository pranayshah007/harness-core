/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Set;

@OwnedBy(HarnessTeam.PL)
public interface ChangeConsumerService {
  List<ACL> getAClsForRoleAssignment(RoleAssignmentDBO roleAssignmentDBO);
  List<ACL> getImplicitACLsForRoleAssignment(
      RoleAssignmentDBO roleAssignment, Set<String> addedPrincipals, Set<String> addedPermissions);
}
