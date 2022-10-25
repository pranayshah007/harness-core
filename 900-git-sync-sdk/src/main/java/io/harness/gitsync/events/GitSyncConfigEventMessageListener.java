/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.events;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.gitsync.persistance.EntityKeySource;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(DX)
public class GitSyncConfigEventMessageListener implements MessageListener {
  // todo(abhinav): same event listener can be used if we at some point make any change to yaml git config and cache in
  // sdk.
  @Inject EntityKeySource entityKeySource;

  @Override
  public boolean handleMessage(Message message) {
    final String messageId = message.getId();
    log.info("Received msg with msg id {} for git sync config change", messageId);
    try (AutoLogContext ignore1 = new NgEventLogContext(messageId, OVERRIDE_ERROR)) {
      try {
        final EntityScopeInfo entityScopeInfo = EntityScopeInfo.parseFrom(message.getMessage().getData());
        entityKeySource.updateKey(entityScopeInfo);
        return true;
      } catch (InvalidProtocolBufferException e) {
        log.error("Invalid message on GIT CONFIG stream");
      }
    }
    log.info("Cannot process the git config stream event with id {}", messageId);

    return false;
  }
}
