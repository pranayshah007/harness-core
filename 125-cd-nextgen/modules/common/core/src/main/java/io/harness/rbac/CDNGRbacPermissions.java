/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.rbac;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * A utility class to contain all the rbac permission for cdng
 */
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class CDNGRbacPermissions {
  public String SERVICE_CREATE_PERMISSION = "core_service_edit";
  public String SERVICE_UPDATE_PERMISSION = "core_service_edit";
  public String SERVICE_RUNTIME_PERMISSION = "core_service_access";
  public String SERVICE_VIEW_PERMISSION = "core_service_view";

  public String ENVIRONMENT_CREATE_PERMISSION = "core_environment_edit";
  public String ENVIRONMENT_RUNTIME_PERMISSION = "core_environment_access";
  public String ENVIRONMENT_UPDATE_PERMISSION = "core_environment_edit";
  public String ENVIRONMENT_VIEW_PERMISSION = "core_environment_view";
  public String ENVIRONMENT_DELETE_PERMISSION = "core_environment_delete";
  // EnvGroup Permission
  public final String ENVIRONMENT_GROUP_CREATE_PERMISSION = "core_environmentgroup_edit";
  public final String ENVIRONMENT_GROUP_UPDATE_PERMISSION = "core_environmentgroup_edit";
  public final String ENVIRONMENT_GROUP_VIEW_PERMISSION = "core_environmentgroup_view";
  public final String ENVIRONMENT_GROUP_DELETE_PERMISSION = "core_environmentgroup_delete";
  public final String ENVIRONMENT_GROUP_RUNTIME_PERMISSION = "core_environmentgroup_access";
}
