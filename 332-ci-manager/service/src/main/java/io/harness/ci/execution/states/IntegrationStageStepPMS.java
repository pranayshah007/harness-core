/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.authorization.AuthorizationServiceHeader.CI_MANAGER;
import static io.harness.beans.FeatureName.CODE_ENABLED;
import static io.harness.beans.steps.outcome.CIOutcomeNames.INTEGRATION_STAGE_OUTCOME;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.ALL_TASK_SELECTORS;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_EXECUTION;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.UNIQUE_STEP_IDENTIFIERS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.app.beans.entities.CIResourceCleanup.CIResourceCleanupResponseKeys;
import io.harness.app.beans.entities.InfraResourceDetails;
import io.harness.app.beans.entities.ResourceDetails;
import io.harness.app.beans.entities.StepExecutionParameters;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.CIRegistry;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.IntegrationStageOutcome;
import io.harness.beans.steps.outcome.IntegrationStageOutcome.IntegrationStageOutcomeBuilder;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageExecutionSweepingOutput;
import io.harness.beans.sweepingoutputs.TaskSelectorSweepingOutput;
import io.harness.beans.sweepingoutputs.UniqueStepIdentifiersSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.IntegrationStageUtils;
import io.harness.ci.execution.utils.CompletableFutures;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.UnexpectedException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ng.core.NGAccess;
import io.harness.persistence.HPersistence;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.repositories.StepExecutionParametersRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.registry.Registry;
import io.harness.yaml.registry.RegistryCredential;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class IntegrationStageStepPMS implements ChildExecutable<StageElementParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType("IntegrationStageStepPMS").setStepCategory(StepCategory.STAGE).build();

  @Inject ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject OutcomeService outcomeService;
  @Inject @Named("ciBackgroundTaskExecutor") private ExecutorService executorService;
  @Inject ConnectorUtils connectorUtils;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject private StepExecutionParametersRepository stepExecutionParametersRepository;
  @Inject private HPersistence persistence;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Class<StageElementParameters> getStepParametersClass() {
    return StageElementParameters.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, StageElementParameters stepParameters, StepInputPackage inputPackage) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    try {
      String jsonString = RecastOrchestrationUtils.toJson(stepParameters);
      byte[] jsonByte = EncodingUtils.compressString(jsonString);
      String base64String = EncodingUtils.encodeBase64(jsonByte);
      log.info("Executing integration stage with params accountId {} projectId {} [{}]",
          ngAccess.getAccountIdentifier(), ngAccess.getProjectIdentifier(), base64String);
    } catch (Exception e) {
      log.error("Could not serialize class StageElementParameters", e);
    }

    String stageRuntimeId = AmbianceUtils.getStageRuntimeIdAmbiance(ambiance);

    stepExecutionParametersRepository.save(StepExecutionParameters.builder()
                                               .accountId(AmbianceUtils.getAccountId(ambiance))
                                               .stageRunTimeId(stageRuntimeId)
                                               .runTimeId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                               .stepParameters(RecastOrchestrationUtils.toJson(stepParameters))
                                               .build());

    IntegrationStageStepParametersPMS integrationStageStepParametersPMS =
        (IntegrationStageStepParametersPMS) stepParameters.getSpecConfig();

    Infrastructure infrastructure = integrationStageStepParametersPMS.getInfrastructure();

    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    ExecutionSource executionSource =
        getExecutionSource(ambiance, integrationStageStepParametersPMS, stepParameters.getIdentifier());
    BuildStatusUpdateParameter buildStatusUpdateParameter =
        integrationStageStepParametersPMS.getBuildStatusUpdateParameter() != null
        ? integrationStageStepParametersPMS.getBuildStatusUpdateParameter()
        : obtainBuildStatusUpdateParameter(
            stepParameters, executionSource, integrationStageStepParametersPMS.getCodeBase());

    StageDetails stageDetails =
        StageDetails.builder()
            .stageID(stepParameters.getIdentifier())
            .stageRuntimeID(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
            .buildStatusUpdateParameter(buildStatusUpdateParameter)
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .registries(getRegistries(ngAccess, integrationStageStepParametersPMS.getRegistry()))
            .executionSource(executionSource)
            .build();

    K8PodDetails k8PodDetails = K8PodDetails.builder()
                                    .stageID(stepParameters.getIdentifier())
                                    .stageName(stepParameters.getName())
                                    .accountId(AmbianceUtils.getAccountId(ambiance))
                                    .build();

    executionSweepingOutputResolver.consume(
        ambiance, ContextElement.podDetails, k8PodDetails, StepOutcomeGroup.STAGE.name());

    executionSweepingOutputResolver.consume(
        ambiance, ContextElement.stageDetails, stageDetails, StepOutcomeGroup.STAGE.name());

    upsertCleanupEntity(ambiance);

    List<TaskSelector> selectorList =
        TaskSelectorYaml.toTaskSelector(integrationStageStepParametersPMS.getPipelineDelegateSelectors());
    if (isNotEmpty(selectorList)) {
      // Add to selectorList also add to sweeping output so that it can be used during other tasks
      selectorList = selectorList.stream()
                         .map(selector -> selector.toBuilder().setOrigin(YAMLFieldNameConstants.PIPELINE).build())
                         .toList();

      // currently only adding pipeline delegate selectors here. We can modify to add others.
      TaskSelectorSweepingOutput taskSelectorSweepingOutput =
          TaskSelectorSweepingOutput.builder().taskSelectors(selectorList).build();
      executionSweepingOutputResolver.consume(
          ambiance, ALL_TASK_SELECTORS, taskSelectorSweepingOutput, StepOutcomeGroup.STAGE.name());
    }

    final String executionNodeId = integrationStageStepParametersPMS.getChildNodeID();
    return ChildExecutableResponse.newBuilder().setChildNodeId(executionNodeId).build();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, StageElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    long startTime = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    long currentTime = System.currentTimeMillis();
    saveStageExecutionSweepingOutput(ambiance, currentTime - startTime);
    updateCleanupEntity(ambiance);

    StepResponseNotifyData stepResponseNotifyData = filterStepResponse(responseDataMap);

    Status stageStatus = stepResponseNotifyData.getStatus();
    log.info("Executed integration stage {} in {} milliseconds with status {} ", stepParameters.getIdentifier(),
        (currentTime - startTime) / 1000, stageStatus);

    IntegrationStageStepParametersPMS integrationStageStepParametersPMS =
        (IntegrationStageStepParametersPMS) stepParameters.getSpecConfig();
    // For running executions initialise would have already happened so sweeping output won't be present.
    // Keeping it as a backup.
    List<String> stepIdentifiers = integrationStageStepParametersPMS.getStepIdentifiers();

    StepResponseBuilder stepResponseBuilder = createStepResponseFromChildResponse(responseDataMap).toBuilder();
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(UNIQUE_STEP_IDENTIFIERS));
    if (optionalSweepingOutput.isFound() || isNotEmpty(stepIdentifiers)) {
      // if sweeping output is found then use step identifiers from it
      if (optionalSweepingOutput.isFound()) {
        UniqueStepIdentifiersSweepingOutput uniqueStepIdentifiersSweepingOutput =
            (UniqueStepIdentifiersSweepingOutput) optionalSweepingOutput.getOutput();
        stepIdentifiers = uniqueStepIdentifiersSweepingOutput.getUniqueStepIdentifiers();
      }
      List<Outcome> outcomes = stepIdentifiers.stream()
                                   .map(stepIdentifier
                                       -> outcomeService.resolveOptional(
                                           ambiance, RefObjectUtils.getOutcomeRefObject("artifact_" + stepIdentifier)))
                                   .filter(OptionalOutcome::isFound)
                                   .map(OptionalOutcome::getOutcome)
                                   .collect(Collectors.toList());
      if (isNotEmpty(outcomes)) {
        IntegrationStageOutcomeBuilder integrationStageOutcomeBuilder = IntegrationStageOutcome.builder();
        for (Outcome outcome : outcomes) {
          if (outcome instanceof CIStepArtifactOutcome) {
            CIStepArtifactOutcome ciStepArtifactOutcome = (CIStepArtifactOutcome) outcome;

            if (ciStepArtifactOutcome.getStepArtifacts() != null) {
              if (isNotEmpty(ciStepArtifactOutcome.getStepArtifacts().getPublishedFileArtifacts())) {
                ciStepArtifactOutcome.getStepArtifacts().getPublishedFileArtifacts().forEach(
                    integrationStageOutcomeBuilder::fileArtifact);
              }
              if (isNotEmpty(ciStepArtifactOutcome.getStepArtifacts().getPublishedImageArtifacts())) {
                ciStepArtifactOutcome.getStepArtifacts().getPublishedImageArtifacts().forEach(
                    integrationStageOutcomeBuilder::imageArtifact);
              }
              if (isNotEmpty(ciStepArtifactOutcome.getStepArtifacts().getPublishedSbomArtifacts())) {
                ciStepArtifactOutcome.getStepArtifacts().getPublishedSbomArtifacts().forEach(
                    integrationStageOutcomeBuilder::sbomArtifact);
              }
            }
          }
        }

        stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                            .name(INTEGRATION_STAGE_OUTCOME)
                                            .outcome(integrationStageOutcomeBuilder.build())
                                            .build());
      }
    }

    return stepResponseBuilder.build();
  }

  private StepResponseNotifyData filterStepResponse(Map<String, ResponseData> responseDataMap) {
    // Filter final response from step
    return responseDataMap.entrySet()
        .stream()
        .filter(entry -> entry.getValue() instanceof StepResponseNotifyData)
        .findFirst()
        .map(obj -> (StepResponseNotifyData) obj.getValue())
        .orElse(null);
  }

  private void saveStageExecutionSweepingOutput(Ambiance ambiance, long buildTime) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION));
    if (!optionalSweepingOutput.isFound()) {
      try {
        StageExecutionSweepingOutput stageExecutionSweepingOutput =
            StageExecutionSweepingOutput.builder().stageExecutionTime(buildTime).build();
        executionSweepingOutputResolver.consume(
            ambiance, STAGE_EXECUTION, stageExecutionSweepingOutput, StepOutcomeGroup.STAGE.name());
      } catch (Exception e) {
        log.error("Error while consuming stage execution sweeping output", e);
      }
    }
  }

  private void upsertCleanupEntity(Ambiance ambiance) {
    UpdateOperations<CIResourceCleanup> updateOperations =
        persistence.createUpdateOperations(CIResourceCleanup.class)
            .setOnInsert(CIResourceCleanupResponseKeys.accountId, AmbianceUtils.getAccountId(ambiance))
            .setOnInsert(CIResourceCleanupResponseKeys.planExecutionId, ambiance.getPlanExecutionId())
            .setOnInsert(CIResourceCleanupResponseKeys.stageExecutionId, ambiance.getStageExecutionId())
            .setOnInsert(CIResourceCleanupResponseKeys.processAfter, System.currentTimeMillis())
            .setOnInsert(CIResourceCleanupResponseKeys.shouldStart, false)
            .setOnInsert(CIResourceCleanupResponseKeys.validUntil, Date.from(OffsetDateTime.now().toInstant()))
            .setOnInsert(CIResourceCleanupResponseKeys.retryCount, 0)
            .setOnInsert(CIResourceCleanupResponseKeys.type, ResourceDetails.Type.INFRA.toString())
            .setOnInsert(CIResourceCleanupResponseKeys.data,
                kryoSerializer.asBytes(InfraResourceDetails.builder().ambiance(ambiance).build()));
    Query<CIResourceCleanup> upsertQuery =
        persistence.createQuery(CIResourceCleanup.class, excludeAuthority)
            .filter(CIResourceCleanupResponseKeys.stageExecutionId, ambiance.getStageExecutionId());
    persistence.upsert(upsertQuery, updateOperations);
  }

  private void updateCleanupEntity(Ambiance ambiance) {
    UpdateOperations<CIResourceCleanup> updateOperations =
        persistence.createUpdateOperations(CIResourceCleanup.class)
            .set(CIResourceCleanupResponseKeys.shouldStart, true)
            .set(CIResourceCleanupResponseKeys.processAfter, System.currentTimeMillis());
    Query<CIResourceCleanup> updateQuery =
        persistence.createQuery(CIResourceCleanup.class, excludeAuthority)
            .filter(CIResourceCleanupResponseKeys.stageExecutionId, ambiance.getStageExecutionId());
    persistence.update(updateQuery, updateOperations);
  }

  private List<CIRegistry> getRegistries(NGAccess ngAccess, Registry registry) {
    if (registry == null || isEmpty(registry.getCredentials())) {
      return Collections.emptyList();
    }
    CompletableFutures<CIRegistry> completableFutures = new CompletableFutures<>(executorService);
    List<RegistryCredential> credentials = registry.getCredentials();
    credentials.stream()
        .filter(c -> !ParameterField.isBlank(c.getName()))
        .forEach(c -> completableFutures.supplyAsync(() -> {
          try {
            String connectorIdentifier = (String) c.getName().fetchFinalValue();
            String match = (String) c.getMatch().fetchFinalValue();
            ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier);
            return CIRegistry.builder()
                .connectorIdentifier(connectorIdentifier)
                .connectorType(connectorDetails.getConnectorType())
                .match(match)
                .build();
          } catch (Exception e) {
            log.warn(String.format("Exception occurred while fetching connector details: %s", e.getMessage()));
          }
          return null;
        }));

    try {
      List<CIRegistry> registries = completableFutures.allOf().get(10, TimeUnit.SECONDS);
      return registries.stream().filter(Objects::nonNull).collect(Collectors.toList());
    } catch (Exception ex) {
      throw new UnexpectedException("Error fetching connector details response from service", ex);
    }
  }

  private ExecutionSource getExecutionSource(
      Ambiance ambiance, IntegrationStageStepParametersPMS integrationStageStepParametersPMS, String identifier) {
    CodeBase codeBase = integrationStageStepParametersPMS.getCodeBase();
    if (codeBase == null) {
      return null;
    }
    if (ParameterField.isNull(codeBase.getBuild())) {
      throw new CIStageExecutionException(" build properties must be defined when codebase is enabled");
    }
    ExecutionTriggerInfo triggerInfo = ambiance.getMetadata().getTriggerInfo();
    TriggerPayload triggerPayload = integrationStageStepParametersPMS.getTriggerPayload();
    // setPrincipalForHarnessSCM(ambiance, codeBase.getConnectorRef().getValue(), triggerInfo);
    return IntegrationStageUtils.buildExecutionSourceV2(ambiance, triggerInfo, triggerPayload, identifier,
        codeBase.getBuild(), codeBase.getConnectorRef().getValue(), connectorUtils, codeBase,
        integrationStageStepParametersPMS.getCloneManually());
  }

  private void setPrincipalForHarnessSCM(Ambiance ambiance, String connectorId, ExecutionTriggerInfo triggerInfo) {
    if (isEmpty(connectorId) && featureFlagService.isEnabled(CODE_ENABLED, AmbianceUtils.getAccountId(ambiance))) {
      if (triggerInfo.getTriggerType() == TriggerType.MANUAL) {
        ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
        String principal = executionPrincipalInfo.getPrincipal();
        TriggeredBy triggeredBy = triggerInfo.getTriggeredBy();
        SecurityContextBuilder.setContext(new UserPrincipal(principal, triggeredBy.getExtraInfoMap().get("email"),
            triggeredBy.getIdentifier(), AmbianceUtils.getAccountId(ambiance)));
      } else if (triggerInfo.getTriggerType() == TriggerType.SCHEDULER_CRON
          || triggerInfo.getTriggerType() == TriggerType.WEBHOOK
          || triggerInfo.getTriggerType() == TriggerType.WEBHOOK_CUSTOM) {
        SecurityContextBuilder.setContext(new ServicePrincipal(CI_MANAGER.getServiceId()));
      } else {
        log.info("Received trigger type " + triggerInfo.getTriggerType());
      }
    }
  }

  private BuildStatusUpdateParameter obtainBuildStatusUpdateParameter(
      StageElementParameters stageElementParameters, ExecutionSource executionSource, CodeBase codeBase) {
    if (codeBase == null) {
      //  code base is not mandatory in case git clone is false, Sending status won't be possible
      return null;
    }

    if (executionSource != null && executionSource.getType() == ExecutionSource.Type.WEBHOOK) {
      String sha = IntegrationStageUtils.retrieveLastCommitSha((WebhookExecutionSource) executionSource);
      return BuildStatusUpdateParameter.builder()
          .sha(sha)
          .connectorIdentifier(codeBase.getConnectorRef().getValue())
          .repoName(codeBase.getRepoName().getValue())
          .name(stageElementParameters.getName())
          .identifier(stageElementParameters.getIdentifier())
          .build();
    } else {
      return BuildStatusUpdateParameter.builder()
          .connectorIdentifier(codeBase.getConnectorRef().getValue())
          .repoName(codeBase.getRepoName().getValue())
          .name(stageElementParameters.getName())
          .identifier(stageElementParameters.getIdentifier())
          .build();
    }
  }
}
