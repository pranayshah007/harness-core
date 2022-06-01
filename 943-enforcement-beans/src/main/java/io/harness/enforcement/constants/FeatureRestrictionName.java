/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.constants;

/**
 * Please register all feature names here.
 */
public enum FeatureRestrictionName {
  // Test purpose
  TEST1,
  TEST2,
  TEST3,
  TEST4,
  TEST5,
  TEST6,
  TEST7,

  // CCM Features
  PERSPECTIVES,
  CCM_K8S_CLUSTERS,
  CCM_AUTOSTOPPING_RULES,

  // All Features
  MULTIPLE_ORGANIZATIONS,
  MULTIPLE_PROJECTS,
  INTEGRATED_APPROVALS_WITH_HARNESS_UI,
  INTEGRATED_APPROVALS_WITH_JIRA,
  SECRET_MANAGERS,
  DEPLOYMENTS,
  INITIAL_DEPLOYMENTS,
  DEPLOYMENTS_PER_MONTH,
  SERVICES,
  BUILDS,
  SAML_SUPPORT,
  OAUTH_SUPPORT,
  LDAP_SUPPORT,
  TWO_FACTOR_AUTH_SUPPORT,
  CUSTOM_ROLES,
  CUSTOM_RESOURCE_GROUPS,
  MAX_TOTAL_BUILDS,
  MAX_BUILDS_PER_MONTH,
  ACTIVE_COMMITTERS,
  TEST_INTELLIGENCE,
  TEMPLATE_SERVICE,

  // CD Step Palette
  K8S_BG_SWAP_SERVICES,
  K8S_BLUE_GREEN_DEPLOY,
  K8S_APPLY,
  K8S_DELETE,
  K8S_CANARY_DELETE,
  K8S_ROLLING_DEPLOY,
  K8S_CANARY_DEPLOY,
  K8S_SCALE,
  K8S_ROLLING_ROLLBACK,
  TERRAFORM_APPLY,
  TERRAFORM_PLAN,
  TERRAFORM_DESTROY,
  TERRAFORM_ROLLBACK,
  INTEGRATED_APPROVALS_WITH_SERVICE_NOW,
  CREATE_STACK,
  DELETE_STACK,
  ROLLBACK_STACK,

  // STO Features
  SECURITY,

  // FF Features
  DEVELOPERS,
  MONTHLY_ACTIVE_USERS;
}
