/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class AccessControlPermissions {
  public static final String VIEW_ROLE_PERMISSION = "core_role_view";
  public static final String EDIT_ROLE_PERMISSION = "core_role_edit";
  public static final String DELETE_ROLE_PERMISSION = "core_role_delete";
  public static final String VIEW_USER_PERMISSION = "core_user_view";
  public static final String VIEW_USERGROUP_PERMISSION = "core_usergroup_view";
  public static final String MANAGE_USERGROUP_PERMISSION = "core_usergroup_manage";
  public static final String MANAGE_USER_PERMISSION = "core_user_manage";
  public static final String VIEW_SERVICEACCOUNT_PERMISSION = "core_serviceaccount_view";
  public static final String EDIT_SERVICEACCOUNT_PERMISSION = "core_serviceaccount_edit";
  public static final String VIEW_PROJECT_PERMISSION = "core_project_view";
  public static final String VIEW_ORGANIZATION_PERMISSION = "core_organization_view";
  public static final String VIEW_ACCOUNT_PERMISSION = "core_account_view";
}
