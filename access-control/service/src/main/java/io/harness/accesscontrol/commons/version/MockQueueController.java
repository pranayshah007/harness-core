/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.version;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.queue.QueueController;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class MockQueueController implements QueueController {
  @Override
  public boolean isPrimary() {
    return true;
  }

  @Override
  public boolean isNotPrimary() {
    return false;
  }
}
