/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.events;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.FullSyncEventRequest;
import io.harness.gitsync.core.fullsync.FullSyncAccumulatorService;
import io.harness.gitsync.fullsync.utils.FullSyncLogContextHelper;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(DX)
public class FullSyncMessageListener implements MessageListener {
  private final FullSyncAccumulatorService fullSyncTriggerService;

  @Override
  public boolean handleMessage(Message message) {
    final String messageId = message.getId();
    log.info("Processing the Full Sync event with the id {}", messageId);
    try (AutoLogContext ignore1 = new NgEventLogContext(messageId, OVERRIDE_ERROR)) {
      final FullSyncEventRequest fullSyncEventRequest = getFullSyncEventRequest(message);
      final EntityScopeInfo entityScopeInfo = fullSyncEventRequest.getGitConfigScope();
      Map<String, String> logContext = FullSyncLogContextHelper.getContext(entityScopeInfo.getAccountId(),
          getStringValueFromProtoString(entityScopeInfo.getOrgId()),
          getStringValueFromProtoString(entityScopeInfo.getProjectId()), messageId);
      try (AutoLogContext ignore2 = new AutoLogContext(logContext, OVERRIDE_ERROR)) {
        fullSyncTriggerService.triggerFullSync(fullSyncEventRequest, messageId);
        log.info("Successfully completed the Full Sync event with the id {}", messageId);
        return true;
      }
    }
  }

  private FullSyncEventRequest getFullSyncEventRequest(Message message) {
    try {
      return FullSyncEventRequest.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException("Unable to parse entity scope info", e);
    }
  }

  private String getStringValueFromProtoString(StringValue stringValue) {
    if (stringValue != null) {
      return stringValue.getValue();
    }
    return null;
  }
}
