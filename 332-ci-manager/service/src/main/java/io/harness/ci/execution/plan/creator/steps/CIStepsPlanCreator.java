/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ci.plan.creator.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.plancreator.V1.GitClonePlanCreator;
import io.harness.ci.plancreator.V1.InitializeStepPlanCreatorV1;
import io.harness.cimanager.stages.V1.IntegrationStageNodeV1;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.NGSectionStep;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CI)
public class CIStepsPlanCreator extends ChildrenPlanCreator<YamlField> {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private GitClonePlanCreator gitClonePlanCreator;
  @Inject private InitializeStepPlanCreatorV1 initializeStepPlanCreatorV1;

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    StepParameters stepParameters = NGSectionStepParameters.builder().childNodeId(childrenNodeIds.get(0)).build();
    return PlanNode.builder()
        .uuid(config.getUuid())
        .identifier(YAMLFieldNameConstants.STEPS)
        .stepType(NGSectionStep.STEP_TYPE)
        .name(YAMLFieldNameConstants.STEPS)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> stages = getStepYamlFields(config);
    List<ExecutionWrapperConfig> executionWrapperConfigs = stages.stream()
                                                               .map(stage
                                                                   -> ExecutionWrapperConfig.builder()
                                                                          .uuid(stage.getUuid())
                                                                          .step(stage.getNode().getCurrJsonNode())
                                                                          .build())
                                                               .collect(Collectors.toList());

    if (EmptyPredicate.isEmpty(stages)) {
      return responseMap;
    }
    createPlanCreators(ctx, responseMap, executionWrapperConfigs, stages.get(0).getUuid());
    // TODO : Figure out corresponding failure stages and put that here as well
    IntStream.range(0, stages.size() - 1).forEach(i -> {
      YamlField curr = stages.get(i);
      responseMap.put(curr.getUuid(),
          PlanCreationResponse.builder()
              .dependencies(Dependencies.newBuilder()
                                .putDependencies(curr.getUuid(), curr.getYamlPath())
                                .putDependencyMetadata(curr.getUuid(),
                                    Dependency.newBuilder()
                                        .putMetadata("nextId",
                                            ByteString.copyFrom(kryoSerializer.asBytes(stages.get(i + 1).getUuid())))
                                        .build())
                                .build())
              .build());
    });

    YamlField curr = stages.get(stages.size() - 1);
    responseMap.put(curr.getUuid(),
        PlanCreationResponse.builder()
            .dependencies(Dependencies.newBuilder().putDependencies(curr.getUuid(), curr.getYamlPath()).build())
            .build());
    return responseMap;
  }

  private void createPlanCreators(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      List<ExecutionWrapperConfig> executionWrapperConfigs, String childNodeID) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    // do in reverse order
    // inject git clone plan creator
    CodeBase codeBase = getCodebase(ctx.getDependency());
    ExecutionSource executionSource =
        IntegrationStageUtils.buildExecutionSourceV2(ctx, codeBase, connectorUtils, ctx.getCurrentField().getId());
    String codeBasePluginChildNodeID =
        createGitClonePlanCreator(ctx, responseMap, executionWrapperConfigs, codeBase, childNodeID);
    childNodeID = codeBasePluginChildNodeID != null ? codeBasePluginChildNodeID : childNodeID;

    // inject initialise plan creator
    IntegrationStageNodeV1 stageNode = getIntegrationStageNode(ctx.getDependency());
    PlanCreationResponse planCreationResponse = initializeStepPlanCreatorV1.createPlan(
        ctx, stageNode, codeBase, executionSource, executionWrapperConfigs, childNodeID);
    planCreationResponseMap.put(planCreationResponse.getPlanNode().getUuid(), planCreationResponse);
    planCreationResponseMap.putAll(responseMap);
  }

  private String createGitClonePlanCreator(PlanCreationContext ctx,
      LinkedHashMap<String, PlanCreationResponse> responseMap, List<ExecutionWrapperConfig> executionWrapperConfigs,
      CodeBase codeBase, String childNodeID) {
    if (codeBase != null) {
      Pair<PlanCreationResponse, JsonNode> plan = gitClonePlanCreator.createPlan(ctx, codeBase, childNodeID);
      PlanNode planNode = plan.getKey().getPlanNode();
      responseMap.put(planNode.getUuid(), plan.getLeft());
      executionWrapperConfigs.add(
          0, ExecutionWrapperConfig.builder().uuid(planNode.getUuid()).step(plan.getRight()).build());
      return planNode.getUuid();
    }
    return null;
  }

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.STEPS, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }

  private List<YamlField> getStepYamlFields(YamlField yamlField) {
    List<YamlNode> yamlNodes = Optional.of(yamlField.getNode().asArray()).orElse(Collections.emptyList());
    return yamlNodes.stream().map(YamlField::new).collect(Collectors.toList());
  }

  private CodeBase getCodebase(Dependency dependency) {
    if (dependency == null || EmptyPredicate.isEmpty(dependency.getMetadataMap())
        || !dependency.getMetadataMap().containsKey("codebase")) {
      return null;
    }
    byte[] codebaseBytes = dependency.getMetadataMap().get("codebase").toByteArray();
    return EmptyPredicate.isEmpty(codebaseBytes) ? null : (CodeBase) kryoSerializer.asObject(codebaseBytes);
  }

  private IntegrationStageNodeV1 getIntegrationStageNode(Dependency dependency) {
    if (dependency == null || EmptyPredicate.isEmpty(dependency.getMetadataMap())
        || !dependency.getMetadataMap().containsKey("stageNode")) {
      return null;
    }
    byte[] bytes = dependency.getMetadataMap().get("stageNode").toByteArray();
    return EmptyPredicate.isEmpty(bytes) ? null : (IntegrationStageNodeV1) kryoSerializer.asObject(bytes);
  }
}
