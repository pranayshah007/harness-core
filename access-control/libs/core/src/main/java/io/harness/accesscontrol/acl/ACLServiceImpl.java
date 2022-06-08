/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl;

import static io.harness.accesscontrol.permissions.PermissionStatus.EXPERIMENTAL;
import static io.harness.accesscontrol.permissions.PermissionStatus.INACTIVE;
import static io.harness.accesscontrol.permissions.PermissionStatus.STAGING;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.persistence.ACLDAO;
import io.harness.accesscontrol.permissions.Permission;
import io.harness.accesscontrol.permissions.PermissionFilter;
import io.harness.accesscontrol.permissions.PermissionService;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@ValidateOnExecution
@Slf4j
public class ACLServiceImpl implements ACLService {
  private final ACLDAO aclDAO;
  private final PermissionService permissionService;
  private static final PermissionFilter permissionFilter =
      PermissionFilter.builder().statusFilter(Sets.newHashSet(INACTIVE, EXPERIMENTAL, STAGING)).build();
  private volatile Set<String> disabledPermissions;

  @Inject
  public ACLServiceImpl(ACLDAO aclDAO, PermissionService permissionService) {
    this.aclDAO = aclDAO;
    this.permissionService = permissionService;
  }

  private PermissionCheckResult getPermissionCheckResult(PermissionCheck permissionCheck, boolean permitted) {
    return PermissionCheckResult.builder()
        .permission(permissionCheck.getPermission())
        .resourceIdentifier(permissionCheck.getResourceIdentifier())
        .resourceScope(permissionCheck.getResourceScope())
        .resourceType(permissionCheck.getResourceType())
        .permitted(permitted)
        .build();
  }

  @Override
  public List<PermissionCheckResult> checkAccess(
      Principal principal, List<PermissionCheck> permissionChecks, List<Map<String, String>> attributes) {
    List<Boolean> allowedAccessList = aclDAO.checkForAccess(principal, permissionChecks, attributes);
    List<PermissionCheckResult> permissionCheckResults = new ArrayList<>();
    ensureDisabledPermissions();

    for (int i = 0; i < permissionChecks.size(); i++) {
      PermissionCheck permissionCheck = permissionChecks.get(i);
      if (disabledPermissions.contains(permissionCheck.getPermission())) {
        permissionCheckResults.add(getPermissionCheckResult(permissionCheck, true));
      } else {
        permissionCheckResults.add(getPermissionCheckResult(permissionCheck, allowedAccessList.get(i)));
      }
    }

    return permissionCheckResults;
  }

  private void ensureDisabledPermissions() {
    if (disabledPermissions == null) {
      updateDisabledPermissions();
    }
  }

  private void updateDisabledPermissions() {
    disabledPermissions =
        permissionService.list(permissionFilter).stream().map(Permission::getIdentifier).collect(Collectors.toSet());
  }
}
