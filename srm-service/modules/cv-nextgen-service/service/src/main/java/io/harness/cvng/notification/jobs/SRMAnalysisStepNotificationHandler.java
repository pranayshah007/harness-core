/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.jobs;

import io.harness.cvng.analysis.entities.SRMAnalysisStepExecutionDetail;
import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.cvng.core.jobs.SafeHandler;

import com.google.inject.Inject;

public class SRMAnalysisStepNotificationHandler extends SafeHandler<SRMAnalysisStepExecutionDetail> {
  @Inject SRMAnalysisStepService srmAnalysisStepService;

  @Override
  public void handleUnsafely(SRMAnalysisStepExecutionDetail entity) {
    srmAnalysisStepService.completeSrmAnalysisStep(entity);
  }
}
