/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.spec;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEPS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.integrationstage.CIIntegrationStageModifier;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.license.CILicenseService;
import io.harness.ci.states.CISpecStep;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CISpecPlanCreator extends ChildrenPlanCreator<YamlField> {
  @Inject private CIIntegrationStageModifier ciIntegrationStageModifier;
  @Inject private CILicenseService ciLicenseService;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    Infrastructure infrastructure = IntegrationStageUtils.getInfrastructureV2();
    YamlField stepsField = Preconditions.checkNotNull(config.getNode().getField(YAMLFieldNameConstants.STEPS));
    ExecutionElementConfig executionElementConfig =
        IntegrationStageUtils.getExecutionElementConfigFromSteps(stepsField);
    boolean shouldClone = IntegrationStageUtils.shouldClone(config);
    IntegrationStageNode stageNode = IntegrationStageUtils.getIntegrationStageNode(
        config.getNode().getParentNode(), executionElementConfig, infrastructure, shouldClone);
    IntegrationStageStepParametersPMS stepParameters =
        IntegrationStageStepParametersPMS.getStepParameters(stageNode, childrenNodeIds.get(0), null, ctx);
    return getSpecPlanNode(ctx, config, stepParameters, infrastructure);
  }

  private PlanNode getSpecPlanNode(PlanCreationContext ctx, YamlField specField,
      IntegrationStageStepParametersPMS stepParameters, Infrastructure infrastructure) {
    PlanCreationContextValue planCreationContextValue = ctx.getGlobalContext().get("metadata");
    Long timeout = IntegrationStageUtils.getStageTtl(
        ciLicenseService, planCreationContextValue.getAccountIdentifier(), infrastructure);
    return PlanNode.builder()
        .uuid(specField.getUuid())
        .identifier(YAMLFieldNameConstants.SPEC)
        .stepType(CISpecStep.STEP_TYPE)
        .name(YAMLFieldNameConstants.SPEC)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .timeoutObtainment(SdkTimeoutObtainment.builder()
                               .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                               .parameters(AbsoluteSdkTimeoutTrackerParameters.builder()
                                               .timeout(ParameterField.createValueField(String.format("%ds", timeout)))
                                               .build())
                               .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.SPEC, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField config) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    Infrastructure infrastructure = IntegrationStageUtils.getInfrastructureV2();
    YamlField stepsField = Preconditions.checkNotNull(config.getNode().getField(YAMLFieldNameConstants.STEPS));
    ExecutionElementConfig executionElementConfig =
        IntegrationStageUtils.getExecutionElementConfigFromSteps(stepsField);
    boolean shouldClone = IntegrationStageUtils.shouldClone(config);
    IntegrationStageNode stageNode = IntegrationStageUtils.getIntegrationStageNode(
        config.getNode().getParentNode(), executionElementConfig, infrastructure, shouldClone);

    YamlNode parentYamlNode = stepsField.getNode().getParentNode();
    CodeBase codeBase = getCodebase(ctx.getDependency());
    ExecutionElementConfig modifiedExecutionElementConfig = ciIntegrationStageModifier.modifyExecutionPlan(
        executionElementConfig, stageNode, ctx, codeBase, infrastructure, null);

    try {
      List<JsonNode> steps = new ArrayList<>();
      modifiedExecutionElementConfig.getSteps().forEach(v -> steps.add(v.getStep()));
      String stepsJsonString = JsonPipelineUtils.writeJsonString(steps);
      JsonNode stepsJsonNode = JsonPipelineUtils.getMapper().readTree(stepsJsonString);
      YamlNode modifiedStepsNode = new YamlNode(STEPS, stepsJsonNode, parentYamlNode);

      YamlField modifiedStepsField = new YamlField(modifiedStepsNode);
      planCreationResponseMap.put(stepsField.getUuid(),
          PlanCreationResponse.builder()
              .dependencies(
                  DependenciesUtils.toDependenciesProto(ImmutableMap.of(stepsField.getUuid(), modifiedStepsField)))
              .yamlUpdates(
                  YamlUpdates.newBuilder().putFqnToYaml(modifiedStepsField.getYamlPath(), stepsJsonString).build())
              .build());
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid yaml", e);
    }
    log.info("Successfully created plan for integration stage {}", stageNode.getIdentifier());
    return planCreationResponseMap;
  }

  private CodeBase getCodebase(Dependency dependency) {
    if (dependency == null || EmptyPredicate.isEmpty(dependency.getMetadataMap())
        || !dependency.getMetadataMap().containsKey("codebase")) {
      return null;
    }
    byte[] codebaseBytes = dependency.getMetadataMap().get("codebase").toByteArray();
    return EmptyPredicate.isEmpty(codebaseBytes) ? null : (CodeBase) kryoSerializer.asObject(codebaseBytes);
  }
}
