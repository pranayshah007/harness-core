/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.plan.creator.stage;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.yaml.YAMLFieldNameConstants.EXECUTION;
import static io.harness.yaml.extended.ci.codebase.Build.builder;
import static io.harness.yaml.extended.ci.codebase.CodeBase.CodeBaseBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.entities.Workspace;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.stages.IACMStageNode;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.steps.IACMStepSpecTypeConstants;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.CIIntegrationStageModifier;
import io.harness.ci.execution.integrationstage.IntegrationStageUtils;
import io.harness.ci.execution.plan.creator.codebase.CodebasePlanCreator;
import io.harness.ci.execution.states.CISpecStep;
import io.harness.ci.execution.utils.CIStagePlanCreationUtils;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.IACMStageExecutionException;
import io.harness.iacm.execution.IACMIntegrationStageStepPMS;
import io.harness.iacm.execution.IACMIntegrationStageStepParametersPMS;
import io.harness.iacm.execution.IACMStepsUtils;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.AbstractStagePlanCreator;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
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
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.Build.BuildBuilder;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.PRCloneStrategy;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IACM)
public class IACMStagePMSPlanCreator extends AbstractStagePlanCreator<IACMStageNode> {
  @Inject private CIIntegrationStageModifier ciIntegrationStageModifier;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private CIStagePlanCreationUtils cIStagePlanCreationUtils;
  @Inject private IACMServiceUtils serviceUtils;

  @Inject private IACMStepsUtils iacmStepsUtils;

  /**
   This function seems to be what is called by the pmsSDK in order to create an execution plan
   It seems that from here, the PMS will take the instructions to which stages are the ones to be executed and from
   those stages, which steps are going to be inside each one. This method is called on the execution step of the
   pipeline.

   This method can also be used to check if the pipeline contains all the required steps as it receives the pipeline
   yaml as a context.
   */

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, IACMStageNode stageNode) {
    log.info("Received plan creation request for iacm stage {}", stageNode.getIdentifier());
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // Spec from the stages/IACM stage
    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));
    YamlField executionField = specField.getNode().getField(EXECUTION);
    YamlNode parentNode = executionField.getNode().getParentNode();
    String childNodeId = executionField.getNode().getUuid();
    String workspace = parentNode.getField("workspace").getNode().getCurrJsonNode().asText();

    // Force the stage execution to clone the codebase
    stageNode.getIacmStageConfig().setCloneCodebase(ParameterField.<Boolean>builder().value(true).build());

    CodeBase codeBase = getIACMCodebase(ctx, workspace);

    // Add the CODEBASE task. This task is required to be able to get the sweeping output for the clone step
    String codeBaseNodeUUID =
        fetchCodeBaseNodeUUID(codeBase, executionField.getNode().getUuid(), planCreationResponseMap);
    if (isNotEmpty(codeBaseNodeUUID)) {
      childNodeId = codeBaseNodeUUID; // Change the child of integration stage to codebase node
    }

    ExecutionSource executionSource = buildExecutionSource(ctx, stageNode);

    // Because we are using a CI stage, the Stage is of type IntegrationStageConfig. From here we are only interested
    // on 3 elements, cloneCodebase, Infrastructure (to use the dlite delegates) and Execution. I think that if any of
    // the other values are present we should fail the execution

    ExecutionElementConfig modifiedExecutionPlan =
        modifyYAMLWithImplicitSteps(ctx, executionSource, executionField, stageNode, codeBase);

    ExecutionElementConfig modifiedExecutionPlanWithWorkspace =
        addWorkspaceToIACMSteps(ctx, modifiedExecutionPlan, workspace);
    // Retrieve the Modified Plan execution where the InitialTask and Git Clone step have been injected. Then retrieve
    // the steps from the plan to the level of steps->spec->stageElementConfig->execution->steps. Here, we can inject
    // any step and that step will be available in the InitialTask step in the path:
    // stageElementConfig -> Execution -> Steps -> InjectedSteps
    putNewExecutionYAMLInResponseMap(
        executionField, planCreationResponseMap, modifiedExecutionPlanWithWorkspace, parentNode);

    BuildStatusUpdateParameter buildStatusUpdateParameter =
        obtainBuildStatusUpdateParameter(ctx, stageNode, executionSource, workspace);

    PlanNode specPlanNode = getSpecPlanNode(specField,
        IACMIntegrationStageStepParametersPMS.getStepParameters(
            ctx, getIntegrationStageNode(stageNode), codeBase, childNodeId));
    planCreationResponseMap.put(
        specPlanNode.getUuid(), PlanCreationResponse.builder().node(specPlanNode.getUuid(), specPlanNode).build());

    log.info("Successfully created plan for security stage {}", stageNode.getIdentifier());
    return planCreationResponseMap;
  }

  /*
  iterateWrapperSteps is going to iterate through all the JsonNodes until it finds the step node. Then, once the node
   with the Step info is found, it will send that to the addIACMVariablesToStep where, depending on what type of step
   is, will get specific IACM variables.
   */
  private void iterateWrapperSteps(JsonNode node, Map<String, String> envVars, Map<String, String> envVarsFromConnector,
      Map<String, String> secretVars, Map<String, String> secretVarsFromConnector, String workspace) {
    if (node == null || node.isNull()) {
      return;
    }

    if (node.isObject()) {
      ObjectNode objectNode = (ObjectNode) node;

      JsonNode stepNode;
      if (objectNode.has("step")) {
        stepNode = objectNode.get("step");
        addIACMVariablesToSteps(
            stepNode, envVars, envVarsFromConnector, secretVars, secretVarsFromConnector, workspace);
      }

      for (JsonNode childNode : objectNode) {
        iterateWrapperSteps(childNode, envVars, envVarsFromConnector, secretVars, secretVarsFromConnector, workspace);
      }
    } else if (node.isArray()) {
      for (JsonNode childNode : node) {
        iterateWrapperSteps(childNode, envVars, envVarsFromConnector, secretVars, secretVarsFromConnector, workspace);
      }
    }
  }

  private ExecutionElementConfig addWorkspaceToIACMSteps(
      PlanCreationContext ctx, ExecutionElementConfig modifiedExecutionPlan, String workspace) {
    Map<String, String> envVars = iacmStepsUtils.getIACMEnvVariables(
        ctx.getOrgIdentifier(), ctx.getProjectIdentifier(), ctx.getAccountIdentifier(), workspace);
    Map<String, String> envVarsFromConnector = iacmStepsUtils.getIACMEnvVariablesFromConnector(
        ctx.getOrgIdentifier(), ctx.getProjectIdentifier(), ctx.getAccountIdentifier(), workspace);
    Map<String, String> secretVars = iacmStepsUtils.getIACMSecretVariables(
        ctx.getOrgIdentifier(), ctx.getProjectIdentifier(), ctx.getAccountIdentifier(), workspace);
    Map<String, String> secretVarsFromConnector = iacmStepsUtils.getIACMSecretVariablesFromConnector(
        ctx.getOrgIdentifier(), ctx.getProjectIdentifier(), ctx.getAccountIdentifier(), workspace);

    List<ExecutionWrapperConfig> modifiedSteps = new ArrayList<>();
    // Bare with me for a sec. The pipeline can have 3 types of steps
    // Normal old good step
    // Parallel step, which can have any number of the other steps including more Parallel steps
    // StepGroup steps, which can also have any number other of the other steps, also including more StepGroups steps
    // So, we want to add the IACM variables to all the steps (depending on the type of the step, we will add more or
    // less variables, but overall there is a subset of variables that we want to add for sure)
    // So in order to do that want we want to do is navigate from the root of the step all the way down to all the
    // step branches that the steps can have, in order to add to all of them the IACM variables, which
    // means...RECURSION!! With that in mind you can continue your path traveler.
    for (ExecutionWrapperConfig wrapperConfig : modifiedExecutionPlan.getSteps()) {
      if (wrapperConfig.getStepGroup() != null) {
        JsonNode stepGroup = wrapperConfig.getStepGroup();
        iterateWrapperSteps(stepGroup, envVars, envVarsFromConnector, secretVars, secretVarsFromConnector, workspace);
      } else if (wrapperConfig.getStep() != null) {
        // I spend some time with the recursion function and the entry point worked perfectly for stepGroup and Parallel
        // as they were both JsonNodes but for the step the problem is that the object was an ExecutionWrapperConfig
        // which a step inside I realised that it was easier to just convert the object into JsonNode and pass that to
        // the recursion function to do it's magic Normally I would not do that because a single step should not be that
        // controversial, and we should be able to just add the IACM variables directly. Enters in the room the
        // lite-engine step. This step contains _all_ the steps as a part of the step, which means that we need to dive
        // in into the step, retrieve the section that contains the steps and then, send that to the recursion function
        // to add all the env variables to all the other steps. Which is why I had to do this. Of course, after adding
        // the variables, I needed to cast back to ExecutionWrapperConfig and set it to wrapperConfig or we will be
        // losing all the changes
        ObjectMapper mapper = new ObjectMapper();
        JsonNode step = mapper.valueToTree(wrapperConfig);
        iterateWrapperSteps(step, envVars, envVarsFromConnector, secretVars, secretVarsFromConnector, workspace);
        wrapperConfig = mapper.convertValue(step, ExecutionWrapperConfig.class);
      } else if (wrapperConfig.getParallel() != null) {
        JsonNode parallelStep = wrapperConfig.getParallel();
        iterateWrapperSteps(
            parallelStep, envVars, envVarsFromConnector, secretVars, secretVarsFromConnector, workspace);
      }
      modifiedSteps.add(wrapperConfig);
    }
    return ExecutionElementConfig.builder()
        .uuid(modifiedExecutionPlan.getUuid())
        .rollbackSteps(modifiedExecutionPlan.getRollbackSteps())
        .steps(modifiedSteps)
        .build();
  }

  private void addIACMVariablesToSteps(JsonNode stepNode, Map<String, String> envVars,
      Map<String, String> envVarsFromConnector, Map<String, String> secretVars,
      Map<String, String> secretVarsFromConnector, String workspace) {
    String type = stepNode.get("type").asText();
    switch (type) {
      case IACMStepSpecTypeConstants.IACM_TERRAFORM_PLUGIN:
      case IACMStepSpecTypeConstants.IACM_APPROVAL: {
        ObjectNode spec = (ObjectNode) stepNode.get("spec");
        spec.put("workspace", workspace);
        Map<String, String> envVarsCopy = new HashMap<>(envVars);
        String command;
        if (Objects.equals(type, IACMStepSpecTypeConstants.IACM_APPROVAL)) {
          command = "approval";
        } else {
          command = spec.get("command").asText();
        }
        envVarsCopy.put("PLUGIN_COMMAND", command);
        ObjectMapper objectMapper = new ObjectMapper();
        spec.set("envVariables", objectMapper.valueToTree(envVarsCopy));
        spec.set("envVariablesFromConnector", objectMapper.valueToTree(envVarsFromConnector));
        spec.set("secretVariables", objectMapper.valueToTree(secretVars));
        spec.set("secretVariablesFromConnector", objectMapper.valueToTree(secretVarsFromConnector));
        break;
      }
      case IACMStepSpecTypeConstants.IACM_LITE_ENGINE:
        for (JsonNode jsonNode : stepNode.get("spec").get("executionElementConfig").get("steps")) {
          iterateWrapperSteps(jsonNode, envVars, envVarsFromConnector, secretVars, secretVarsFromConnector, workspace);
        }
        break;
      default:
        // For any other step, we want to put only the variables defined in the workspace. Skip this for the
        // liteTaskEngine and clone step
        ObjectNode spec = (ObjectNode) stepNode.get("spec");
        if (!Objects.equals(type, IACMStepSpecTypeConstants.IACM_LITE_ENGINE)) {
          if (!Objects.equals(type, IACMStepSpecTypeConstants.IACM_CLONE_CODEBASE)) {
            Map<String, String> copyMap = new HashMap<>();
            // Copy the envVariables from the step to the copyMap map before starting to add IACM env variables
            if (spec.has("envVariables")) {
              ObjectMapper objectMapper = new ObjectMapper();
              JsonNode envVarsNode = spec.get("envVariables");
              Map<String, String> map = objectMapper.convertValue(envVarsNode, Map.class);
              copyMap.putAll(map);
            }
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
              if (!entry.getKey().startsWith("PLUGIN_")) {
                copyMap.put(entry.getKey(), entry.getValue());
              }
            }
            spec.put("workspace", workspace);
            Map<String, String> pluginEnvVarsCopy = new HashMap<>(copyMap);
            ObjectMapper pluginObjectMapper = new ObjectMapper();
            spec.set("envVariables", pluginObjectMapper.valueToTree(pluginEnvVarsCopy));
          }
        }

        break;
    }
  }

  @Override
  public Set<String> getSupportedStageTypes() {
    return ImmutableSet.of(IACMStepSpecTypeConstants.IACM_STAGE);
  }

  @Override
  public StepType getStepType(IACMStageNode stageNode) {
    return IACMIntegrationStageStepPMS.STEP_TYPE;
  }

  @Override
  /*
   * This function creates the spec parameters for the Stage. The stage is treated as if it were another step, so this
   * function basically identifies the spec under the stage and returns it as IACMIntegrationStageStepParametersPMS
   * */
  public SpecParameters getSpecParameters(String childNodeId, PlanCreationContext ctx, IACMStageNode stageNode) {
    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));
    YamlField executionField = specField.getNode().getField(EXECUTION);
    YamlNode parentNode = executionField.getNode().getParentNode();
    String workspace = parentNode.getField("workspace").getNode().getCurrJsonNode().asText();
    CodeBase codeBase = getIACMCodebase(ctx, workspace);

    return IACMIntegrationStageStepParametersPMS.getStepParameters(
        ctx, getIntegrationStageNode(stageNode), codeBase, childNodeId);
  }

  @Override
  public Class<IACMStageNode> getFieldClass() {
    return IACMStageNode.class;
  }

  @Override
  /*
   * This method creates a plan to follow for the Parent node, which is the stage. If I get this right, because the
   * stage is treated as another step, this follows the same procedure where stages are defined in what order need to be
   * executed and then for each step a Plan for the child nodes (steps?) will be executed
   * */
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, IACMStageNode stageNode, List<String> childrenNodeIds) {
    stageNode.setIdentifier(StrategyUtils.getIdentifierWithExpression(ctx, stageNode.getIdentifier()));
    stageNode.setName(StrategyUtils.getIdentifierWithExpression(ctx, stageNode.getName()));

    StageElementParametersBuilder stageParameters =
        cIStagePlanCreationUtils.getStageParameters(getIntegrationStageNode(stageNode));
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

  /**
   This function is the one used to send back to the PMS SDK the modified Yaml
   */
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

  /**
   * Modifies the yaml to add the init and clone steps
   */
  private ExecutionElementConfig modifyYAMLWithImplicitSteps(PlanCreationContext ctx, ExecutionSource executionSource,
      YamlField executionYAMLField, IACMStageNode stageNode, CodeBase codeBase) {
    ExecutionElementConfig executionElementConfig;
    try {
      executionElementConfig = YamlUtils.read(executionYAMLField.getNode().toString(), ExecutionElementConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid yaml", e);
    }
    IntegrationStageNode integrationStageNode = getIntegrationStageNode(stageNode);
    return ciIntegrationStageModifier.modifyExecutionPlan(executionElementConfig, integrationStageNode, ctx, codeBase,
        IACMIntegrationStageStepParametersPMS.getInfrastructure(integrationStageNode, ctx), executionSource);
  }

  /**
   This is one of the functions that I think that we really need to understand. This function creates a PlanNode for a
   step and the step is the CISpecStep, but I don't understand what is this step doing. Is this the
   */
  private PlanNode getSpecPlanNode(YamlField specField, IACMIntegrationStageStepParametersPMS stepParameters) {
    return PlanNode.builder()
        .uuid(specField.getNode().getUuid())
        .identifier(YAMLFieldNameConstants.SPEC)
        .stepType(CISpecStep.STEP_TYPE) // TODO: What is this step doing?
        .name(YAMLFieldNameConstants.SPEC)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }

  /**
   This function seems to build the ExecutionSource object which contains information about how the Execution was
   triggered (Webhook, manual, custom). Because this is the CI world, it could be possible that the webhook is
   related with changes in the repository, so that should be something that we may want to investigate.
   If we want to disallow custom or webhook scenarios for some reason this would also be the place
   */
  private ExecutionSource buildExecutionSource(PlanCreationContext ctx, IACMStageNode stageNode) {
    PlanCreationContextValue planCreationContextValue = ctx.getGlobalContext().get("metadata");

    CodeBase codeBase = getIACMCodebase(ctx, stageNode.getIacmStageConfig().getWorkspace().getValue());

    if (codeBase == null) {
      //  code base is not mandatory in case git clone is false, Sending status won't be possible
      return null;
    }
    ExecutionTriggerInfo triggerInfo = planCreationContextValue.getMetadata().getTriggerInfo();
    TriggerPayload triggerPayload = planCreationContextValue.getTriggerPayload();

    return IntegrationStageUtils.buildExecutionSource(triggerInfo, triggerPayload, stageNode.getIdentifier(),
        codeBase.getBuild(), codeBase.getConnectorRef().getValue(), connectorUtils, ctx, codeBase);
  }

  /**
   Used for Webhooks
   TODO: Needs investigation
   */
  private BuildStatusUpdateParameter obtainBuildStatusUpdateParameter(
      PlanCreationContext ctx, IACMStageNode stageNode, ExecutionSource executionSource, String workspace) {
    CodeBase codeBase = getIACMCodebase(ctx, workspace);

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

  /**
   Used for Webhooks
   TODO: Needs investigation
   */
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

  /**
   *  This method will retrieve the properties/ci/codebase information from the yaml similar to:
   pipeline:
   properties:
   ci:
   codebase:
   NOTE: If we want to add information at this level, the way to do it will be similar to this method
   */
  private CodeBase getIACMCodebase(PlanCreationContext ctx, String workspaceId) {
    try {
      CodeBaseBuilder iacmCodeBase = CodeBase.builder();
      BuildBuilder buildObject = builder();

      Workspace workspace = serviceUtils.getIACMWorkspaceInfo(
          ctx.getOrgIdentifier(), ctx.getProjectIdentifier(), ctx.getAccountIdentifier(), workspaceId);

      // If the trigger type is WEBHOOK, we need to get the repository name from the webhook payload.
      // If the trigger is not a WEBHOOK, then we retrieve the repository from the Workspace
      if (ctx.getTriggerInfo().getTriggerType().name().equals("WEBHOOK")) {
        // It looks like the connector type in the workspace has to match with the connector type in the webhook,.
        // I could not find a way to get the connector type from the webhook, so I will use the connector type from the
        // workspace and assume that both are the same. This is required because if the connector is an account
        // connector, the repository name can only contain the name and not the full url.
        if (!Objects.equals(workspace.getRepository_connector(), "") && workspace.getRepository_connector() != null) {
          iacmCodeBase.repoName(
              ParameterField.<String>builder()
                  .value(ctx.getMetadata().getTriggerPayload().getParsedPayload().getPr().getRepo().getName())
                  .build());
        } else {
          iacmCodeBase.repoName(
              ParameterField.<String>builder()
                  .value(ctx.getMetadata().getTriggerPayload().getParsedPayload().getPr().getRepo().getClone())
                  .build());
        }
        // If getPr is not null the trigger type is a PR trigger, and we want to use the PRBuildSpec.
        // If getPush is not null, the trigger type is then a Push trigger, and we want to use the BranchBuildSpec as
        // PR does not makes sense.
        if (ctx.getTriggerPayload().getParsedPayload().getPr().hasPr()) {
          buildObject.type(BuildType.PR);
          buildObject.spec(PRBuildSpec.builder()
                               .number(ParameterField.<String>builder().value("<+trigger.prNumber>").build())
                               .build());
        } else if (ctx.getTriggerPayload().getParsedPayload().getPush().hasCommit()) {
          buildObject.type(BuildType.BRANCH);
          buildObject.spec(BranchBuildSpec.builder()
                               .branch(ParameterField.<String>builder().value("<+trigger.branch>").build())
                               .build());
        } else {
          // This should be triggered only if the trigger is a tag. There could be a chance that we hit this with
          // a trigger with comments but I was unable to test this so I will need to check if that has been implemented
          // or not
          buildObject.type(BuildType.TAG);
          buildObject.spec(TagBuildSpec.builder()
                               .tag(ParameterField.<String>builder().value("<+trigger.tag>").expression(true).build())
                               .build());
        }

      } else {
        // If the repository name is empty, it means that the connector is an account connector and the repo needs to be
        // defined
        if (!Objects.equals(workspace.getRepository(), "") && workspace.getRepository() != null) {
          iacmCodeBase.repoName(ParameterField.<String>builder().value(workspace.getRepository()).build());
        } else {
          iacmCodeBase.repoName(ParameterField.<String>builder().value(null).build());
        }
        if (!Objects.equals(workspace.getRepository_branch(), "") && workspace.getRepository_branch() != null) {
          buildObject.type(BuildType.BRANCH);
          buildObject.spec(BranchBuildSpec.builder()
                               .branch(ParameterField.<String>builder().value(workspace.getRepository_branch()).build())
                               .build());
        } else if (!Objects.equals(workspace.getRepository_commit(), "") && workspace.getRepository_commit() != null) {
          buildObject.type(BuildType.TAG);
          buildObject.spec(TagBuildSpec.builder()
                               .tag(ParameterField.<String>builder().value(workspace.getRepository_commit()).build())
                               .build());
        } else {
          throw new IACMStageExecutionException(
              "Unexpected connector information while writing the CodeBase block. There was not repository branch nor commit id defined in the workspace "
              + workspace);
        }
      }

      iacmCodeBase.connectorRef(ParameterField.<String>builder().value(workspace.getRepository_connector()).build());
      iacmCodeBase.depth(ParameterField.<Integer>builder().value(50).build());
      iacmCodeBase.prCloneStrategy(ParameterField.<PRCloneStrategy>builder().value(null).build());
      iacmCodeBase.sslVerify(ParameterField.<Boolean>builder().value(null).build());
      iacmCodeBase.uuid(generateUuid());

      // Now we need to build the Build type for the Codebase.
      // We support 2,

      return iacmCodeBase.build(ParameterField.<Build>builder().value(buildObject.build()).build()).build();

    } catch (Exception ex) {
      // Ignore exception because code base is not mandatory in case git clone is false
      log.warn("Failed to retrieve iacmCodeBase from pipeline");
      throw new IACMStageExecutionException("Unexpected error building the connector information from the workspace: "
          + workspaceId + " ." + ex.getMessage());
    }
  }
  /**
   This is the step that creates the integrationStageNode class from the stageNode yaml file. Important note is that
   we are using the IntegrationStageConfigImpl, which belongs to the CI module, we are NOT using the
   IACMIntegrationStageConfig. If we want to use the code in CI we need to do that, which is the reason of why we are
   injecting invisible steps to bypass this limitation
   */
  private IntegrationStageNode getIntegrationStageNode(IACMStageNode stageNode) {
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
  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V0);
  }

  private String fetchCodeBaseNodeUUID(CodeBase codeBase, String executionNodeUUid,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap) {
    String codeBaseNodeUUID = generateUuid();
    List<PlanNode> codeBasePlanNodeList =
        CodebasePlanCreator.buildCodebasePlanNodes(codeBaseNodeUUID, executionNodeUUid, kryoSerializer, codeBase, null);
    if (isNotEmpty(codeBasePlanNodeList)) {
      for (PlanNode planNode : codeBasePlanNodeList) {
        planCreationResponseMap.put(
            planNode.getUuid(), PlanCreationResponse.builder().node(planNode.getUuid(), planNode).build());
      }
      return codeBaseNodeUUID;
    }
    return null;
  }
}
