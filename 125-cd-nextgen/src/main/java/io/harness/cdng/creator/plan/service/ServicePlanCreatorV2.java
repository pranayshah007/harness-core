/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.licenserestriction.EnforcementValidator;
import io.harness.cdng.service.steps.ServiceSectionStep;
import io.harness.cdng.service.steps.ServiceSectionStepParameters;
import io.harness.cdng.service.steps.ServiceStepParametersV2;
import io.harness.cdng.service.steps.ServiceStepV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class ServicePlanCreatorV2 extends ChildrenPlanCreator<NGServiceV2InfoConfig> {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private EnforcementValidator enforcementValidator;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, NGServiceV2InfoConfig config) {
    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // enforcement validator
    enforcementValidator.validate(ctx.getMetadata().getAccountIdentifier(), ctx.getMetadata().getOrgIdentifier(),
        ctx.getMetadata().getProjectIdentifier(), ctx.getMetadata().getMetadata().getPipelineIdentifier(),
        ctx.getYaml(), ctx.getMetadata().getMetadata().getExecutionUuid());

    YamlField serviceField = ctx.getCurrentField();
    YamlField serviceDefField = serviceField.getNode().getField(YamlTypes.SERVICE_DEFINITION);
    if (serviceDefField == null || isEmpty(serviceDefField.getNode().getUuid())) {
      throw new InvalidRequestException("ServiceDefinition node is invalid in service - " + config.getIdentifier());
    }

    String serviceDefinitionNodeUuid = serviceDefField.getNode().getUuid();
    addServiceNode(config, planCreationResponseMap, serviceDefinitionNodeUuid);

    planCreationResponseMap.put(serviceDefinitionNodeUuid,
        PlanCreationResponse.builder()
            .dependencies(getDependenciesForServiceDefinitionNode(serviceDefField, ctx))
            .build());

    return planCreationResponseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, NGServiceV2InfoConfig config, List<String> childrenNodeIds) {
    YamlField serviceField = ctx.getCurrentField();
    String serviceUuid = serviceField.getNode().getUuid();
    String serviceActualStepUUid = "service-" + serviceUuid;
    ServiceSectionStepParameters stepParameters =
        ServiceSectionStepParameters.builder()
            .childNodeId(serviceActualStepUUid)
            .serviceRef(ParameterField.createValueField(config.getIdentifier()))
            .build();

    String infraSectionNodeUUid = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.NEXT_UUID).toByteArray());

    // Creating service section node
    return PlanNode.builder()
        .uuid(serviceUuid)
        .stepType(ServiceSectionStep.STEP_TYPE)
        .name(PlanCreatorConstants.SERVICE_NODE_NAME)
        // Keeping this identifier same as v1 so that expressions work
        .identifier(YamlTypes.SERVICE_CONFIG)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .adviserObtainment(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                    OnSuccessAdviserParameters.builder().nextNodeId(infraSectionNodeUUid).build())))
                .build())
        .build();
  }

  private void addServiceNode(NGServiceV2InfoConfig config,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, String serviceDefinitionNodeId) {
    ServiceStepParametersV2 stepParameters = ServiceStepParametersV2.fromServiceV2InfoConfig(config);
    String uuid = "service-" + config.getUuid();

    PlanNode node =
        PlanNode.builder()
            .uuid(uuid)
            .stepType(ServiceStepV2.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_NODE_NAME)
            .identifier(YamlTypes.SERVICE_ENTITY)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .adviserObtainment(
                AdviserObtainment.newBuilder()
                    .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                    .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                        OnSuccessAdviserParameters.builder().nextNodeId(serviceDefinitionNodeId).build())))
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().node(node.getUuid(), node).build());
  }

  @Override
  public Class<NGServiceV2InfoConfig> getFieldClass() {
    return NGServiceV2InfoConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.SERVICE_ENTITY, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  private Dependencies getDependenciesForServiceDefinitionNode(
      YamlField serviceDefinitionField, PlanCreationContext ctx) {
    Map<String, YamlField> serviceDefYamlFieldMap = new HashMap<>();
    String serviceDefUuid = serviceDefinitionField.getNode().getUuid();
    serviceDefYamlFieldMap.put(serviceDefUuid, serviceDefinitionField);

    Map<String, ByteString> serviceDefDependencyMap = new HashMap<>();
    serviceDefDependencyMap.put(
        YamlTypes.ENVIRONMENT_NODE_ID, ctx.getDependency().getMetadataMap().get(YamlTypes.ENVIRONMENT_NODE_ID));
    Dependency serviceDefDependency = Dependency.newBuilder().putAllMetadata(serviceDefDependencyMap).build();
    return DependenciesUtils.toDependenciesProto(serviceDefYamlFieldMap)
        .toBuilder()
        .putDependencyMetadata(serviceDefUuid, serviceDefDependency)
        .build();
  }
}
