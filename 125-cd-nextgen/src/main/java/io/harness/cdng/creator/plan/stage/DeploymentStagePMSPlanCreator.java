package io.harness.cdng.creator.plan.stage;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.execution.CDExecutionPMSPlanCreator;
import io.harness.cdng.creator.plan.infrastructure.InfrastructurePmsPlanCreator;
import io.harness.cdng.creator.plan.service.ServicePMSPlanCreator;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.pipeline.beans.DeploymentStageStepParameters;
import io.harness.cdng.pipeline.steps.DeploymentStageStep;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.stages.GenericStagePlanCreator;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.GenericStepPMSPlanCreator;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.utils.CommonPlanCreatorUtils;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@OwnedBy(CDC)
public class DeploymentStagePMSPlanCreator extends GenericStagePlanCreator {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ServicePMSPlanCreator servicePMSPlanCreator;

  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton("Deployment");
  }

  @Override
  public StepType getStepType(StageElementConfig stageElementConfig) {
    return DeploymentStageStep.STEP_TYPE;
  }

  @Override
  public SpecParameters getSpecParameters(
      String childNodeId, PlanCreationContext ctx, StageElementConfig stageElementConfig) {
    return DeploymentStageStepParameters.getStepParameters(childNodeId);
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, StageElementConfig field) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // Validate Stage Failure strategy.
    validateFailureStrategy(field);

    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));

    // Adding service child
    YamlField serviceField = specField.getNode().getField(YamlTypes.SERVICE_CONFIG);
    if (serviceField == null) {
      throw new InvalidRequestException("ServiceConfig Section cannot be absent in a pipeline");
    }

    YamlField infraField = specField.getNode().getField(YamlTypes.PIPELINE_INFRASTRUCTURE);
    if (infraField == null) {
      throw new InvalidRequestException("Infrastructure section cannot be absent in a pipeline");
    }

    PipelineInfrastructure pipelineInfrastructure = ((DeploymentStageConfig) field.getStageType()).getInfrastructure();
    PipelineInfrastructure actualInfraConfig =
        InfrastructurePmsPlanCreator.getActualInfraConfig(pipelineInfrastructure, infraField);

    PlanCreationResponse servicePlanCreationResponse = servicePMSPlanCreator.createPlanForServiceNode(serviceField,
        ((DeploymentStageConfig) field.getStageType()).getServiceConfig(), kryoSerializer,
        InfrastructurePmsPlanCreator.getInfraSectionStepParams(actualInfraConfig, ""), ctx);
    planCreationResponseMap.put(servicePlanCreationResponse.getStartingNodeId(),
        PlanCreationResponse.builder().nodes(servicePlanCreationResponse.getNodes()).build());

    // Adding Spec node
    PlanNode specPlanNode =
        CommonPlanCreatorUtils.getSpecPlanNode(specField.getNode().getUuid(), serviceField.getNode().getUuid());
    planCreationResponseMap.put(
        specPlanNode.getUuid(), PlanCreationResponse.builder().node(specPlanNode.getUuid(), specPlanNode).build());

    // Adding infrastructure node
    PlanNode infraStepNode = InfrastructurePmsPlanCreator.getInfraStepPlanNode(pipelineInfrastructure, infraField);
    planCreationResponseMap.put(
        infraStepNode.getUuid(), PlanCreationResponse.builder().node(infraStepNode.getUuid(), infraStepNode).build());
    String infraSectionNodeChildId = infraStepNode.getUuid();

    if (InfrastructurePmsPlanCreator.isProvisionerConfigured(actualInfraConfig)) {
      planCreationResponseMap.putAll(InfrastructurePmsPlanCreator.createPlanForProvisioner(
          actualInfraConfig, infraField, infraStepNode.getUuid(), kryoSerializer));
      infraSectionNodeChildId = InfrastructurePmsPlanCreator.getProvisionerNodeId(infraField);
    }

    YamlField infrastructureDefField =
        Preconditions.checkNotNull(infraField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEF));
    PlanNode infraDefPlanNode =
        InfrastructurePmsPlanCreator.getInfraDefPlanNode(infrastructureDefField, infraSectionNodeChildId);
    planCreationResponseMap.put(infraDefPlanNode.getUuid(),
        PlanCreationResponse.builder().node(infraDefPlanNode.getUuid(), infraDefPlanNode).build());

    YamlNode infraNode = infraField.getNode();
    planCreationResponseMap.putAll(InfrastructurePmsPlanCreator.createPlanForInfraSection(
        infraNode, infraDefPlanNode.getUuid(), pipelineInfrastructure, kryoSerializer, infraField));

    // Add dependency for execution
    YamlField executionField = specField.getNode().getField(YAMLFieldNameConstants.EXECUTION);
    if (executionField == null) {
      throw new InvalidRequestException("Execution section cannot be absent in a pipeline");
    }
    PlanCreationResponse planForExecution = CDExecutionPMSPlanCreator.createPlanForExecution(executionField);
    planCreationResponseMap.put(executionField.getNode().getUuid(), planForExecution);

    return planCreationResponseMap;
  }

  private void validateFailureStrategy(StageElementConfig stageElementConfig) {
    // Failure strategy should be present.
    List<FailureStrategyConfig> stageFailureStrategies = stageElementConfig.getFailureStrategies();
    if (EmptyPredicate.isEmpty(stageFailureStrategies)) {
      throw new InvalidRequestException("There should be atleast one failure strategy configured at stage level.");
    }

    // checking stageFailureStrategies is having one strategy with error type as AllErrors and along with that no
    // error type is involved
    if (!GenericStepPMSPlanCreator.containsOnlyAllErrorsInSomeConfig(stageFailureStrategies)) {
      throw new InvalidRequestException(
          "There should be a Failure strategy that contains one error type as AllErrors, with no other error type along with it in that Failure Strategy.");
    }
  }
}
