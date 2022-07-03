/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

import io.harness.delegate.DelegateId;
import io.harness.grpc.utils.HTimestamps;
import io.harness.managerclient.DelegateAgentManagerClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class PerpetualTaskServiceAgentClient {
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;

  public List<PerpetualTaskAssignDetails> perpetualTaskList(String delegateId, String accountId) {
    PerpetualTaskListRequest request =
        PerpetualTaskListRequest.newBuilder().setDelegateId(DelegateId.newBuilder().setId(delegateId).build()).build();
    try {
      PerpetualTaskListResponse perpetualTaskListResponse =
          delegateAgentManagerClient.perpetualTaskList(delegateId, accountId).execute().body();
      assert perpetualTaskListResponse != null;
      return perpetualTaskListResponse.getPerpetualTaskAssignDetailsList();

    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public PerpetualTaskExecutionContext perpetualTaskContext(PerpetualTaskId taskId, String accountId) {
    try {
      PerpetualTaskContextResponse perpetualTaskContextResponse =
          delegateAgentManagerClient.perpetualTaskContext(taskId.getId(), accountId).execute().body();
      assert perpetualTaskContextResponse != null;
      return perpetualTaskContextResponse.getPerpetualTaskContext();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public void heartbeat(PerpetualTaskId taskId, Instant taskStartTime, PerpetualTaskResponse perpetualTaskResponse) {
    try {
      HeartbeatRequest heartbeatRequest = HeartbeatRequest.newBuilder()
                                              .setId(taskId.getId())
                                              .setHeartbeatTimestamp(HTimestamps.fromInstant(taskStartTime))
                                              .setResponseCode(perpetualTaskResponse.getResponseCode())
                                              .setResponseMessage(perpetualTaskResponse.getResponseMessage())
                                              .build();
      HeartbeatResponse heartbeatResponse = delegateAgentManagerClient.heartbeat(heartbeatRequest).execute().body();
    } catch (IOException ex) {
      log.error(ex.getMessage());
    }
  }
}
