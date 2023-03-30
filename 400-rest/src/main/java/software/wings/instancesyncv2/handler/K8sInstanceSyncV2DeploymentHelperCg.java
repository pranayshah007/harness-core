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
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;

import static software.wings.beans.TaskType.PT_SERIALIZATION_SUPPORT;

import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.Capability;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesyncv2.DirectK8sReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.api.ContainerDeploymentInfoWithLabels;
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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
  @VisibleForTesting static final long RELEASE_PRESERVE_TIME = TimeUnit.DAYS.toMillis(7);
  private final ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  private final InfrastructureMappingService infrastructureMappingService;

  private final KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private DelegateTaskService delegateTaskService;

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
                       .setKryoCapability(ByteString.copyFrom(
                           getKryoSerializer(cloudProvider.getAccountId()).asDeflatedBytes(executionCapability)))
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
    identifiers.addAll(existingIdentifiers);
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
      return createReleaseIdentifiersK8sDeploymentInfo(deploymentInfo);
    } else if (deploymentInfo instanceof ContainerDeploymentInfoWithLabels) {
      return createReleaseIdentifiersContainerDeploymentInfoWithLabels(deploymentInfo);
    } else {
      throw new InvalidRequestException("DeploymentInfo of type: [" + deploymentInfo.getClass().getCanonicalName()
          + "] not supported with V2 Instance Sync framework.");
    }
  }

  private Set<CgReleaseIdentifiers> createReleaseIdentifiersContainerDeploymentInfoWithLabels(
      DeploymentInfo deploymentInfo) {
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

    Set<CgReleaseIdentifiers> cgReleaseIdentifiersSet = new HashSet<>();
    if (isNotEmpty(controllers)) {
      for (String namespace : namespaces) {
        cgReleaseIdentifiersSet.addAll(controllers.stream()
                                           .map(controller
                                               -> CgK8sReleaseIdentifier.builder()
                                                      .containerServiceName(isEmpty(controller) ? null : controller)
                                                      .namespace(namespace)
                                                      .releaseName(containerDeploymentInfo.getReleaseName())
                                                      .isHelmDeployment(true)
                                                      .build())
                                           .collect(Collectors.toSet()));
      }

      return cgReleaseIdentifiersSet;
    } else if (isNotEmpty(containerDeploymentInfo.getContainerInfoList())) {
      for (String namespace : namespaces) {
        cgReleaseIdentifiersSet.add(CgK8sReleaseIdentifier.builder()
                                        .namespace(namespace)
                                        .releaseName(containerDeploymentInfo.getReleaseName())
                                        .isHelmDeployment(true)
                                        .build());
      }
      return cgReleaseIdentifiersSet;
    }

    return Collections.emptySet();
  }

  private Set<CgReleaseIdentifiers> createReleaseIdentifiersK8sDeploymentInfo(DeploymentInfo deploymentInfo) {
    K8sDeploymentInfo k8sDeploymentInfo = (K8sDeploymentInfo) deploymentInfo;
    Set<String> namespaces = getNamespaces(k8sDeploymentInfo.getNamespaces(), k8sDeploymentInfo.getNamespace());
    if (CollectionUtils.isEmpty(namespaces)) {
      log.error("No namespace found for deployment info. Returning empty");
      return Collections.emptySet();
    }

    Set<CgReleaseIdentifiers> cgReleaseIdentifiersSet = new HashSet<>();
    for (String namespace : namespaces) {
      cgReleaseIdentifiersSet.add(CgK8sReleaseIdentifier.builder()
                                      .clusterName(k8sDeploymentInfo.getClusterName())
                                      .releaseName(k8sDeploymentInfo.getReleaseName())
                                      .namespace(namespace)
                                      .isHelmDeployment(false)
                                      .build());
    }
    return cgReleaseIdentifiersSet;
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

    if (infraMapping == null) {
      return Collections.emptyList();
    }

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
        .filter(CgK8sReleaseIdentifier.class ::isInstance)
        .map(CgK8sReleaseIdentifier.class ::cast)
        .forEach(releaseIdentifier
            -> releaseDetails.add(CgDeploymentReleaseDetails.newBuilder()
                                      .setTaskDetailsId(taskDetails.getUuid())
                                      .setInfraMappingId(taskDetails.getInfraMappingId())
                                      .setInfraMappingType(infraMapping.getInfraMappingType())
                                      .setReleaseDetails(Any.pack(
                                          directK8sInstanceSyncTaskDetailsBuilder(releaseIdentifier, clusterConfig)))
                                      .build()));

    return releaseDetails;
  }

  private DirectK8sInstanceSyncTaskDetails directK8sInstanceSyncTaskDetailsBuilder(
      CgK8sReleaseIdentifier releaseIdentifier, K8sClusterConfig clusterConfig) {
    return DirectK8sInstanceSyncTaskDetails.newBuilder()
        .setReleaseName(releaseIdentifier.getReleaseName())
        .setNamespace(releaseIdentifier.getNamespace())
        .setK8SClusterConfig(ByteString.copyFrom(kryoSerializer.asBytes(clusterConfig)))
        .setIsHelm(releaseIdentifier.isHelmDeployment())
        .setContainerServiceName(
            isEmpty(releaseIdentifier.getContainerServiceName()) ? "" : releaseIdentifier.getContainerServiceName())
        .build();
  }

  public Map<CgReleaseIdentifiers, InstanceSyncData> getCgReleaseIdentifiersList(
      List<InstanceSyncData> instanceSyncDataList) {
    Map<CgReleaseIdentifiers, InstanceSyncData> instanceSyncDataMap = new HashMap<>();
    if (isEmpty(instanceSyncDataList)) {
      return instanceSyncDataMap;
    }
    for (InstanceSyncData instanceSyncData : instanceSyncDataList) {
      instanceSyncDataMap.put(getCgReleaseIdentifiers(instanceSyncData), instanceSyncData);
    }
    return instanceSyncDataMap;
  }

  public CgK8sReleaseIdentifier getCgReleaseIdentifiers(InstanceSyncData instanceSyncData) {
    DirectK8sReleaseDetails directK8sReleaseDetails =
        AnyUtils.unpack(instanceSyncData.getReleaseDetails(), DirectK8sReleaseDetails.class);
    return CgK8sReleaseIdentifier.builder()
        .releaseName(directK8sReleaseDetails.getReleaseName())
        .namespace(directK8sReleaseDetails.getNamespace())
        .isHelmDeployment(directK8sReleaseDetails.getIsHelm())
        .containerServiceName(isEmpty(directK8sReleaseDetails.getContainerServiceName())
                ? null
                : directK8sReleaseDetails.getContainerServiceName())
        .build();
  }

  @Override
  public long getDeleteReleaseAfter(CgReleaseIdentifiers releaseIdentifier, InstanceSyncData instanceSyncData) {
    if (instanceSyncData.getInstanceCount() == 0
        || !instanceSyncData.getExecutionStatus().equals(CommandExecutionStatus.SUCCESS.name())) {
      return releaseIdentifier.getDeleteAfter();
    }
    return System.currentTimeMillis() + RELEASE_PRESERVE_TIME;
  }

  private KryoSerializer getKryoSerializer(String accountId) {
    return delegateTaskService.isTaskTypeSupportedByAllDelegates(accountId, PT_SERIALIZATION_SUPPORT.name())
        ? referenceFalseKryoSerializer
        : kryoSerializer;
  }
}
