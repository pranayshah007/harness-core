/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.persistence.repositories;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@OwnedBy(HarnessTeam.PL)
public interface ACLRepository {
  long insertAllIgnoringDuplicates(List<ACL> acls);

  long deleteByRoleAssignmentId(String id);

  Set<ResourceSelector> getDistinctResourceSelectorsInACLs(String roleAssignmentId);

  long deleteByRoleAssignmentIdAndResourceSelectors(
      String roleAssignmentId, Set<ResourceSelector> resourceSelectorsToDelete);

  long deleteByRoleAssignmentIdAndPermissions(String roleAssignmentId, Set<String> permissions);

  long deleteByRoleAssignmentIdAndPrincipals(String roleAssignmentId, Set<String> principals);

  long deleteByRoleAssignmentIdAndImplicitForScope(String roleAssignmentId);

  List<String> getDistinctPermissionsInACLsForRoleAssignment(String roleAssignmentId);

  List<String> getDistinctPrincipalsInACLsForRoleAssignment(String id);

  List<ACL> getByAclQueryStringInAndEnabled(Collection<String> aclQueryStrings, boolean enabled);

  List<ACL> getByAclQueryStringIn(Collection<String> aclQueryStrings);

  void cleanCollection();

  void renameCollection(String newCollectionName);
}
