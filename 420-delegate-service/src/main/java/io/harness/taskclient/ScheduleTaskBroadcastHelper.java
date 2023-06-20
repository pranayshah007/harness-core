/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskclient;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.SchedulingTaskEvent;
import io.harness.serializer.JsonUtils;

import software.wings.beans.ScheduleTaskBroadcast;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;

@Singleton
@Slf4j
@OwnedBy(DEL)
public class ScheduleTaskBroadcastHelper {
  public static final String STREAM_DELEGATE_PATH = "/stream/delegate/";
  @Inject private BroadcasterFactory broadcasterFactory;

  public void broadcastRequestEvent(
      @NotNull final DelegateTask delegateTask, SchedulingTaskEvent.Method method, String uri) {
    ScheduleTaskBroadcast delegateTaskBroadcast =
        ScheduleTaskBroadcast.builder()
            .delegateIdsToBroadcast(delegateTask.getBroadcastToDelegateIds())
            .accountId(delegateTask.getAccountId())
            .taskId(delegateTask.getUuid())
            .message(JsonUtils.asJson(SchedulingTaskEvent.builder()
                                          .accountId(delegateTask.getAccountId())
                                          .taskId(delegateTask.getUuid())
                                          .uri(uri)
                                          .method(method.name())
                                          .build()))
            .build();
    Broadcaster broadcaster = broadcasterFactory.lookup(STREAM_DELEGATE_PATH + delegateTask.getAccountId(), true);
    broadcaster.broadcast(delegateTaskBroadcast);
  }
}
