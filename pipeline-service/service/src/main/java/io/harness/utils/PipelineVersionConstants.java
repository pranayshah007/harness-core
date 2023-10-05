/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

public enum PipelineVersionConstants {
  PIPELINE_VERSION_V0("v0"),
  PIPELINE_VERSION_V1("v1"),
  ;

  String value;

  public String getValue() {
    return value;
  }

  PipelineVersionConstants(String value) {
    this.value = value;
  }
}
