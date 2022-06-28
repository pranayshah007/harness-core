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
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.ResourceInfo;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.conditions.ACLExpressionEvaluatorProvider;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.ACLDAO;
import io.harness.accesscontrol.permissions.Permission;
import io.harness.accesscontrol.permissions.PermissionFilter;
import io.harness.accesscontrol.permissions.PermissionService;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
  private final ACLExpressionEvaluatorProvider aclExpressionEvaluatorProvider;

  @Inject
  public ACLServiceImpl(ACLDAO aclDAO, PermissionService permissionService,
      ACLExpressionEvaluatorProvider aclExpressionEvaluatorProvider) {
    this.aclDAO = aclDAO;
    this.permissionService = permissionService;
    this.aclExpressionEvaluatorProvider = aclExpressionEvaluatorProvider;
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
  public List<PermissionCheckResult> checkAccess(Principal principal, List<PermissionCheck> permissionChecks,
      ResourceAttributeProvider resourceAttributeProvider) {
    List<List<ACL>> matchingACLs = aclDAO.getMatchingACLs(principal, permissionChecks);
    List<Boolean> allowedAccessList = checkAccessInternal(permissionChecks, matchingACLs, resourceAttributeProvider);

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

  private List<Boolean> checkAccessInternal(List<PermissionCheck> permissionChecks, List<List<ACL>> matchedACLs,
      ResourceAttributeProvider resourceAttributeProvider) {
    Set<ResourceInfo> resourcesWhoseAttributesAreRequired = new HashSet<>();
    Boolean[] accessCheckResults = new Boolean[permissionChecks.size()];
    for (int i = 0; i < permissionChecks.size(); i++) {
      if (matchedACLs.get(i).isEmpty()) {
        accessCheckResults[i] = Boolean.FALSE;
      } else if (matchedACLs.get(i).stream().anyMatch(acl -> !acl.isConditional())) {
        accessCheckResults[i] = Boolean.TRUE;
      } else if (isNotEmpty(permissionChecks.get(i).getResourceIdentifier())) {
        resourcesWhoseAttributesAreRequired.add(permissionChecks.get(i).getResourceInfo());
      } else {
        accessCheckResults[i] = Boolean.FALSE;
      }
    }

    Map<ResourceInfo, Map<String, String>> attributes = null;
    try {
      attributes = resourceAttributeProvider.getAttributes(resourcesWhoseAttributesAreRequired);
    } catch (Exception ex) {
      log.error("Exception occurred fetching attributes for {}", permissionChecks, ex);
    }
    for (int i = 0; i < permissionChecks.size(); i++) {
      if (accessCheckResults[i] == null) {
        accessCheckResults[i] = (attributes != null) ? evaluateAccessFromConditionalACLs(permissionChecks.get(i),
                                    matchedACLs.get(i), attributes.get(permissionChecks.get(i).getResourceInfo()))
                                                     : Boolean.FALSE;
      }
    }

    return Arrays.stream(accessCheckResults).collect(Collectors.toList());
  }

  private Boolean evaluateAccessFromConditionalACLs(
      PermissionCheck permissionCheck, List<ACL> matchedACLs, Map<String, String> resourceAttributes) {
    return matchedACLs.stream()
        .filter(ACL::isConditional)
        .anyMatch(acl
            -> aclExpressionEvaluatorProvider.get(permissionCheck, resourceAttributes)
                   .evaluateExpression(acl.getCondition()));
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
