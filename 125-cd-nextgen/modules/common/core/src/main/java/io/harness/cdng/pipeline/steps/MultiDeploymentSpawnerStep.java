/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterHelper;
import io.harness.cdng.environment.helper.EnvironmentStepsUtils;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsMetadata;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.environment.yaml.ServiceOverrideInputsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.pipeline.beans.MultiDeploymentStepParameters;
import io.harness.cdng.pipeline.steps.EnvironmentMapResponse.EnvironmentMapResponseBuilder;
import io.harness.cdng.service.NGServiceEntityHelper;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesMetadata;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.utils.ParameterFieldUtils;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.steps.executable.ChildrenExecutableWithRollbackAndRbac;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MultiDeploymentSpawnerStep extends ChildrenExecutableWithRollbackAndRbac<MultiDeploymentStepParameters> {
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private EnvironmentInfraFilterHelper environmentInfraFilterHelper;
  @Inject private NGServiceEntityHelper serviceEntityHelper;
  @Inject private AccessControlClient accessControlClient;
  @Inject private EnvironmentGroupService environmentGroupService;
  @Inject SdkGraphVisualizationDataService sdkGraphVisualizationDataService;

  public static final List<String> SKIP_KEYS_LIST_FROM_STAGE_NAME =
      Arrays.asList("environmentInputs", "serviceInputs", "infraInputs");
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType("multiDeployment").setStepCategory(StepCategory.STRATEGY).build();

  public static final String SVC_ENV_COUNT = "svcEnvCount";

  @Override
  public StepResponse handleChildrenResponseInternal(
      Ambiance ambiance, MultiDeploymentStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Completed  execution for MultiDeploymentSpawner Step [{}]", stepParameters);
    // Mark the status as Skipped if all children are skipped.
    if (StatusUtils.checkIfAllChildrenSkipped(responseDataMap.values()
                                                  .stream()
                                                  .filter(o -> o instanceof StepResponseNotifyData)
                                                  .map(o -> ((StepResponseNotifyData) o).getStatus())
                                                  .collect(Collectors.toList()))) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }
    return createStepResponseFromChildResponse(responseDataMap);
  }

  @Override
  public Class<MultiDeploymentStepParameters> getStepParametersClass() {
    return MultiDeploymentStepParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, MultiDeploymentStepParameters stepParameters) {
    if (stepParameters.getEnvironmentGroup() != null && stepParameters.getEnvironmentGroup().getEnvGroupRef() != null) {
      final ParameterField<String> envGroupRef = stepParameters.getEnvironmentGroup().getEnvGroupRef();
      if (envGroupRef.isExpression()) {
        throw new UnresolvedExpressionsException(List.of(envGroupRef.getExpressionValue()));
      }
      if (isNotBlank(envGroupRef.getValue())) {
        String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
        String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
        String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

        Optional<EnvironmentGroupEntity> environmentGroupEntity = environmentGroupService.get(
            accountIdentifier, orgIdentifier, projectIdentifier, envGroupRef.getValue(), false);
        if (environmentGroupEntity.isEmpty()) {
          throw new InvalidRequestException(
              String.format("Could not find environment group with identifier: %s", envGroupRef.getValue()));
        }

        IdentifierRef envGroupIdentifierRef = IdentifierRefHelper.getIdentifierRef(
            envGroupRef.getValue(), accountIdentifier, orgIdentifier, projectIdentifier);

        final ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
        final String principal = executionPrincipalInfo.getPrincipal();
        if (isEmpty(principal)) {
          return;
        }

        io.harness.accesscontrol.principals.PrincipalType principalType =
            PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
                executionPrincipalInfo.getPrincipalType());
        accessControlClient.checkForAccessOrThrow(Principal.of(principalType, principal),
            ResourceScope.of(envGroupIdentifierRef.getAccountIdentifier(), envGroupIdentifierRef.getOrgIdentifier(),
                envGroupIdentifierRef.getProjectIdentifier()),
            Resource.of(NGResourceType.ENVIRONMENT_GROUP, envGroupIdentifierRef.getIdentifier()),
            CDNGRbacPermissions.ENVIRONMENT_GROUP_RUNTIME_PERMISSION,
            format("Validation for runtime access to environmentGroup: [%s] failed", envGroupRef.getValue()));
      }
    }
  }

  @Override
  public ChildrenExecutableResponse obtainChildrenAfterRbac(
      Ambiance ambiance, MultiDeploymentStepParameters stepParameters, StepInputPackage inputPackage) {
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    List<Map<String, String>> servicesMap = getServicesMap(stepParameters.getServices());

    List<EnvironmentMapResponse> environmentsMapList = new ArrayList<>();

    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    String childNodeId = stepParameters.getChildNodeId();

    // Separate handling as parallelism works differently when filters are present with service.tags expression
    if (environmentInfraFilterHelper.isServiceTagsExpressionPresent(stepParameters.getEnvironments())
        || environmentInfraFilterHelper.isServiceTagsExpressionPresent(stepParameters.getEnvironmentGroup())) {
      return getChildrenExecutableResponse(
          ambiance, stepParameters, children, accountIdentifier, orgIdentifier, projectIdentifier, childNodeId);
    }

    environmentInfraFilterHelper.processEnvInfraFiltering(accountIdentifier, orgIdentifier, projectIdentifier,
        stepParameters.getEnvironments(), stepParameters.getEnvironmentGroup(), stepParameters.getDeploymentType());
    if (stepParameters.getEnvironments() != null) {
      environmentsMapList = getEnvironmentMapList(stepParameters.getEnvironments());
    } else if (stepParameters.getEnvironmentGroup() != null) {
      environmentsMapList = getEnvironmentsGroupMap(stepParameters.getEnvironmentGroup());
    }

    if (servicesMap.isEmpty()) {
      // This case is when the deployment is of type single service multiple environment/infras
      publishSvcEnvCount(ambiance, 1, MultiDeploymentSpawnerUtils.getEnvCount(environmentsMapList));
      return getChildrenExecutionResponseForMultiEnvironment(
          stepParameters, children, environmentsMapList, childNodeId);
    }

    if (environmentsMapList.isEmpty()) {
      List<ServiceOverrideInputsYaml> servicesOverrides = stepParameters.getServicesOverrides();
      Map<String, Map<String, Object>> serviceRefToOverrides = new HashMap<>();
      if (servicesOverrides != null) {
        serviceRefToOverrides =
            servicesOverrides.stream().collect(Collectors.toMap(ServiceOverrideInputsYaml::getServiceRef,
                overrideInput -> overrideInput.getServiceOverrideInputs().getValue()));
      }
      int currentIteration = 0;
      int totalIterations = servicesMap.size();
      int maxConcurrency = 0;
      if (stepParameters.getServices().getServicesMetadata() != null
          && ParameterField.isNotNull(stepParameters.getServices().getServicesMetadata().getParallel())) {
        if (stepParameters.getServices().getServicesMetadata().getParallel().isExpression()) {
          throw new InvalidYamlException("services parallel value could not be resolved: "
              + stepParameters.getServices().getServicesMetadata().getParallel().getExpressionValue());
        }
        if (!ParameterFieldUtils.getBooleanValue(stepParameters.getServices().getServicesMetadata().getParallel())) {
          maxConcurrency = 1;
        }
      }
      for (Map<String, String> serviceMap : servicesMap) {
        String serviceRef = MultiDeploymentSpawnerUtils.getServiceRef(serviceMap);
        if (serviceRefToOverrides.containsKey(serviceRef)) {
          MultiDeploymentSpawnerUtils.addServiceOverridesToMap(serviceMap, serviceRefToOverrides.get(serviceRef));
        }
        children.add(getChild(childNodeId, currentIteration, totalIterations, serviceMap,
            MultiDeploymentSpawnerUtils.MULTI_SERVICE_DEPLOYMENT));
        currentIteration++;
      }
      publishSvcEnvCount(ambiance, servicesMap.size(), 1);
      return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
    }

    publishSvcEnvCount(ambiance, servicesMap.size(), MultiDeploymentSpawnerUtils.getEnvCount(environmentsMapList));

    boolean isServiceParallel = stepParameters.getServices() != null
        && shouldDeployInParallel(stepParameters.getServices().getServicesMetadata());
    boolean isEnvironmentParallel = stepParameters.getEnvironmentGroup() != null
        || (stepParameters.getEnvironments() != null
            && shouldDeployInParallel(stepParameters.getEnvironments().getEnvironmentsMetadata()));

    int currentIteration = 0;
    int totalIterations = servicesMap.size() * environmentsMapList.size();
    int maxConcurrency = 0;
    if (isServiceParallel) {
      if (!isEnvironmentParallel) {
        maxConcurrency = servicesMap.size();
      } else {
        maxConcurrency = totalIterations;
      }
      for (EnvironmentMapResponse environmentMap : environmentsMapList) {
        for (Map<String, String> serviceMap : servicesMap) {
          children.add(
              getChildForMultiServiceInfra(childNodeId, currentIteration, totalIterations, serviceMap, environmentMap));
          currentIteration++;
        }
      }
    } else if (isEnvironmentParallel) {
      maxConcurrency = environmentsMapList.size();
      for (Map<String, String> serviceMap : servicesMap) {
        for (EnvironmentMapResponse environmentMap : environmentsMapList) {
          children.add(
              getChildForMultiServiceInfra(childNodeId, currentIteration, totalIterations, serviceMap, environmentMap));
          currentIteration++;
        }
      }
    } else {
      maxConcurrency = 1;
      for (EnvironmentMapResponse environmentMap : environmentsMapList) {
        for (Map<String, String> serviceMap : servicesMap) {
          children.add(
              getChildForMultiServiceInfra(childNodeId, currentIteration, totalIterations, serviceMap, environmentMap));
          currentIteration++;
        }
      }
    }
    return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
  }

  private ChildrenExecutableResponse getChildrenExecutionResponseForMultiEnvironment(
      MultiDeploymentStepParameters stepParameters, List<ChildrenExecutableResponse.Child> children,
      List<EnvironmentMapResponse> environmentsMapList, String childNodeId) {
    int currentIteration = 0;
    int totalIterations = environmentsMapList.size();
    int maxConcurrency = 0;
    if (isEnvironmentSeries(stepParameters)) {
      maxConcurrency = 1;
    }
    for (EnvironmentMapResponse environmentMapResponse : environmentsMapList) {
      Map<String, String> environmentMap = environmentMapResponse.getEnvironmentsMapList();
      if (environmentMapResponse.getServiceOverrideInputsYamlMap() != null
          && environmentMapResponse.getServiceOverrideInputsYamlMap().size() > 1) {
        throw new InvalidYamlException(
            "Found more than one service in overrides for a single service deployment. Please correct the yaml and try");
      }
      if (EmptyPredicate.isNotEmpty(environmentMapResponse.getServiceOverrideInputsYamlMap())) {
        MultiDeploymentSpawnerUtils.addServiceOverridesToMap(environmentMap,
            environmentMapResponse.getServiceOverrideInputsYamlMap()
                .entrySet()
                .iterator()
                .next()
                .getValue()
                .getServiceOverrideInputs()
                .getValue());
      }
      children.add(getChild(childNodeId, currentIteration, totalIterations, environmentMap,
          MultiDeploymentSpawnerUtils.MULTI_ENV_DEPLOYMENT));
      currentIteration++;
    }
    return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
  }

  private boolean isEnvironmentSeries(MultiDeploymentStepParameters stepParameters) {
    return stepParameters.getEnvironments() != null
        && stepParameters.getEnvironments().getEnvironmentsMetadata() != null
        && stepParameters.getEnvironments().getEnvironmentsMetadata().getParallel() != null
        && !stepParameters.getEnvironments().getEnvironmentsMetadata().getParallel()
        || stepParameters.getEnvironmentGroup() != null
        && stepParameters.getEnvironmentGroup().getEnvironmentGroupMetadata() != null
        && stepParameters.getEnvironmentGroup().getEnvironmentGroupMetadata().getParallel() != null
        && !stepParameters.getEnvironmentGroup().getEnvironmentGroupMetadata().getParallel();
  }

  private ChildrenExecutableResponse getChildrenExecutableResponse(Ambiance ambiance,
      MultiDeploymentStepParameters stepParameters, List<ChildrenExecutableResponse.Child> children,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String childNodeId) {
    Map<String, Map<String, String>> serviceToMatrixMetadataMap =
        getServiceToMatrixMetadataMap(stepParameters.getServices());
    // Find service tags
    Map<String, List<NGTag>> serviceTagsMap = serviceEntityHelper.getServiceTags(accountIdentifier, orgIdentifier,
        projectIdentifier, stepParameters.getServices(), stepParameters.getServiceYamlV2());

    // Parse the yaml and set the FilterYaml tag value
    Map<String, EnvironmentsYaml> serviceEnvYamlMap =
        getServiceToEnvsYaml(serviceTagsMap, stepParameters.getEnvironments());

    Map<String, EnvironmentGroupYaml> serviceEnvGroupMap =
        getServiceToEnvGroup(serviceTagsMap, stepParameters.getEnvironmentGroup());

    Map<String, List<EnvironmentMapResponse>> serviceEnvMatrixMap = new LinkedHashMap<>();
    for (String serviceRef : serviceTagsMap.keySet()) {
      EnvironmentsYaml environmentsYaml = serviceEnvYamlMap.get(serviceRef);
      EnvironmentGroupYaml environmentGroupYaml = serviceEnvGroupMap.get(serviceRef);
      environmentInfraFilterHelper.processEnvInfraFiltering(accountIdentifier, orgIdentifier, projectIdentifier,
          environmentsYaml, environmentGroupYaml, stepParameters.getDeploymentType());
      List<EnvironmentMapResponse> environmentMapList;
      if (environmentsYaml != null) {
        environmentMapList = getEnvironmentMapList(environmentsYaml);
      } else if (environmentGroupYaml != null) {
        environmentMapList = getEnvironmentsGroupMap(environmentGroupYaml);
      } else {
        throw new InvalidRequestException("No environments found for service: " + serviceRef);
      }
      serviceEnvMatrixMap.put(serviceRef, environmentMapList);
    }
    // publish Service count for Stage Graph, Product Spec needed for displaying Env count
    publishSvcEnvCount(ambiance, serviceEnvMatrixMap.size(), 1);

    int maxConcurrency = 0;
    // If Both service and env are non-parallel
    if (stepParameters.getServices() != null && stepParameters.getServices().getServicesMetadata() != null
        && ParameterField.isNotNull(stepParameters.getServices().getServicesMetadata().getParallel())
        && !stepParameters.getServices().getServicesMetadata().getParallel().getValue()
        && ((stepParameters.getEnvironments() != null
                && stepParameters.getEnvironments().getEnvironmentsMetadata() != null
                && stepParameters.getEnvironments().getEnvironmentsMetadata().getParallel() != null
                && !stepParameters.getEnvironments().getEnvironmentsMetadata().getParallel())
            || (stepParameters.getEnvironmentGroup() != null
                && stepParameters.getEnvironmentGroup().getEnvironmentGroupMetadata() != null
                && stepParameters.getEnvironmentGroup().getEnvironmentGroupMetadata().getParallel() != null
                && !stepParameters.getEnvironmentGroup().getEnvironmentGroupMetadata().getParallel()))) {
      maxConcurrency = 1;
    }
    int totalIterations = 0;
    for (Map.Entry<String, List<EnvironmentMapResponse>> serviceEnv : serviceEnvMatrixMap.entrySet()) {
      totalIterations += serviceEnv.getValue().size();
    }

    int currentIteration = 0;
    for (Map.Entry<String, List<EnvironmentMapResponse>> serviceEnv : serviceEnvMatrixMap.entrySet()) {
      String serviceRef = serviceEnv.getKey();
      for (EnvironmentMapResponse envMap : serviceEnv.getValue()) {
        Map<String, String> serviceMatrixMetadata =
            CollectionUtils.emptyIfNull(serviceToMatrixMetadataMap.get(serviceRef));
        children.add(getChildForMultiServiceInfra(
            childNodeId, currentIteration++, totalIterations, serviceMatrixMetadata, envMap));
      }
    }
    return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
  }

  private Map<String, Map<String, String>> getServiceToMatrixMetadataMap(ServicesYaml servicesYaml) {
    if (servicesYaml == null) {
      return new LinkedHashMap<>();
    }
    if (ParameterField.isNull(servicesYaml.getValues())) {
      throw new InvalidYamlException("Expected a value of serviceRefs to be provided but found null");
    }
    if (servicesYaml.getValues().isExpression()) {
      throw new InvalidYamlException("Expression could not be resolved for services yaml");
    }
    List<ServiceYamlV2> services = servicesYaml.getValues().getValue();
    if (services.isEmpty()) {
      throw new InvalidYamlException("No value of services provided. Please provide atleast one value");
    }
    Map<String, Map<String, String>> serviceToMatrixMetadataMap = new LinkedHashMap<>();
    for (ServiceYamlV2 service : services) {
      serviceToMatrixMetadataMap.put(
          service.getServiceRef().getValue(), MultiDeploymentSpawnerUtils.getMapFromServiceYaml(service));
    }
    return serviceToMatrixMetadataMap;
  }

  private Map<String, EnvironmentGroupYaml> getServiceToEnvGroup(
      Map<String, List<NGTag>> serviceTagsMap, EnvironmentGroupYaml environmentGroup) {
    Map<String, EnvironmentGroupYaml> serviceEnvGroupMap = new LinkedHashMap<>();

    if (environmentGroup == null) {
      return serviceEnvGroupMap;
    }
    for (Map.Entry<String, List<NGTag>> serviceTag : serviceTagsMap.entrySet()) {
      EnvironmentGroupYaml envGroupPerService = environmentGroup.clone();

      if (ParameterField.isNotNull(envGroupPerService.getFilters())) {
        ParameterField<List<FilterYaml>> filters = envGroupPerService.getFilters();
        environmentInfraFilterHelper.resolveServiceTags(filters, serviceTag.getValue());
      }

      ParameterField<List<EnvironmentYamlV2>> environments = envGroupPerService.getEnvironments();
      if (ParameterField.isNotNull(environments) && isNotEmpty(environments.getValue())) {
        resolveServiceTags(environments.getValue(), serviceTag.getValue());
      }
      serviceEnvGroupMap.put(serviceTag.getKey(), envGroupPerService);
    }

    return serviceEnvGroupMap;
  }

  private Map<String, EnvironmentsYaml> getServiceToEnvsYaml(
      Map<String, List<NGTag>> serviceTagsMap, EnvironmentsYaml environments) {
    Map<String, EnvironmentsYaml> serviceEnvMap = new LinkedHashMap<>();

    if (environments == null) {
      return serviceEnvMap;
    }
    for (Map.Entry<String, List<NGTag>> serviceTag : serviceTagsMap.entrySet()) {
      EnvironmentsYaml environmentsYamlPerService = environments.clone();

      if (ParameterField.isNotNull(environmentsYamlPerService.getFilters())) {
        ParameterField<List<FilterYaml>> filters = environmentsYamlPerService.getFilters();
        environmentInfraFilterHelper.resolveServiceTags(filters, serviceTag.getValue());
      }

      ParameterField<List<EnvironmentYamlV2>> values = environmentsYamlPerService.getValues();
      if (ParameterField.isNotNull(values) && isNotEmpty(values.getValue())) {
        resolveServiceTags(values.getValue(), serviceTag.getValue());
      }
      serviceEnvMap.put(serviceTag.getKey(), environmentsYamlPerService);
    }

    return serviceEnvMap;
  }

  private void resolveServiceTags(List<EnvironmentYamlV2> environmentYamlV2s, List<NGTag> serviceTags) {
    if (isEmpty(environmentYamlV2s)) {
      return;
    }
    for (EnvironmentYamlV2 environmentYamlV2 : environmentYamlV2s) {
      ParameterField<List<FilterYaml>> filters = environmentYamlV2.getFilters();
      environmentInfraFilterHelper.resolveServiceTags(filters, serviceTags);
    }
  }

  private ChildrenExecutableResponse.Child getChild(
      String childNodeId, int currentIteration, int totalIterations, Map<String, String> serviceMap, String subType) {
    return ChildrenExecutableResponse.Child.newBuilder()
        .setChildNodeId(childNodeId)
        .setStrategyMetadata(StrategyMetadata.newBuilder()
                                 .setCurrentIteration(currentIteration)
                                 .setTotalIterations(totalIterations)
                                 .setMatrixMetadata(MatrixMetadata.newBuilder()
                                                        .setSubType(subType)
                                                        .addMatrixCombination(currentIteration)
                                                        .addAllMatrixKeysToSkipInName(SKIP_KEYS_LIST_FROM_STAGE_NAME)
                                                        .putAllMatrixValues(serviceMap)
                                                        .build())
                                 .build())
        .build();
  }

  private boolean shouldDeployInParallel(EnvironmentsMetadata metadata) {
    // If metadata is not provided, we assume parallel by default.
    return metadata == null || Boolean.TRUE == metadata.getParallel();
  }

  private boolean shouldDeployInParallel(ServicesMetadata metadata) {
    // If metadata is not provided, we assume parallel by default.
    return metadata == null || ParameterFieldUtils.getBooleanValue(metadata.getParallel());
  }

  private ChildrenExecutableResponse.Child getChildForMultiServiceInfra(String childNodeId, int currentIteration,
      int totalIterations, Map<String, String> serviceMap, EnvironmentMapResponse environmentMapResponse) {
    Map<String, String> matrixMetadataMap = new HashMap<>();
    matrixMetadataMap.putAll(serviceMap);
    Map<String, String> environmentMap = environmentMapResponse.getEnvironmentsMapList();
    String serviceRef = MultiDeploymentSpawnerUtils.getServiceRef(serviceMap);
    if (environmentMapResponse.getServiceOverrideInputsYamlMap() != null
        && environmentMapResponse.getServiceOverrideInputsYamlMap().containsKey(serviceRef)) {
      MultiDeploymentSpawnerUtils.addServiceOverridesToMap(environmentMap,
          environmentMapResponse.getServiceOverrideInputsYamlMap()
              .get(serviceRef)
              .getServiceOverrideInputs()
              .getValue());
    }
    matrixMetadataMap.putAll(environmentMap);
    String subType;
    if (environmentMap.isEmpty()) {
      subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_DEPLOYMENT;
    } else if (serviceMap.isEmpty()) {
      subType = MultiDeploymentSpawnerUtils.MULTI_ENV_DEPLOYMENT;
    } else {
      subType = MultiDeploymentSpawnerUtils.MULTI_SERVICE_ENV_DEPLOYMENT;
    }
    return getChild(childNodeId, currentIteration, totalIterations, matrixMetadataMap, subType);
  }

  private List<EnvironmentMapResponse> getEnvironmentMapList(EnvironmentsYaml environmentsYaml) {
    if (environmentsYaml == null) {
      return new ArrayList<>();
    }
    if (ParameterField.isNull(environmentsYaml.getValues())) {
      throw new InvalidYamlException("Expected a value of serviceRefs to be provided but found null");
    }
    if (environmentsYaml.getValues().isExpression()) {
      throw new InvalidYamlException("Expression could not be resolved for environments yaml");
    }
    List<EnvironmentYamlV2> environments = environmentsYaml.getValues().getValue();
    return getEnvironmentsMap(environments, null);
  }

  private List<EnvironmentMapResponse> getEnvironmentsGroupMap(EnvironmentGroupYaml environmentGroupYaml) {
    if (environmentGroupYaml.getEnvironments().isExpression()) {
      throw new InvalidYamlException("Expected a value of environmentRefs to be provided but found expression");
    }
    List<EnvironmentYamlV2> environments = environmentGroupYaml.getEnvironments().getValue();
    if (EmptyPredicate.isEmpty(environments)) {
      throw new InvalidYamlException("Expected a value of environmentRefs to be provided but found empty");
    }
    return getEnvironmentsMap(environments,
        EnvironmentStepsUtils.getScopeForRef((String) environmentGroupYaml.getEnvGroupRef().fetchFinalValue()));
  }

  private List<EnvironmentMapResponse> getEnvironmentsMap(List<EnvironmentYamlV2> environments, Scope envGroupScope) {
    if (EmptyPredicate.isEmpty(environments)) {
      throw new InvalidYamlException("No value of environment provided. Please provide atleast one value");
    }
    List<EnvironmentMapResponse> environmentMapResponses = new ArrayList<>();
    for (EnvironmentYamlV2 environmentYamlV2 : environments) {
      if (ParameterField.isNull(environmentYamlV2.getInfrastructureDefinitions())) {
        environmentMapResponses.add(getEnvironmentsMapResponse(
            environmentYamlV2, environmentYamlV2.getInfrastructureDefinition().getValue(), envGroupScope));
      } else {
        if (environmentYamlV2.getInfrastructureDefinitions().getValue() == null) {
          throw new InvalidYamlException("No infrastructure definition provided. Please provide atleast one value");
        }
        for (InfraStructureDefinitionYaml infra : environmentYamlV2.getInfrastructureDefinitions().getValue()) {
          environmentMapResponses.add(getEnvironmentsMapResponse(environmentYamlV2, infra, envGroupScope));
        }
      }
    }
    return environmentMapResponses;
  }

  private EnvironmentMapResponse getEnvironmentsMapResponse(
      EnvironmentYamlV2 environmentYamlV2, InfraStructureDefinitionYaml infra, Scope envGroupScope) {
    EnvironmentMapResponseBuilder environmentMapResponseBuilder = EnvironmentMapResponse.builder();

    environmentMapResponseBuilder.environmentsMapList(
        MultiDeploymentSpawnerUtils.getMapFromEnvironmentYaml(environmentYamlV2, infra, envGroupScope));
    if (EmptyPredicate.isNotEmpty(environmentYamlV2.getServicesOverrides())) {
      Map<String, ServiceOverrideInputsYaml> serviceRefToServiceOverrides = new HashMap<>();
      for (ServiceOverrideInputsYaml serviceOverrideInputsYaml : environmentYamlV2.getServicesOverrides()) {
        serviceRefToServiceOverrides.put(serviceOverrideInputsYaml.getServiceRef(), serviceOverrideInputsYaml);
      }
      environmentMapResponseBuilder.serviceOverrideInputsYamlMap(serviceRefToServiceOverrides);
    }
    return environmentMapResponseBuilder.build();
  }

  private List<Map<String, String>> getServicesMap(ServicesYaml servicesYaml) {
    if (servicesYaml == null) {
      return new ArrayList<>();
    }
    if (ParameterField.isNull(servicesYaml.getValues())) {
      throw new InvalidYamlException("Expected a value of serviceRefs to be provided but found null");
    }
    if (servicesYaml.getValues().isExpression()) {
      throw new InvalidYamlException("Expression could not be resolved for services yaml");
    }
    List<ServiceYamlV2> services = servicesYaml.getValues().getValue();
    if (services.isEmpty()) {
      throw new InvalidYamlException("No value of services provided. Please provide atleast one value");
    }
    List<Map<String, String>> environmentsMap = new ArrayList<>();
    for (ServiceYamlV2 service : services) {
      environmentsMap.add(MultiDeploymentSpawnerUtils.getMapFromServiceYaml(service));
    }
    return environmentsMap;
  }

  private void publishSvcEnvCount(Ambiance ambiance, int svcCount, int envCount) {
    // publish Service and Env count for Stage Graph
    MultiDeploymentSpawnerStepDetailsInfo multiDeploymentSpawnerStepDetailsInfo =
        MultiDeploymentSpawnerStepDetailsInfo.builder().svcCount(svcCount).envCount(envCount).build();
    sdkGraphVisualizationDataService.publishStepDetailInformation(
        ambiance, multiDeploymentSpawnerStepDetailsInfo, SVC_ENV_COUNT, StepCategory.STRATEGY);
  }
}
