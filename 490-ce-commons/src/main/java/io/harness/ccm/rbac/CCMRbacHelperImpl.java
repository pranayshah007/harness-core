/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.rbac;

import static io.harness.ccm.rbac.CCMRbacPermissions.*;
import static io.harness.ccm.rbac.CCMResources.BUDGET;
import static io.harness.ccm.rbac.CCMResources.COST_CATEGORY;
import static io.harness.ccm.rbac.CCMResources.FOLDER;
import static io.harness.ccm.rbac.CCMResources.PERSPECTIVE;
import static io.harness.ccm.rbac.CCMResources.POLICY;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;

import com.google.inject.Inject;

public class CCMRbacHelperImpl implements CCMRbacHelper {
  @Inject AccessControlClient accessControlClient;
  private static final String PERMISSION_MISSING_MESSAGE = "User not Authorized: Missing permission %s on %s";
  private static final String VIEW_PERMISSION = "View";
  private static final String EDIT_PERMISSION = "Create/Edit";
  private static final String DELETE_PERMISSION = "Delete";
  private static final String RESOURCE_COST_CATEGORY = "Cost Categories";
  private static final String RESOURCE_FOLDER = "Folders";
  private static final String RESOURCE_PERSPECTIVE = "Perspectives";
  private static final String RESOURCE_BUDGET = "Budgets";
  private static final String RESOURCE_POLICY = "Policies";

  @Override
  public void checkFolderViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, null), FOLDER_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_FOLDER));
  }

  @Override
  public void checkFolderEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, null), FOLDER_CREATE_AND_EDIT,
        String.format(PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_FOLDER));
  }

  @Override
  public void checkFolderDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, null), FOLDER_DELETE,
        String.format(PERMISSION_MISSING_MESSAGE, DELETE_PERMISSION, RESOURCE_FOLDER));
  }

  @Override
  public void checkPerspectiveViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(PERSPECTIVE, null), PERSPECTIVE_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_PERSPECTIVE));
    // Check if user has folder view permission
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(FOLDER, null), FOLDER_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_FOLDER));
  }

  @Override
  public void checkPerspectiveEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(PERSPECTIVE, null), PERSPECTIVE_CREATE_AND_EDIT,
        String.format(PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_PERSPECTIVE));
  }

  @Override
  public void checkPerspectiveDeletePermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(PERSPECTIVE, null), PERSPECTIVE_DELETE,
        String.format(PERMISSION_MISSING_MESSAGE, DELETE_PERMISSION, RESOURCE_PERSPECTIVE));
  }

  @Override
  public void checkBudgetViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(BUDGET, null), BUDGET_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_BUDGET));
    checkPerspectiveOnlyViewPermission(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public void checkBudgetEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(BUDGET, null), BUDGET_CREATE_AND_EDIT,
        String.format(PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_BUDGET));
  }

  @Override
  public void checkBudgetDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(BUDGET, null), BUDGET_DELETE,
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
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    checkPerspectiveOnlyViewPermission(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public void checkAnomalyViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    checkPerspectiveOnlyViewPermission(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public boolean hasCostOverviewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(PERSPECTIVE, null), COST_OVERVIEW_VIEW);
  }

  @Override
  public void checkPolicyEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(POLICY, null), POLICY_CREATE_AND_EDIT,
            String.format(PERMISSION_MISSING_MESSAGE, EDIT_PERMISSION, RESOURCE_POLICY));

  }
  @Override
  public void checkPolicyViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {

    System.out.println("in checkPolicyViewPermission ");
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(POLICY, null), POLICY_VIEW,
            String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_POLICY));
  }
  @Override
  public void checkPolicyDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(POLICY, null), POLICY_DELETE,
            String.format(PERMISSION_MISSING_MESSAGE, DELETE_PERMISSION, RESOURCE_POLICY));
  }

  public void checkPerspectiveOnlyViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(PERSPECTIVE, null), PERSPECTIVE_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, VIEW_PERMISSION, RESOURCE_PERSPECTIVE));
  }
}
