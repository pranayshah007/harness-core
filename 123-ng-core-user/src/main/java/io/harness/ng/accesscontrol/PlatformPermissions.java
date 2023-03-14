/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.accesscontrol;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class PlatformPermissions {
  public static final String DELETE_ORGANIZATION_PERMISSION = "core_organization_delete";
  public static final String VIEW_ORGANIZATION_PERMISSION = "core_organization_view";
  public static final String EDIT_ORGANIZATION_PERMISSION = "core_organization_edit";
  public static final String CREATE_ORGANIZATION_PERMISSION = "core_organization_create";
  public static final String DELETE_PROJECT_PERMISSION = "core_project_delete";
  public static final String VIEW_PROJECT_PERMISSION = "core_project_view";
  public static final String EDIT_PROJECT_PERMISSION = "core_project_edit";
  public static final String CREATE_PROJECT_PERMISSION = "core_project_create";
  public static final String VIEW_USERGROUP_PERMISSION = "core_usergroup_view";
  public static final String MANAGE_USERGROUP_PERMISSION = "core_usergroup_manage";
  public static final String VIEW_USER_PERMISSION = "core_user_view";
  public static final String MANAGE_USER_PERMISSION = "core_user_manage";
  public static final String INVITE_PERMISSION_IDENTIFIER = "core_user_invite";
  public static final String VIEW_AUTHSETTING_PERMISSION = "core_authsetting_view";
  public static final String EDIT_AUTHSETTING_PERMISSION = "core_authsetting_edit";
  public static final String DELETE_AUTHSETTING_PERMISSION = "core_authsetting_delete";
  public static final String VIEW_SERVICEACCOUNT_PERMISSION = "core_serviceaccount_view";
  public static final String LIST_SERVICEACCOUNT_PERMISSION = "core_serviceaccount_list";
  public static final String EDIT_SERVICEACCOUNT_PERMISSION = "core_serviceaccount_edit";
  public static final String DELETE_SERVICEACCOUNT_PERMISSION = "core_serviceaccount_delete";
  public static final String MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION = "core_serviceaccount_manageapikey";
  public static final String VIEW_ACCOUNT_PERMISSION = "core_account_view";
}
