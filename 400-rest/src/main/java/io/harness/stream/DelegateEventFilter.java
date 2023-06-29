/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.stream;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.DelegateTaskEvent.DelegateTaskEventBuilder.aDelegateTaskEvent;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.DelegateHeartbeatResponseStreaming;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.serializer.JsonUtils;

import software.wings.beans.DelegateTaskBroadcast;
import software.wings.beans.PerpetualTaskBroadcastEvent;
import software.wings.beans.ScheduleTaskBroadcast;
import software.wings.logcontext.WebsocketLogContext;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.BroadcastFilter.BroadcastAction.ACTION;
import org.atmosphere.cpr.BroadcastFilterAdapter;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class DelegateEventFilter extends BroadcastFilterAdapter {
  @Inject private DelegateService delegateService;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;

  @Override
  public BroadcastAction filter(String broadcasterId, AtmosphereResource r, Object originalMessage, Object message) {
    AtmosphereRequest req = r.getRequest();
    String delegateId = req.getParameter("delegateId");

    if (message instanceof DelegateTaskBroadcast) {
      DelegateTaskBroadcast broadcast = (DelegateTaskBroadcast) message;
      try (AutoLogContext ignore1 = new AccountLogContext(broadcast.getAccountId(), OVERRIDE_ERROR);
           AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR);
           AutoLogContext ignore3 = new WebsocketLogContext(r.uuid(), OVERRIDE_ERROR)) {
        if (isNotBlank(broadcast.getPreAssignedDelegateId())
            && !StringUtils.equals(broadcast.getPreAssignedDelegateId(), delegateId)) {
          return abort(message);
        }

        if (isEmpty(broadcast.getBroadcastToDelegatesIds())) {
          return abort(message);
        }

        if (!broadcast.getBroadcastToDelegatesIds().contains(delegateId)) {
          return abort(message);
        }

        if (!delegateService.filter(broadcast.getAccountId(), delegateId)) {
          return abort(message);
        }

        log.info("Broadcasting task {} to account: {} delegate: {} by {}", broadcast.getTaskId(),
            broadcast.getAccountId(), delegateId, broadcasterId);
        return new BroadcastAction(JsonUtils.asJson(aDelegateTaskEvent()
                                                        .withDelegateTaskId(broadcast.getTaskId())
                                                        .withSync(!broadcast.isAsync())
                                                        .withAccountId(broadcast.getAccountId())
                                                        .withTaskType(broadcast.getTaskType())
                                                        .build()));
      }
    }

    if (message instanceof ScheduleTaskBroadcast) {
      return processDelegateRequestEvent((ScheduleTaskBroadcast) message, delegateId, broadcasterId, r.uuid());
    }

    if (message instanceof DelegateTaskAbortEvent) {
      DelegateTaskAbortEvent abortEvent = (DelegateTaskAbortEvent) message;
      if (!delegateTaskServiceClassic.filter(delegateId, abortEvent)) {
        return abort(message);
      }

      return continueWith(message);
    }
    if (message instanceof DelegateHeartbeatResponseStreaming) {
      DelegateHeartbeatResponseStreaming response = (DelegateHeartbeatResponseStreaming) message;
      if (!delegateId.equals(response.getDelegateId())) {
        return abort(message);
      }
      return continueWith(message);
    }

    if (message instanceof String && ((String) message).startsWith("[X]")) {
      String msg = (String) message;
      int seqIndex = msg.lastIndexOf("[TOKEN]");
      if (seqIndex != -1) {
        msg = msg.substring(3, seqIndex);
      } else {
        msg = msg.substring(3);
      }

      if (!delegateId.equals(msg)) {
        return abort(message);
      }
      return continueWith(message);
    }

    if (message instanceof PerpetualTaskBroadcastEvent) {
      PerpetualTaskBroadcastEvent taskBroadcastEvent = (PerpetualTaskBroadcastEvent) message;

      if (isNotBlank(taskBroadcastEvent.getBroadcastDelegateId())
          && !StringUtils.equals(taskBroadcastEvent.getBroadcastDelegateId(), delegateId)) {
        return abort(message);
      }
      return continueWith(message);
    }
    log.info("Broadcasting generic event to delegate: {} by {}", delegateId, broadcasterId);
    return continueWith(message);
  }

  private BroadcastAction processDelegateRequestEvent(
      ScheduleTaskBroadcast broadcast, String requestingDelegateId, String broadcasterId, String resourceUuid) {
    try (AutoLogContext ignore1 = new AccountLogContext(broadcast.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(requestingDelegateId, OVERRIDE_ERROR);
         AutoLogContext ignore3 = new WebsocketLogContext(resourceUuid, OVERRIDE_ERROR)) {
      if (isEmpty(broadcast.getDelegateIdsToBroadcast())) {
        return abort(broadcast);
      }

      if (!broadcast.getDelegateIdsToBroadcast().contains(requestingDelegateId)) {
        return abort(broadcast);
      }

      if (!delegateService.filter(broadcast.getAccountId(), requestingDelegateId)) {
        return abort(broadcast);
      }

      log.info("Broadcasting api request {} to account: {} delegate: {} by {}", broadcast.getTaskId(),
          broadcast.getAccountId(), requestingDelegateId, broadcasterId);
      return new BroadcastAction(broadcast.getMessage());
    }
  }

  @NotNull
  private BroadcastAction continueWith(Object message) {
    return new BroadcastAction(message);
  }

  @NotNull
  private BroadcastAction abort(Object message) {
    return new BroadcastAction(ACTION.ABORT, message);
  }
}
