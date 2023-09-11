/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.webhookevent;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.pms.events.base.PmsAbstractMessageListener;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionServiceV2;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class WebhookEventStreamListener
    extends PmsAbstractMessageListener<WebhookDTO, TriggerWebhookExecutionServiceV2> {
  @Inject
  public WebhookEventStreamListener(
      @Named(SDK_SERVICE_NAME) String serviceName, TriggerWebhookExecutionServiceV2 triggerWebhookExecutionServiceV2) {
    super(serviceName, WebhookDTO.class, triggerWebhookExecutionServiceV2);
  }

  @Override
  protected WebhookDTO extractEntity(ByteString message) throws InvalidProtocolBufferException {
    return WebhookDTO.parseFrom(message);
  }

  @Override
  public boolean isProcessable(Message message) {
    return true;
  }
}
