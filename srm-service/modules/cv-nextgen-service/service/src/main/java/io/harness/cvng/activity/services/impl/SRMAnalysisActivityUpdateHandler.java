/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.services.impl;

import io.harness.cvng.activity.entities.SRMStepAnalysisActivity;
import io.harness.cvng.activity.services.api.ActivityUpdateHandler;

public class SRMAnalysisActivityUpdateHandler extends ActivityUpdateHandler<SRMStepAnalysisActivity> {
  @Override
  public void handleCreate(SRMStepAnalysisActivity activity) {}

  @Override
  public void handleDelete(SRMStepAnalysisActivity activity) {}

  @Override
  public void handleUpdate(SRMStepAnalysisActivity existingActivity, SRMStepAnalysisActivity newActivity) {}
}
