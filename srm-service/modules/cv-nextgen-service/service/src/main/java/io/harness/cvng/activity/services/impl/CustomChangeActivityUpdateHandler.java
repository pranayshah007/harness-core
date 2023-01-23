/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.services.impl;

import io.harness.cvng.activity.entities.CustomChangeActivity;
import io.harness.cvng.activity.entities.InternalChangeActivity;
import io.harness.cvng.activity.services.api.ActivityUpdateHandler;

public class CustomChangeActivityUpdateHandler extends ActivityUpdateHandler<CustomChangeActivity> {
  @Override
  public void handleCreate(CustomChangeActivity activity) {}

  @Override
  public void handleDelete(CustomChangeActivity activity) {}

  @Override
  public void handleUpdate(CustomChangeActivity existingActivity, CustomChangeActivity newActivity) {}
}
