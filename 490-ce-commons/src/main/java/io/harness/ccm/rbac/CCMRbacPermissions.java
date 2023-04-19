/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.rbac;

public interface CCMRbacPermissions {
  String PERSPECTIVE_CREATE_AND_EDIT = "ccm_perspective_edit";
  String PERSPECTIVE_VIEW = "ccm_perspective_view";
  String PERSPECTIVE_DELETE = "ccm_perspective_delete";

  String BUDGET_CREATE_AND_EDIT = "ccm_budget_edit";
  String BUDGET_VIEW = "ccm_budget_view";
  String BUDGET_DELETE = "ccm_budget_delete";

  String FOLDER_CREATE_AND_EDIT = "ccm_folder_edit";
  String FOLDER_VIEW = "ccm_folder_view";
  String FOLDER_DELETE = "ccm_folder_delete";

  String COST_CATEGORY_CREATE_AND_EDIT = "ccm_costCategory_edit";
  String COST_CATEGORY_VIEW = "ccm_costCategory_view";
  String COST_CATEGORY_DELETE = "ccm_costCategory_delete";

  String COST_OVERVIEW_VIEW = "ccm_overview_view";

  String CURRENCY_PREFERENCE_SET_AND_EDIT = "ccm_currencyPreference_edit";
  String CURRENCY_PREFERENCE_VIEW = "ccm_currencyPreference_view";

  String RULE_CREATE_AND_EDIT = "ccm_cloudAssetGovernanceRule_edit";
  String RULE_VIEW = "ccm_cloudAssetGovernanceRule_view";
  String RULE_DELETE = "ccm_cloudAssetGovernanceRule_delete";
  String RULE_EXECUTE = "ccm_cloudAssetGovernanceRule_execute";

  String RULE_SET_CREATE_AND_EDIT = "ccm_cloudAssetGovernanceRuleSet_edit";
  String RULE_SET_VIEW = "ccm_cloudAssetGovernanceRuleSet_view";
  String RULE_SET_DELETE = "ccm_cloudAssetGovernanceRuleSet_delete";

  String RULE_ENFORCEMENT_CREATE_AND_EDIT = "ccm_cloudAssetGovernanceEnforcement_edit";
  String RULE_ENFORCEMENT_VIEW = "ccm_cloudAssetGovernanceEnforcement_view";
  String RULE_ENFORCEMENT_DELETE = "ccm_cloudAssetGovernanceEnforcement_delete";
}
