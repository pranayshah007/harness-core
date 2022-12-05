/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsMetadata;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.pipeline.beans.MultiDeploymentStepParameters;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesMetadata;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.executable.ChildrenExecutableWithRollbackAndRbac;
import io.harness.tasks.ResponseData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MultiDeploymentSpawnerStep extends ChildrenExecutableWithRollbackAndRbac<MultiDeploymentStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType("multiDeployment").setStepCategory(StepCategory.STRATEGY).build();

  @Override
  public StepResponse handleChildrenResponseInternal(
      Ambiance ambiance, MultiDeploymentStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Completed  execution for MultiDeploymentSpawner Step [{}]", stepParameters);
    return createStepResponseFromChildResponse(responseDataMap);
  }

  @Override
  public Class<MultiDeploymentStepParameters> getStepParametersClass() {
    return MultiDeploymentStepParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, MultiDeploymentStepParameters stepParameters) {
    // Do Nothing
  }

  @Override
  public ChildrenExecutableResponse obtainChildrenAfterRbac(
      Ambiance ambiance, MultiDeploymentStepParameters stepParameters, StepInputPackage inputPackage) {
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    List<Map<String, String>> servicesMap = getServicesMap(stepParameters.getServices());
    List<Map<String, String>> environmentsMapList = new ArrayList<>();
    if (stepParameters.getEnvironments() != null) {
      environmentsMapList = getEnvironmentMapList(stepParameters.getEnvironments());
    } else if (stepParameters.getEnvironmentGroup() != null) {
      environmentsMapList = getEnvironmentsGroupMap(stepParameters.getEnvironmentGroup());
    }
    String childNodeId = stepParameters.getChildNodeId();
    if (servicesMap.isEmpty()) {
      int currentIteration = 0;
      int totalIterations = environmentsMapList.size();
      int maxConcurrency = 0;
      if (stepParameters.getEnvironments() != null && stepParameters.getEnvironments().getEnvironmentsMetadata() != null
              && stepParameters.getEnvironments().getEnvironmentsMetadata().getParallel() != null
              && !stepParameters.getEnvironments().getEnvironmentsMetadata().getParallel()
          || stepParameters.getEnvironmentGroup() != null
              && stepParameters.getEnvironmentGroup().getEnvironmentGroupMetadata() != null
              && stepParameters.getEnvironmentGroup().getEnvironmentGroupMetadata().getParallel() != null
              && !stepParameters.getEnvironmentGroup().getEnvironmentGroupMetadata().getParallel()) {
        maxConcurrency = 1;
      }
      for (Map<String, String> environmentMap : environmentsMapList) {
        children.add(getChild(childNodeId, currentIteration, totalIterations, environmentMap,
            MultiDeploymentSpawnerUtils.MULTI_ENV_DEPLOYMENT));
        currentIteration++;
      }
      return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
    }

    if (environmentsMapList.isEmpty()) {
      int currentIteration = 0;
      int totalIterations = servicesMap.size();
      int maxConcurrency = 0;
      if (stepParameters.getServices().getServicesMetadata() != null
          && stepParameters.getServices().getServicesMetadata().getParallel() != null
          && !stepParameters.getServices().getServicesMetadata().getParallel()) {
        maxConcurrency = 1;
      }
      for (Map<String, String> serviceMap : servicesMap) {
        children.add(getChild(childNodeId, currentIteration, totalIterations, serviceMap,
            MultiDeploymentSpawnerUtils.MULTI_SERVICE_DEPLOYMENT));
        currentIteration++;
      }
      return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
    }

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
      for (Map<String, String> environmentMap : environmentsMapList) {
        for (Map<String, String> serviceMap : servicesMap) {
          children.add(
              getChildForMultiServiceInfra(childNodeId, currentIteration, totalIterations, serviceMap, environmentMap));
          currentIteration++;
        }
      }
    } else if (isEnvironmentParallel) {
      maxConcurrency = environmentsMapList.size();
      for (Map<String, String> serviceMap : servicesMap) {
        for (Map<String, String> environmentMap : environmentsMapList) {
          children.add(
              getChildForMultiServiceInfra(childNodeId, currentIteration, totalIterations, serviceMap, environmentMap));
          currentIteration++;
        }
      }
    } else {
      for (Map<String, String> environmentMap : environmentsMapList) {
        for (Map<String, String> serviceMap : servicesMap) {
          children.add(
              getChildForMultiServiceInfra(childNodeId, currentIteration, totalIterations, serviceMap, environmentMap));
          currentIteration++;
        }
      }
    }
    return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
  }

  private ChildrenExecutableResponse.Child getChild(
      String childNodeId, int currentIteration, int totalIterations, Map<String, String> serviceMap, String subType) {
    return ChildrenExecutableResponse.Child.newBuilder()
        .setChildNodeId(childNodeId)
        .setStrategyMetadata(
            StrategyMetadata.newBuilder()
                .setCurrentIteration(currentIteration)
                .setTotalIterations(totalIterations)
                .setMatrixMetadata(
                    MatrixMetadata.newBuilder().setSubType(subType).putAllMatrixValues(serviceMap).build())
                .build())
        .build();
  }

  private boolean shouldDeployInParallel(EnvironmentsMetadata metadata) {
    return metadata != null && metadata.getParallel() != null && metadata.getParallel();
  }

  private boolean shouldDeployInParallel(ServicesMetadata metadata) {
    return metadata != null && metadata.getParallel() != null && metadata.getParallel();
  }

  private ChildrenExecutableResponse.Child getChildForMultiServiceInfra(String childNodeId, int currentIteration,
      int totalIterations, Map<String, String> serviceMap, Map<String, String> environmentMap) {
    Map<String, String> matrixMetadataMap = new HashMap<>();
    matrixMetadataMap.putAll(serviceMap);
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

  private List<Map<String, String>> getEnvironmentMapList(EnvironmentsYaml environmentsYaml) {
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
    return getEnvironmentsMap(environments);
  }

  private List<Map<String, String>> getEnvironmentsGroupMap(EnvironmentGroupYaml environmentGroupYaml) {
    if (environmentGroupYaml.getEnvironments().isExpression()) {
      throw new InvalidYamlException("Expected a value of environmentRefs to be provided but found expression");
    }
    List<EnvironmentYamlV2> environments = environmentGroupYaml.getEnvironments().getValue();
    if (EmptyPredicate.isEmpty(environments)) {
      throw new InvalidYamlException("Expected a value of environmentRefs to be provided but found empty");
    }

    return getEnvironmentsMap(environments);
  }

  private List<Map<String, String>> getEnvironmentsMap(List<EnvironmentYamlV2> environments) {
    if (EmptyPredicate.isEmpty(environments)) {
      throw new InvalidYamlException("No value of environment provided. Please provide atleast one value");
    }
    List<Map<String, String>> environmentsMap = new ArrayList<>();
    for (EnvironmentYamlV2 environmentYamlV2 : environments) {
      if (ParameterField.isNull(environmentYamlV2.getInfrastructureDefinitions())) {
        environmentsMap.add(MultiDeploymentSpawnerUtils.getMapFromEnvironmentYaml(
            environmentYamlV2, environmentYamlV2.getInfrastructureDefinition().getValue()));
      } else {
        for (InfraStructureDefinitionYaml infra : environmentYamlV2.getInfrastructureDefinitions().getValue()) {
          environmentsMap.add(MultiDeploymentSpawnerUtils.getMapFromEnvironmentYaml(environmentYamlV2, infra));
        }
      }
    }
    return environmentsMap;
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
}
