/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.pms.yaml.YAMLFieldNameConstants.PARALLEL;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP_CHILD_NODE_ID;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StepGroupPMSPlanCreatorV2 extends ChildrenPlanCreator<StepGroupElementConfig> {
  public static final int DEFAULT_TIMEOUT = 600 * 1000;

  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepGroupHandlerFactory stepGroupHandlerFactory;
  @Inject private StepGroupPMSPlanCreator stepGroupPMSPlanCreator;

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, StepGroupElementConfig config, List<String> childrenNodeIds) {
    return stepGroupPMSPlanCreator.createPlanForParentNode(ctx, config, childrenNodeIds);
  }

  @Override
  public Class<StepGroupElementConfig> getFieldClass() {
    return StepGroupElementConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP_GROUP, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, StepGroupElementConfig config) {
    if (config.getStepGroupInfra() == null) {
      return stepGroupPMSPlanCreator.createPlanForChildrenNodes(ctx, config);
    }
    List<YamlField> dependencyNodeIdsList = ctx.getStepYamlFields();

    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    // Add Steps Node
    if (EmptyPredicate.isNotEmpty(dependencyNodeIdsList)) {
      YamlField stepsField =
          Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STEPS));

      StepGroupInfraHandler stepGroupInfraHandler =
          stepGroupHandlerFactory.getHandler(config.getStepGroupInfra().getType());
      Dependency stepsChildNodeId = null;
      if (stepGroupInfraHandler != null) {
        PlanNode initNode = stepGroupInfraHandler.handle(config, ctx, stepsField);
        if (initNode != null) {
          stepsChildNodeId = Dependency.newBuilder()
                                 .putMetadata(STEP_GROUP_CHILD_NODE_ID,
                                     ByteString.copyFrom(kryoSerializer.asDeflatedBytes(initNode.getUuid())))
                                 .build();
          responseMap.put(initNode.getUuid(), PlanCreationResponse.builder().planNode(initNode).build());
        }
      }

      String stepsNodeId = stepsField.getNode().getUuid();
      Map<String, YamlField> stepsYamlFieldMap = new HashMap<>();
      stepsYamlFieldMap.put(stepsNodeId, stepsField);
      Dependencies dependencies = DependenciesUtils.toDependenciesProto(stepsYamlFieldMap);

      if (stepsChildNodeId != null) {
        dependencies = dependencies.toBuilder().putDependencyMetadata(stepsNodeId, stepsChildNodeId).build();
      }
      responseMap.put(stepsNodeId, PlanCreationResponse.builder().dependencies(dependencies).build());
    }
    addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, config.getUuid(), config.getName(), config.getIdentifier(),
        responseMap, new HashMap<>(), getAdviserObtainmentFromMetaData(ctx.getCurrentField(), false));

    return responseMap;
  }

  protected List<AdviserObtainment> getAdviserObtainmentFromMetaData(YamlField currentField, boolean checkForStrategy) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (checkForStrategy) {
      return adviserObtainments;
    }

    /*
     * Adding OnSuccess adviser if stepGroup is inside rollback section else adding NextStep adviser for when condition
     * to work.
     */
    if (currentField != null && currentField.getNode() != null) {
      // Check if step is inside RollbackStep
      if (YamlUtils.findParentNode(currentField.getNode(), ROLLBACK_STEPS) != null) {
        addOnSuccessAdviser(currentField, adviserObtainments);
      } else {
        // Adding NextStep Adviser at last due to giving priority to Failure strategy more. DO NOT CHANGE.
        addNextStepAdviser(currentField, adviserObtainments);
      }
    }
    return adviserObtainments;
  }

  private void addNextStepAdviser(YamlField currentField, List<AdviserObtainment> adviserObtainments) {
    if (currentField.checkIfParentIsParallel(STEPS)) {
      return;
    }
    YamlField siblingField = currentField.getNode().nextSiblingFromParentArray(
        currentField.getName(), Arrays.asList(YAMLFieldNameConstants.STEP, PARALLEL, STEP_GROUP));
    if (siblingField != null && siblingField.getNode().getUuid() != null) {
      adviserObtainments.add(
          AdviserObtainment.newBuilder()
              .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STEP.name()).build())
              .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                  NextStepAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
              .build());
    }
  }

  private void addOnSuccessAdviser(YamlField currentField, List<AdviserObtainment> adviserObtainments) {
    if (currentField.checkIfParentIsParallel(ROLLBACK_STEPS)) {
      return;
    }
    YamlField siblingField = currentField.getNode().nextSiblingFromParentArray(
        currentField.getName(), Arrays.asList(YAMLFieldNameConstants.STEP, PARALLEL, STEP_GROUP));
    if (siblingField != null && siblingField.getNode().getUuid() != null) {
      adviserObtainments.add(
          AdviserObtainment.newBuilder()
              .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
              .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                  OnSuccessAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
              .build());
    }
  }

  public void addStrategyFieldDependencyIfPresent(KryoSerializer kryoSerializer, PlanCreationContext ctx, String uuid,
      String name, String identifier, LinkedHashMap<String, PlanCreationResponse> responseMap,
      HashMap<Object, Object> objectObjectHashMap, List<AdviserObtainment> adviserObtainmentFromMetaData) {
    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, uuid, name, identifier, responseMap,
        new HashMap<>(), getAdviserObtainmentFromMetaData(ctx.getCurrentField(), false));
  }
}
