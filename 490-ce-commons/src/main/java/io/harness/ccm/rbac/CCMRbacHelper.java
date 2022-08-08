package io.harness.ccm.rbac;

public interface CCMRbacHelper {
  void checkFolderViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkFolderEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkFolderDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void checkPerspectiveViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkPerspectiveEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkPerspectiveDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void checkBudgetViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkBudgetEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkBudgetDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void checkCostCategoryViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkCostCategoryEditPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
  void checkCostCategoryDeletePermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void checkRecommendationsViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void checkAnomalyViewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  boolean hasCostOverviewPermission(String accountIdentifier, String orgIdentifier, String projectIdentifier);
}
