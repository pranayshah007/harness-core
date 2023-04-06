/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.plan.creator.stage;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.yaml.YAMLFieldNameConstants.EXECUTION;
import static io.harness.yaml.extended.ci.codebase.Build.BuildBuilder;
import static io.harness.yaml.extended.ci.codebase.Build.builder;
import static io.harness.yaml.extended.ci.codebase.CodeBase.CodeBaseBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.entities.Stack;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.stages.IACMStageNode;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.IACMStepSpecTypeConstants;
import io.harness.beans.steps.nodes.iacm.IACMTerraformPlanStepNode;
import io.harness.beans.steps.stepinfo.IACMTerraformPlanInfo;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.integrationstage.CIIntegrationStageModifier;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.states.CISpecStep;
import io.harness.ci.states.IntegrationStageStepPMS;
import io.harness.ci.utils.CIStagePlanCreationUtils;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.IACMStageExecutionException;
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
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.Build.BuildBuilder;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.PRCloneStrategy;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IACM)
public class IACMStagePMSPlanCreator extends AbstractStagePlanCreator<IACMStageNode> {
  private static final String TERRAFORM_PLAN_ID = "IACMTerraformPlan";
  private static final String TERRAFORM_APPLY_ID = "IACMTerraformApply";
  private static final String TERRAFORM_DESTROY_ID = "IACMTerraformDestroy";
  private static final String TERRAFORM_PLAN_NAME = "Terraform Plan";
  private static final String TERRAFORM_APPLY_NAME = "Terraform Apply";
  private static final String TERRAFORM_DESTROY_NAME = "Terraform Destroy";
  @Inject private CIIntegrationStageModifier ciIntegrationStageModifier;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ConnectorUtils connectorUtils;

  @Inject private IACMServiceUtils serviceUtils;

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
    String stackId = parentNode.getField("stackID").getNode().getCurrJsonNode().asText();
    String workflow = parentNode.getField("workflow").getNode().getCurrJsonNode().asText();

    // Force the stage execution to clone the codebase
    stageNode.getIacmStageConfig().setCloneCodebase(ParameterField.<Boolean>builder().value(true).build());

    CodeBase codeBase = getIACMCodebase(ctx, stackId);
    ExecutionSource executionSource = buildExecutionSource(ctx, stageNode);

    // Because we are using a CI stage, the Stage is of type IntegrationStageConfig. From here we are only interested
    // on 3 elements, cloneCodebase, Infrastructure (to use the dlite delegates) and Execution. I think that if any of
    // the other values are present we should fail the execution

    ExecutionElementConfig modifiedExecutionPlan =
        modifyYAMLWithImplicitSteps(ctx, executionSource, executionField, stageNode, codeBase);

    ExecutionElementConfig cleanedExecutionPlan = CleanTemplateStep(modifiedExecutionPlan);

    ExecutionElementConfig modifiedIACMExecutionPlan = modifyIACMYamlSteps(cleanedExecutionPlan, workflow);

    ExecutionElementConfig modifiedExecutionPlanWithStackID = addStackIdToIACMSteps(modifiedIACMExecutionPlan, stackId);
    // Retrieve the Modified Plan execution where the InitialTask and Git Clone step have been injected. Then retrieve
    // the steps from the plan to the level of steps->spec->stageElementConfig->execution->steps. Here, we can inject
    // any step and that step will be available in the InitialTask step in the path:
    // stageElementConfig -> Execution -> Steps -> InjectedSteps
    putNewExecutionYAMLInResponseMap(
        executionField, planCreationResponseMap, modifiedExecutionPlanWithStackID, parentNode);

    BuildStatusUpdateParameter buildStatusUpdateParameter =
        obtainBuildStatusUpdateParameter(ctx, stageNode, executionSource, stackId);
    PlanNode specPlanNode = getSpecPlanNode(specField,
        IntegrationStageStepParametersPMS.getStepParameters(
            getIntegrationStageNode(stageNode), childNodeId, buildStatusUpdateParameter, ctx));
    planCreationResponseMap.put(
        specPlanNode.getUuid(), PlanCreationResponse.builder().node(specPlanNode.getUuid(), specPlanNode).build());

    log.info("Successfully created plan for security stage {}", stageNode.getIdentifier());
    return planCreationResponseMap;
  }

  private ExecutionElementConfig modifyIACMYamlSteps(ExecutionElementConfig modifiedExecutionPlan, String workflow) {
    List<String> operations = new ArrayList<>();
    if (Objects.equals(workflow, "provision")) {
      operations.add("TerraformPlan");
      operations.add("TerraformApply");
    } else if (Objects.equals(workflow, "teardown")) {
      operations.add("TerraformDestroy");
    } else {
      throw new IACMStageExecutionException("Unexpected workflow in the IACM stage");
    }

    for (String operation : operations) {
      modifiedExecutionPlan.getSteps().add(
          createStep(operation, Timeout.fromString("10m"))); // TODO: Hardcoded value for now
    }
    return modifiedExecutionPlan;
  }

  private ExecutionElementConfig CleanTemplateStep(ExecutionElementConfig modifiedExecutionPlan) {
    modifiedExecutionPlan.getSteps().removeIf(
        e -> e.getStep().get("type").asText().equals(IACMStepSpecTypeConstants.IACM_TEMPLATE));
    return modifiedExecutionPlan;
  }

  private ExecutionWrapperConfig createStep(String stepType, Timeout timeout) {
    switch (stepType) {
      case "TerraformPlan": {
        HashMap<String, String> env = new HashMap<>();
        env.put("command", "plan");
        IACMTerraformPlanInfo iacmTerraformPlanInfo =
            IACMTerraformPlanInfo.builder().env(ParameterField.createValueField(env)).name(TERRAFORM_PLAN_NAME).build();
        String uuid = generateUuid();
        try {
          String jsonString = JsonPipelineUtils.writeJsonString(IACMTerraformPlanStepNode.builder()
                                                                    .identifier(TERRAFORM_PLAN_ID)
                                                                    .name(TERRAFORM_PLAN_NAME)
                                                                    .uuid(uuid)
                                                                    .timeout(ParameterField.createValueField(timeout))
                                                                    .iacmTerraformPlanInfo(iacmTerraformPlanInfo)
                                                                    .build());
          JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
          return ExecutionWrapperConfig.builder().uuid(uuid).step(jsonNode).build();

        } catch (IOException ex) {
          throw new IACMStageExecutionException("Faied to create IACM Terraform plan step", ex);
        }
      }
      case "TerraformApply": {
        HashMap<String, String> env = new HashMap<>();
        env.put("command", "apply");
        IACMTerraformPlanInfo iacmTerraformPlanInfo = IACMTerraformPlanInfo.builder()
                                                          .env(ParameterField.createValueField(env))
                                                          .name(TERRAFORM_APPLY_NAME)
                                                          .build();
        String uuid = generateUuid();
        try {
          String jsonString = JsonPipelineUtils.writeJsonString(IACMTerraformPlanStepNode.builder()
                                                                    .identifier(TERRAFORM_APPLY_ID)
                                                                    .name(TERRAFORM_APPLY_NAME)
                                                                    .uuid(uuid)
                                                                    .timeout(ParameterField.createValueField(timeout))
                                                                    .iacmTerraformPlanInfo(iacmTerraformPlanInfo)
                                                                    .build());
          JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
          return ExecutionWrapperConfig.builder().uuid(uuid).step(jsonNode).build();

        } catch (IOException ex) {
          throw new IACMStageExecutionException("Faied to create IACM Terraform apply step", ex);
        }
      }
      case "TerraformDestroy": {
        HashMap<String, String> env = new HashMap<>();
        env.put("command", "destroy");
        IACMTerraformPlanInfo iacmTerraformPlanInfo = IACMTerraformPlanInfo.builder()
                                                          .env(ParameterField.createValueField(env))
                                                          .name(TERRAFORM_DESTROY_NAME)
                                                          .build();
        String uuid = generateUuid();
        try {
          String jsonString = JsonPipelineUtils.writeJsonString(IACMTerraformPlanStepNode.builder()
                                                                    .identifier(TERRAFORM_DESTROY_ID)
                                                                    .name(TERRAFORM_DESTROY_NAME)
                                                                    .uuid(uuid)
                                                                    .timeout(ParameterField.createValueField(timeout))
                                                                    .iacmTerraformPlanInfo(iacmTerraformPlanInfo)
                                                                    .build());
          JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
          return ExecutionWrapperConfig.builder().uuid(uuid).step(jsonNode).build();

        } catch (IOException ex) {
          throw new IACMStageExecutionException("Faied to create IACM Terraform destroy step", ex);
        }
      }
      default:
        throw new IACMStageExecutionException("IACM step not recognized");
    }
  }

  private ExecutionElementConfig addStackIdToIACMSteps(ExecutionElementConfig modifiedExecutionPlan, String stackID) {
    List<ExecutionWrapperConfig> modifiedSteps = new ArrayList<>();
    for (ExecutionWrapperConfig wrapperConfig : modifiedExecutionPlan.getSteps()) {
      switch (wrapperConfig.getStep().get("type").asText()) {
        case IACMStepSpecTypeConstants.IACM_TERRAFORM_PLAN:
          ((ObjectNode) wrapperConfig.getStep().get("spec")).put("stackID", stackID);
          break;
        default:
          break;
      }
      modifiedSteps.add(wrapperConfig);
    }
    return ExecutionElementConfig.builder()
        .uuid(modifiedExecutionPlan.getUuid())
        .rollbackSteps(modifiedExecutionPlan.getRollbackSteps())
        .steps(modifiedSteps)
        .build();
  }

  @Override
  public Set<String> getSupportedStageTypes() {
    return ImmutableSet.of(IACMStepSpecTypeConstants.IACM_STAGE);
  }

  @Override
  public StepType getStepType(IACMStageNode stageNode) {
    return IntegrationStageStepPMS.STEP_TYPE;
  }

  @Override
  /*
   * This function creates the spec parameters for the Stage. The stage is treated as if it were another step, so this
   * function basically identifies the spec under the stage and returns it as IntegrationStageStepParametersPMS
   * */
  public SpecParameters getSpecParameters(String childNodeId, PlanCreationContext ctx, IACMStageNode stageNode) {
    ExecutionSource executionSource = buildExecutionSource(ctx, stageNode);
    BuildStatusUpdateParameter buildStatusUpdateParameter = obtainBuildStatusUpdateParameter(
        ctx, stageNode, executionSource, stageNode.getIacmStageConfig().getStackID().getValue());
    return IntegrationStageStepParametersPMS.getStepParameters(
        getIntegrationStageNode(stageNode), childNodeId, buildStatusUpdateParameter, ctx);
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
        CIStagePlanCreationUtils.getStageParameters(getIntegrationStageNode(stageNode));
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
  I don't know if we need this function or not. It seems to modify the yaml file to add implicits steps but I don't know
  if we want or need this.
  TODO: Marked for investigation of its utility
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
        IntegrationStageStepParametersPMS.getInfrastructure(integrationStageNode, ctx), executionSource);
  }

  /**
  This is one of the functions that I think that we really need to understand. This function creates a PlanNode for a
  step and the step is the CISpecStep, but I don't understand what is this step doing. Is this the
   */
  private PlanNode getSpecPlanNode(YamlField specField, IntegrationStageStepParametersPMS stepParameters) {
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

    CodeBase codeBase = getIACMCodebase(ctx, stageNode.getIacmStageConfig().getStackID().getValue());

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
      PlanCreationContext ctx, IACMStageNode stageNode, ExecutionSource executionSource, String stackId) {
    CodeBase codeBase = getIACMCodebase(ctx, stackId);

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
  private CodeBase getIACMCodebase(PlanCreationContext ctx, String stackId) {
    try {
      CodeBaseBuilder iacmCodeBase = CodeBase.builder();
      Stack stack = serviceUtils.getIACMStackInfo(
          ctx.getOrgIdentifier(), ctx.getProjectIdentifier(), ctx.getAccountIdentifier(), stackId);
      // If the repository name is empty, it means that the connector is an account connector and the repo needs to be
      // defined
      if (!Objects.equals(stack.getRepository(), "") && stack.getRepository() != null) {
        iacmCodeBase.repoName(ParameterField.<String>builder().value(stack.getRepository()).build());
      } else {
        iacmCodeBase.repoName(ParameterField.<String>builder().value(null).build());
      }

      iacmCodeBase.connectorRef(ParameterField.<String>builder().value(stack.getRepository_connector()).build());
      iacmCodeBase.depth(ParameterField.<Integer>builder().value(50).build());
      iacmCodeBase.prCloneStrategy(ParameterField.<PRCloneStrategy>builder().value(null).build());
      iacmCodeBase.sslVerify(ParameterField.<Boolean>builder().value(null).build());
      iacmCodeBase.uuid(generateUuid());

      // Now we need to build the Build type for the Codebase.
      // We support 2,

      BuildBuilder buildObject = builder();
      if (!Objects.equals(stack.getRepository_branch(), "") && stack.getRepository_branch() != null) {
        buildObject.type(BuildType.BRANCH);
        buildObject.spec(BranchBuildSpec.builder()
                             .branch(ParameterField.<String>builder().value(stack.getRepository_branch()).build())
                             .build());
      } else if (!Objects.equals(stack.getRepository_commit(), "") && stack.getRepository_commit() != null) {
        buildObject.type(BuildType.TAG);
        buildObject.spec(TagBuildSpec.builder()
                             .tag(ParameterField.<String>builder().value(stack.getRepository_commit()).build())
                             .build());
      } else {
        throw new IACMStageExecutionException(
            "Unexpected connector information while writing the CodeBase block. There was not repository branch nor commit id defined in the stack "
            + stackId);
      }

      return iacmCodeBase.build(ParameterField.<Build>builder().value(buildObject.build()).build()).build();

    } catch (Exception ex) {
      // Ignore exception because code base is not mandatory in case git clone is false
      log.warn("Failed to retrieve iacmCodeBase from pipeline");
      throw new IACMStageExecutionException(
          "Unexpected error building the connector information from the stack: " + stackId + " ." + ex.getMessage());
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
    return Set.of(PipelineVersion.V0);
  }
}
