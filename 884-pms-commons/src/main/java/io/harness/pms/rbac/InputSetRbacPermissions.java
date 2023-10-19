/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.rbac;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PIPELINE)
public interface InputSetRbacPermissions {
  String INPUTSET_CREATE_AND_EDIT = "core_inputset_edit";
  String INPUTSET_DELETE = "core_inputset_delete";
  String INPUTSET_VIEW = "core_inputset_view";
}
