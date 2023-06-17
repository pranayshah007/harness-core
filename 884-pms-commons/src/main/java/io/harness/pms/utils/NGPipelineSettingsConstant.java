/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.utils;

public enum NGPipelineSettingsConstant {
  ENABLE_MATRIX_FIELD_NAME_SETTING("enable_matrix_label_by_name"),
  DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTANER("default_image_pull_policy_for_add_on_container"),
  ENABLE_NODE_EXECUTION_AUDIT_EVENTS("enable_node_execution_audit_events"),
  ENABLE_EXPRESSION_ENGINE_V2("enable_expression_engine_v2");
  private final String name;

  NGPipelineSettingsConstant(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
