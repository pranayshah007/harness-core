/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.envGroup;

import static java.util.Collections.singleton;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.creator.plan.environment.EnvironmentMapper;
import io.harness.cdng.creator.plan.environment.steps.EnvironmentStepV2;
import io.harness.cdng.creator.plan.infrastructure.InfrastructurePmsPlanCreator;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.environment.steps.EnvironmentStepParameters;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class EnvGroupPlanCreator extends ChildrenPlanCreator<EnvGroupPlanCreatorConfig> {
  @Inject KryoSerializer kryoSerializer;
  @Override
  public Class<EnvGroupPlanCreatorConfig> getFieldClass() {
    return EnvGroupPlanCreatorConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.ENVIRONMENT_GROUP_YAML, singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, EnvGroupPlanCreatorConfig config) {
    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    boolean gitOpsEnabled = (boolean) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YAMLFieldNameConstants.GITOPS_ENABLED).toByteArray());
    if (gitOpsEnabled) {
      String postServiceStepUuid = (String) kryoSerializer.asInflatedObject(
          ctx.getDependency().getMetadataMap().get(YamlTypes.POST_SERVICE_SPEC_UUID).toByteArray());
      PlanNode gitopsNode = InfrastructurePmsPlanCreator.createPlanForGitopsClusters(
          ctx.getCurrentField(), postServiceStepUuid, config, kryoSerializer);
      planCreationResponseMap.put(gitopsNode.getUuid(), PlanCreationResponse.builder().planNode(gitopsNode).build());
    }
    return planCreationResponseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, EnvGroupPlanCreatorConfig config, List<String> childrenNodeIds) {
    EnvironmentStepParameters environmentStepParameters = EnvironmentMapper.toEnvironmentStepParameters(config);

    String serviceSpecNodeUuid = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.NEXT_UUID).toByteArray());

    String uuid = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.UUID).toByteArray());

    ByteString advisorParameters = ByteString.copyFrom(
        kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(serviceSpecNodeUuid).build()));
    return PlanNode.builder()
        .uuid(uuid)
        .stepType(EnvironmentStepV2.STEP_TYPE)
        .name(PlanCreatorConstants.ENVIRONMENT_NODE_NAME)
        .identifier(YamlTypes.ENVIRONMENT_YAML)
        .stepParameters(environmentStepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                .build())
        .adviserObtainment(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                .setParameters(advisorParameters)
                .build())
        .skipExpressionChain(false)
        .build();
  }
}
