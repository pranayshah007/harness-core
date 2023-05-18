/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

public enum NGPipelineSettingsConstant {
  ENABLE_MATRIX_FIELD_NAME_SETTING("enable_matrix_label_by_name"),
  DEFAULT_IMAGE_PULL_POLICY_ADD_ON_CONTANER("default_image_pull_policy_for_add_on_container");

  private final String name;

  NGPipelineSettingsConstant(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
