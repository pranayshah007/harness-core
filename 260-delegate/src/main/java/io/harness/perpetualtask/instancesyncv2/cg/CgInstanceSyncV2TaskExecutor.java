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
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncTrackedDeploymentDetails;
import io.harness.serializer.KryoSerializer;
import io.harness.util.DelegateRestUtils;

import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Response;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class CgInstanceSyncV2TaskExecutor implements PerpetualTaskExecutor {
  private final CgK8sInstancesDetailsFetcher instanceDetailsFetcher;
  private final DelegateAgentManagerClient delegateAgentManagerClient;
  private final KryoSerializer kryoSerializer;

  private static final int INSTANCE_COUNT_LIMIT = 150;

  private static final int RELEASE_COUNT_LIMIT = 15;
  private int batchInstanceCount = 0;
  private int batchReleaseDetailsCount = 0;
  private Map<String, List<InstanceInfo> > buffer = new HashMap<>();

  @SneakyThrows
  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the InstanceSyncV2 perpetual task executor for task id: {}", taskId);
    CgInstanceSyncTaskParams taskParams = AnyUtils.unpack(params.getCustomizedParams(), CgInstanceSyncTaskParams.class);
    String cloudProviderType = taskParams.getCloudProviderType();

    InstanceSyncTrackedDeploymentDetails trackedDeploymentDetails = DelegateRestUtils.executeRestCall(
        delegateAgentManagerClient.fetchTrackedReleaseDetails(taskId.getId(), taskParams.getAccountId()));

    trackedDeploymentDetails.getDeploymentDetailsList().stream().forEach(details -> {
      if (details.getInfraMappingType().equals("DIRECT_KUBERNETES")) {
        DirectK8sInstanceSyncTaskDetails k8sInstanceSyncTaskDetails =
            AnyUtils.unpack(details.getReleaseDetails(), DirectK8sInstanceSyncTaskDetails.class);

        CgK8sInstancesDetailsFetcher cgK8sInstancesDetailsFetcher =
            (CgK8sInstancesDetailsFetcher) instanceDetailsFetcher;

        K8sClusterConfig config =
            (K8sClusterConfig) kryoSerializer.asObject(k8sInstanceSyncTaskDetails.getK8SClusterConfig().toByteArray());
        try {
          List<InstanceInfo> instanceInfos =
              cgK8sInstancesDetailsFetcher.fetchRunningInstanceDetails(taskId, config, k8sInstanceSyncTaskDetails);
          buffer.put(details.getTaskDetailsId(), instanceInfos);
          batchInstanceCount += instanceInfos.size();
          batchReleaseDetailsCount++;
          if (batchInstanceCount >= INSTANCE_COUNT_LIMIT || batchReleaseDetailsCount >= RELEASE_COUNT_LIMIT) {
            K8sPodInfo k8sPodInfo = (K8sPodInfo) instanceInfos;
            List<K8sContainer> k8sContainerList = new ArrayList<>();
            for (K8sContainerInfo k8sContainerInfo : k8sPodInfo.getContainers()) {
              k8sContainerList.add(K8sContainer.builder()
                                       .containerId(k8sContainerInfo.getContainerId())
                                       .image(k8sContainerInfo.getImage())
                                       .name(k8sContainerInfo.getName())
                                       .build());
            }

            DelegateRestUtils.executeRestCall(
                delegateAgentManagerClient.publishInstanceSyncV2Result(taskId.getId(), taskParams.getAccountId(),
                    CgInstanceSyncResponse.newBuilder()
                        .setPerpetualTaskId(taskId.getId())
                        .setAccountId(taskParams.getAccountId())
                        .addInstanceData(InstanceSyncData.newBuilder()
                                             .setTaskDetailsId(details.getTaskDetailsId())
                                             .addInstanceData(ByteString.copyFrom(
                                                 kryoSerializer.asBytes(K8sPod.builder()
                                                                            .containerList(k8sContainerList)
                                                                            .podIP(k8sPodInfo.getIp())
                                                                            .releaseName(k8sPodInfo.getReleaseName())
                                                                            .name(k8sPodInfo.getPodName())
                                                                            .namespace(k8sPodInfo.getNamespace())
                                                                            .build())))
                                             .build())
                        .build()));

            buffer = new HashMap<>();
            batchInstanceCount = 0;
            batchReleaseDetailsCount = 0;
          }
        } catch (IOException e) {
          throw new RuntimeException("Failure in Publishing the Instance sync V2 info", e);
        }

      } else {
        throw new InvalidRequestException(
            format("Cloud Provider of given type : %s isn't supported", cloudProviderType));
      }
    });

    return PerpetualTaskResponse.builder().responseCode(Response.SC_OK).responseMessage("success").build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
