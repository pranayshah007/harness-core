/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.servicehook;

import static io.harness.cdng.hooks.ServiceHookConstants.POST_HOOK;
import static io.harness.cdng.hooks.ServiceHookConstants.PRE_HOOK;
import static io.harness.cdng.utilities.ServiceHookUtility.fetchIndividualServiceHookYamlField;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.hooks.ServiceHook;
import io.harness.cdng.hooks.ServiceHookType;
import io.harness.cdng.hooks.ServiceHookWrapper;
import io.harness.cdng.hooks.ServiceHooks;
import io.harness.cdng.hooks.steps.ServiceHookStepParameters;
import io.harness.cdng.hooks.steps.ServiceHookStepParameters.ServiceHookStepParametersBuilder;
import io.harness.cdng.hooks.steps.ServiceHooksStep;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.NativeHelmServiceSpec;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.fork.ForkStepParameters;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.CDP)
public class ServiceHooksPlanCreator extends ChildrenPlanCreator<ServiceHooks> {
  @Inject KryoSerializer kryoSerializer;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, ServiceHooks serviceHook) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    if (ctx.getDependency().getMetadataMap().containsKey(YamlTypes.SERVICE_CONFIG)) {
      // v1
      ServiceConfig serviceConfig = (ServiceConfig) kryoSerializer.asInflatedObject(
          ctx.getDependency().getMetadataMap().get(YamlTypes.SERVICE_CONFIG).toByteArray());

      ServiceHookList serviceHookList =
          new ServiceHookListBuilder().addServiceDefinition(serviceConfig.getServiceDefinition()).build();

      if (isEmpty(serviceHookList.getServiceHooks())) {
        return planCreationResponseMap;
      }

      YamlField serviceHooksYamlField = ctx.getCurrentField();

      for (Map.Entry<String, ServiceHookStepParameters> identifierToServiceHookStepParametersEntry :
          serviceHookList.getServiceHooks().entrySet()) {
        addDependenciesForIndividualServiceHook(identifierToServiceHookStepParametersEntry.getKey(),
            identifierToServiceHookStepParametersEntry.getValue(), serviceHooksYamlField, planCreationResponseMap);
      }
    } else if (ctx.getDependency().getMetadataMap().containsKey(YamlTypes.SERVICE_HOOKS)) {
      List<ServiceHookWrapper> serviceHooks = (List<ServiceHookWrapper>) kryoSerializer.asInflatedObject(
          ctx.getDependency().getMetadataMap().get(YamlTypes.SERVICE_HOOKS).toByteArray());
      ServiceHookList serviceHookList = new ServiceHookListBuilder().addServiceHooks(serviceHooks).build();
      if (isEmpty(serviceHookList.getServiceHooks())) {
        return planCreationResponseMap;
      }

      YamlField serviceHooksYamlField = ctx.getCurrentField();

      for (Map.Entry<String, ServiceHookStepParameters> identifierToServiceHookStepParametersEntry :
          serviceHookList.getServiceHooks().entrySet()) {
        addDependenciesForIndividualServiceHook(identifierToServiceHookStepParametersEntry.getKey(),
            identifierToServiceHookStepParametersEntry.getValue(), serviceHooksYamlField, planCreationResponseMap);
      }
    }

    else if (ctx.getDependency().getMetadataMap().containsKey(YamlTypes.SERVICE_ENTITY)) {
      // v2
      NGServiceV2InfoConfig serviceV2InfoConfig = (NGServiceV2InfoConfig) kryoSerializer.asInflatedObject(
          ctx.getDependency().getMetadataMap().get(YamlTypes.SERVICE_ENTITY).toByteArray());
      ServiceHookList serviceHookList =
          new ServiceHookListBuilder().addServiceDefinition(serviceV2InfoConfig.getServiceDefinition()).build();

      if (isEmpty(serviceHookList.getServiceHooks())) {
        return planCreationResponseMap;
      }

      YamlField serviceHooksYamlField = ctx.getCurrentField();

      for (Map.Entry<String, ServiceHookStepParameters> identifierToServiceHookStepParametersEntry :
          serviceHookList.getServiceHooks().entrySet()) {
        addDependenciesForIndividualServiceHook(identifierToServiceHookStepParametersEntry.getKey(),
            identifierToServiceHookStepParametersEntry.getValue(), serviceHooksYamlField, planCreationResponseMap);
      }
    }

    return planCreationResponseMap;
  }

  public void addDependenciesForIndividualServiceHook(final String serviceHookIdentifier,
      ServiceHookStepParameters stepParameters, YamlField serviceHooksYamlField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap) {
    YamlField individualServiceHookYamlField =
        fetchIndividualServiceHookYamlField(serviceHookIdentifier, serviceHooksYamlField);
    // To create a utility class
    String individualServiceHookPlanNodeId = UUIDGenerator.generateUuid();

    PlanCreationResponse individualServiceHookPlanResponse =
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils
                              .toDependenciesProto(
                                  getDependencies(individualServiceHookPlanNodeId, individualServiceHookYamlField))
                              .toBuilder()
                              .putDependencyMetadata(individualServiceHookPlanNodeId,
                                  getDependencyMetadata(individualServiceHookPlanNodeId, stepParameters))
                              .build())
            .build();

    planCreationResponseMap.put(individualServiceHookPlanNodeId, individualServiceHookPlanResponse);
  }

  private Map<String, YamlField> getDependencies(
      final String individualServiceHookPlanNodeId, YamlField individualServiceHook) {
    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(individualServiceHookPlanNodeId, individualServiceHook);
    return dependenciesMap;
  }

  private Dependency getDependencyMetadata(
      final String individualServiceHookPlanNodeId, ServiceHookStepParameters stepParameters) {
    Map<String, ByteString> metadataDependency =
        prepareMetadataForIndividualServiceHookPlanCreator(individualServiceHookPlanNodeId, stepParameters);
    return Dependency.newBuilder().putAllMetadata(metadataDependency).build();
  }

  public Map<String, ByteString> prepareMetadataForIndividualServiceHookPlanCreator(
      String individualServiceHookPlanNodeId, ServiceHookStepParameters stepParameters) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(
        YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(individualServiceHookPlanNodeId)));
    metadataDependency.put(PlanCreatorConstants.SERVICE_HOOK_STEP_PARAMETER,
        ByteString.copyFrom(kryoSerializer.asDeflatedBytes(stepParameters)));
    return metadataDependency;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, ServiceHooks serviceHooks, List<String> childrenNodeIds) {
    String serviceHooksId = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.UUID).toByteArray());

    ForkStepParameters stepParameters = ForkStepParameters.builder().parallelNodeIds(childrenNodeIds).build();
    return PlanNode.builder()
        .uuid(serviceHooksId)
        .stepType(ServiceHooksStep.STEP_TYPE)
        .name(PlanCreatorConstants.SERVICE_HOOKS_NODE_NAME)
        .identifier(YamlTypes.SERVICE_HOOKS)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                .build())
        .skipExpressionChain(false)
        .build();
  }

  @Override
  public Class<ServiceHooks> getFieldClass() {
    return ServiceHooks.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.SERVICE_HOOKS, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class ServiceHookListBuilder {
    Map<String, ServiceHookStepParametersBuilder> serviceHookStepParametersBuilder;

    ServiceHookListBuilder() {
      this.serviceHookStepParametersBuilder = Collections.emptyMap();
    }

    public ServiceHookListBuilder addServiceDefinition(ServiceDefinition serviceDefinition) {
      if (serviceDefinition.getServiceSpec() instanceof KubernetesServiceSpec) {
        List<ServiceHookWrapper> serviceHookWrappers =
            ((KubernetesServiceSpec) serviceDefinition.getServiceSpec()).getServiceHooks();
        return addServiceHooks(serviceHookWrappers);
      }
      if (serviceDefinition.getServiceSpec() instanceof NativeHelmServiceSpec) {
        List<ServiceHookWrapper> serviceHookWrappers =
            ((NativeHelmServiceSpec) serviceDefinition.getServiceSpec()).getServiceHooks();
        return addServiceHooks(serviceHookWrappers);
      }
      return null;
    }

    public ServiceHookListBuilder addServiceHooks(List<ServiceHookWrapper> serviceHookWrappers) {
      if (serviceHookWrappers == null) {
        return this;
      }

      this.serviceHookStepParametersBuilder = new HashMap<>();
      if (EmptyPredicate.isNotEmpty(serviceHookWrappers)) {
        serviceHookWrappers.forEach(serviceHookWrapper -> {
          String hookType = null;
          ServiceHook serviceHook;
          if (serviceHookWrapper.getPreHook() != null) {
            hookType = PRE_HOOK;
            serviceHook = serviceHookWrapper.getPreHook();
          } else if (serviceHookWrapper.getPostHook() != null) {
            hookType = POST_HOOK;
            serviceHook = serviceHookWrapper.getPostHook();
          } else {
            throw new InvalidRequestException("Unknown Hook Type. Can be either preHook or postHook");
          }
          if (serviceHookStepParametersBuilder.containsKey(serviceHook.getIdentifier())) {
            throw new InvalidRequestException(
                String.format("Duplicate identifier: [%s] in ServiceHooks", serviceHook.getIdentifier()));
          }

          serviceHookStepParametersBuilder.put(serviceHook.getIdentifier(),
              ServiceHookStepParameters.builder()
                  .identifier(serviceHook.getIdentifier())
                  .actions(serviceHook.getActions())
                  .store(serviceHook.getStore())
                  .storetype(serviceHook.getStoretype())
                  .type(ServiceHookType.getHookType(hookType))
                  .order(serviceHookStepParametersBuilder.size()));
        });
      }
      return this;
    }

    public ServiceHookList build() {
      return new ServiceHookList(serviceHookStepParametersBuilder.entrySet().stream().collect(
          Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build())));
    }
  }

  @Value
  private static class ServiceHookList {
    Map<String, ServiceHookStepParameters> serviceHooks;
  }
}
