/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.environment;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.creator.plan.environment.steps.EnvironmentStepV2;
import io.harness.cdng.creator.plan.infrastructure.InfrastructurePmsPlanCreator;
import io.harness.cdng.environment.steps.EnvironmentStepParameters;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentPlanCreatorV2 extends ChildrenPlanCreator<EnvironmentPlanCreatorConfig> {
  @Inject KryoSerializer kryoSerializer;

  @Override
  public Class<EnvironmentPlanCreatorConfig> getFieldClass() {
    return EnvironmentPlanCreatorConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.ENVIRONMENT_YAML,
        new HashSet<>(Arrays.asList(EnvironmentType.PreProduction.name(), EnvironmentType.Production.name())));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, EnvironmentPlanCreatorConfig config) {
    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    boolean gitOpsEnabled = (boolean) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YAMLFieldNameConstants.GITOPS_ENABLED).toByteArray());

    if (!gitOpsEnabled) {
      // if gitOpsEnabled is false, add dependency for infrastructure
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          config.getInfrastructureDefinitions().get(0).getInfrastructureDefinitionConfig();
      YamlField infraField = ctx.getCurrentField().getNode().getField(YamlTypes.INFRASTRUCTURE_DEFS);

      YamlField infraDefinitionField = ctx.getCurrentField()
                                           .getNode()
                                           .getField(YamlTypes.INFRASTRUCTURE_DEFS)
                                           .getNode()
                                           .asArray()
                                           .get(0)
                                           .getField(YamlTypes.INFRASTRUCTURE_DEF);

      PlanNode infraSpecNode =
          InfrastructurePmsPlanCreator.getInfraStepPlanNode(infrastructureDefinitionConfig.getSpec());
      planCreationResponseMap.put(
          infraSpecNode.getUuid(), PlanCreationResponse.builder().node(infraSpecNode.getUuid(), infraSpecNode).build());
      String infraSectionNodeChildId = infraSpecNode.getUuid();

      PlanNode infraDefPlanNode =
          InfrastructurePmsPlanCreator.getInfraDefPlanNode(infraDefinitionField, infraSectionNodeChildId);
      planCreationResponseMap.put(infraDefPlanNode.getUuid(),
          PlanCreationResponse.builder().node(infraDefPlanNode.getUuid(), infraDefPlanNode).build());

      String infraSectionUuid = (String) kryoSerializer.asInflatedObject(
          ctx.getDependency().getMetadataMap().get(YamlTypes.INFRA_SECTION_UUID).toByteArray());
      planCreationResponseMap.putAll(InfrastructurePmsPlanCreator.createPlanForInfraSectionV2(infraField.getNode(),
          infraDefPlanNode.getUuid(), infrastructureDefinitionConfig, kryoSerializer, infraSectionUuid));
    } else {
      String infraSectionUuid = (String) kryoSerializer.asInflatedObject(
          ctx.getDependency().getMetadataMap().get(YamlTypes.INFRA_SECTION_UUID).toByteArray());
      PlanNode gitopsNode = InfrastructurePmsPlanCreator.createPlanForGitopsClusters(
          ctx.getCurrentField(), infraSectionUuid, config, kryoSerializer);
      planCreationResponseMap.put(gitopsNode.getUuid(), PlanCreationResponse.builder().planNode(gitopsNode).build());
    }
    return planCreationResponseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx,
      EnvironmentPlanCreatorConfig environmentPlanCreatorConfig, List<String> childrenNodeIds) {
    /*
    EnvironmentPlanCreator is dependent on infraSectionStepParameters and serviceSpecNodeUuid which is used as advisor
     */
    EnvironmentStepParameters environmentStepParameters =
        EnvironmentMapper.toEnvironmentStepParameters(environmentPlanCreatorConfig);

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