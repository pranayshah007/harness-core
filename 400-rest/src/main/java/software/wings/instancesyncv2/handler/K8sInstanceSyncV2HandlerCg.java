/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.handler;

import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;

import io.harness.delegate.Capability;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.serializer.KryoSerializer;

import software.wings.api.DeploymentSummary;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.service.intfc.InfrastructureMappingService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.apache.groovy.util.Maps;

@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
public class K8sInstanceSyncV2HandlerCg implements CgInstanceSyncV2Handler {
  private final InfrastructureMappingService infrastructureMappingService;
  private final ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  private final KryoSerializer kryoSerializer;

  @Override
  public PerpetualTaskExecutionBundle fetchInfraConnectorDetails(DeploymentSummary deploymentSummary) {
    InfrastructureMapping infrastructureMapping =
        infrastructureMappingService.get(deploymentSummary.getAppId(), deploymentSummary.getInfraMappingId());
    if (!(infrastructureMapping instanceof ContainerInfrastructureMapping)) {
      return null;
    }

    K8sClusterConfig k8sClusterConfig = containerDeploymentManagerHelper.getK8sClusterConfig(
        (ContainerInfrastructureMapping) infrastructureMapping, null);

    PerpetualTaskExecutionBundle.Builder builder =
        PerpetualTaskExecutionBundle.newBuilder()
            .setTaskParams(
                Any.pack(CgInstanceSyncTaskParams.newBuilder()
                             .setAccountId(deploymentSummary.getAccountId())
                             .setCloudProviderType("KUBERNETES")
                             .setCloudProviderDetails(ByteString.copyFrom(kryoSerializer.asBytes(k8sClusterConfig)))
                             .build()))
            .putAllSetupAbstractions(Maps.of(NG, "false", OWNER, deploymentSummary.getAccountId()));
    k8sClusterConfig.fetchRequiredExecutionCapabilities(null).forEach(executionCapability
        -> builder
               .addCapabilities(
                   Capability.newBuilder()
                       .setKryoCapability(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(executionCapability)))
                       .build())
               .build());
    return builder.build();
  }
}
