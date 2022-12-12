/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.creator.plan.envGroup.EnvGroupPlanCreatorHelper;
import io.harness.cdng.creator.plan.environment.EnvironmentPlanCreatorHelper;
import io.harness.cdng.creator.plan.infrastructure.InfrastructurePmsPlanCreator;
import io.harness.cdng.creator.plan.service.ServiceAllInOnePlanCreatorUtils;
import io.harness.cdng.creator.plan.service.ServicePlanCreatorHelper;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterHelper;
import io.harness.cdng.environment.helper.EnvironmentsPlanCreatorHelper;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.pipeline.beans.DeploymentStageStepParameters;
import io.harness.cdng.pipeline.beans.MultiDeploymentStepParameters;
import io.harness.cdng.pipeline.steps.CdStepParametersUtils;
import io.harness.cdng.pipeline.steps.DeploymentStageStep;
import io.harness.cdng.pipeline.steps.MultiDeploymentSpawnerUtils;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.ngexception.NGFreezeException;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.helpers.FreezeRBACHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.plancreator.stages.AbstractStagePlanCreator;
import io.harness.plancreator.steps.GenericStepPMSPlanCreator;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.strategy.StrategyValidationUtils;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * Stage plan graph V1 -
 *  Stage
 *      spec (1 children, serviceConfig, next = infra)
 *          serviceConfig (1 children, serviceNode)
 *              service (next = serviceDefinitionNode)
 *              serviceDefinition (1 children, env)
 *                Environment (next = spec node)
 *                spec
 *                  artifacts
 *                  manifests
 *          infrastructureSection(UI visible)
 *            infraDefinition
 *              spec
 *          execution
 *
 * Stage plan graph V2 -
 *  Stage
 *      spec (1 children, service)
 *          serviceSection (1 children, service, next = infra) [Done to keep previous plan creators in sync with v2]
 *              service (next = serviceDef)
 *              serviceDefinition (1 children, env)
 *                Environment (next = spec node)
 *                spec
 *                  artifacts
 *                  manifests
 *          infrastructureSection(UI visible)/Gitops(UI visible)
 *            infraDefinition
 *              spec
 *          execution
 */

@OwnedBy(CDC)
@Slf4j
public class DeploymentStagePMSPlanCreatorV2 extends AbstractStagePlanCreator<DeploymentStageNode> {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private EnvironmentService environmentService;
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private InfrastructureEntityService infrastructure;
  @Inject private ServiceOverrideService serviceOverrideService;
  @Inject private EnvGroupPlanCreatorHelper envGroupPlanCreatorHelper;
  @Inject private EnvironmentsPlanCreatorHelper environmentsPlanCreatorHelper;
  @Inject private ServicePlanCreatorHelper servicePlanCreatorHelper;
  @Inject private FreezeEvaluateService freezeEvaluateService;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;

  @Inject private EnvironmentInfraFilterHelper environmentInfraFilterHelper;

  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton("Deployment");
  }

  @Override
  public StepType getStepType(DeploymentStageNode stageElementConfig) {
    return DeploymentStageStep.STEP_TYPE;
  }

  @Override
  public SpecParameters getSpecParameters(String childNodeId, PlanCreationContext ctx, DeploymentStageNode stageNode) {
    return DeploymentStageStepParameters.getStepParameters(childNodeId);
  }

  @Override
  public Class<DeploymentStageNode> getFieldClass() {
    return DeploymentStageNode.class;
  }

  @Override
  public String getExecutionInputTemplateAndModifyYamlField(YamlField yamlField) {
    return RuntimeInputFormHelper.createExecutionInputFormAndUpdateYamlFieldForStage(
        yamlField.getNode().getParentNode().getCurrJsonNode());
  }

  @SneakyThrows
  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, DeploymentStageNode stageNode, List<String> childrenNodeIds) {
    if (stageNode.getStrategy() != null && MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(stageNode)) {
      throw new InvalidRequestException("Both strategy and multi-deployment is not supported. Please use any one");
    }
    stageNode.setIdentifier(getIdentifierWithExpression(ctx, stageNode, stageNode.getIdentifier()));
    stageNode.setName(getIdentifierWithExpression(ctx, stageNode, stageNode.getName()));
    StageElementParametersBuilder stageParameters = CdStepParametersUtils.getStageParameters(stageNode);
    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));
    stageParameters.specConfig(getSpecParameters(specField.getNode().getUuid(), ctx, stageNode));
    String uuid = MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(stageNode);
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (!MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(stageNode)) {
      adviserObtainments = getAdviserObtainmentFromMetaData(ctx.getCurrentField());
    }
    // We need to swap the ids if strategy is present
    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(StrategyUtils.getSwappedPlanNodeId(ctx, uuid))
            .name(stageNode.getName())
            .identifier(stageNode.getIdentifier())
            .group(StepOutcomeGroup.STAGE.name())
            .stepParameters(stageParameters.build())
            .stepType(getStepType(stageNode))
            .skipCondition(SkipInfoUtils.getSkipCondition(stageNode.getSkipCondition()))
            .whenCondition(RunInfoUtils.getRunCondition(stageNode.getWhen()))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .adviserObtainments(adviserObtainments);

    if (!EmptyPredicate.isEmpty(ctx.getExecutionInputTemplate())) {
      builder.executionInputTemplate(ctx.getExecutionInputTemplate());
    }
    return builder.build();
  }

  public String getIdentifierWithExpression(PlanCreationContext ctx, DeploymentStageNode node, String identifier) {
    if (MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(node)) {
      return identifier + StrategyValidationUtils.STRATEGY_IDENTIFIER_POSTFIX;
    }
    return StrategyUtils.getIdentifierWithExpression(ctx, identifier);
  }
  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, DeploymentStageNode stageNode) {
    failIfProjectIsFrozen(ctx);

    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    try {
      // Validate Stage Failure strategy.
      validateFailureStrategy(stageNode);

      Map<String, ByteString> metadataMap = new HashMap<>();

      YamlField specField =
          Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));

      YamlField executionField = specField.getNode().getField(YAMLFieldNameConstants.EXECUTION);
      if (executionField == null) {
        throw new InvalidRequestException("Execution section cannot be absent in deploy stage");
      }

      if (v2Flow(stageNode)) {
        if (isGitopsEnabled(stageNode.getDeploymentStageConfig())) {
          // GitOps flow doesn't fork on environments, so handling it in this function.
          return buildPlanCreationResponse(ctx, planCreationResponseMap, stageNode, specField, executionField);
        } else {
          List<AdviserObtainment> adviserObtainments =
              addResourceConstraintDependencyWithWhenCondition(planCreationResponseMap, specField);
          String infraNodeId = addInfrastructureNode(planCreationResponseMap, stageNode, adviserObtainments);
          String serviceNodeId = addServiceNode(specField, planCreationResponseMap, stageNode, infraNodeId);
          addSpecNode(planCreationResponseMap, specField, serviceNodeId);
        }
      } else {
        final YamlField serviceField = servicePlanCreatorHelper.getResolvedServiceField(specField);
        PipelineInfrastructure pipelineInfrastructure = stageNode.getDeploymentStageConfig().getInfrastructure();
        addEnvAndInfraDependency(planCreationResponseMap, specField, pipelineInfrastructure);
        addServiceDependency(planCreationResponseMap, specField, stageNode, serviceField);
      }

      addCDExecutionDependencies(planCreationResponseMap, executionField);
      addMultiDeploymentDependency(planCreationResponseMap, stageNode, ctx);

      StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, stageNode.getUuid(), stageNode.getName(),
          stageNode.getIdentifier(), planCreationResponseMap, metadataMap,
          StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false), false);

      return planCreationResponseMap;
    } catch (IOException e) {
      throw new InvalidRequestException(
          "Invalid yaml for Deployment stage with identifier - " + stageNode.getIdentifier(), e);
    }
  }

  private LinkedHashMap<String, PlanCreationResponse> buildPlanCreationResponse(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNode stageNode,
      YamlField specField, YamlField executionField) throws IOException {
    String infraNodeId = addGitOpsClustersNode(ctx, planCreationResponseMap, stageNode, executionField);
    String serviceNodeId = addServiceNodeForGitOps(specField, planCreationResponseMap, stageNode, infraNodeId);
    addSpecNode(planCreationResponseMap, specField, serviceNodeId);

    addCDExecutionDependencies(planCreationResponseMap, executionField);
    addMultiDeploymentDependencyForGitOps(planCreationResponseMap, stageNode, ctx);

    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, stageNode.getUuid(), stageNode.getName(),
        stageNode.getIdentifier(), planCreationResponseMap, new HashMap<>(),
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false), false);

    return planCreationResponseMap;
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, DeploymentStageNode config) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField yamlField = context.getCurrentField();
    if (MultiDeploymentSpawnerUtils.hasMultiDeploymentConfigured(config)) {
      YamlField siblingField = yamlField.getNode().nextSiblingFromParentArray(
          yamlField.getName(), Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.PARALLEL));
      EdgeLayoutList edgeLayoutList;
      String planNodeId = MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(config);
      if (siblingField == null) {
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
              .setNodeIdentifier(config.getIdentifier())
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

  private List<AdviserObtainment> addResourceConstraintDependencyWithWhenCondition(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, YamlField specField) {
    return InfrastructurePmsPlanCreator.addResourceConstraintDependency(
        planCreationResponseMap, specField, kryoSerializer);
  }

  private boolean v2Flow(DeploymentStageNode stageNode) {
    final DeploymentStageConfig deploymentStageConfig = stageNode.getDeploymentStageConfig();

    boolean isServiceV2 = deploymentStageConfig.getService() != null
        && ParameterField.isNotNull(deploymentStageConfig.getService().getServiceRef());
    boolean serviceV2UseFromStage = deploymentStageConfig.getService() != null
        && deploymentStageConfig.getService().getUseFromStage() != null
        && EmptyPredicate.isNotEmpty(deploymentStageConfig.getService().getUseFromStage().getStage());
    boolean isServices = deploymentStageConfig.getServices() != null;
    return isServices || isServiceV2 || serviceV2UseFromStage;
  }

  private void addEnvAndInfraDependency(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      YamlField specField, PipelineInfrastructure pipelineInfrastructure) throws IOException {
    final YamlField infraField = specField.getNode().getField(YamlTypes.PIPELINE_INFRASTRUCTURE);
    if (infraField != null) {
      // Adding infrastructure node
      PlanNode infraStepNode = InfrastructurePmsPlanCreator.getInfraStepPlanNode(
          pipelineInfrastructure.getInfrastructureDefinition().getSpec());
      planCreationResponseMap.put(
          infraStepNode.getUuid(), PlanCreationResponse.builder().node(infraStepNode.getUuid(), infraStepNode).build());
      String infraSectionNodeChildId = infraStepNode.getUuid();

      if (InfrastructurePmsPlanCreator.isProvisionerConfigured(pipelineInfrastructure)) {
        planCreationResponseMap.putAll(InfrastructurePmsPlanCreator.createPlanForProvisioner(
            pipelineInfrastructure, infraField, infraStepNode.getUuid(), kryoSerializer));
        infraSectionNodeChildId = InfrastructurePmsPlanCreator.getProvisionerNodeId(infraField);
      }

      YamlField infrastructureDefField =
          Preconditions.checkNotNull(infraField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEF));
      PlanNode infraDefPlanNode =
          InfrastructurePmsPlanCreator.getInfraDefPlanNode(infrastructureDefField, infraSectionNodeChildId);
      planCreationResponseMap.put(infraDefPlanNode.getUuid(),
          PlanCreationResponse.builder().node(infraDefPlanNode.getUuid(), infraDefPlanNode).build());

      YamlNode infraNode = infraField.getNode();
      planCreationResponseMap.putAll(InfrastructurePmsPlanCreator.createPlanForInfraSectionV1(
          infraNode, infraDefPlanNode.getUuid(), pipelineInfrastructure, kryoSerializer, infraNode.getUuid()));
    }
  }

  private void addMultiDeploymentDependency(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      DeploymentStageNode stageNode, PlanCreationContext ctx) {
    DeploymentStageConfig stageConfig = stageNode.getDeploymentStageConfig();
    String accountIdentifier = ctx.getAccountIdentifier();
    String orgIdentifier = ctx.getOrgIdentifier();
    String projectIdentifier = ctx.getProjectIdentifier();

    // If deploying to Environments with filters
    if (featureFlagHelperService.isEnabled(ctx.getAccountIdentifier(), FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS)) {
      if (stageNode.getDeploymentStageConfig().getEnvironments() != null
          && environmentInfraFilterHelper.areFiltersPresent(stageNode.getDeploymentStageConfig().getEnvironments())) {
        EnvironmentsYaml environmentsYaml = stageConfig.getEnvironments();
        List<EnvironmentYamlV2> finalyamlV2List = new ArrayList<>();
        if (environmentInfraFilterHelper.areFiltersPresent(environmentsYaml)) {
          finalyamlV2List = processFilteringForEnvironmentsLevelFilters(
              accountIdentifier, orgIdentifier, projectIdentifier, environmentsYaml);
        }
        // Set the filtered envYamlV2 in the environments yaml so normal processing continues
        environmentsYaml.getValues().setValue(finalyamlV2List);
      }

      // If deploying to environment group with filters
      if (stageConfig.getEnvironmentGroup() != null
          && (isNotEmpty(stageConfig.getEnvironmentGroup().getFilters().getValue())
              || isNotEmpty(getEnvYamlV2WithFilters(stageConfig.getEnvironmentGroup())))) {
        EnvironmentGroupYaml environmentGroupYaml = stageConfig.getEnvironmentGroup();

        List<EnvironmentYamlV2> finalyamlV2List =
            processFilteringForEnvironmentGroupLevelFilters(accountIdentifier, orgIdentifier, projectIdentifier,
                environmentGroupYaml, stageConfig.getEnvironmentGroup().getFilters().getValue());

        // Set the filtered envYamlV2 in the environmentGroup yaml so normal processing continues
        environmentGroupYaml.getEnvironments().setValue(finalyamlV2List);
      }
    }
    MultiDeploymentSpawnerUtils.validateMultiServiceInfra(stageConfig);
    if (stageConfig.getServices() == null && stageConfig.getEnvironments() == null
        && stageConfig.getEnvironmentGroup() == null) {
      return;
    }

    String subType;
    if (stageConfig.getEnvironments() == null) {
      subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_DEPLOYMENT;
    } else if (stageConfig.getServices() == null) {
      subType = MultiDeploymentSpawnerUtils.MULTI_ENV_DEPLOYMENT;
    } else {
      subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_ENV_DEPLOYMENT;
    }
    if (stageConfig.getServices() != null && ParameterField.isBlank(stageConfig.getServices().getValues())) {
      throw new InvalidYamlException(
          "No values of services provided. Please provide at least one service for deployment");
    }
    if (stageConfig.getEnvironments() != null && ParameterField.isBlank(stageConfig.getEnvironments().getValues())) {
      throw new InvalidYamlException(
          "No values of environments provided. Please provide at least one service for deployment");
    }

    MultiDeploymentStepParameters stepParameters =
        MultiDeploymentStepParameters.builder()
            .strategyType(StrategyType.MATRIX)
            .childNodeId(MultiDeploymentSpawnerUtils.getUuidForMultiDeployment(stageNode))
            .environments(stageConfig.getEnvironments())
            .environmentGroup(stageConfig.getEnvironmentGroup())
            .services(stageConfig.getServices())
            .subType(subType)
            .build();

    buildMultiDeploymentMetadata(planCreationResponseMap, stageNode, ctx, stepParameters);
  }

  @NotNull
  private List<EnvironmentYamlV2> processFilteringForEnvironmentsLevelFilters(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, EnvironmentsYaml environmentsYaml) {
    List<EnvironmentYamlV2> finalyamlV2List;
    Set<EnvironmentYamlV2> envsLevelEnvironmentYamlV2 = new HashSet<>();
    if (isNotEmpty(environmentsYaml.getFilters().getValue())) {
      List<EnvironmentYamlV2> filteredEnvList = processEnvironmentInfraFilters(
          accountIdentifier, orgIdentifier, projectIdentifier, environmentsYaml.getFilters().getValue());
      envsLevelEnvironmentYamlV2.addAll(filteredEnvList);
    }

    // Process filtering at individual Environment level
    Set<EnvironmentYamlV2> individualEnvironmentYamlV2 = new HashSet<>();
    if (environmentInfraFilterHelper.areFiltersSetOnIndividualEnvironments(environmentsYaml)) {
      processFiltersOnIndividualEnvironmentsLevel(accountIdentifier, orgIdentifier, projectIdentifier,
          individualEnvironmentYamlV2, environmentInfraFilterHelper.getEnvV2YamlsWithFilters(environmentsYaml));
    }

    // Merge the two lists
    List<EnvironmentYamlV2> mergedFilteredEnvs =
        getEnvOrEnvGrouplevelAndIndividualEnvFilteredEnvs(envsLevelEnvironmentYamlV2, individualEnvironmentYamlV2);

    // If there are envs in the filtered list and there are
    // specific infras specific, pick the specified infras
    finalyamlV2List = getFinalEnvsList(environmentsYaml.getValues().getValue(), mergedFilteredEnvs);

    return finalyamlV2List;
  }

  private List<EnvironmentYamlV2> processFilteringForEnvironmentGroupLevelFilters(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, EnvironmentGroupYaml environmentGroupYaml,
      List<FilterYaml> filterYamls) {
    Set<EnvironmentYamlV2> envsLevelEnvironmentYamlV2 = new HashSet<>();
    if (isNotEmpty(filterYamls)) {
      List<EnvironmentYamlV2> filteredEnvList =
          processEnvironmentInfraFilters(accountIdentifier, orgIdentifier, projectIdentifier, filterYamls);
      envsLevelEnvironmentYamlV2.addAll(filteredEnvList);
    }

    Set<EnvironmentYamlV2> individualEnvironmentYamlV2 = new HashSet<>();
    if (isNotEmpty(environmentGroupYaml.getEnvironments().getValue())) {
      if (isNotEmpty(getEnvYamlV2WithFilters(environmentGroupYaml))) {
        processFiltersOnIndividualEnvironmentsLevel(accountIdentifier, orgIdentifier, projectIdentifier,
            individualEnvironmentYamlV2, getEnvYamlV2WithFilters(environmentGroupYaml));
      }
    }

    // Merge the two lists
    List<EnvironmentYamlV2> mergedFilteredEnvs =
        getEnvOrEnvGrouplevelAndIndividualEnvFilteredEnvs(envsLevelEnvironmentYamlV2, individualEnvironmentYamlV2);

    return getFinalEnvsList(environmentGroupYaml.getEnvironments().getValue(), mergedFilteredEnvs);
  }

  @NotNull
  private static List<EnvironmentYamlV2> getEnvYamlV2WithFilters(EnvironmentGroupYaml environmentGroupYaml) {
    return environmentGroupYaml.getEnvironments()
        .getValue()
        .stream()
        .filter(eg -> ParameterField.isNotNull(eg.getFilters()))
        .collect(Collectors.toList());
  }

  @NotNull
  private static List<EnvironmentYamlV2> getFinalEnvsList(
      List<EnvironmentYamlV2> envsFromYaml, List<EnvironmentYamlV2> mergedFilteredEnvs) {
    List<EnvironmentYamlV2> finalyamlV2List = new ArrayList<>();
    if (isNotEmpty(envsFromYaml)) {
      for (EnvironmentYamlV2 e : envsFromYaml) {
        List<EnvironmentYamlV2> list = mergedFilteredEnvs.stream()
                                           .filter(in -> in.getEnvironmentRef().equals(e.getEnvironmentRef()))
                                           .collect(Collectors.toList());
        if (isNotEmpty(list) || ParameterField.isNull(e.getInfrastructureDefinitions())
            || isEmpty(e.getInfrastructureDefinitions().getValue())) {
          continue;
        }
        finalyamlV2List.add(e);
      }
    }
    finalyamlV2List.addAll(mergedFilteredEnvs);
    return finalyamlV2List;
  }

  @NotNull
  private static List<EnvironmentYamlV2> getEnvOrEnvGrouplevelAndIndividualEnvFilteredEnvs(
      Set<EnvironmentYamlV2> envsLevelEnvironmentYamlV2, Set<EnvironmentYamlV2> individualEnvironmentYamlV2) {
    List<EnvironmentYamlV2> mergedFilteredEnvs = new ArrayList<>();
    for (EnvironmentYamlV2 envYamlV2 : envsLevelEnvironmentYamlV2) {
      List<EnvironmentYamlV2> eV2 = individualEnvironmentYamlV2.stream()
                                        .filter(e -> e.getEnvironmentRef().equals(envYamlV2.getEnvironmentRef()))
                                        .collect(Collectors.toList());
      if (isNotEmpty(eV2)) {
        continue;
      }
      mergedFilteredEnvs.add(envYamlV2);
    }
    mergedFilteredEnvs.addAll(individualEnvironmentYamlV2);
    return mergedFilteredEnvs;
  }

  private List<EnvironmentYamlV2> processFiltersOnIndividualEnvironmentsLevel(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, Set<EnvironmentYamlV2> individualEnvironmentYamlV2,
      List<EnvironmentYamlV2> envV2YamlsWithFilters) {
    List<EnvironmentYamlV2> filteredInfraList = new ArrayList<>();
    for (EnvironmentYamlV2 envYamlV2 : envV2YamlsWithFilters) {
      Set<InfrastructureEntity> infrastructureEntitySet =
          environmentInfraFilterHelper.getInfrastructureForEnvironmentList(
              accountIdentifier, orgIdentifier, projectIdentifier, envYamlV2.getEnvironmentRef().getValue());

      filteredInfraList = filterInfras(
          envYamlV2.getFilters().getValue(), envYamlV2.getEnvironmentRef().getValue(), infrastructureEntitySet);
      individualEnvironmentYamlV2.addAll(filteredInfraList);
    }
    return filteredInfraList;
  }

  /**
   * Method Handles creation of the multi-service deployments for GitOps Deployments.
   * Note: GitOps Flow doesn't fork stages for deploying to multiple environments or environmentgroup
   */
  private void addMultiDeploymentDependencyForGitOps(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNode stageNode,
      PlanCreationContext ctx) {
    DeploymentStageConfig stageConfig = stageNode.getDeploymentStageConfig();
    if (stageConfig.getServices() == null && stageConfig.getEnvironments() == null
        && stageConfig.getEnvironmentGroup() == null) {
      return;
    }

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
      DeploymentStageNode stageNode, PlanCreationContext ctx, MultiDeploymentStepParameters stepParameters) {
    MultiDeploymentMetadata metadata =
        MultiDeploymentMetadata.builder()
            .multiDeploymentNodeId(ctx.getCurrentField().getNode().getUuid())
            .multiDeploymentStepParameters(stepParameters)
            .strategyNodeIdentifier(stageNode.getIdentifier())
            .strategyNodeName(stageNode.getName())
            .adviserObtainments(StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false))
            .build();

    PlanNode node = MultiDeploymentStepPlanCreator.createPlan(metadata);
    planCreationResponseMap.put(UUIDGenerator.generateUuid(), PlanCreationResponse.builder().planNode(node).build());
  }

  // This function adds the service dependency and returns the resolved service field
  private void addServiceDependency(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      YamlField specField, DeploymentStageNode stageNode, YamlField serviceField) throws IOException {
    // Adding service child by resolving the serviceField
    String serviceNodeUuid = serviceField.getNode().getUuid();

    // Adding Spec node
    planCreationResponseMap.put(specField.getNode().getUuid(),
        PlanCreationResponse.builder().dependencies(getDependenciesForSpecNode(specField, serviceNodeUuid)).build());

    // Adding dependency for service
    // Adding serviceField to yamlUpdates as its resolved value should be updated.
    planCreationResponseMap.put(serviceNodeUuid,
        PlanCreationResponse.builder()
            .dependencies(servicePlanCreatorHelper.getDependenciesForService(serviceField, stageNode))
            .yamlUpdates(YamlUpdates.newBuilder()
                             .putFqnToYaml(serviceField.getYamlPath(),
                                 YamlUtils.writeYamlString(serviceField).replace("---\n", ""))
                             .build())
            .build());
  }

  private String addServiceNode(YamlField specField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNode stageNode,
      String nextNodeId) throws IOException {
    // Adding service child by resolving the serviceField
    ServiceDefinitionType deploymentType = stageNode.getDeploymentStageConfig().getDeploymentType();
    ServiceYamlV2 service;
    if (stageNode.getDeploymentStageConfig().getServices() != null) {
      service = MultiDeploymentSpawnerUtils.getServiceYamlV2Node();
    } else {
      service = stageNode.getDeploymentStageConfig().getService();
    }

    EnvironmentYamlV2 environment;
    if (stageNode.getDeploymentStageConfig().getEnvironments() != null
        || stageNode.getDeploymentStageConfig().getEnvironmentGroup() != null) {
      environment = MultiDeploymentSpawnerUtils.getEnvironmentYamlV2Node();
    } else {
      environment = stageNode.getDeploymentStageConfig().getEnvironment();
    }
    String serviceNodeId = service.getUuid();
    planCreationResponseMap.putAll(ServiceAllInOnePlanCreatorUtils.addServiceNode(
        specField, kryoSerializer, service, environment, serviceNodeId, nextNodeId, deploymentType));
    return serviceNodeId;
  }

  private String addServiceNodeForGitOps(YamlField specField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNode stageNode,
      String nextNodeId) {
    // Adding service child by resolving the serviceField
    ServiceDefinitionType deploymentType = stageNode.getDeploymentStageConfig().getDeploymentType();
    ServiceYamlV2 service;
    if (stageNode.getDeploymentStageConfig().getServices() != null) {
      service = MultiDeploymentSpawnerUtils.getServiceYamlV2Node();
    } else {
      service = stageNode.getDeploymentStageConfig().getService();
    }

    EnvironmentGroupYaml environmentGroupYaml = stageNode.getDeploymentStageConfig().getEnvironmentGroup();
    String serviceNodeId = service.getUuid();
    if (stageNode.getDeploymentStageConfig().getEnvironmentGroup() != null) {
      planCreationResponseMap.putAll(ServiceAllInOnePlanCreatorUtils.addServiceNodeForGitOpsEnvGroup(
          specField, kryoSerializer, service, environmentGroupYaml, serviceNodeId, nextNodeId, deploymentType));
    } else if (stageNode.deploymentStageConfig.getEnvironments() != null) {
      planCreationResponseMap.putAll(
          ServiceAllInOnePlanCreatorUtils.addServiceNodeForGitOpsEnvironments(specField, kryoSerializer, service,
              stageNode.deploymentStageConfig.getEnvironments(), serviceNodeId, nextNodeId, deploymentType));
    } else if (stageNode.deploymentStageConfig.getEnvironment() != null) {
      planCreationResponseMap.putAll(ServiceAllInOnePlanCreatorUtils.addServiceNode(specField, kryoSerializer, service,
          stageNode.deploymentStageConfig.getEnvironment(), serviceNodeId, nextNodeId, deploymentType));
    }

    return serviceNodeId;
  }
  private String addInfrastructureNode(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      DeploymentStageNode stageNode, List<AdviserObtainment> adviserObtainments) throws IOException {
    EnvironmentYamlV2 environment;
    if (stageNode.getDeploymentStageConfig().getEnvironments() != null
        || stageNode.getDeploymentStageConfig().getEnvironmentGroup() != null) {
      environment = MultiDeploymentSpawnerUtils.getEnvironmentYamlV2Node();
    } else {
      environment = stageNode.getDeploymentStageConfig().getEnvironment();
    }
    PlanNode node = InfrastructurePmsPlanCreator.getInfraTaskExecutableStepV2PlanNode(environment, adviserObtainments,
        stageNode.getDeploymentStageConfig().getDeploymentType(), stageNode.skipInstances);
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());
    return node.getUuid();
  }

  private String addGitOpsClustersNode(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, DeploymentStageNode stageNode,
      YamlField executionUuid) {
    PlanNode gitopsNode = null;
    final String postServiceStepUuid = "gitOpsClusters-" + UUIDGenerator.generateUuid();

    EnvironmentGroupYaml envGroupYaml = stageNode.getDeploymentStageConfig().getEnvironmentGroup();
    if (envGroupYaml != null) {
      EnvGroupPlanCreatorConfig config = envGroupPlanCreatorHelper.createEnvGroupPlanCreatorConfig(ctx, envGroupYaml);

      gitopsNode = InfrastructurePmsPlanCreator.createPlanForGitopsClusters(
          executionUuid, postServiceStepUuid, config, kryoSerializer);
    } else if (stageNode.getDeploymentStageConfig().getEnvironments() != null) {
      EnvironmentsPlanCreatorConfig environmentsPlanCreatorConfig =
          environmentsPlanCreatorHelper.createEnvironmentsPlanCreatorConfig(
              ctx, stageNode.getDeploymentStageConfig().getEnvironments());

      gitopsNode = InfrastructurePmsPlanCreator.createPlanForGitopsClusters(
          executionUuid, postServiceStepUuid, environmentsPlanCreatorConfig, kryoSerializer);
    } else if (stageNode.deploymentStageConfig.getEnvironment() != null) {
      String serviceRef;

      if (stageNode.getDeploymentStageConfig().getServices() != null) {
        serviceRef = MultiDeploymentSpawnerUtils.getServiceYamlV2Node().getServiceRef().getValue();
      } else {
        serviceRef = stageNode.getDeploymentStageConfig().getService().getServiceRef().getValue();
      }
      EnvironmentPlanCreatorConfig environmentPlanCreatorConfig =
          EnvironmentPlanCreatorHelper.getResolvedEnvRefs(ctx, stageNode.deploymentStageConfig.getEnvironment(), true,
              serviceRef, serviceOverrideService, environmentService, infrastructure);
      gitopsNode = InfrastructurePmsPlanCreator.createPlanForGitopsClusters(
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

  public void addCDExecutionDependencies(
      Map<String, PlanCreationResponse> planCreationResponseMap, YamlField executionField) {
    Map<String, YamlField> executionYamlFieldMap = new HashMap<>();
    executionYamlFieldMap.put(executionField.getNode().getUuid(), executionField);

    planCreationResponseMap.put(executionField.getNode().getUuid(),
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils.toDependenciesProto(executionYamlFieldMap))
            .build());
  }

  private void validateFailureStrategy(DeploymentStageNode stageNode) {
    // Failure strategy should be present.
    List<FailureStrategyConfig> stageFailureStrategies = stageNode.getFailureStrategies();
    if (EmptyPredicate.isEmpty(stageFailureStrategies)) {
      throw new InvalidRequestException("There should be at least one failure strategy configured at stage level.");
    }

    // checking stageFailureStrategies is having one strategy with error type as AllErrors and along with that no
    // error type is involved
    if (!GenericStepPMSPlanCreator.containsOnlyAllErrorsInSomeConfig(stageFailureStrategies)) {
      throw new InvalidRequestException(
          "There should be a Failure strategy that contains one error type as AllErrors, with no other error type along with it in that Failure Strategy.");
    }
  }

  protected void failIfProjectIsFrozen(PlanCreationContext ctx) {
    List<FreezeSummaryResponseDTO> projectFreezeConfigs = null;
    try {
      if (!EmptyPredicate.isEmpty(ctx.getAccountIdentifier())
          && featureFlagHelperService.isEnabled(ctx.getAccountIdentifier(), FeatureName.NG_DEPLOYMENT_FREEZE)) {
        String accountId = ctx.getAccountIdentifier();
        String orgId = ctx.getOrgIdentifier();
        String projectId = ctx.getProjectIdentifier();
        if (FreezeRBACHelper.checkIfUserHasFreezeOverrideAccess(
                featureFlagHelperService, accountId, orgId, projectId, accessControlClient)) {
          return;
        }
        projectFreezeConfigs = freezeEvaluateService.getActiveFreezeEntities(accountId, orgId, projectId);
      }
    } catch (Exception e) {
      log.error(
          "NG Freeze: Failure occurred when evaluating execution should fail due to freeze at the time of plan creation");
    }
    if (EmptyPredicate.isNotEmpty(projectFreezeConfigs)) {
      throw new NGFreezeException("Execution can't be performed because project is frozen");
    }
  }

  private boolean isGitopsEnabled(DeploymentStageConfig deploymentStageConfig) {
    return deploymentStageConfig.getGitOpsEnabled();
  }

  private List<EnvironmentYamlV2> processEnvironmentInfraFilters(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<FilterYaml> filterYamls) {
    Set<Environment> allEnvsInProject =
        environmentInfraFilterHelper.getAllEnvironmentsInProject(accountIdentifier, orgIdentifier, projectIdentifier);

    // Apply filters on environments
    Set<Environment> filteredEnvs = environmentInfraFilterHelper.applyFiltersOnEnvs(allEnvsInProject, filterYamls);

    // Get All InfraDefinitions
    List<EnvironmentYamlV2> environmentYamlV2List = new ArrayList<>();
    for (Environment env : filteredEnvs) {
      Set<InfrastructureEntity> infrastructureEntitySet =
          environmentInfraFilterHelper.getInfrastructureForEnvironmentList(
              accountIdentifier, orgIdentifier, projectIdentifier, env.getIdentifier());

      if (isNotEmpty(infrastructureEntitySet)) {
        List<EnvironmentYamlV2> temp = filterInfras(filterYamls, env.getIdentifier(), infrastructureEntitySet);
        if (isNotEmpty(temp)) {
          environmentYamlV2List.add(temp.get(0));
        }
      }
    }
    return environmentYamlV2List;
  }

  public List<EnvironmentYamlV2> filterInfras(
      List<FilterYaml> filterYamls, String env, Set<InfrastructureEntity> infrastructureEntitySet) {
    List<EnvironmentYamlV2> environmentYamlV2List = new ArrayList<>();
    Set<InfrastructureEntity> filteredInfras =
        environmentInfraFilterHelper.applyFilteringOnInfras(filterYamls, infrastructureEntitySet);

    if (isNotEmpty(filteredInfras)) {
      List<InfraStructureDefinitionYaml> infraDefYamlList = new ArrayList<>();

      for (InfrastructureEntity in : filteredInfras) {
        infraDefYamlList.add(createInfraDefinitionYaml(in));
      }

      EnvironmentYamlV2 environmentYamlV2 =
          EnvironmentYamlV2.builder()
              .environmentRef(ParameterField.createValueField(env))
              .infrastructureDefinitions(ParameterField.createValueField(infraDefYamlList))
              .build();

      environmentYamlV2List.add(environmentYamlV2);
    }
    return environmentYamlV2List;
  }

  @VisibleForTesting
  protected InfraStructureDefinitionYaml createInfraDefinitionYaml(InfrastructureEntity infrastructureEntity) {
    return InfraStructureDefinitionYaml.builder()
        .identifier(ParameterField.createValueField(infrastructureEntity.getIdentifier()))
        .build();
  }
}
