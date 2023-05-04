/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.websocketapiclient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateWebsocketAPIEvent;
import io.harness.serializer.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import software.wings.beans.DelegateWebsocketAPIBroadcast;

import javax.validation.constraints.NotNull;

import static io.harness.annotations.dev.HarnessTeam.DEL;

@Singleton
@Slf4j
@OwnedBy(DEL)
public class DelegateWebsocketAPIBroadcastHelper {
    public static final String STREAM_DELEGATE_PATH = "/stream/delegate/";
    @Inject private BroadcasterFactory broadcasterFactory;

    public void broadcastWebsocketAPIEvent(@NotNull final DelegateTask delegateTask, DelegateWebsocketAPIEvent.Method method, String uri) {
        DelegateWebsocketAPIBroadcast delegateTaskBroadcast = DelegateWebsocketAPIBroadcast.builder()
            .broadcastToDelegatesIds(delegateTask.getBroadcastToDelegateIds())
            .accountId(delegateTask.getAccountId())
            .stateMachineId(delegateTask.getUuid())
            .message(JsonUtils.asJson(DelegateWebsocketAPIEvent.builder()
                .accountId(delegateTask.getAccountId())
                .stateMachineId(delegateTask.getUuid())
                .uri(uri)
                .method(method.name())
                .build()))
            .build();
        Broadcaster broadcaster = broadcasterFactory.lookup(STREAM_DELEGATE_PATH + delegateTask.getAccountId(), true);
        broadcaster.broadcast(delegateTaskBroadcast);
    }
}
