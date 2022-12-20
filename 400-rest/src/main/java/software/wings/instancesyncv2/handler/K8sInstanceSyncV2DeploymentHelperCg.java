/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.handler;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.instancesyncv2.CgInstanceSyncServiceV2.AUTO_SCALE;

import static java.util.Collections.singletonList;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.Capability;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.serializer.KryoSerializer;

import software.wings.api.ContainerDeploymentInfoWithLabels;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.Application;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.PodInstanceKey;
import software.wings.dl.WingsMongoPersistence;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.instancesyncv2.model.CgK8sReleaseIdentifier;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;
import software.wings.instancesyncv2.model.InstanceSyncTaskDetails;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;
import software.wings.service.impl.ContainerMetadata;
import software.wings.service.impl.instance.InstanceUtils;
import software.wings.service.impl.instance.sync.ContainerSync;
import software.wings.service.impl.instance.sync.response.ContainerSyncResponse;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.groovy.util.Maps;

@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class K8sInstanceSyncV2DeploymentHelperCg implements CgInstanceSyncV2DeploymentHelper {
  private final ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  private final InfrastructureMappingService infrastructureMappingService;
  private final KryoSerializer kryoSerializer;
  private final InstanceUtils instanceUtil;
  private final ServiceResourceService serviceResourceService;
  private final EnvironmentService environmentService;
  private final AppService appService;
  private final WingsMongoPersistence wingsPersistence;
  private final ContainerSync containerSync;

  @Override
  public PerpetualTaskExecutionBundle fetchInfraConnectorDetails(SettingAttribute cloudProvider) {
    if (!SettingVariableTypes.KUBERNETES_CLUSTER.name().equals(cloudProvider.getValue().getType())) {
      log.error("Passed cloud provider is not of type KUBERNETES_CLUSTER. Type passed: [{}]",
          cloudProvider.getValue().getType());
      throw new InvalidRequestException("Cloud Provider not of type KUBERNETES_CLUSTER");
    }

    PerpetualTaskExecutionBundle.Builder builder =
        PerpetualTaskExecutionBundle.newBuilder()
            .setTaskParams(Any.pack(CgInstanceSyncTaskParams.newBuilder()
                                        .setAccountId(cloudProvider.getAccountId())
                                        .setCloudProviderType(cloudProvider.getValue().getType())
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
      Set<CgReleaseIdentifiers> existingIdentifiers, Set<CgReleaseIdentifiers> newIdentifiers) {
    if (CollectionUtils.isEmpty(existingIdentifiers)) {
      return newIdentifiers;
    }

    if (CollectionUtils.isEmpty(newIdentifiers)) {
      return existingIdentifiers;
    }

    Set<CgReleaseIdentifiers> identifiers = new HashSet<>();
    for (CgReleaseIdentifiers newIdentifier : newIdentifiers) {
      if (newIdentifier instanceof CgK8sReleaseIdentifier) {
        CgK8sReleaseIdentifier k8sNewIdentifier = (CgK8sReleaseIdentifier) newIdentifier;
        identifiers.add(k8sNewIdentifier);

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
      Set<String> namespaces = getNamespaces(k8sDeploymentInfo.getNamespaces(), k8sDeploymentInfo.getNamespace());
      if (CollectionUtils.isEmpty(namespaces)) {
        log.error("No namespace found for deployment info. Returning empty");
        return Collections.emptySet();
      }

      return Collections.singleton(CgK8sReleaseIdentifier.builder()
                                       .clusterName(k8sDeploymentInfo.getClusterName())
                                       .releaseName(k8sDeploymentInfo.getReleaseName())
                                       .namespaces(namespaces)
                                       .isHelmDeployment(false)
                                       .build());
    } else if (deploymentInfo instanceof ContainerDeploymentInfoWithLabels) {
      ContainerDeploymentInfoWithLabels containerDeploymentInfo = (ContainerDeploymentInfoWithLabels) deploymentInfo;
      Set<String> namespaces =
          getNamespaces(containerDeploymentInfo.getNamespaces(), containerDeploymentInfo.getNamespace());
      if (CollectionUtils.isEmpty(namespaces)) {
        log.error("No namespace found for deployment info. Returning empty");
        return Collections.emptySet();
      }

      Set<String> controllers = emptyIfNull(containerDeploymentInfo.getContainerInfoList())
                                    .stream()
                                    .map(io.harness.container.ContainerInfo::getWorkloadName)
                                    .filter(EmptyPredicate::isNotEmpty)
                                    .collect(Collectors.toSet());
      if (isNotEmpty(controllers)) {
        return controllers.stream()
            .map(controller
                -> CgK8sReleaseIdentifier.builder()
                       .containerServiceName(controller)
                       .namespaces(namespaces)
                       .releaseName(containerDeploymentInfo.getReleaseName())
                       .isHelmDeployment(true)
                       .build())
            .collect(Collectors.toSet());
      } else if (isNotEmpty(containerDeploymentInfo.getContainerInfoList())) {
        return Collections.singleton(CgK8sReleaseIdentifier.builder()
                                         .namespaces(namespaces)
                                         .releaseName(containerDeploymentInfo.getReleaseName())
                                         .isHelmDeployment(true)
                                         .build());
      }

      return Collections.emptySet();
    }

    throw new InvalidRequestException("DeploymentInfo of type: [" + deploymentInfo.getClass().getCanonicalName()
        + "] not supported with V2 Instance Sync framework.");
  }

  private Set<String> getNamespaces(Collection<String> namespaces, String namespace) {
    Set<String> namespacesSet = new HashSet<>();
    if (StringUtils.isNotBlank(namespace)) {
      namespacesSet.add(namespace);
    }

    if (CollectionUtils.isNotEmpty(namespaces)) {
      namespacesSet.addAll(namespaces);
      namespacesSet = namespacesSet.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    return namespacesSet;
  }

  @Override
  public List<CgDeploymentReleaseDetails> getDeploymentReleaseDetails(InstanceSyncTaskDetails taskDetails) {
    InfrastructureMapping infraMapping =
        infrastructureMappingService.get(taskDetails.getAppId(), taskDetails.getInfraMappingId());

    if (!(infraMapping instanceof ContainerInfrastructureMapping)) {
      log.error("Unsupported infrastructure mapping being tracked here: [{}]. InfraMappingType found: [{}]",
          taskDetails, infraMapping.getClass().getName());
      return Collections.emptyList();
    }

    if (CollectionUtils.isEmpty(taskDetails.getReleaseIdentifiers())) {
      return Collections.emptyList();
    }

    ContainerInfrastructureMapping containerInfraMapping = (ContainerInfrastructureMapping) infraMapping;
    K8sClusterConfig clusterConfig = containerDeploymentManagerHelper.getK8sClusterConfig(containerInfraMapping, null);

    List<CgDeploymentReleaseDetails> releaseDetails = new ArrayList<>();
    taskDetails.getReleaseIdentifiers()
        .stream()
        .filter(releaseIdentifier -> releaseIdentifier instanceof CgK8sReleaseIdentifier)
        .map(releaseIdentifier -> (CgK8sReleaseIdentifier) releaseIdentifier)
        .forEach(releaseIdentifier
            -> releaseDetails.addAll(
                releaseIdentifier.getNamespaces()
                    .stream()
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
                                       .setIsHelm(releaseIdentifier.isHelmDeployment())
                                       .setContainerServiceName(isEmpty(releaseIdentifier.getContainerServiceName())
                                               ? ""
                                               : releaseIdentifier.getContainerServiceName())
                                       .build()))
                               .build())
                    .collect(Collectors.toList())));

    return releaseDetails;
  }
}
