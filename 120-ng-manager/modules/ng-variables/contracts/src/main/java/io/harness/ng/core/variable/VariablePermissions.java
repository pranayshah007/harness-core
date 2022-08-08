/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class VariablePermissions {
  public static final String VARIABLE_RESOURCE_TYPE = "VARIABLE";
  public static final String VARIABLE_VIEW_PERMISSION = "core_variable_view";
  public static final String VARIABLE_EDIT_PERMISSION = "core_variable_edit";
  public static final String VARIABLE_DELETE_PERMISSION = "core_variable_delete";
}
