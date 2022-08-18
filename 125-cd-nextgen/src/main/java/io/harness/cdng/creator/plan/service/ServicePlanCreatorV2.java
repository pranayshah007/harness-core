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
import io.harness.cdng.artifact.steps.ArtifactsStepV2;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.licenserestriction.EnforcementValidator;
import io.harness.cdng.service.steps.ServiceDefinitionStep;
import io.harness.cdng.service.steps.ServiceDefinitionStepParameters;
import io.harness.cdng.service.steps.ServiceSectionStep;
import io.harness.cdng.service.steps.ServiceSectionStepParameters;
import io.harness.cdng.service.steps.ServiceSpecStep;
import io.harness.cdng.service.steps.ServiceSpecStepParameters;
import io.harness.cdng.service.steps.ServiceStepParametersV2;
import io.harness.cdng.service.steps.ServiceStepV2;
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.service.steps.ServiceStepV3Parameters;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class ServicePlanCreatorV2 extends ChildrenPlanCreator<ServicePlanCreatorV2Config> {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private EnforcementValidator enforcementValidator;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, ServicePlanCreatorV2Config config) {
    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // enforcement validator
    enforcementValidator.validate(ctx.getMetadata().getAccountIdentifier(), ctx.getMetadata().getOrgIdentifier(),
        ctx.getMetadata().getProjectIdentifier(), ctx.getMetadata().getMetadata().getPipelineIdentifier(),
        ctx.getYaml(), ctx.getMetadata().getMetadata().getExecutionUuid());

    YamlField serviceField = ctx.getCurrentField();
    YamlField serviceDefField = serviceField.getNode().getField(YamlTypes.SERVICE_DEFINITION);

    if (serviceRefExpression(config)) {
      addServiceNodeWithExpression(config, planCreationResponseMap,
          (String) kryoSerializer.asInflatedObject(
              ctx.getDependency().getMetadataMap().get(YamlTypes.ENVIRONMENT_NODE_ID).toByteArray()),
          (String) kryoSerializer.asInflatedObject(
              ctx.getDependency().getMetadataMap().get("SERVICE_SPEC_NODE_ID").toByteArray()));

      return planCreationResponseMap;
    }

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

  // Todo:(yogesh) Better way to check expression
  private boolean serviceRefExpression(ServicePlanCreatorV2Config config) {
    return config.getIdentifier().isExpression();
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, ServicePlanCreatorV2Config config, List<String> childrenNodeIds) {
    YamlField serviceField = ctx.getCurrentField();
    String serviceUuid = serviceField.getNode().getUuid();
    String serviceActualStepUUid = "service-" + serviceUuid;
    ServiceSectionStepParameters stepParameters = ServiceSectionStepParameters.builder()
                                                      .childNodeId(serviceActualStepUUid)
                                                      .serviceRef(config.getIdentifier())
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

  private void addServiceNode(ServicePlanCreatorV2Config config,
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

  private void addServiceNodeWithExpression(ServicePlanCreatorV2Config config,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, String envNodeId,
      String service_spec_node_id) {
    final ServiceStepV3Parameters stepParameters =
        ServiceStepV3Parameters.builder().serviceRef(config.getIdentifier()).build();
    final String serviceDefinitionNodeUuid = UUIDGenerator.generateUuid();
    final String serviceStepUuid = "service-" + config.getUuid();

    final PlanNode node =
        PlanNode.builder()
            .uuid(serviceStepUuid)
            .stepType(ServiceStepV3.STEP_TYPE)
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
                        OnSuccessAdviserParameters.builder().nextNodeId(serviceDefinitionNodeUuid).build())))
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());

    addServiceDefinitionNode(planCreationResponseMap, serviceDefinitionNodeUuid, envNodeId, service_spec_node_id);
  }

  private void addServiceDefinitionNode(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      String serviceDefinitionNodeId, String envNodeId, String service_spec_node_id) {
    // Add service definition node
    final PlanNode artifactsNode =
        PlanNode.builder()
            .uuid("artifacts-" + UUIDGenerator.generateUuid())
            .stepType(ArtifactsStepV2.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_NODE_NAME)
            .identifier(YamlTypes.SERVICE_ENTITY)
            .stepParameters(new EmptyStepParameters())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    planCreationResponseMap.put(
        artifactsNode.getUuid(), PlanCreationResponse.builder().planNode(artifactsNode).build());

    // Add service definition node
    final ServiceDefinitionStepParameters stepParameters =
        ServiceDefinitionStepParameters.builder().childNodeId(envNodeId).build();
    PlanNode serviceDefNode =
        PlanNode.builder()
            .uuid(serviceDefinitionNodeId)
            .stepType(ServiceDefinitionStep.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_DEFINITION_NODE_NAME)
            .identifier(YamlTypes.SERVICE_DEFINITION)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .skipExpressionChain(false)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    planCreationResponseMap.put(
        serviceDefinitionNodeId, PlanCreationResponse.builder().planNode(serviceDefNode).build());

    // Add service spec node
    final ServiceSpecStepParameters serviceSpecStepParameters =
        ServiceSpecStepParameters.builder().childrenNodeIds(Collections.singletonList(artifactsNode.getUuid())).build();
    final PlanNode serviceSpecNode =
        PlanNode.builder()
            .uuid(service_spec_node_id)
            .stepType(ServiceSpecStep.STEP_TYPE)
            .name(PlanCreatorConstants.SERVICE_SPEC_NODE_NAME)
            .identifier(YamlTypes.SERVICE_SPEC)
            .stepParameters(serviceSpecStepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                    .build())
            .skipExpressionChain(false)
            .build();
    planCreationResponseMap.put(
        serviceSpecNode.getUuid(), PlanCreationResponse.builder().planNode(serviceSpecNode).build());
  }

  @Override
  public Class<ServicePlanCreatorV2Config> getFieldClass() {
    return ServicePlanCreatorV2Config.class;
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
    serviceDefDependencyMap.put(
        YamlTypes.ENVIRONMENT_REF, ctx.getDependency().getMetadataMap().get(YamlTypes.ENVIRONMENT_REF));
    Dependency serviceDefDependency = Dependency.newBuilder().putAllMetadata(serviceDefDependencyMap).build();
    return DependenciesUtils.toDependenciesProto(serviceDefYamlFieldMap)
        .toBuilder()
        .putDependencyMetadata(serviceDefUuid, serviceDefDependency)
        .build();
  }
}
