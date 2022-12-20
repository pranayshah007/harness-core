/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.elastigroup.deploy;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.SpotServerInstanceInfo;
import io.harness.delegate.task.spot.elastigroup.deploy.ElastigroupDeployTaskParameters;
import io.harness.delegate.task.spot.elastigroup.deploy.ElastigroupDeployTaskResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class ElastigroupDeployStep extends TaskExecutableWithRollbackAndRbac<ElastigroupDeployTaskResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ELASTIGROUP_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ElastigroupDeployStepHelper stepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Noop
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    ElastigroupDeployStepParameters elastigroupDeployStepParameters =
        (ElastigroupDeployStepParameters) stepParameters.getSpec();
    validateStepParameters(elastigroupDeployStepParameters);

    ElastigroupDeployTaskParameters taskParameters =
        stepHelper.getElastigroupDeployTaskParameters(elastigroupDeployStepParameters, ambiance, stepParameters);

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(TaskType.ELASTIGROUP_DEPLOY.name())
            .parameters(new Object[] {taskParameters})
            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), StepUtils.DEFAULT_STEP_TIMEOUT))
            .build();

    return stepHelper.prepareTaskRequest(ambiance, taskData, stepHelper.getExecutionUnits(),
        TaskType.ELASTIGROUP_DEPLOY.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(elastigroupDeployStepParameters.getDelegateSelectors()))));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<ElastigroupDeployTaskResponse> responseDataSupplier) throws Exception {
    ElastigroupDeployTaskResponse taskResponse;
    try {
      taskResponse = responseDataSupplier.get();
    } catch (Exception ex) {
      return stepHelper.handleTaskFailure(ambiance, stepParameters, ex);
    }

    if (taskResponse == null) {
      return stepHelper.handleTaskFailure(ambiance, stepParameters,
          new InvalidArgumentsException("Failed to process elastigroup deploy task response"));
    }

    StepResponse.StepOutcome stepOutcome = saveSpotServerInstanceInfosToSweepingOutput(taskResponse, ambiance);

    return stepHelper.handleTaskResult(ambiance, stepParameters, taskResponse, stepOutcome);
  }

  private StepResponse.StepOutcome saveSpotServerInstanceInfosToSweepingOutput(
      ElastigroupDeployTaskResponse elastigroupDeployTaskResponse, Ambiance ambiance) {
    List<ServerInstanceInfo> spotServerInstanceInfos =
        createSpotServerInstanceInfos(elastigroupDeployTaskResponse, ambiance);
    if (isNotEmpty(spotServerInstanceInfos)) {
      return instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, spotServerInstanceInfos);
    }
    return null;
  }

  private List<ServerInstanceInfo> createSpotServerInstanceInfos(
      ElastigroupDeployTaskResponse elastigroupDeployTaskResponse, Ambiance ambiance) {
    if (isEmpty(elastigroupDeployTaskResponse.getEc2InstanceIdsExisting())
        && isEmpty(elastigroupDeployTaskResponse.getEc2InstanceIdsAdded())) {
      return null;
    }

    InfrastructureOutcome infrastructure = cdStepHelper.getInfrastructureOutcome(ambiance);

    ElastigroupSetupDataOutcome elastigroupSetupDataOutcome = stepHelper.getElastigroupSetupOutcome(ambiance);
    ElastiGroup oldElastigroup = elastigroupSetupDataOutcome.getOldElastigroupOriginalConfig();
    ElastiGroup newElastigroup = elastigroupSetupDataOutcome.getNewElastigroupOriginalConfig();

    if (oldElastigroup == null && newElastigroup == null) {
      return null;
    }

    List<SpotServerInstanceInfo> oldSpotServerInstanceInfos;
    if (oldElastigroup != null && isNotEmpty(oldElastigroup.getId())) {
      String oldElastigroupId = oldElastigroup.getId();
      List<String> oldInstanceIds = elastigroupDeployTaskResponse.getEc2InstanceIdsExisting();
      oldSpotServerInstanceInfos = oldInstanceIds == null
          ? Collections.emptyList()
          : oldInstanceIds.stream()
                .map(id -> mapToSpotServerInstanceInfo(infrastructure.getInfrastructureKey(), oldElastigroupId, id))
                .collect(Collectors.toList());

    } else {
      oldSpotServerInstanceInfos = Collections.emptyList();
    }

    List<SpotServerInstanceInfo> newSpotServerInstanceInfos;
    if (newElastigroup != null && isNotEmpty(newElastigroup.getId())) {
      String newElastigroupId = newElastigroup.getId();
      List<String> newInstanceIds = elastigroupDeployTaskResponse.getEc2InstanceIdsAdded();
      newSpotServerInstanceInfos = newInstanceIds == null
          ? Collections.emptyList()
          : newInstanceIds.stream()
                .map(id -> mapToSpotServerInstanceInfo(infrastructure.getInfrastructureKey(), newElastigroupId, id))
                .collect(Collectors.toList());

    } else {
      newSpotServerInstanceInfos = Collections.emptyList();
    }

    return Stream.of(oldSpotServerInstanceInfos, newSpotServerInstanceInfos)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  private SpotServerInstanceInfo mapToSpotServerInstanceInfo(
      String infrastructureKey, String groupId, String instanceId) {
    return SpotServerInstanceInfo.builder()
        .infrastructureKey(infrastructureKey)
        .ec2InstanceId(instanceId)
        .elastigroupId(groupId)
        .build();
  }

  private void validateStepParameters(ElastigroupDeployStepParameters elastigroupDeployStepParameters) {}
}
