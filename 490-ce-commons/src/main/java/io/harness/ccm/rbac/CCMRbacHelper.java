/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.rbac;

import java.util.Set;

public interface CCMRbacHelper {
  void checkFolderViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId);
  void checkFolderEditPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId);
  void checkFolderDeletePermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId);
  Set<String> checkFolderIdsGivenPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      Set<String> folderIds, String permission);

  void checkPerspectiveViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId);
  void checkPerspectiveEditPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId);
  void checkPerspectiveDeletePermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId);

  void checkBudgetViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId);
  void checkBudgetEditPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId);
  void checkBudgetDeletePermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId);

  void checkCostCategoryViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkCostCategoryEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkCostCategoryDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void checkRecommendationsViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId);

  void checkAnomalyViewPermission(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String folderId);

  boolean hasCostOverviewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void checkCurrencyPreferenceViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkCurrencyPreferenceEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void checkRuleEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkRuleViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkRuleDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void checkRuleSetViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkRuleSetDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkRuleSetEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void checkRuleEnforcementViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkRuleEnforcementDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkRuleEnforcementEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
}
