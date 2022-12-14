/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.services.impl;

import io.harness.cvng.activity.entities.InternalChangeActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.activity.services.api.ActivityUpdateHandler;

import com.google.inject.Inject;

public class InternalChangeActivityUpdateHandler extends ActivityUpdateHandler<InternalChangeActivity> {
  @Inject ActivityService activityService;

  @Override
  public void handleCreate(InternalChangeActivity activity) {}

  @Override
  public void handleDelete(InternalChangeActivity activity) {}

  @Override
  public void handleUpdate(InternalChangeActivity existingActivity, InternalChangeActivity newActivity) {}
}
