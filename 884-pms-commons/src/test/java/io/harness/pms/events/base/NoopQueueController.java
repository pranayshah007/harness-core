/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events.base;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.queue.QueueController;

@OwnedBy(HarnessTeam.PIPELINE)
public class NoopQueueController implements QueueController {
  @Override
  public boolean isPrimary() {
    return true;
  }

  @Override
  public boolean isNotPrimary() {
    return false;
  }
}
