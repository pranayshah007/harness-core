/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotinstTrafficShiftAlbDeployParameters;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotinstTrafficShiftAlbDeployResponse;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;

import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Singleton;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SpotinstTrafficShiftAlbDeployTaskHandler extends SpotInstTaskHandler {
  @Override
  protected SpotInstTaskExecutionResponse executeTaskInternal(SpotInstTaskParameters spotinstTaskParameters,
      SpotInstConfig spotinstConfig, AwsConfig awsConfig) throws Exception {
    if (!(spotinstTaskParameters instanceof SpotinstTrafficShiftAlbDeployParameters)) {
      String message =
          format("Parameters of unrecognized class: [%s] found while executing setup traffic shift deploy.",
              spotinstTaskParameters.getClass().getSimpleName());
      log.error(message);
      return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
    }
    SpotinstTrafficShiftAlbDeployParameters deployParameters =
        (SpotinstTrafficShiftAlbDeployParameters) spotinstTaskParameters;

    ElastiGroup newElastigroup = deployParameters.getNewElastigroup();
    if (newElastigroup == null) {
      String message = "Found null newElastigroup when trying to scale up Alb traffic shift deployment.";
      log.error(message);
      return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
    }
    updateElastiGroupAndWait(String.valueOf(spotinstConfig.getSpotInstToken()), spotinstConfig.getSpotInstAccountId(),
        deployParameters.getNewElastigroup(), deployParameters.getTimeoutIntervalInMin(), deployParameters,
        UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
    List<Instance> newInstances = getAllEc2InstancesOfElastiGroup(awsConfig, deployParameters.getAwsRegion(),
        String.valueOf(spotinstConfig.getSpotInstToken()), spotinstConfig.getSpotInstAccountId(),
        newElastigroup.getId());
    List<Instance> oldInstances = emptyList();
    if (deployParameters.getOldElastigroup() != null) {
      oldInstances = getAllEc2InstancesOfElastiGroup(awsConfig, deployParameters.getAwsRegion(),
          String.valueOf(spotinstConfig.getSpotInstToken()), spotinstConfig.getSpotInstAccountId(),
          deployParameters.getOldElastigroup().getId());
    }
    return SpotInstTaskExecutionResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .spotInstTaskResponse(SpotinstTrafficShiftAlbDeployResponse.builder()
                                  .ec2InstancesAdded(newInstances)
                                  .ec2InstancesExisting(oldInstances)
                                  .build())
        .build();
  }
}
