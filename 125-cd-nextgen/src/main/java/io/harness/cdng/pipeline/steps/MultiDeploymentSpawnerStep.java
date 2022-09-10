package io.harness.cdng.pipeline.steps;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.pipeline.beans.MultiDeploymentStepParameters;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
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
    // Todo: Check if user has access permission on service and environment
  }

  @Override
  public ChildrenExecutableResponse obtainChildrenAfterRbac(
      Ambiance ambiance, MultiDeploymentStepParameters stepParameters, StepInputPackage inputPackage) {
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    List<Map<String, String>> servicesMap = getServicesMap(stepParameters.getServices());
    List<Map<String, String>> environmentsMapList = getEnvironmentMapList(stepParameters.getEnvironments());
    String childNodeId = stepParameters.getChildNodeId();
    if (servicesMap.isEmpty()) {
      int currentIteration = 0;
      int totalIterations = environmentsMapList.size();
      int maxConcurrency = 1;
      if (stepParameters.getEnvironments().getEnvironmentsMetadata() != null
          && stepParameters.getEnvironments().getEnvironmentsMetadata().getParallel() != null
          && stepParameters.getEnvironments().getEnvironmentsMetadata().getParallel()) {
        maxConcurrency = 0;
      }
      for (Map<String, String> environmentMap : environmentsMapList) {
        children.add(getChild(childNodeId, currentIteration, totalIterations, environmentMap));
        currentIteration++;
      }
      return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
    }

    if (environmentsMapList.isEmpty()) {
      int currentIteration = 0;
      int totalIterations = servicesMap.size();
      int maxConcurrency = 1;
      if (stepParameters.getServices().getServicesMetadata() != null
          && stepParameters.getServices().getServicesMetadata().getParallel() != null
          && stepParameters.getServices().getServicesMetadata().getParallel()) {
        maxConcurrency = 0;
      }
      for (Map<String, String> serviceMap : servicesMap) {
        children.add(getChild(childNodeId, currentIteration, totalIterations, serviceMap));
        currentIteration++;
      }
      return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
    }

    boolean isServiceParallel = stepParameters.getServices().getServicesMetadata() != null
        && stepParameters.getServices().getServicesMetadata().getParallel() != null
        && stepParameters.getServices().getServicesMetadata().getParallel();
    boolean isEnvironmentParallel = stepParameters.getEnvironments().getEnvironmentsMetadata() != null
        && stepParameters.getEnvironments().getEnvironmentsMetadata().getParallel() != null
        && stepParameters.getEnvironments().getEnvironmentsMetadata().getParallel();
    int currentIteration = 0;
    int totalIterations = servicesMap.size() + environmentsMapList.size();
    int maxConcurrency = 0;
    if (isServiceParallel) {
      if (isEnvironmentParallel) {
        maxConcurrency = totalIterations;
      } else {
        maxConcurrency = servicesMap.size();
      }
      for (Map<String, String> serviceMap : servicesMap) {
        for (Map<String, String> environmentMap : environmentsMapList) {
          children.add(
              getChildForMultiServiceInfra(childNodeId, currentIteration, totalIterations, serviceMap, environmentMap));
          currentIteration++;
        }
      }
    } else if (isEnvironmentParallel) {
      maxConcurrency = environmentsMapList.size();
      for (Map<String, String> environmentMap : environmentsMapList) {
        for (Map<String, String> serviceMap : servicesMap) {
          children.add(
              getChildForMultiServiceInfra(childNodeId, currentIteration, totalIterations, serviceMap, environmentMap));
          currentIteration++;
        }
      }
    } else {
      maxConcurrency = 1;
      for (Map<String, String> environmentMap : environmentsMapList) {
        for (Map<String, String> serviceMap : servicesMap) {
          children.add(
              getChildForMultiServiceInfra(childNodeId, currentIteration, totalIterations, serviceMap, environmentMap));
          currentIteration++;
        }
      }
    }
    // Todo: Add support for environment group
    return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
  }

  private ChildrenExecutableResponse.Child getChild(
      String childNodeId, int currentIteration, int totalIterations, Map<String, String> serviceMap) {
    return ChildrenExecutableResponse.Child.newBuilder()
        .setChildNodeId(childNodeId)
        .setStrategyMetadata(StrategyMetadata.newBuilder()
                                 .setCurrentIteration(currentIteration)
                                 .setTotalIterations(totalIterations)
                                 .setMatrixMetadata(MatrixMetadata.newBuilder().putAllMatrixValues(serviceMap).build())
                                 .build())
        .build();
  }

  private ChildrenExecutableResponse.Child getChildForMultiServiceInfra(String childNodeId, int currentIteration,
      int totalIterations, Map<String, String> serviceMap, Map<String, String> environmentMap) {
    Map<String, String> matrixMetadataMap = new HashMap<>();
    matrixMetadataMap.putAll(serviceMap);
    matrixMetadataMap.putAll(environmentMap);
    return getChild(childNodeId, currentIteration, totalIterations, matrixMetadataMap);
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
    List<Map<String, String>> environmentsMap = new ArrayList<>();
    for (ServiceYamlV2 service : services) {
      environmentsMap.add(MultiDeploymentSpawnerUtils.getMapFromServiceYaml(service));
    }
    return environmentsMap;
  }
}
