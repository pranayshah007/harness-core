/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.websocketapiclient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.beans.DelegateTask;
import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.delegate.beans.DelegateWebsocketAPIEvent;
import io.harness.delegate.task.tasklogging.WebsocketAPILogContext;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.logging.AutoLogContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.wings.service.intfc.DelegateTaskServiceClassic;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class DelegateAPIClientImpl implements DelegateAPIClient {
    private final DelegateTaskServiceClassic delegateTaskServiceClassic;
    private final DelegateWebsocketAPIBroadcastHelper broadcastHelper;
    private final DelegateTaskMigrationHelper delegateTaskMigrationHelper;

    @Override
    public void sendAPIRequest(DelegateTask stateMachineEntity, DelegateWebsocketAPIEvent.Method method, String uri) {
        //stateMachineEntity.getData().setAsync(true);
        if (stateMachineEntity.getUuid() == null) {
            stateMachineEntity.setUuid(delegateTaskMigrationHelper.generateDelegateTaskUUID());
        }
        try (AutoLogContext ignore1 = new WebsocketAPILogContext(stateMachineEntity.getUuid(), uri, method.name(), OVERRIDE_ERROR)) {
            log.info("Queueing websocket api - id {}, method {}, uri {}", stateMachineEntity.getUuid(), method.name(), uri);
            // Handles routing the reqest to the right delegate instance
            delegateTaskServiceClassic.processWebsocketAPIRequest(stateMachineEntity, QUEUED);
            // Send out request via websocket
            broadcastHelper.broadcastWebsocketAPIEvent(stateMachineEntity, method, uri);
        }
    }
}
