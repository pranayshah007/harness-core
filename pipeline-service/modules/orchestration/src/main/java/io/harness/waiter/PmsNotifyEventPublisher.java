/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;

import com.google.inject.Inject;
import com.google.inject.name.Named;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsNotifyEventPublisher extends RedisNotifyQueuePublisher {
  @Inject
  public PmsNotifyEventPublisher(@Named(EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT) Producer producer) {
    super(producer);
  }
}
