/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.triggerwebhookevent;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.TriggerExecutionDTO;
import io.harness.pms.events.base.PmsAbstractMessageListener;
import io.harness.pms.triggers.webhook.service.TriggerWebhookEventExecutionService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class TriggerExecutionEventStreamListener
    extends PmsAbstractMessageListener<TriggerExecutionDTO, TriggerWebhookEventExecutionService> {
  @Inject
  public TriggerExecutionEventStreamListener(
      @Named(SDK_SERVICE_NAME) String serviceName, TriggerWebhookEventExecutionService handler) {
    super(serviceName, TriggerExecutionDTO.class, handler);
  }

  @Override
  protected TriggerExecutionDTO extractEntity(ByteString message) throws InvalidProtocolBufferException {
    return TriggerExecutionDTO.parseFrom(message);
  }

  @Override
  public boolean isProcessable(Message message) {
    return true;
  }
}
