/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ci.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.waiter.RedisNotifyQueuePublisher;

import com.google.inject.Inject;
import com.google.inject.name.Named;

@OwnedBy(HarnessTeam.CI)
public class CINotifyEventPublisher extends RedisNotifyQueuePublisher {
  @Inject
  public CINotifyEventPublisher(@Named(EventsFrameworkConstants.CI_ORCHESTRATION_NOTIFY_EVENT) Producer producer) {
    super(producer);
  }
}