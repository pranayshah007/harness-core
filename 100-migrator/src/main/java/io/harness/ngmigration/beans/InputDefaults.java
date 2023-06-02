/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.beans;

import io.harness.encryption.Scope;

import lombok.Data;

@Data
public class InputDefaults {
  private boolean skipMigration;
  private Scope scope;
  // Note: This only makes sense if the entity is workflow
  // Note: Also we will create workflow as template first then reuse that stage template as pipeline
  private Boolean workflowAsPipeline;
}
