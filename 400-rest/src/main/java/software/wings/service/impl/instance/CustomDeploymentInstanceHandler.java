/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.service.impl.instance.InstanceSyncFlow.MANUAL;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.beans.ArtifactMetadata;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.runtime.NoInstancesException;
import io.harness.validation.Validator;

import software.wings.api.CustomDeploymentTypeInfo;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.api.shellscript.provision.ShellScriptProvisionExecutionData;
import software.wings.beans.CustomInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceBuilder;
import software.wings.beans.infrastructure.instance.info.PhysicalHostInstanceInfo;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.beans.infrastructure.instance.key.deployment.CustomDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.DeploymentKey;
import software.wings.beans.template.deploymenttype.CustomDeploymentTypeTemplate;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.CustomDeploymentInstanceSyncPTCreator;
import software.wings.service.InstanceSyncPerpetualTaskCreator;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeService;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.StepExecutionSummary;
import software.wings.sm.states.customdeploymentng.InstanceMapperUtils;
import software.wings.sm.states.customdeploymentng.InstanceMapperUtils.HostProperties;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class CustomDeploymentInstanceHandler extends InstanceHandler implements InstanceSyncByPerpetualTaskHandler {
  @Inject private CustomDeploymentInstanceSyncPTCreator perpetualTaskCreator;
  @Inject private CustomDeploymentTypeService customDeploymentTypeService;
  @Inject ArtifactService artifactService;
  public static final String artifactBuildNum = "artifactBuildNumber";
  static Function<HostProperties, PhysicalHostInstanceInfo> jsonMapper = hostProperties
      -> PhysicalHostInstanceInfo.builder()
             .hostId(hostProperties.getHostName())
             .hostName(hostProperties.getHostName())
             .properties(hostProperties.getOtherPropeties())
             .build();

  @Override
  public void syncInstances(String appId, String infraMappingId, InstanceSyncFlow instanceSyncFlow) {
    if (MANUAL != instanceSyncFlow) {
      syncInstancesInternal(appId, infraMappingId, null, null, instanceSyncFlow);
    }
  }

  private void syncInstancesInternal(String appId, String infraMappingId,
      List<DeploymentSummary> newDeploymentSummaries, ShellScriptProvisionExecutionData response,
      InstanceSyncFlow instanceSyncFlow) {
    log.debug("Performing Custom Deployment Type Instance sync via [{}], Infrastructure Mapping : [{}]",
        instanceSyncFlow, infraMappingId);
    InfrastructureMapping infrastructureMapping = infraMappingService.get(appId, infraMappingId);
    validateInfraMapping(infrastructureMapping);

    final CustomDeploymentTypeTemplate deploymentTypeTemplate = customDeploymentTypeService.fetchDeploymentTemplate(
        infrastructureMapping.getAccountId(), infrastructureMapping.getCustomDeploymentTemplateId(),
        ((CustomInfrastructureMapping) infrastructureMapping).getDeploymentTypeTemplateVersion());

    final List<Instance> instancesInDb = instanceService.getInstancesForAppAndInframapping(appId, infraMappingId);

    final String scriptOutput = getScriptOutput(newDeploymentSummaries, response);

    Validator.notEmptyCheck("Instance Fetch Script Must Have Some Output", scriptOutput);

    final List<PhysicalHostInstanceInfo> latestHostInfos =
        InstanceMapperUtils.mapJsonToInstanceElements(deploymentTypeTemplate.getHostAttributes(),
            deploymentTypeTemplate.getHostObjectArrayPath(), scriptOutput, jsonMapper);

    if (instanceSyncFlow == InstanceSyncFlow.NEW_DEPLOYMENT) {
      rewriteInstances(instancesInDb, latestHostInfos, newDeploymentSummaries, infrastructureMapping);
    } else {
      incrementalUpdate(instancesInDb, latestHostInfos, newDeploymentSummaries, infrastructureMapping);
    }
  }

  private void rewriteInstances(List<Instance> instancesInDb, List<PhysicalHostInstanceInfo> latestHostInfos,
      List<DeploymentSummary> newDeploymentSummaries, InfrastructureMapping infraMapping) {
    log.debug("Instances to be added {}", latestHostInfos.size());
    log.debug("Instances to be deleted {}", instancesInDb.size());

    if (isNotEmpty(instancesInDb)) {
      instanceService.delete(instancesInDb.stream().map(Instance::getUuid).collect(Collectors.toSet()));
    }

    final DeploymentSummary deploymentSummary;
    if (isNotEmpty(latestHostInfos) && isNotEmpty(newDeploymentSummaries)) {
      deploymentSummary = updateDeploymentSummaryFromDeploymentInfo(newDeploymentSummaries.get(0));
      if (EmptyPredicate.isNotEmpty(deploymentSummary.getArtifactId())) {
        // Artifact can be null in the case of rollback in first deployment, in which we shouldn't add the instances
        latestHostInfos.stream()
            .map(hostInstanceInfo -> buildInstanceFromHostInfo(infraMapping, hostInstanceInfo, deploymentSummary))
            .forEach(instanceService::save);
      }
    }
  }

  private DeploymentSummary updateDeploymentSummaryFromDeploymentInfo(DeploymentSummary deploymentSummary) {
    // We are setting the artifact from deployment info, as it has the rollbackArtifact if it's rollback otherwise it
    // contains the artifact only
    CustomDeploymentTypeInfo deploymentInfo = (CustomDeploymentTypeInfo) deploymentSummary.getDeploymentInfo();
    deploymentSummary.setArtifactId(deploymentInfo.getArtifactId());
    deploymentSummary.setArtifactBuildNum(deploymentInfo.getArtifactBuildNum());
    deploymentSummary.setArtifactName(deploymentInfo.getArtifactName());
    deploymentSummary.setArtifactSourceName(deploymentInfo.getArtifactSourceName());
    deploymentSummary.setArtifactStreamId(deploymentInfo.getArtifactStreamId());
    return deploymentSummary;
  }

  private void incrementalUpdate(List<Instance> instancesInDb, List<PhysicalHostInstanceInfo> latestHostInfos,
      List<DeploymentSummary> newDeploymentSummaries, InfrastructureMapping infraMapping) {
    if (isEmpty(instancesInDb)) {
      throw new NoInstancesException(
          format("No instances found in database for custom deployment. Infra Mapping ID [%s]",
              infraMapping.getInfrastructureDefinitionId()));
    }

    Map<String, Instance> instancesInDbMap = instancesInDb.stream().collect(Collectors.toMap(
        instance -> ((PhysicalHostInstanceInfo) instance.getInstanceInfo()).getHostName(), Function.identity()));

    final Map<String, PhysicalHostInstanceInfo> latestHostInfoMap =
        latestHostInfos.stream().collect(Collectors.toMap(PhysicalHostInstanceInfo::getHostName, Function.identity()));

    Sets.SetView<String> instancesToBeAdded = Sets.difference(latestHostInfoMap.keySet(), instancesInDbMap.keySet());

    Sets.SetView<String> instancesToBeDeleted = Sets.difference(instancesInDbMap.keySet(), latestHostInfoMap.keySet());

    log.debug("Instances to be added {}", instancesToBeAdded);
    log.debug("Instances to be deleted {}", instancesToBeDeleted);
    log.debug(
        format("Instances in database=%d, Instances from server=%d", instancesInDb.size(), latestHostInfos.size()));

    Set<String> instanceIdsForDeletion = instancesInDbMap.entrySet()
                                             .stream()
                                             .filter(entry -> instancesToBeDeleted.contains(entry.getKey()))
                                             .map(Map.Entry::getValue)
                                             .map(Instance::getUuid)
                                             .collect(Collectors.toSet());
    if (isNotEmpty(instanceIdsForDeletion)) {
      instanceService.delete(instanceIdsForDeletion);
    }

    final DeploymentSummary deploymentSummary;
    if (isNotEmpty(instancesToBeAdded)) {
      if (isEmpty(newDeploymentSummaries)) {
        Optional<Instance> instanceWithExecutionInfoOptional = getInstanceWithExecutionInfo(instancesInDb);
        if (instanceWithExecutionInfoOptional.isEmpty()) {
          throw new NoInstancesException(
              format("No instances found in DB which contain execution summary. Infra mapping ID: [%s]",
                  infraMapping.getInfrastructureDefinitionId()));
        }
        DeploymentSummary deploymentSummaryFromPrevious =
            DeploymentSummary.builder().deploymentInfo(CustomDeploymentTypeInfo.builder().build()).build();
        deploymentSummary = generateDeploymentSummaryFromInstance(
            instanceWithExecutionInfoOptional.get(), deploymentSummaryFromPrevious);
      } else {
        deploymentSummary = getDeploymentSummaryForInstanceCreation(newDeploymentSummaries.get(0), false);
      }

      instancesToBeAdded.stream()
          .map(latestHostInfoMap::get)
          .map(hostInstanceInfo -> buildInstanceFromHostInfo(infraMapping, hostInstanceInfo, deploymentSummary))
          .forEach(instanceService::save);
    }
  }

  private Instance buildInstanceFromHostInfo(InfrastructureMapping infraMapping,
      PhysicalHostInstanceInfo hostInstanceInfo, DeploymentSummary deploymentSummary) {
    Artifact artifact = null;
    if (featureFlagService.isEnabled(
            FeatureName.CUSTOM_DEPLOYMENT_ARTIFACT_FROM_INSTANCE_JSON, infraMapping.getAccountId())) {
      if (isNotEmpty(hostInstanceInfo.getProperties())
          && !isNull(hostInstanceInfo.getProperties().get(artifactBuildNum))) {
        artifact = artifactService.getArtifactByBuildNumber(deploymentSummary.getAccountId(),
            deploymentSummary.getArtifactStreamId(), hostInstanceInfo.getProperties().get(artifactBuildNum).toString(),
            false);
        if (artifact == null) {
          log.info(
              "Couldn't find an artifact from the provided artifact build number, so setting artifact value for instance {} as empty",
              hostInstanceInfo.getHostName());
          artifact = Artifact.Builder.anArtifact()
                         .withDisplayName("")
                         .withUuid("")
                         .withArtifactStreamId(deploymentSummary.getArtifactStreamId())
                         .withArtifactSourceName("")
                         .withMetadata(new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "")))
                         .build();
        }
      }
    }
    InstanceBuilder builder = buildInstanceBase(null, infraMapping, deploymentSummary, artifact);
    builder.hostInstanceKey(HostInstanceKey.builder()
                                .hostName(hostInstanceInfo.getHostName())
                                .infraMappingId(infraMapping.getUuid())
                                .build());
    builder.instanceInfo(hostInstanceInfo);
    return builder.build();
  }

  private String getScriptOutput(
      List<DeploymentSummary> newDeploymentSummaries, ShellScriptProvisionExecutionData response) {
    if (isNotEmpty(newDeploymentSummaries)) {
      return newDeploymentSummaries.stream()
          .map(DeploymentSummary::getDeploymentInfo)
          .map(CustomDeploymentTypeInfo.class ::cast)
          .map(CustomDeploymentTypeInfo::getScriptOutput)
          .findFirst()
          .orElse(null);
    } else if (response != null) {
      return response.getOutput();
    }
    return null;
  }

  private void validateInfraMapping(InfrastructureMapping infrastructureMapping) {
    Objects.requireNonNull(infrastructureMapping);

    if (!(infrastructureMapping instanceof CustomInfrastructureMapping)) {
      String msg = "Incompatible infra mapping type. Expecting Custom type. Found:"
          + infrastructureMapping.getInfraMappingType();
      throw new InvalidRequestException(msg);
    }
  }

  @Override
  public void handleNewDeployment(
      List<DeploymentSummary> deploymentSummaries, boolean rollback, OnDemandRollbackInfo onDemandRollbackInfo) {
    if (isEmpty(deploymentSummaries)) {
      return;
    }

    String infraMappingId = deploymentSummaries.iterator().next().getInfraMappingId();
    String appId = deploymentSummaries.iterator().next().getAppId();

    syncInstancesInternal(appId, infraMappingId, deploymentSummaries, null, InstanceSyncFlow.NEW_DEPLOYMENT);
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToStopIteratorBasedInstanceSync() {
    return Optional.empty();
  }

  @Override
  public Optional<List<DeploymentInfo>> getDeploymentInfo(PhaseExecutionData phaseExecutionData,
      PhaseStepExecutionData phaseStepExecutionData, WorkflowExecution workflowExecution,
      InfrastructureMapping infrastructureMapping, String stateExecutionInstanceId, Artifact artifact) {
    PhaseStepExecutionSummary phaseStepExecutionSummary = phaseStepExecutionData.getPhaseStepExecutionSummary();
    if (phaseStepExecutionSummary != null) {
      List<StepExecutionSummary> stepExecutionSummaryList = phaseStepExecutionSummary.getStepExecutionSummaryList();
      if (stepExecutionSummaryList != null) {
        for (StepExecutionSummary stepExecutionSummary : stepExecutionSummaryList) {
          if (stepExecutionSummary instanceof DeploymentInfoExtractor) {
            return ((DeploymentInfoExtractor) stepExecutionSummary).extractDeploymentInfo();
          }
        }
      }
    }
    return Optional.empty();
  }

  @Override
  public DeploymentKey generateDeploymentKey(DeploymentInfo deploymentInfo) {
    final CustomDeploymentTypeInfo customDeploymentTypeInfo = (CustomDeploymentTypeInfo) deploymentInfo;
    return CustomDeploymentKey.builder()
        .instanceFetchScriptHash(customDeploymentTypeInfo.getInstanceFetchScript().hashCode())
        .tags(customDeploymentTypeInfo.getTags())
        .build();
  }

  @Override
  protected void setDeploymentKey(DeploymentSummary deploymentSummary, DeploymentKey deploymentKey) {
    deploymentSummary.setCustomDeploymentKey((CustomDeploymentKey) deploymentKey);
  }

  @Override
  public Optional<FeatureName> getFeatureFlagToEnablePerpetualTaskForInstanceSync() {
    return Optional.empty();
  }

  @Override
  public InstanceSyncPerpetualTaskCreator getInstanceSyncPerpetualTaskCreator() {
    return perpetualTaskCreator;
  }

  @Override
  public void processInstanceSyncResponseFromPerpetualTask(
      InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    syncInstancesInternal(infrastructureMapping.getAppId(), infrastructureMapping.getUuid(), null,
        (ShellScriptProvisionExecutionData) response, InstanceSyncFlow.PERPETUAL_TASK);
  }

  @Override
  public Status getStatus(InfrastructureMapping infrastructureMapping, DelegateResponseData response) {
    ShellScriptProvisionExecutionData executionData = (ShellScriptProvisionExecutionData) response;
    return Status.builder()
        .success(executionData.getExecutionStatus() == ExecutionStatus.SUCCESS)
        .retryable(true)
        .build();
  }
}
