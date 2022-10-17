/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2;

import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.AccountId;
import io.harness.delegate.Capability;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.metrics.service.api.MetricService;
import io.harness.perpetualtask.PerpetualTaskClientContextDetails;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.serializer.KryoSerializer;

import software.wings.api.DeploymentEvent;
import software.wings.api.DeploymentSummary;
import software.wings.beans.SettingAttribute;
import software.wings.instancesyncv2.handler.CgInstanceSyncV2HandlerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.Durations;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.groovy.util.Maps;

@OwnedBy(HarnessTeam.CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class CgInstanceSyncServiceV2 {
  private final MetricService metricService;
  private final CgInstanceSyncV2HandlerFactory handlerFactory;
  private final DelegateServiceGrpcClient delegateServiceClient;
  private final KryoSerializer kryoSerializer;

  public static final String INSTANCE_SYNC_V2_DURATION_METRIC = "instance_sync_v2_duration";

  public void handleInstanceSync(DeploymentEvent event) {
    if (Objects.isNull(event)) {
      log.error("Null event sent for Instance Sync Processing. Doing nothing");
      return;
    }

    if (CollectionUtils.isEmpty(event.getDeploymentSummaries())) {
      log.error("No deployment summaries present in the deployment event. Doing nothing");
      return;
    }

    event.getDeploymentSummaries()
        .parallelStream()
        .filter(deployment -> Objects.nonNull(deployment.getDeploymentInfo()))
        .forEach(deploymentSummary -> {
          if (Objects.isNull(handlerFactory.getHandler(deploymentSummary.getDeploymentInfo()))) {
            log.error("No handler registered for deploymentInfo type: [{}]. Doing nothing",
                deploymentSummary.getDeploymentInfo().getClass());
            return;
          }

          upsertInstanceSyncPerpetualTask(deploymentSummary);
        });
  }

  private void upsertInstanceSyncPerpetualTask(DeploymentSummary deploymentSummary) {
    String accountId = deploymentSummary.getAccountId();
    SettingAttribute connectorDetails =
        handlerFactory.getHandler(deploymentSummary.getDeploymentInfo()).fetchInfraConnectorDetails(deploymentSummary);
    PerpetualTaskExecutionBundle.Builder builder =
        PerpetualTaskExecutionBundle.newBuilder()
            .setTaskParams(
                Any.pack(CgInstanceSyncTaskParams.newBuilder()
                             .setAccountId(accountId)
                             .setCloudProviderType(connectorDetails.getValue().getType())
                             .setCloudProviderDetails(ByteString.copyFrom(kryoSerializer.asBytes(connectorDetails)))
                             .build()))
            .putAllSetupAbstractions(Maps.of(NG, "false", OWNER, accountId + "/" + connectorDetails.getUuid()));
    connectorDetails.getValue().fetchRequiredExecutionCapabilities(null).forEach(executionCapability
        -> builder
               .addCapabilities(
                   Capability.newBuilder()
                       .setKryoCapability(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(executionCapability)))
                       .build())
               .build());

    PerpetualTaskId taskId = delegateServiceClient.createPerpetualTask(AccountId.newBuilder().setId(accountId).build(),
        "CG_INSTANCE_SYNC_V2", preparePerpetualTaskSchedule(),
        PerpetualTaskClientContextDetails.newBuilder().setExecutionBundle(builder.build()).build(), true,
        "Instance Sync V2 Perpetual Task");
    log.info("Created Perpetual Task for cloud provider: id: [{}], name: [{}], with ID: [{}]",
        connectorDetails.getUuid(), connectorDetails.getName(), taskId.getId());
  }

  private PerpetualTaskSchedule preparePerpetualTaskSchedule() {
    return PerpetualTaskSchedule.newBuilder()
        .setInterval(Durations.fromMinutes(10))
        .setTimeout(Durations.fromMinutes(5))
        .build();
  }
}
