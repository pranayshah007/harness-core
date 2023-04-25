/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.rbac;

import static io.harness.ccm.rbac.CCMRbacPermissions.BUDGET_CREATE_AND_EDIT;
import static io.harness.ccm.rbac.CCMRbacPermissions.BUDGET_DELETE;
import static io.harness.ccm.rbac.CCMRbacPermissions.BUDGET_VIEW;
import static io.harness.ccm.rbac.CCMRbacPermissions.COST_CATEGORY_CREATE_AND_EDIT;
import static io.harness.ccm.rbac.CCMRbacPermissions.COST_CATEGORY_DELETE;
import static io.harness.ccm.rbac.CCMRbacPermissions.COST_CATEGORY_VIEW;
import static io.harness.ccm.rbac.CCMRbacPermissions.COST_OVERVIEW_VIEW;
import static io.harness.ccm.rbac.CCMRbacPermissions.CURRENCY_PREFERENCE_SET_AND_EDIT;
import static io.harness.ccm.rbac.CCMRbacPermissions.CURRENCY_PREFERENCE_VIEW;
import static io.harness.ccm.rbac.CCMRbacPermissions.FOLDER_CREATE_AND_EDIT;
import static io.harness.ccm.rbac.CCMRbacPermissions.FOLDER_DELETE;
import static io.harness.ccm.rbac.CCMRbacPermissions.FOLDER_VIEW;
import static io.harness.ccm.rbac.CCMRbacPermissions.PERSPECTIVE_CREATE_AND_EDIT;
import static io.harness.ccm.rbac.CCMRbacPermissions.PERSPECTIVE_DELETE;
import static io.harness.ccm.rbac.CCMRbacPermissions.PERSPECTIVE_VIEW;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_CREATE_AND_EDIT;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_DELETE;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_ENFORCEMENT_CREATE_AND_EDIT;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_ENFORCEMENT_DELETE;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_ENFORCEMENT_VIEW;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_EXECUTE;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_SET_CREATE_AND_EDIT;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_SET_DELETE;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_SET_VIEW;
import static io.harness.ccm.rbac.CCMRbacPermissions.RULE_VIEW;
import static io.harness.ccm.rbac.CCMResources.COST_CATEGORY;
import static io.harness.ccm.rbac.CCMResources.CURRENCY_PREFERENCE;
import static io.harness.ccm.rbac.CCMResources.FOLDER;
import static io.harness.ccm.rbac.CCMResources.GOVERNANCE_CONNECTOR;
import static io.harness.ccm.rbac.CCMResources.GOVERNANCE_RULE;
import static io.harness.ccm.rbac.CCMResources.GOVERNANCE_RULE_ENFORCEMENT;
import static io.harness.ccm.rbac.CCMResources.GOVERNANCE_RULE_SET;
import static io.harness.ccm.rbac.CCMResources.PERSPECTIVE;

import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CCMRbacHelperImpl implements CCMRbacHelper {
  @Inject AccessControlClient accessControlClient;
  public static final String PERMISSION_MISSING_MESSAGE = "Missing permission %s on %s";
  private static final String DESCRIPTIVE_PERMISSION_MISSING_MESSAGE =
      "You do not have Permission to view %s. Permission to view %s is obtained by providing %s Permission against %s.";
  private static final String ANOMALIES = "Anomalies";
  private static final String RECOMMENDATIONS = "Recommendations";
  private static final String VIEW_PERMISSION = "View";
  private static final String EDIT_PERMISSION = "Create/Edit";
  private static final String DELETE_PERMISSION = "Delete";
  private static final String EXECUTE_PERMISSION = "Execute";
  private static final String RESOURCE_COST_CATEGORY = "Cost Categories";
  public static final String RESOURCE_FOLDER = "Folders";
  private static final String RESOURCE_PERSPECTIVE = "Perspectives";
  private static final String RESOURCE_BUDGET = "Budgets";
  private static final String RESOURCE_CURRENCY_PREFERENCES = "Currency Preferences";
  public static final String RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE = "CloudAssetGovernanceRule";
  public static final String RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE_SET = "CloudAssetGovernanceRuleSet";
  public static final String RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT =
      "CloudAssetGovernanceRuleEnforcement";
  private static final String ALL_RESOURCES = "All Resources";

  @Override
  public void checkFolderViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, folderId), FOLDER_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_FOLDER));
  }

  @Override
  public void checkFolderEditPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, folderId), FOLDER_CREATE_AND_EDIT,
        String.format(PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_FOLDER));
  }

  @Override
  public void checkFolderDeletePermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, folderId), FOLDER_DELETE,
        String.format(PERMISSION_MISSING_MESSAGE, DELETE_PERMISSION, RESOURCE_FOLDER));
  }

  @Override
  public Set<String> checkFolderIdsGivenPermission(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Set<String> folderIds, String permission) {
    // We check if user have access to all the folders
    // We return all folderIds as is in that case
    if (accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(FOLDER, null), permission)) {
      return folderIds;
    }
    List<PermissionCheckDTO> permissionCheckDTOList =
        folderIds.stream()
            .map(folderId
                -> PermissionCheckDTO.builder()
                       .resourceScope(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier))
                       .resourceType(FOLDER)
                       .resourceIdentifier(folderId)
                       .permission(permission)
                       .build())
            .collect(Collectors.toList());

    List<AccessControlDTO> accessCheckResponseDTO =
        accessControlClient.checkForAccess(permissionCheckDTOList).getAccessControlList();

    if (accessCheckResponseDTO == null) {
      return null;
    }

    return accessCheckResponseDTO.stream()
        .filter(accessControlDTO -> accessControlDTO.isPermitted())
        .map(accessControlDTO -> accessControlDTO.getResourceIdentifier())
        .collect(Collectors.toSet());
  }

  @Override
  public void checkPerspectiveViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, folderId), PERSPECTIVE_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_PERSPECTIVE));
  }

  @Override
  public boolean hasPerspectiveViewOnAllResources(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, null), PERSPECTIVE_VIEW);
  }

  @Override
  public void checkPerspectiveEditPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, folderId), PERSPECTIVE_CREATE_AND_EDIT,
        String.format(PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_PERSPECTIVE));
  }

  @Override
  public void checkPerspectiveDeletePermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, folderId), PERSPECTIVE_DELETE,
        String.format(PERMISSION_MISSING_MESSAGE, DELETE_PERMISSION, RESOURCE_PERSPECTIVE));
  }

  @Override
  public void checkBudgetViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, folderId), BUDGET_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_BUDGET));
    checkPerspectiveViewPermission(accountIdentifier, orgIdentifier, projectIdentifier, folderId);
  }

  @Override
  public void checkBudgetEditPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, folderId), BUDGET_CREATE_AND_EDIT,
        String.format(PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_BUDGET));
  }

  @Override
  public void checkBudgetDeletePermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, folderId), BUDGET_DELETE,
        String.format(PERMISSION_MISSING_MESSAGE, DELETE_PERMISSION, RESOURCE_BUDGET));
  }

  @Override
  public void checkCostCategoryViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(COST_CATEGORY, null), COST_CATEGORY_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_COST_CATEGORY));
  }

  @Override
  public void checkCostCategoryEditPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(COST_CATEGORY, null), COST_CATEGORY_CREATE_AND_EDIT,
        String.format(PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_COST_CATEGORY));
  }

  @Override
  public void checkCostCategoryDeletePermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(COST_CATEGORY, null), COST_CATEGORY_DELETE,
        String.format(PERMISSION_MISSING_MESSAGE, DELETE_PERMISSION, RESOURCE_COST_CATEGORY));
  }

  @Override
  public void checkRecommendationsViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId) {
    checkPerspectiveViewPermission(accountIdentifier, orgIdentifier, projectIdentifier, folderId);
  }

  @Override
  public void checkAnomalyViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId) {
    checkPerspectiveViewPermission(accountIdentifier, orgIdentifier, projectIdentifier, folderId);
  }

  @Override
  public boolean hasCostOverviewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(PERSPECTIVE, null), COST_OVERVIEW_VIEW);
  }

  @Override
  public void checkCurrencyPreferenceViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(CURRENCY_PREFERENCE, null), CURRENCY_PREFERENCE_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_CURRENCY_PREFERENCES));
  }

  @Override
  public void checkCurrencyPreferenceEditPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(CURRENCY_PREFERENCE, null), CURRENCY_PREFERENCE_SET_AND_EDIT,
        String.format(PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_CURRENCY_PREFERENCES));
  }

  @Override
  public void checkRuleEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(GOVERNANCE_RULE, null), RULE_CREATE_AND_EDIT,
        String.format(PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE));
  }

  @Override
  public void checkRuleViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(GOVERNANCE_RULE, null), RULE_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE));
  }

  @Override
  public void checkRuleDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(GOVERNANCE_RULE, null), RULE_DELETE,
        String.format(PERMISSION_MISSING_MESSAGE, DELETE_PERMISSION, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE));
  }

  @Override
  public void checkRuleExecutePermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String ruleId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(GOVERNANCE_RULE, ruleId), RULE_EXECUTE,
        String.format(PERMISSION_MISSING_MESSAGE, EXECUTE_PERMISSION, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE));
  }

  @Override
  public Set<String> checkRuleIdsGivenPermission(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Set<String> ruleIds, String permission) {
    if (accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(GOVERNANCE_RULE, null), permission)) {
      return ruleIds;
    }
    List<PermissionCheckDTO> permissionCheckDTOList =
        ruleIds.stream()
            .map(ruleId
                -> PermissionCheckDTO.builder()
                       .resourceScope(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier))
                       .resourceType(GOVERNANCE_RULE)
                       .resourceIdentifier(ruleId)
                       .permission(permission)
                       .build())
            .collect(Collectors.toList());
    List<AccessControlDTO> accessCheckResponseDTO =
        accessControlClient.checkForAccess(permissionCheckDTOList).getAccessControlList();
    if (accessCheckResponseDTO == null) {
      return null;
    }
    return accessCheckResponseDTO.stream()
        .filter(accessControlDTO -> accessControlDTO.isPermitted())
        .map(accessControlDTO -> accessControlDTO.getResourceIdentifier())
        .collect(Collectors.toSet());
  }

  @Override
  public void checkAccountExecutePermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String accountId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(GOVERNANCE_CONNECTOR, accountId), RULE_EXECUTE,
        String.format(PERMISSION_MISSING_MESSAGE, EXECUTE_PERMISSION, GOVERNANCE_CONNECTOR));
  }

  @Override
  public Set<String> checkAccountIdsGivenPermission(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Set<String> accountIds, String permission) {
    if (accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(GOVERNANCE_CONNECTOR, null), permission)) {
      return accountIds;
    }
    List<PermissionCheckDTO> permissionCheckDTOList =
        accountIds.stream()
            .map(accountId
                -> PermissionCheckDTO.builder()
                       .resourceScope(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier))
                       .resourceType(GOVERNANCE_CONNECTOR)
                       .resourceIdentifier(accountId)
                       .permission(permission)
                       .build())
            .collect(Collectors.toList());
    List<AccessControlDTO> accessCheckResponseDTO =
        accessControlClient.checkForAccess(permissionCheckDTOList).getAccessControlList();
    if (accessCheckResponseDTO == null) {
      return null;
    }
    return accessCheckResponseDTO.stream()
        .filter(accessControlDTO -> accessControlDTO.isPermitted())
        .map(accessControlDTO -> accessControlDTO.getResourceIdentifier())
        .collect(Collectors.toSet());
  }

  @Override
  public void checkRuleSetEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(GOVERNANCE_RULE_SET, null), RULE_SET_CREATE_AND_EDIT,
        String.format(PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE_SET));
  }

  @Override
  public void checkRuleSetViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(GOVERNANCE_RULE_SET, null), RULE_SET_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE_SET));
  }

  @Override
  public void checkRuleSetDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(GOVERNANCE_RULE_SET, null), RULE_SET_DELETE,
        String.format(PERMISSION_MISSING_MESSAGE, DELETE_PERMISSION, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE_SET));
  }

  @Override
  public void checkRuleEnforcementEditPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(GOVERNANCE_RULE_ENFORCEMENT, null), RULE_ENFORCEMENT_CREATE_AND_EDIT,
        String.format(
            PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT));
  }

  @Override
  public void checkRuleEnforcementViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(GOVERNANCE_RULE_ENFORCEMENT, null), RULE_ENFORCEMENT_VIEW,
        String.format(
            PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT));
  }

  @Override
  public void checkRuleEnforcementDeletePermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(GOVERNANCE_RULE_ENFORCEMENT, null), RULE_ENFORCEMENT_DELETE,
        String.format(
            PERMISSION_MISSING_MESSAGE, DELETE_PERMISSION, RESOURCE_CCM_CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT));
  }
}
