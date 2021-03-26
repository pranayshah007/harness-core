package io.harness.accesscontrol.acl.daos;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.Principal;
import io.harness.accesscontrol.acl.models.ACL;
import io.harness.accesscontrol.clients.PermissionCheckDTO;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;

@OwnedBy(PL)
public interface ACLDAO {
  List<ACL> get(Principal principal, List<PermissionCheckDTO> permissionsRequired);

  ACL save(ACL acl);

  long insertAllIgnoringDuplicates(List<ACL> acls);

  long saveAll(List<ACL> acls);

  void deleteAll(List<ACL> acls);

  long deleteByRoleAssignmentId(String roleAssignmentId);

  List<ACL> getByUserGroup(String scopeIdentifier, String userGroupIdentifier);

  List<ACL> getByRole(String scopeIdentifier, String identifier, boolean managed);

  List<ACL> getByResourceGroup(String scopeIdentifier, String identifier, boolean managed);

  List<ACL> getByRoleAssignmentId(String id);
}