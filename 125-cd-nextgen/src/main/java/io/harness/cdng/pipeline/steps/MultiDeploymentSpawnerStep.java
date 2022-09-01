package io.harness.cdng.pipeline.steps;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.pipeline.beans.MultiDeploymentStepParameters;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.ChildrenExecutableWithRollbackAndRbac;
import io.harness.tasks.ResponseData;

import java.util.ArrayList;
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
    List<ServiceYamlV2> services = stepParameters.getServices().getValues().getValue();
    List<EnvironmentYamlV2> environments = stepParameters.getEnvironments().getValues().getValue();
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();

    if (EmptyPredicate.isEmpty(environments)) {
      int currentIteration = 0;
      int totalIterations = services.size();
      for (ServiceYamlV2 service : services) {
        children.add(ChildrenExecutableResponse.Child.newBuilder()
                         .setChildNodeId(stepParameters.getChildNodeId())
                         .setStrategyMetadata(
                             StrategyMetadata.newBuilder()
                                 .setCurrentIteration(currentIteration)
                                 .setTotalIterations(totalIterations)
                                 .setMatrixMetadata(
                                     MatrixMetadata.newBuilder()
                                         .putAllMatrixValues(MultiDeploymentSpawnerUtils.getMapFromServiceYaml(service))
                                         .build())
                                 .build())
                         .build());
        currentIteration++;
      }
    }
    // Todo: Add support for environments and infras
    // Todo: MaxConcurrency should be populated from yaml.
    return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(2).build();
  }
}
