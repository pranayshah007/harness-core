/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.queue.QueueController;

import com.google.inject.name.Named;

public class InternalChangeEventConsumer extends AbstractStreamConsumer {
  private static final int MAX_WAIT_TIME_SEC = 10;
  ChangeEventService changeEventService;

  public InternalChangeEventConsumer(@Named(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_FF) Consumer consumer,
      QueueController queueController, ChangeEventService changeEventService) {
    super(MAX_WAIT_TIME_SEC, consumer, queueController);
    this.changeEventService = changeEventService;
  }

  @Override
  protected boolean processMessage(Message message) {
    return false;
  }
}
