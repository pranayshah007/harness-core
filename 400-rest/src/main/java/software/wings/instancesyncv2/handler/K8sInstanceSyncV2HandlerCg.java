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
import io.harness.exception.InvalidRequestException;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.serializer.KryoSerializer;

import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.instancesyncv2.model.CgK8sReleaseIdentifier;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.groovy.util.Maps;

@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class K8sInstanceSyncV2HandlerCg implements CgInstanceSyncV2Handler {
  private final ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  private final InfrastructureMappingService infrastructureMappingService;
  private final KryoSerializer kryoSerializer;

  @Override
  public PerpetualTaskExecutionBundle fetchInfraConnectorDetails(SettingAttribute cloudProvider) {
    if (!SettingVariableTypes.KUBERNETES_CLUSTER.name().equals(cloudProvider.getValue().getType())) {
      log.error("Passed cloud provider is not of type KUBERNETES_CLUSTER. Type passed: [{}]",
          cloudProvider.getValue().getType());
      throw new InvalidRequestException("Cloud Provider not of type KUBERNETES_CLUSTER");
    }

    PerpetualTaskExecutionBundle.Builder builder =
        PerpetualTaskExecutionBundle.newBuilder()
            .setTaskParams(
                Any.pack(CgInstanceSyncTaskParams.newBuilder()
                             .setAccountId(cloudProvider.getAccountId())
                             .setCloudProviderType(cloudProvider.getValue().getType())
                             .setCloudProviderDetails(ByteString.copyFrom(kryoSerializer.asBytes(cloudProvider)))
                             .build()))
            .putAllSetupAbstractions(Maps.of(NG, "false", OWNER, cloudProvider.getAccountId()));
    cloudProvider.getValue().fetchRequiredExecutionCapabilities(null).forEach(executionCapability
        -> builder
               .addCapabilities(
                   Capability.newBuilder()
                       .setKryoCapability(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(executionCapability)))
                       .build())
               .build());
    return builder.build();
  }

  @Override
  public Set<CgReleaseIdentifiers> mergeReleaseIdentifiers(
      Set<CgReleaseIdentifiers> existingIdentifiers, Set<? extends CgReleaseIdentifiers> newIdentifiers) {
    Set<CgReleaseIdentifiers> identifiers = new HashSet<>();
    for (CgReleaseIdentifiers newIdentifier : newIdentifiers) {
      if (newIdentifier instanceof CgK8sReleaseIdentifier) {
        Optional<CgReleaseIdentifiers> matchingIdentifier =
            existingIdentifiers.parallelStream()
                .filter(existingIdentifier -> existingIdentifier instanceof CgK8sReleaseIdentifier)
                .filter(existingIdentifier
                    -> StringUtils.equals(((CgK8sReleaseIdentifier) existingIdentifier).getReleaseName(),
                        (((CgK8sReleaseIdentifier) newIdentifier).getReleaseName())))
                .filter(existingIdentifier
                    -> StringUtils.equals(((CgK8sReleaseIdentifier) existingIdentifier).getClusterName(),
                        ((CgK8sReleaseIdentifier) newIdentifier).getClusterName()))
                .findAny();

        if (matchingIdentifier.isPresent()) {
          CgK8sReleaseIdentifier k8sReleaseIdentifier = (CgK8sReleaseIdentifier) matchingIdentifier.get();
          k8sReleaseIdentifier.getNamespaces().addAll(((CgK8sReleaseIdentifier) newIdentifier).getNamespaces());
          identifiers.add(k8sReleaseIdentifier);
        } else {
          identifiers.add(newIdentifier);
        }
      } else {
        log.error("Unknown release identifier found: [{}]", newIdentifier);
      }
    }

    return identifiers;
  }

  @Override
  public InstanceSyncTaskDetails prepareTaskDetails(
      DeploymentSummary deploymentSummary, String cloudProviderId, String perpetualTaskId) {
    return InstanceSyncTaskDetails.builder()
        .accountId(deploymentSummary.getAccountId())
        .appId(deploymentSummary.getAppId())
        .perpetualTaskId(perpetualTaskId)
        .cloudProviderId(cloudProviderId)
        .infraMappingId(deploymentSummary.getInfraMappingId())
        .lastSuccessfulRun(0L)
        .releaseIdentifiers(buildReleaseIdentifiers(deploymentSummary.getDeploymentInfo()))
        .build();
  }

  @Override
  public Set<CgReleaseIdentifiers> buildReleaseIdentifiers(DeploymentInfo deploymentInfo) {
    if (deploymentInfo instanceof K8sDeploymentInfo) {
      K8sDeploymentInfo k8sDeploymentInfo = (K8sDeploymentInfo) deploymentInfo;
      Set<String> namespaces = new HashSet<>(k8sDeploymentInfo.getNamespaces());
      namespaces.add(k8sDeploymentInfo.getNamespace());
      return Collections.singleton(CgK8sReleaseIdentifier.builder()
                                       .clusterName(k8sDeploymentInfo.getClusterName())
                                       .releaseName(k8sDeploymentInfo.getReleaseName())
                                       .namespaces(namespaces)
                                       .build());
    }

    throw new InvalidRequestException("DeploymentInfo of type: [" + deploymentInfo.getClass().getCanonicalName()
        + "] not supported with V2 Instance Sync framework.");
  }

  @Override
  public List<CgDeploymentReleaseDetails> getDeploymentReleaseDetails(InstanceSyncTaskDetails taskDetails) {
    InfrastructureMapping infraMapping =
        infrastructureMappingService.get(taskDetails.getAppId(), taskDetails.getInfraMappingId());

    if (!(infraMapping instanceof ContainerInfrastructureMapping)) {
      log.error("Unsupported infrastructure mapping being tracked here: [{}]. InfraMappingType found: [{}]",
          taskDetails, infraMapping.getClass().getName());
      return null;
    }

    ContainerInfrastructureMapping containerInfraMapping = (ContainerInfrastructureMapping) infraMapping;
    K8sClusterConfig clusterConfig = containerDeploymentManagerHelper.getK8sClusterConfig(containerInfraMapping, null);

    List<CgDeploymentReleaseDetails> releaseDetails = new ArrayList<>();
    taskDetails.getReleaseIdentifiers()
        .parallelStream()
        .filter(releaseIdentifier -> releaseIdentifier instanceof CgK8sReleaseIdentifier)
        .map(releaseIdentifier -> (CgK8sReleaseIdentifier) releaseIdentifier)
        .forEach(releaseIdentifier
            -> releaseDetails.addAll(
                releaseIdentifier.getNamespaces()
                    .parallelStream()
                    .map(namespace
                        -> CgDeploymentReleaseDetails.newBuilder()
                               .setTaskDetailsId(taskDetails.getUuid())
                               .setInfraMappingId(taskDetails.getInfraMappingId())
                               .setInfraMappingType(infraMapping.getInfraMappingType())
                               .setReleaseDetails(Any.pack(
                                   DirectK8sInstanceSyncTaskDetails.newBuilder()
                                       .setReleaseName(releaseIdentifier.getReleaseName())
                                       .setNamespace(namespace)
                                       .setK8SClusterConfig(ByteString.copyFrom(kryoSerializer.asBytes(clusterConfig)))
                                       .build()))
                               .build())
                    .collect(Collectors.toList())));

    return releaseDetails;
  }
}
