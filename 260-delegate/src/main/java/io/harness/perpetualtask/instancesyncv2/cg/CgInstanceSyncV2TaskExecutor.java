/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sPod;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncTrackedDeploymentDetails;
import io.harness.serializer.KryoSerializer;
import io.harness.util.DelegateRestUtils;

import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Response;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class CgInstanceSyncV2TaskExecutor implements PerpetualTaskExecutor {
  private final DelegateAgentManagerClient delegateAgentManagerClient;

  private final InstanceDetailsFetcherFactory instanceDetailsFetcherFactory;
  private final KryoSerializer kryoSerializer;

  private static final int INSTANCE_COUNT_LIMIT = 150;

  private static final int RELEASE_COUNT_LIMIT = 15;

  @SneakyThrows
  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the InstanceSyncV2 perpetual task executor for task id: {}", taskId);
    CgInstanceSyncTaskParams taskParams = AnyUtils.unpack(params.getCustomizedParams(), CgInstanceSyncTaskParams.class);
    InstanceSyncTrackedDeploymentDetails trackedDeploymentDetails = DelegateRestUtils.executeRestCall(
        delegateAgentManagerClient.fetchTrackedReleaseDetails(taskId.getId(), taskParams.getAccountId()));

    int batchInstanceCount = 0;

    int batchReleaseDetailsCount = 0;
    int deploymentDetailListSize = trackedDeploymentDetails.getDeploymentDetailsList().size();
    for (int i = 0; i < deploymentDetailListSize; i++) {
      CgDeploymentReleaseDetails details = trackedDeploymentDetails.getDeploymentDetailsList().get(i);
      if (details.getInfraMappingType().equals("DIRECT_KUBERNETES")) {
        DirectK8sInstanceSyncTaskDetails k8sInstanceSyncTaskDetails =
            AnyUtils.unpack(details.getReleaseDetails(), DirectK8sInstanceSyncTaskDetails.class);

        K8sClusterConfig config =
            (K8sClusterConfig) kryoSerializer.asObject(k8sInstanceSyncTaskDetails.getK8SClusterConfig().toByteArray());

        CgK8sInstancesDetailsFetcher cgK8sInstancesDetailsFetcher =
            (CgK8sInstancesDetailsFetcher) instanceDetailsFetcherFactory.getFetcher("DIRECT_KUBERNETES");
        try {
          List<InstanceInfo> instanceInfos =
              cgK8sInstancesDetailsFetcher.fetchRunningInstanceDetails(taskId, config, k8sInstanceSyncTaskDetails);
          batchInstanceCount += instanceInfos.size();
          batchReleaseDetailsCount++;
          if (batchInstanceCount >= INSTANCE_COUNT_LIMIT || batchReleaseDetailsCount == RELEASE_COUNT_LIMIT
              || i == (deploymentDetailListSize - 1)) {
            List<K8sPodInfo> k8sPodInfoList =
                instanceInfos.stream().map(instanceInfo -> (K8sPodInfo) instanceInfo).collect(Collectors.toList());

            InstanceSyncData.Builder instanceSyncDataBuilder =
                InstanceSyncData.newBuilder().setTaskDetailsId(details.getTaskDetailsId());

            for (K8sPodInfo k8sPodInfo : k8sPodInfoList) {
              instanceSyncDataBuilder.addInstanceData(ByteString.copyFrom(kryoSerializer.asBytes(
                  K8sPod.builder()
                      .containerList(k8sPodInfo.getContainers()
                                         .stream()
                                         .map(k8sContainerInfo
                                             -> K8sContainer.builder()
                                                    .containerId(k8sContainerInfo.getContainerId())
                                                    .image(k8sContainerInfo.getImage())
                                                    .name(k8sContainerInfo.getName())
                                                    .build())
                                         .collect(Collectors.toList()))
                      .podIP(k8sPodInfo.getIp())
                      .releaseName(k8sPodInfo.getReleaseName())
                      .name(k8sPodInfo.getPodName())
                      .namespace(k8sPodInfo.getNamespace())
                      .build())));
            }

            CgInstanceSyncResponse.Builder cgInstanceSyncResponseOrBuilder =
                CgInstanceSyncResponse.newBuilder()
                    .setPerpetualTaskId(taskId.getId())
                    .setAccountId(taskParams.getAccountId())
                    .addInstanceData(instanceSyncDataBuilder.build());

            DelegateRestUtils.executeRestCall(
                delegateAgentManagerClient.publishInstanceSyncV2Result(taskId.getId(), taskParams.getAccountId(),
                    CgInstanceSyncResponse.newBuilder()
                        .setPerpetualTaskId(taskId.getId())
                        .setAccountId(taskParams.getAccountId())
                        .addInstanceData(InstanceSyncData.newBuilder()
                                             .setTaskDetailsId(details.getTaskDetailsId())
                                             .addInstanceData(ByteString.copyFrom(kryoSerializer.asBytes(K8sPod
                                                                                                             .builder()

                                                                                                             .build())))
                                             .build())
                        .build()));

            batchInstanceCount = 0;
            batchReleaseDetailsCount = 0;
          }
        } catch (IOException e) {
          throw new RuntimeException("Failure in Publishing the Instance sync V2 info", e);
        }

      } else {
        throw new InvalidRequestException(
            format("Cloud Provider of given type : %s isn't supported", details.getInfraMappingType()));
      }
    }

    return PerpetualTaskResponse.builder().responseCode(Response.SC_OK).responseMessage("success").build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
