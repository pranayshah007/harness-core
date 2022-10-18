/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.grpc.utils.AnyUtils;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.serializer.KryoSerializer;

import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import com.google.inject.Inject;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class CgInstanceSyncV2TaskExecutor implements PerpetualTaskExecutor {
  private final KryoSerializer kryoSerializer;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Came here. Add more details for task executor");
    CgInstanceSyncTaskParams taskParams = AnyUtils.unpack(params.getCustomizedParams(), CgInstanceSyncTaskParams.class);
    String cloudProviderType = taskParams.getCloudProviderType();
    K8sClusterConfig config =
        (K8sClusterConfig) kryoSerializer.asObject(taskParams.getCloudProviderDetails().toByteArray());
    return null;
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
