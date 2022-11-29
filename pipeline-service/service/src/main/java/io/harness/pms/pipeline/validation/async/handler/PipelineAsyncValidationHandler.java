/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.handler;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.service.PipelineAsyncValidationService;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Builder
@Slf4j
public class PipelineAsyncValidationHandler implements Runnable {
  private final PipelineValidationEvent validationEvent;
  private final PipelineAsyncValidationService validationService;

  @Override
  public void run() {
    // Validate templates, nested chaining, and policies.
  }
}