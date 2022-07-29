/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.async.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.waiter.RedisNotifyQueuePublisher;

import com.google.inject.Inject;
import com.google.inject.name.Named;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanNotifyEventPublisher extends RedisNotifyQueuePublisher {
  @Inject
  PlanNotifyEventPublisher(@Named(EventsFrameworkConstants.PLAN_NOTIFY_EVENT_PRODUCER) Producer producer) {
    super(producer);
  }
}
