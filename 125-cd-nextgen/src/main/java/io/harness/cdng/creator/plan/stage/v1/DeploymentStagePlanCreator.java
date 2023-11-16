/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage.v1;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.creator.plan.envGroup.EnvGroupPlanCreatorHelper;
import io.harness.cdng.creator.plan.infrastructure.InfrastructurePmsPlanCreator;
import io.harness.cdng.creator.plan.service.ServiceAllInOnePlanCreatorUtils;
import io.harness.cdng.creator.plan.stage.DeploymentStagePlanCreationInfo;
import io.harness.cdng.creator.plan.stage.DeploymentStageType;
import io.harness.cdng.creator.plan.stage.MultiDeploymentMetadata;
import io.harness.cdng.creator.plan.stage.MultiDeploymentStepPlanCreator;
import io.harness.cdng.creator.plan.stage.SingleServiceEnvDeploymentStageDetailsInfo;
import io.harness.cdng.creator.plan.stage.StagePlanCreatorHelper;
import io.harness.cdng.creator.plan.stage.service.DeploymentStagePlanCreationInfoService;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterUtils;
import io.harness.cdng.environment.helper.EnvironmentPlanCreatorHelper;
import io.harness.cdng.environment.helper.EnvironmentsPlanCreatorHelper;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsPlanCreatorConfig;
import io.harness.cdng.environment.yaml.ServiceOverrideInputsYaml;
import io.harness.cdng.pipeline.beans.DeploymentStageStepParametersV1;
import io.harness.cdng.pipeline.beans.MultiDeploymentStepParameters;
import io.harness.cdng.pipeline.steps.MultiDeploymentSpawnerUtils;
import io.harness.cdng.pipeline.steps.v1.DeploymentStageStepV1;
import io.harness.cdng.service.NGServiceEntityHelper;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceUseFromStageV2;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesMetadata;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.ngexception.NGFreezeException;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.helpers.FreezeRBACHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.v1.StageElementParametersV1.StageElementParametersV1Builder;
import io.harness.plancreator.steps.v1.FailureStrategiesUtilsV1;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.plancreator.strategy.StrategyUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.serializer.KryoSerializer;
import io.harness.strategy.StrategyValidationUtils;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PlanCreatorUtilsCommon;
import io.harness.when.utils.v1.RunInfoUtilsV1;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class DeploymentStagePlanCreator extends ChildrenPlanCreator<YamlField> {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private EnvironmentService environmentService;
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private InfrastructureEntityService infrastructure;
  @Inject private ServiceOverrideService serviceOverrideService;
  @Inject private EnvGroupPlanCreatorHelper envGroupPlanCreatorHelper;
  @Inject private EnvironmentsPlanCreatorHelper environmentsPlanCreatorHelper;
  @Inject private FreezeEvaluateService freezeEvaluateService;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private StagePlanCreatorHelper stagePlanCreatorHelper;
  @Inject private NGServiceEntityHelper serviceEntityHelper;
  @Inject private DeploymentStagePlanCreationInfoService deploymentStagePlanCreationInfoService;
  @Inject @Named("deployment-stage-plan-creation-info-v1-executor") private ExecutorService executorService;

  public SpecParameters getSpecParameters(
      String childNodeId, PlanCreationContext ctx, DeploymentStageNodeV1 stageNode) {
    return DeploymentStageStepParametersV1.getStepParameters(childNodeId);
  }

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(YAMLFieldNameConstants.DEPLOYMENT_STAGE_V1));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField field) {
    DeploymentStageNodeV1 deploymentStageNode;
    try {
      deploymentStageNode = YamlUtils.read(field.getNode().toString(), DeploymentStageNodeV1.class);
    } catch (Exception e) {
      throw new InvalidYamlException(
          "Unable to parse deployment stage yaml. Please ensure that it is in correct format", e);
    }

    failIfProjectIsFrozen(ctx);
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    validateFailureStrategy(deploymentStageNode);
    Map<String, ByteString> metadataMap = new HashMap<>();
    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));

    YamlField stepsField = specField.getNode().getField(YAMLFieldNameConstants.STEPS);
    if (stepsField == null) {
      throw new InvalidRequestException("Steps section is required in deployment stage");
    }

    final boolean isProjectScopedResourceConstraintQueue =
        stagePlanCreatorHelper.isProjectScopedResourceConstraintQueueByFFOrSetting(ctx);
    if (deploymentStageNode.getSpec().getGitOpsEnabled()) {
      // GitOps flow doesn't fork on environments, so handling it in this function.
      return buildPlanCreationResponse(ctx, planCreationResponseMap, deploymentStageNode, specField, stepsField, field);
    }

    List<AdviserObtainment> adviserObtainments =
        stagePlanCreatorHelper.addResourceConstraintDependencyWithWhenConditionForV1Schema(
            planCreationResponseMap, specField, ctx, isProjectScopedResourceConstraintQueue);

    String infraNodeId =
        addInfrastructureNode(planCreationResponseMap, deploymentStageNode, adviserObtainments, specField);
    Optional<String> provisionerIdOptional =
        addProvisionerNodeIfNeeded(specField, planCreationResponseMap, deploymentStageNode, infraNodeId);
    String serviceNextNodeId = provisionerIdOptional.orElse(infraNodeId);
    String serviceNodeId =
        addServiceNode(ctx, specField, planCreationResponseMap, deploymentStageNode, serviceNextNodeId);
    saveSingleServiceEnvDeploymentStagePlanCreationSummary(
        planCreationResponseMap.get(serviceNodeId), ctx, deploymentStageNode);
    addSpecNode(planCreationResponseMap, specField, serviceNodeId);

    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    dependenciesNodeMap.put(specField.getNode().getUuid(), specField);

    Dependency strategyDependency = getDependencyForStrategy(dependenciesNodeMap, field, ctx);

    planCreationResponseMap.put(specField.getNode().getUuid(),
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                              .toBuilder()
                              .putDependencyMetadata(field.getUuid(), strategyDependency)
                              .putDependencyMetadata(specField.getNode().getUuid(), getDependencyForSteps(field))
                              .build())
            .build());

    addMultiDeploymentDependency(planCreationResponseMap, deploymentStageNode, ctx, specField);

    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, deploymentStageNode.getUuid(),
        deploymentStageNode.getName(), deploymentStageNode.getId(), planCreationResponseMap, metadataMap,
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false), false);

    return planCreationResponseMap;
  }

  Dependency getDependencyForStrategy(
      Map<String, YamlField> dependenciesNodeMap, YamlField field, PlanCreationContext ctx) {
    Map<String, HarnessValue> dependencyMetadata = StrategyUtilsV1.getStrategyFieldDependencyMetadataIfPresent(
        kryoSerializer, ctx, field.getUuid(), dependenciesNodeMap, getBuild(ctx.getDependency()));
    return Dependency.newBuilder()
        .setNodeMetadata(HarnessStruct.newBuilder().putAllData(dependencyMetadata).build())
        .build();
  }

  Dependency getDependencyForSteps(YamlField field) {
    List<FailureConfigV1> stageFailureStrategies = PlanCreatorUtilsV1.getFailureStrategies(field.getNode());
    if (stageFailureStrategies != null) {
      return Dependency.newBuilder()
          .setParentInfo(
              HarnessStruct.newBuilder()
                  .putData(PlanCreatorConstants.STAGE_FAILURE_STRATEGIES,
                      HarnessValue.newBuilder()
                          .setBytesValue(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(stageFailureStrategies)))
                          .build())
                  .build())
          .build();
    }
    return Dependency.newBuilder().setNodeMetadata(HarnessStruct.newBuilder().build()).build();
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    DeploymentStageNodeV1 stageNode;
    try {
      stageNode = YamlUtils.read(config.getNode().toString(), DeploymentStageNodeV1.class);
    } catch (Exception e) {
      throw new InvalidYamlException(
          "Unable to parse deployment stage yaml. Please ensure that it is in correct format", e);
    }

    if (stageNode.getStrategy() != null && MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(stageNode)) {
      throw new InvalidRequestException(
          "Looping Strategy and Multi Service/Environment configurations are not supported together in a single stage. Please use any one of these");
    }

    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));

    //    DeploymentStageStepParametersV1 stepParameters = DeploymentStageStepParametersV1.builder().build();
    DeploymentStageStepParametersV1 stepParameters =
        (DeploymentStageStepParametersV1) getSpecParameters(specField.getNode().getUuid(), ctx, stageNode);

    StageElementParametersV1Builder stageParameters = StepParametersUtils.getStageParameters(stageNode);
    stageParameters.type(YAMLFieldNameConstants.DEPLOYMENT_STAGE_V1);
    stageParameters.spec(stepParameters);
    String name = stageNode.getName();
    stageParameters.spec(stepParameters);

    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (!MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(stageNode)) {
      adviserObtainments =
          StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, true, ctx.getDependency());
    }

    // We need to swap the ids if strategy is present
    PlanNodeBuilder planNodeBuilder =
        PlanNode.builder()
            .uuid(StrategyUtilsV1.getSwappedPlanNodeId(ctx, stageNode.getUuid()))
            .name(StrategyUtilsV1.getIdentifierWithExpression(ctx, name))
            .identifier(StrategyUtilsV1.getIdentifierWithExpression(ctx, stageNode.getId()))
            .stepType(DeploymentStageStepV1.STEP_TYPE)
            .group(StepOutcomeGroup.STAGE.name())
            .skipUnresolvedExpressionsCheck(true)
            .whenCondition(RunInfoUtilsV1.getStageWhenCondition(stageNode.getWhen()))
            .stepParameters(stageParameters.build())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .skipExpressionChain(false)
            .timeoutObtainment(PlanCreatorUtilsV1.getTimeoutObtainmentForStage(stageNode))
            .adviserObtainments(adviserObtainments);

    // If strategy present then don't add advisers. Strategy node will take care of running the stage nodes.
    if (ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField("strategy") == null) {
      planNodeBuilder.adviserObtainments(getBuild(ctx.getDependency()));
    }

    if (!EmptyPredicate.isEmpty(ctx.getExecutionInputTemplate())) {
      planNodeBuilder.executionInputTemplate(ctx.getExecutionInputTemplate());
    }
    return planNodeBuilder.build();
  }

  private List<AdviserObtainment> getBuild(Dependency dependency) {
    return PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, dependency);
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }

  public String getIdentifierWithExpression(PlanCreationContext ctx, DeploymentStageNodeV1 node, String identifier) {
    if (MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(node)) {
      return identifier + StrategyValidationUtils.STRATEGY_IDENTIFIER_POSTFIX;
    }
    return StrategyUtils.getIdentifierWithExpression(ctx, identifier);
  }

  private LinkedHashMap<String, PlanCreationResponse> buildPlanCreationResponse(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNodeV1 stageNode,
      YamlField specField, YamlField stepsField, YamlField field) {
    String infraNodeId = addGitOpsClustersNode(ctx, planCreationResponseMap, stageNode, stepsField);
    Optional<String> provisionerIdOptional =
        addProvisionerNodeIfNeeded(specField, planCreationResponseMap, stageNode, infraNodeId);
    String serviceNextNodeId = provisionerIdOptional.orElse(infraNodeId);
    String serviceNodeId =
        addServiceNodeForGitOps(ctx, specField, planCreationResponseMap, stageNode, serviceNextNodeId);
    addSpecNode(planCreationResponseMap, specField, serviceNodeId);

    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    dependenciesNodeMap.put(specField.getNode().getUuid(), specField);

    Dependency strategyDependency = getDependencyForStrategy(dependenciesNodeMap, field, ctx);

    planCreationResponseMap.put(specField.getNode().getUuid(),
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                              .toBuilder()
                              .putDependencyMetadata(field.getUuid(), strategyDependency)
                              .putDependencyMetadata(specField.getNode().getUuid(), getDependencyForSteps(field))
                              .build())
            .build());

    addMultiDeploymentDependencyForGitOps(planCreationResponseMap, stageNode, ctx, specField);

    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, stageNode.getUuid(), stageNode.getName(),
        stageNode.getId(), planCreationResponseMap, new HashMap<>(),
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false), false);

    return planCreationResponseMap;
  }

  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, DeploymentStageNodeV1 config) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField yamlField = context.getCurrentField();
    if (MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(config)) {
      YamlField siblingField = yamlField.getNode().nextSiblingFromParentArray(
          yamlField.getName(), Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.PARALLEL));
      EdgeLayoutList edgeLayoutList;
      String planNodeId = MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(config);
      String pipelineRollbackStageId = StrategyUtils.getPipelineRollbackStageId(context.getCurrentField());
      if (siblingField == null || Objects.equals(siblingField.getUuid(), pipelineRollbackStageId)) {
        edgeLayoutList = EdgeLayoutList.newBuilder().addCurrentNodeChildren(planNodeId).build();
      } else {
        edgeLayoutList = EdgeLayoutList.newBuilder()
                             .addNextIds(siblingField.getNode().getUuid())
                             .addCurrentNodeChildren(planNodeId)
                             .build();
      }
      stageYamlFieldMap.put(yamlField.getNode().getUuid(),
          GraphLayoutNode.newBuilder()
              .setNodeUUID(yamlField.getNode().getUuid())
              .setNodeType(StrategyType.MATRIX.name())
              .setName(yamlField.getNode().getName())
              .setNodeGroup(StepOutcomeGroup.STRATEGY.name())
              .setNodeIdentifier(yamlField.getNode().getIdentifier())
              .setEdgeLayoutList(edgeLayoutList)
              .build());
      stageYamlFieldMap.put(planNodeId,
          GraphLayoutNode.newBuilder()
              .setNodeUUID(planNodeId)
              .setNodeType(yamlField.getNode().getType())
              .setName(config.getName())
              .setNodeGroup(StepOutcomeGroup.STAGE.name())
              .setNodeIdentifier(config.getId())
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().build())
              .build());
      return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
    }
    YamlField stageYamlField = context.getCurrentField();
    if (StrategyUtils.isWrappedUnderStrategy(context.getCurrentField())) {
      stageYamlFieldMap = StrategyUtils.modifyStageLayoutNodeGraph(stageYamlField);
    }
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }

  private void addMultiDeploymentDependency(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      DeploymentStageNodeV1 stageNode, PlanCreationContext ctx, YamlField specField) {
    DeploymentStageConfigV1 stageConfig = stageNode.getSpec();
    ServicesYaml finalServicesYaml = useFromStage(stageConfig.getServices())
        ? useServicesYamlFromStage(stageConfig, specField)
        : stageConfig.getServices();
    stageConfig.setServices(finalServicesYaml);

    MultiDeploymentSpawnerUtils.validateMultiServiceInfra(stageConfig);
    if (stageConfig.getServices() == null && stageConfig.getEnvironments() == null
        && stageConfig.getEnvironmentGroup() == null) {
      return;
    }

    String subType;

    // If filters are present
    if (EnvironmentInfraFilterUtils.areFiltersPresent(stageNode.getSpec().getEnvironments())) {
      subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_ENV_DEPLOYMENT;
    } else {
      if (stageConfig.getEnvironments() == null) {
        subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_DEPLOYMENT;
      } else if (stageConfig.getServices() == null) {
        subType = MultiDeploymentSpawnerUtils.MULTI_ENV_DEPLOYMENT;
      } else {
        subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_ENV_DEPLOYMENT;
      }
    }
    List<ServiceOverrideInputsYaml> servicesOverrides = null;
    if (stageConfig.getEnvironment() != null
        && EmptyPredicate.isNotEmpty(stageConfig.getEnvironment().getServicesOverrides())) {
      servicesOverrides = stageConfig.getEnvironment().getServicesOverrides();
    }

    MultiDeploymentStepParameters stepParameters =
        MultiDeploymentStepParameters.builder()
            .strategyType(StrategyType.MATRIX)
            .childNodeId(MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(stageNode))
            .environments(stageConfig.getEnvironments())
            .environmentGroup(stageConfig.getEnvironmentGroup())
            .services(stageConfig.getServices())
            .serviceYamlV2(getServiceYaml(specField, stageConfig))
            .subType(subType)
            .servicesOverrides(servicesOverrides)
            .build();

    buildMultiDeploymentMetadata(planCreationResponseMap, stageNode, ctx, stepParameters);
  }

  private ServicesMetadata getServicesMetadataOfCurrentStage(DeploymentStageConfigV1 stageConfig) {
    if (stageConfig.getServices() != null && stageConfig.getServices().getServicesMetadata() != null
        && ParameterField.isNotNull(stageConfig.getServices().getServicesMetadata().getParallel())) {
      return stageConfig.getServices().getServicesMetadata();
    }

    return ServicesMetadata.builder().parallel(ParameterField.createValueField(Boolean.TRUE)).build();
  }

  @VisibleForTesting
  ServicesYaml useServicesYamlFromStage(DeploymentStageConfigV1 currentStageConfig, YamlField specField) {
    ServiceUseFromStageV2 useFromStage = currentStageConfig.getServices().getUseFromStage();
    final YamlField serviceField = specField.getNode().getField(YamlTypes.SERVICE_ENTITIES);
    String stage = useFromStage.getStage();
    if (isBlank(stage)) {
      throw new InvalidArgumentsException("Stage identifier is empty in useFromStage");
    }

    try {
      YamlField propagatedFromStageConfig = PlanCreatorUtils.getStageConfig(serviceField, stage);
      if (propagatedFromStageConfig == null) {
        throw new InvalidArgumentsException(
            "Stage with identifier [" + stage + "] given for service propagation does not exist.");
      }

      DeploymentStageNodeV1 stageElementConfigPropagatedFrom =
          YamlUtils.read(propagatedFromStageConfig.getNode().toString(), DeploymentStageNodeV1.class);
      DeploymentStageConfigV1 deploymentStagePropagatedFrom = stageElementConfigPropagatedFrom.getSpec();
      if (deploymentStagePropagatedFrom != null) {
        if (deploymentStagePropagatedFrom.getServices() != null
            && useFromStage(deploymentStagePropagatedFrom.getServices())) {
          throw new InvalidArgumentsException("Invalid identifier [" + stage
              + "] given in useFromStage. Cannot reference a stage which also has useFromStage parameter");
        }

        if (deploymentStagePropagatedFrom.getServices() == null) {
          throw new InvalidRequestException(String.format(
              "Could not find multi service configuration in stage [%s], hence not possible to propagate service from that stage",
              stage));
        }

        ServicesYaml propagatedServices = deploymentStagePropagatedFrom.getServices();
        ServicesYaml currentStageServices = ServicesYaml.builder()
                                                .values(propagatedServices.getValues())
                                                .servicesMetadata(getServicesMetadataOfCurrentStage(currentStageConfig))
                                                .build();

        String svcYaml = YamlUtils.writeYamlString(currentStageServices);
        String injectUuidSvc = YamlUtils.injectUuid(svcYaml);
        return YamlUtils.read(injectUuidSvc, ServicesYaml.class);
      } else {
        throw new InvalidArgumentsException("Stage identifier given in useFromStage doesn't exist");
      }
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot parse stage: " + stage);
    }
  }

  private boolean useFromStage(ServicesYaml services) {
    return services != null && services.getUseFromStage() != null && isNotBlank(services.getUseFromStage().getStage());
  }

  private ServiceYamlV2 getServiceYaml(YamlField specField, DeploymentStageConfigV1 stageConfig) {
    if (stageConfig.getService() != null && ServiceAllInOnePlanCreatorUtils.useFromStage(stageConfig.getService())) {
      return ServiceAllInOnePlanCreatorUtils.useServiceYamlFromStage(
          stageConfig.getService().getUseFromStage(), specField);
    }

    return stageConfig.getService();
  }

  /**
   * Method Handles creation of the multi-service deployments for GitOps Deployments.
   * Note: GitOps Flow doesn't fork stages for deploying to multiple environments or environmentgroup
   */
  private void addMultiDeploymentDependencyForGitOps(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNodeV1 stageNode,
      PlanCreationContext ctx, YamlField specField) {
    DeploymentStageConfigV1 stageConfig = stageNode.getSpec();
    if (stageConfig.getServices() == null && stageConfig.getEnvironments() == null
        && stageConfig.getEnvironmentGroup() == null) {
      return;
    }

    final ServicesYaml finalServicesYaml = useFromStage(stageConfig.getServices())
        ? useServicesYamlFromStage(stageConfig, specField)
        : stageConfig.getServices();
    stageConfig.setServices(finalServicesYaml);

    String subType;
    if (stageConfig.getServices() != null) {
      subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_DEPLOYMENT;

      // Environment and EnvironmentGroup is not set for GitOps since we do not want to fork for gitops.
      // The GitOps cluster step takes care of deploying to multi infra
      MultiDeploymentStepParameters stepParameters =
          MultiDeploymentStepParameters.builder()
              .strategyType(StrategyType.MATRIX)
              .childNodeId(MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(stageNode))
              .services(stageConfig.getServices())
              .subType(subType)
              .build();

      buildMultiDeploymentMetadata(planCreationResponseMap, stageNode, ctx, stepParameters);
    }
  }

  private void buildMultiDeploymentMetadata(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      DeploymentStageNodeV1 stageNode, PlanCreationContext ctx, MultiDeploymentStepParameters stepParameters) {
    MultiDeploymentMetadata metadata =
        MultiDeploymentMetadata.builder()
            .multiDeploymentNodeId(ctx.getCurrentField().getNode().getUuid())
            .multiDeploymentStepParameters(stepParameters)
            .strategyNodeIdentifier(stageNode.getId())
            .strategyNodeName(stageNode.getName())
            .adviserObtainments(StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false))
            .build();

    PlanNode node = MultiDeploymentStepPlanCreator.createPlan(metadata);
    planCreationResponseMap.put(UUIDGenerator.generateUuid(), PlanCreationResponse.builder().planNode(node).build());
  }

  private String addServiceNode(PlanCreationContext ctx, YamlField specField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNodeV1 stageNode,
      String nextNodeId) {
    // Adding service child by resolving the serviceField
    ServiceYamlV2 service;
    if (stageNode.getSpec().getServices() != null) {
      service = MultiDeploymentSpawnerUtils.getServiceYamlV2Node();
    } else {
      service = stageNode.getSpec().getService();
    }

    ParameterField<String> envGroupRef = ParameterField.ofNull();
    EnvironmentYamlV2 environment;
    if (stageNode.getSpec().getEnvironmentGroup() != null) {
      environment = MultiDeploymentSpawnerUtils.getEnvironmentYamlV2Node();
      envGroupRef = stageNode.getSpec().getEnvironmentGroup().getEnvGroupRef();
    } else if (stageNode.getSpec().getEnvironments() != null) {
      environment = MultiDeploymentSpawnerUtils.getEnvironmentYamlV2Node();
    } else {
      environment = stageNode.getSpec().getEnvironment();
    }
    String serviceNodeId = service.getUuid();
    ServiceDefinitionType serviceType = serviceEntityHelper.getServiceDefinitionTypeFromService(ctx, service);
    planCreationResponseMap.putAll(ServiceAllInOnePlanCreatorUtils.addServiceNode(
        specField, kryoSerializer, service, environment, serviceNodeId, nextNodeId, serviceType, envGroupRef, ctx));
    return serviceNodeId;
  }

  private String addServiceNodeForGitOps(PlanCreationContext ctx, YamlField specField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNodeV1 stageNode,
      String nextNodeId) {
    // Adding service child by resolving the serviceField
    ServiceYamlV2 service;
    if (stageNode.getSpec().getServices() != null) {
      service = MultiDeploymentSpawnerUtils.getServiceYamlV2Node();
    } else {
      service = stageNode.getSpec().getService();
    }

    EnvironmentGroupYaml environmentGroupYaml = stageNode.getSpec().getEnvironmentGroup();
    String serviceNodeId = service.getUuid();
    ServiceDefinitionType serviceType = serviceEntityHelper.getServiceDefinitionTypeFromService(ctx, service);

    if (stageNode.getSpec().getEnvironmentGroup() != null) {
      planCreationResponseMap.putAll(ServiceAllInOnePlanCreatorUtils.addServiceNodeForGitOpsEnvGroup(
          specField, kryoSerializer, service, environmentGroupYaml, serviceNodeId, nextNodeId, serviceType, ctx));
    } else if (stageNode.getSpec().getEnvironments() != null) {
      planCreationResponseMap.putAll(ServiceAllInOnePlanCreatorUtils.addServiceNodeForGitOpsEnvironments(specField,
          kryoSerializer, service, stageNode.getSpec().getEnvironments(), serviceNodeId, nextNodeId, serviceType, ctx));
    } else if (stageNode.getSpec().getEnvironment() != null) {
      planCreationResponseMap.putAll(ServiceAllInOnePlanCreatorUtils.addServiceNode(specField, kryoSerializer, service,
          stageNode.getSpec().getEnvironment(), serviceNodeId, nextNodeId, serviceType, ParameterField.ofNull(), ctx));
    }

    return serviceNodeId;
  }
  private String addInfrastructureNode(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      DeploymentStageNodeV1 stageNode, List<AdviserObtainment> adviserObtainments, YamlField specField) {
    EnvironmentYamlV2 environment;
    if (stageNode.getSpec().getEnvironments() != null || stageNode.getSpec().getEnvironmentGroup() != null) {
      environment = MultiDeploymentSpawnerUtils.getEnvironmentYamlV2Node();
    } else {
      environment = stageNode.getSpec().getEnvironment();
    }

    final EnvironmentYamlV2 finalEnvironmentYamlV2 = ServiceAllInOnePlanCreatorUtils.useFromStage(environment)
        ? ServiceAllInOnePlanCreatorUtils.useEnvironmentYamlFromStage(environment.getUseFromStage(), specField)
        : environment;

    ServiceDefinitionType serviceType =
        ServiceDefinitionType.KUBERNETES; // somehow get the service type from service or infra?

    PlanNode node = InfrastructurePmsPlanCreator.getInfraTaskExecutableStepV2PlanNode(
        finalEnvironmentYamlV2, adviserObtainments, serviceType, stageNode.skipInstances);
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());
    return node.getUuid();
  }

  private Optional<String> addProvisionerNodeIfNeeded(YamlField specField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNodeV1 stageNode,
      String infraNodeId) {
    if (stageNode.getSpec().getEnvironment() == null || stageNode.getSpec().getEnvironment().getProvisioner() == null) {
      return Optional.empty();
    }

    YamlField envField = specField.getNode().getField(YAMLFieldNameConstants.ENVIRONMENT);
    YamlField provisionerField = envField.getNode().getField(YAMLFieldNameConstants.PROVISIONER);
    YamlField provisionerStepsField = provisionerField.getNode().getField(YAMLFieldNameConstants.STEPS);

    Map<String, YamlField> stepsYamlFieldMap = new HashMap<>();
    stepsYamlFieldMap.put(provisionerStepsField.getNode().getUuid(), provisionerStepsField);
    planCreationResponseMap.put(provisionerStepsField.getNode().getUuid(),
        PlanCreationResponse.builder().dependencies(DependenciesUtils.toDependenciesProto(stepsYamlFieldMap)).build());

    PlanNode node = InfrastructurePmsPlanCreator.getProvisionerPlanNode(
        provisionerField, provisionerStepsField.getUuid(), infraNodeId, kryoSerializer);
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());
    return Optional.of(node.getUuid());
  }

  private String addGitOpsClustersNode(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNodeV1 stageNode,
      YamlField executionUuid) {
    PlanNode gitopsNode = null;
    final String postServiceStepUuid = "gitOpsClusters-" + UUIDGenerator.generateUuid();
    EnvironmentGroupYaml envGroupYaml = stageNode.getSpec().getEnvironmentGroup();
    if (envGroupYaml != null) {
      EnvGroupPlanCreatorConfig config = envGroupPlanCreatorHelper.createEnvGroupPlanCreatorConfig(ctx, envGroupYaml);

      gitopsNode = InfrastructurePmsPlanCreator.createPlanForGitopsClusters_V1(
          executionUuid, postServiceStepUuid, config, kryoSerializer);
    } else if (stageNode.getSpec().getEnvironments() != null) {
      EnvironmentsPlanCreatorConfig environmentsPlanCreatorConfig =
          environmentsPlanCreatorHelper.createEnvironmentsPlanCreatorConfig(ctx, stageNode.getSpec().getEnvironments());

      gitopsNode = InfrastructurePmsPlanCreator.createPlanForGitopsClusters_V1(
          executionUuid, postServiceStepUuid, environmentsPlanCreatorConfig, kryoSerializer);
    } else if (stageNode.getSpec().getEnvironment() != null) {
      String serviceRef;

      if (stageNode.getSpec().getServices() != null) {
        serviceRef = MultiDeploymentSpawnerUtils.getServiceYamlV2Node().getServiceRef().getValue();
      } else {
        serviceRef = stageNode.getSpec().getService().getServiceRef().getValue();
      }
      EnvironmentPlanCreatorConfig environmentPlanCreatorConfig =
          EnvironmentPlanCreatorHelper.getResolvedEnvRefs(ctx, stageNode.getSpec().getEnvironment(), true, serviceRef,
              serviceOverrideService, environmentService, infrastructure);
      gitopsNode = InfrastructurePmsPlanCreator.createPlanForGitopsClusters_V1(
          executionUuid, postServiceStepUuid, environmentPlanCreatorConfig, kryoSerializer);
    }

    if (gitopsNode == null) {
      log.error("Gitops node was not created, hence not adding it to plan creation");
      return null;
    }

    planCreationResponseMap.put(gitopsNode.getUuid(), PlanCreationResponse.builder().planNode(gitopsNode).build());
    return gitopsNode.getUuid();
  }

  private void addSpecNode(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, YamlField specField, String nextNodeId) {
    // Adding Spec node
    planCreationResponseMap.put(specField.getNode().getUuid(),
        PlanCreationResponse.builder().dependencies(getDependenciesForSpecNode(specField, nextNodeId)).build());
  }

  public Dependencies getDependenciesForSpecNode(YamlField specField, String childNodeUuid) {
    Map<String, YamlField> specYamlFieldMap = new HashMap<>();
    String specNodeUuid = specField.getNode().getUuid();
    specYamlFieldMap.put(specNodeUuid, specField);

    Map<String, ByteString> specDependencyMap = new HashMap<>();
    specDependencyMap.put(
        YAMLFieldNameConstants.CHILD_NODE_OF_SPEC, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(childNodeUuid)));
    Dependency specDependency = Dependency.newBuilder().putAllMetadata(specDependencyMap).build();
    return DependenciesUtils.toDependenciesProto(specYamlFieldMap)
        .toBuilder()
        .putDependencyMetadata(specNodeUuid, specDependency)
        .build();
  }

  private void validateFailureStrategy(DeploymentStageNodeV1 stageNode) {
    // Failure strategy should be present.
    ParameterField<List<FailureConfigV1>> stageFailureStrategies = stageNode.getFailure();
    if (ParameterField.isNull(stageFailureStrategies) || isEmpty(stageFailureStrategies.getValue())) {
      throw new InvalidRequestException("There should be at least one failure strategy configured at stage level.");
    }

    // checking stageFailureStrategies is having one strategy with error type as AllErrors and along with that no
    // error type is involved
    if (!FailureStrategiesUtilsV1.containsOnlyAllErrorsInSomeConfig(stageFailureStrategies)) {
      throw new InvalidRequestException(
          "There should be a Failure strategy that contains one error type as AllErrors, with no other error type along with it in that Failure Strategy.");
    }
  }

  protected void failIfProjectIsFrozen(PlanCreationContext ctx) {
    List<FreezeSummaryResponseDTO> projectFreezeConfigs = null;
    try {
      String accountId = ctx.getAccountIdentifier();
      String orgId = ctx.getOrgIdentifier();
      String projectId = ctx.getProjectIdentifier();
      String pipelineId = ctx.getPipelineIdentifier();
      if (FreezeRBACHelper.checkIfUserHasFreezeOverrideAccess(featureFlagHelperService, accountId, orgId, projectId,
              accessControlClient, CDNGRbacUtility.getExecutionPrincipalInfo(ctx))) {
        return;
      }

      projectFreezeConfigs = freezeEvaluateService.getActiveFreezeEntities(accountId, orgId, projectId, pipelineId);
    } catch (Exception e) {
      log.error(
          "NG Freeze: Failure occurred when evaluating execution should fail due to freeze at the time of plan creation");
    }
    if (EmptyPredicate.isNotEmpty(projectFreezeConfigs)) {
      throw new NGFreezeException("Execution can't be performed because project is frozen");
    }
  }

  private HarnessStruct generateParentInfo(PlanCreationContext ctx, DeploymentStageNodeV1 stageNode) {
    YamlField field = ctx.getCurrentField();
    HarnessStruct.Builder parentInfo = HarnessStruct.newBuilder();
    parentInfo.putData(PlanCreatorConstants.STAGE_ID,
        HarnessValue.newBuilder().setStringValue(getFinalPlanNodeId(ctx, stageNode)).build());
    if (StrategyUtils.isWrappedUnderStrategy(field)) {
      String strategyId = stageNode.getUuid();
      parentInfo.putData(
          PlanCreatorConstants.NEAREST_STRATEGY_ID, HarnessValue.newBuilder().setStringValue(strategyId).build());
      parentInfo.putData(PlanCreatorConstants.ALL_STRATEGY_IDS,
          PlanCreatorUtilsCommon.appendToParentInfoList(PlanCreatorConstants.ALL_STRATEGY_IDS, strategyId, ctx));
      parentInfo.putData(PlanCreatorConstants.STRATEGY_NODE_TYPE,
          HarnessValue.newBuilder().setStringValue(YAMLFieldNameConstants.STAGE).build());
    }
    return parentInfo.build();
  }

  private String getFinalPlanNodeId(PlanCreationContext ctx, DeploymentStageNodeV1 stageNode) {
    String uuid = MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(stageNode);
    return StrategyUtils.getSwappedPlanNodeId(ctx, uuid);
  }

  protected void saveSingleServiceEnvDeploymentStagePlanCreationSummary(
      PlanCreationResponse servicePlanCreationResponse, @NotNull PlanCreationContext ctx,
      @NotNull DeploymentStageNodeV1 stageNode) {
    // TODO: get names of ser/env/infra if possible
    DeploymentStageConfigV1 stageConfig = stageNode.getSpec();
    if (isNull(servicePlanCreationResponse) || isNull(servicePlanCreationResponse.getPlanNode())) {
      log.warn(
          "Plan node or stage config corresponding to service not found while saving deployment info at plan creation, returning");
      return;
    }
    if (MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(stageConfig)) {
      // since multi-service / env configured, info will be saved while adding multi dependency
      log.debug("Multi service and(or) environment deployment stage encountered, skipping saving info");
      return;
    }

    StepParameters stepParameters = servicePlanCreationResponse.getPlanNode().getStepParameters();
    if (isNull(stepParameters) || !(stepParameters instanceof ServiceStepV3Parameters)) {
      log.warn(
          "Step params for service node not of type ServiceStepV3Parameters while saving deployment info at plan creation, returning");
      return;
    }

    ServiceStepV3Parameters serviceStepV3Parameters = (ServiceStepV3Parameters) stepParameters;
    executorService.submit(() -> {
      try {
        deploymentStagePlanCreationInfoService.save(
            DeploymentStagePlanCreationInfo.builder()
                .planExecutionId(ctx.getExecutionUuid())
                .accountIdentifier(ctx.getAccountIdentifier())
                .orgIdentifier(ctx.getOrgIdentifier())
                .projectIdentifier(ctx.getProjectIdentifier())
                .pipelineIdentifier(ctx.getPipelineIdentifier())
                .stageType(DeploymentStageType.SINGLE_SERVICE_ENVIRONMENT)
                .deploymentStageDetailsInfo(
                    // saving the expressions as well, so we know in notifications that these fields were expressions
                    SingleServiceEnvDeploymentStageDetailsInfo.builder()
                        .envIdentifier(ParameterFieldHelper.getParameterFieldFinalValueStringOrNullIfBlank(
                            serviceStepV3Parameters.getEnvRef()))
                        .serviceIdentifier(ParameterFieldHelper.getParameterFieldFinalValueStringOrNullIfBlank(
                            serviceStepV3Parameters.getServiceRef()))
                        .infraIdentifier(ParameterFieldHelper.getParameterFieldFinalValueStringOrNullIfBlank(
                            serviceStepV3Parameters.getInfraId()))
                        .build())
                .deploymentType(serviceStepV3Parameters.getDeploymentType())
                .stageIdentifier(stageNode.getId())
                .stageName(stageNode.getName())
                .build());
      } catch (Exception ex) {
        log.warn("Exception occurred while async saving deployment stage plan creation info: {}",
            ExceptionUtils.getMessage(ex), ex);
      }
    });
  }
}
