/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.eventsframework.EventsFrameworkConstants.SRM_STATEMACHINE_EVENT;

import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.cv.StateMachineTrigger;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class StateMachineEventPublisherServiceImpl implements StateMachineEventPublisherService {
  @Inject @Named(SRM_STATEMACHINE_EVENT) private Producer eventProducer;
  @Override
  public void registerTaskComplete(String accountId, String verificationTaskId) {
    StateMachineTrigger trigger = StateMachineTrigger.newBuilder().setVerificationTaskId(verificationTaskId).build();

    Message message = Message.newBuilder().setData(trigger.toByteString()).build();
    eventProducer.send(message);
  }
}
