/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.stages.plancreator;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.yaml.YAMLFieldNameConstants.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.execution.*;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.CIIntegrationStageModifier;
import io.harness.ci.execution.integrationstage.IntegrationStageUtils;
import io.harness.ci.execution.plan.creator.codebase.CodebasePlanCreator;
import io.harness.ci.execution.states.CISpecStep;
import io.harness.ci.execution.states.IntegrationStageStepPMS;
import io.harness.ci.execution.utils.CIStagePlanCreationUtils;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.pipeline.IDPStageSpecTypeConstants;
import io.harness.idp.pipeline.stages.node.IDPStageNode;
import io.harness.idp.pipeline.stages.step.IDPStageStepPMS;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.stages.AbstractStagePlanCreator;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.*;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.NGSpecStep;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IDPStagePlanCreator extends AbstractStagePlanCreator<IDPStageNode> {
  @Inject private CIIntegrationStageModifier ciIntegrationStageModifier;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private CIStagePlanCreationUtils ciStagePlanCreationUtils;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, IDPStageNode stageNode) {
    log.info("Received plan creation request for IDP stage {}", stageNode.getIdentifier());
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();

    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));
    YamlField executionField = specField.getNode().getField(EXECUTION);
    YamlNode parentNode = executionField.getNode().getParentNode();
    String childNodeId = executionField.getNode().getUuid();
    ExecutionSource executionSource = buildExecutionSource(ctx, stageNode);

    IntegrationStageConfig integrationStageConfig = (IntegrationStageConfig) stageNode.getStageInfoConfig();
    boolean cloneCodebase =
        RunTimeInputHandler.resolveBooleanParameter(integrationStageConfig.getCloneCodebase(), false);

    if (cloneCodebase) {
      String codeBaseNodeUUID =
          fetchCodeBaseNodeUUID(ctx, executionField.getNode().getUuid(), executionSource, planCreationResponseMap);
      if (isNotEmpty(codeBaseNodeUUID)) {
        childNodeId = codeBaseNodeUUID; // Change the child of security stage to codebase node
      }
    }

    ExecutionElementConfig modifiedExecutionPlan =
        modifyYAMLWithImplicitSteps(ctx, executionSource, executionField, stageNode);

    addStrategyFieldDependencyIfPresent(ctx, stageNode, planCreationResponseMap, metadataMap);

    putNewExecutionYAMLInResponseMap(executionField, planCreationResponseMap, modifiedExecutionPlan, parentNode);

    PlanNode specPlanNode =
        getSpecPlanNode(specField, NGSectionStepParameters.builder().childNodeId(childNodeId).build());
    planCreationResponseMap.put(
        specPlanNode.getUuid(), PlanCreationResponse.builder().node(specPlanNode.getUuid(), specPlanNode).build());

    log.info("Successfully created plan for IDP Stage {}", stageNode.getIdentifier());
    return planCreationResponseMap;
  }

  @Override
  public Set<String> getSupportedStageTypes() {
    return ImmutableSet.of(IDPStageSpecTypeConstants.IDP_STAGE);
  }

  @Override
  public StepType getStepType(IDPStageNode stageNode) {
    return IDPStageStepPMS.STEP_TYPE;
  }

  @Override
  public SpecParameters getSpecParameters(String childNodeId, PlanCreationContext ctx, IDPStageNode stageNode) {
    ExecutionSource executionSource = buildExecutionSource(ctx, stageNode);
    BuildStatusUpdateParameter buildStatusUpdateParameter =
        obtainBuildStatusUpdateParameter(ctx, stageNode, executionSource);
    return IntegrationStageStepParametersPMS.getStepParameters(
        getIntegrationStageNode(stageNode), childNodeId, buildStatusUpdateParameter, ctx);
  }

  @Override
  public Class<IDPStageNode> getFieldClass() {
    return IDPStageNode.class;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, IDPStageNode stageNode, List<String> childrenNodeIds) {
    stageNode.setIdentifier(StrategyUtils.getIdentifierWithExpression(ctx, stageNode.getIdentifier()));
    stageNode.setName(StrategyUtils.getIdentifierWithExpression(ctx, stageNode.getName()));

    StageElementParameters.StageElementParametersBuilder stageParameters =
        ciStagePlanCreationUtils.getStageParameters(getIntegrationStageNode(stageNode));
    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));
    stageParameters.specConfig(getSpecParameters(specField.getNode().getUuid(), ctx, stageNode));
    return PlanNode.builder()
        .uuid(StrategyUtils.getSwappedPlanNodeId(ctx, stageNode.getUuid()))
        .name(stageNode.getName())
        .identifier(stageNode.getIdentifier())
        .group(StepOutcomeGroup.STAGE.name())
        .stepParameters(stageParameters.build())
        .stepType(getStepType(stageNode))
        .skipCondition(SkipInfoUtils.getSkipCondition(stageNode.getSkipCondition()))
        .whenCondition(RunInfoUtils.getRunConditionForStage(stageNode.getWhen()))
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .adviserObtainments(StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, true))
        .build();
  }

  private void putNewExecutionYAMLInResponseMap(YamlField executionField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, ExecutionElementConfig modifiedExecutionPlan,
      YamlNode parentYamlNode) {
    try {
      String jsonString = JsonPipelineUtils.writeJsonString(modifiedExecutionPlan);
      JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
      YamlNode modifiedExecutionNode = new YamlNode(EXECUTION, jsonNode, parentYamlNode);

      YamlField yamlField = new YamlField(EXECUTION, modifiedExecutionNode);
      planCreationResponseMap.put(executionField.getNode().getUuid(),
          PlanCreationResponse.builder()
              .dependencies(
                  DependenciesUtils.toDependenciesProto(ImmutableMap.of(yamlField.getNode().getUuid(), yamlField)))
              .yamlUpdates(YamlUpdates.newBuilder().putFqnToYaml(yamlField.getYamlPath(), jsonString).build())
              .build());

    } catch (IOException e) {
      throw new InvalidRequestException("Invalid yaml", e);
    }
  }

  private ExecutionElementConfig modifyYAMLWithImplicitSteps(
      PlanCreationContext ctx, ExecutionSource executionSource, YamlField executionYAMLField, IDPStageNode stageNode) {
    ExecutionElementConfig executionElementConfig;
    try {
      executionElementConfig = YamlUtils.read(executionYAMLField.getNode().toString(), ExecutionElementConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid yaml", e);
    }
    IntegrationStageNode integrationStageNode = getIntegrationStageNode(stageNode);
    return ciIntegrationStageModifier.modifyExecutionPlan(executionElementConfig, integrationStageNode, ctx,
        getCICodebase(ctx), IntegrationStageStepParametersPMS.getInfrastructure(integrationStageNode, ctx),
        executionSource);
  }

  private String fetchCodeBaseNodeUUID(PlanCreationContext ctx, String executionNodeUUid,
      ExecutionSource executionSource, LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap) {
    YamlField ciCodeBaseField = getCodebaseYamlField(ctx);
    if (ciCodeBaseField != null) {
      String codeBaseNodeUUID = generateUuid();
      List<PlanNode> codeBasePlanNodeList = CodebasePlanCreator.createPlanForCodeBase(
          ciCodeBaseField, executionNodeUUid, kryoSerializer, codeBaseNodeUUID, executionSource);
      if (isNotEmpty(codeBasePlanNodeList)) {
        for (PlanNode planNode : codeBasePlanNodeList) {
          planCreationResponseMap.put(
              planNode.getUuid(), PlanCreationResponse.builder().node(planNode.getUuid(), planNode).build());
        }
        return codeBaseNodeUUID;
      }
    }
    return null;
  }

  private PlanNode getSpecPlanNode(YamlField specField, NGSectionStepParameters stepParameters) {
    return PlanNode.builder()
        .uuid(specField.getNode().getUuid())
        .identifier(YAMLFieldNameConstants.SPEC)
        .stepType(NGSpecStep.STEP_TYPE)
        .name(YAMLFieldNameConstants.SPEC)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }

  private ExecutionSource buildExecutionSource(PlanCreationContext ctx, IDPStageNode stageNode) {
    CodeBase codeBase = getCICodebase(ctx);

    if (codeBase == null) {
      //  code base is not mandatory in case git clone is false, Sending status won't be possible
      return null;
    }
    ExecutionTriggerInfo triggerInfo = ctx.getTriggerInfo();
    TriggerPayload triggerPayload = ctx.getTriggerPayload();

    return IntegrationStageUtils.buildExecutionSource(triggerInfo, triggerPayload, stageNode.getIdentifier(),
        codeBase.getBuild(), codeBase.getConnectorRef().getValue(), connectorUtils, ctx, codeBase);
  }

  private BuildStatusUpdateParameter obtainBuildStatusUpdateParameter(
      PlanCreationContext ctx, IDPStageNode stageNode, ExecutionSource executionSource) {
    CodeBase codeBase = getCICodebase(ctx);

    if (codeBase == null) {
      //  code base is not mandatory in case git clone is false, Sending status won't be possible
      return null;
    }

    if (executionSource != null && executionSource.getType() == ExecutionSource.Type.WEBHOOK) {
      String sha = retrieveLastCommitSha((WebhookExecutionSource) executionSource);
      return BuildStatusUpdateParameter.builder()
          .sha(sha)
          .connectorIdentifier(codeBase.getConnectorRef().getValue())
          .repoName(codeBase.getRepoName().getValue())
          .name(stageNode.getName())
          .identifier(stageNode.getIdentifier())
          .build();
    } else {
      return BuildStatusUpdateParameter.builder()
          .connectorIdentifier(codeBase.getConnectorRef().getValue())
          .repoName(codeBase.getRepoName().getValue())
          .name(stageNode.getName())
          .identifier(stageNode.getIdentifier())
          .build();
    }
  }

  private String retrieveLastCommitSha(WebhookExecutionSource webhookExecutionSource) {
    if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.PR) {
      PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();
      return prWebhookEvent.getBaseAttributes().getAfter();
    } else if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.BRANCH) {
      BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();
      return branchWebhookEvent.getBaseAttributes().getAfter();
    }

    log.error("Non supported event type, status will be empty");
    return "";
  }

  private CodeBase getCICodebase(PlanCreationContext ctx) {
    CodeBase ciCodeBase = null;
    try {
      YamlNode properties = YamlUtils.getGivenYamlNodeFromParentPath(ctx.getCurrentField().getNode(), PROPERTIES);
      YamlNode ciCodeBaseNode = properties.getField(CI).getNode().getField(CI_CODE_BASE).getNode();
      ciCodeBase = IntegrationStageUtils.getCiCodeBase(ciCodeBaseNode);
    } catch (Exception ex) {
      // Ignore exception because code base is not mandatory in case git clone is false
      log.warn("Failed to retrieve ciCodeBase from pipeline");
    }

    return ciCodeBase;
  }

  private YamlField getCodebaseYamlField(PlanCreationContext ctx) {
    YamlField ciCodeBaseYamlField = null;
    try {
      YamlNode properties = YamlUtils.getGivenYamlNodeFromParentPath(ctx.getCurrentField().getNode(), PROPERTIES);
      ciCodeBaseYamlField = properties.getField(CI).getNode().getField(CI_CODE_BASE);
    } catch (Exception ex) {
      // Ignore exception because code base is not mandatory in case git clone is false
      log.warn("Failed to retrieve ciCodeBase from pipeline");
    }
    return ciCodeBaseYamlField;
  }
  private IntegrationStageNode getIntegrationStageNode(IDPStageNode stageNode) {
    IntegrationStageConfig currentStageConfig = (IntegrationStageConfig) stageNode.getStageInfoConfig();
    IntegrationStageConfigImpl integrationConfig = IntegrationStageConfigImpl.builder()
                                                       .sharedPaths(currentStageConfig.getSharedPaths())
                                                       .execution(currentStageConfig.getExecution())
                                                       .runtime(currentStageConfig.getRuntime())
                                                       .serviceDependencies(currentStageConfig.getServiceDependencies())
                                                       .platform(currentStageConfig.getPlatform())
                                                       .cloneCodebase(currentStageConfig.getCloneCodebase())
                                                       .infrastructure(currentStageConfig.getInfrastructure())
                                                       .build();

    return IntegrationStageNode.builder()
        .uuid(stageNode.getUuid())
        .name(stageNode.getName())
        .failureStrategies(stageNode.getFailureStrategies())
        .type(IntegrationStageNode.StepType.CI)
        .identifier(stageNode.getIdentifier())
        .variables(stageNode.getVariables())
        .integrationStageConfig(integrationConfig)
        .build();
  }
}