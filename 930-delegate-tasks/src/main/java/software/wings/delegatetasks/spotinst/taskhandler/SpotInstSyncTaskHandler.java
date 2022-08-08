/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.spotinst.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static java.lang.String.format;
import static java.lang.String.valueOf;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.spotinst.request.SpotInstGetElastigroupJsonParameters;
import io.harness.delegate.task.spotinst.request.SpotInstListElastigroupInstancesParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstGetElastigroupJsonResponse;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupInstancesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupNamesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskResponse;
import io.harness.exception.InvalidRequestException;
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
public class SpotInstSyncTaskHandler extends SpotInstTaskHandler {
  @Override
  protected SpotInstTaskExecutionResponse executeTaskInternal(SpotInstTaskParameters spotInstTaskParameters,
      SpotInstConfig spotInstConfig, AwsConfig awsConfig) throws Exception {
    if (!spotInstTaskParameters.isSyncTask()) {
      throw new InvalidRequestException(format("Unrecognized object of class: [%s] while executing sync task",
          spotInstTaskParameters.getCommandType().name()));
    }

    String spotInstAccountId = spotInstConfig.getSpotInstAccountId();
    String spotInstToken = valueOf(spotInstConfig.getSpotInstToken());
    SpotInstTaskResponse syncTaskResponse;
    switch (spotInstTaskParameters.getCommandType()) {
      case SPOT_INST_LIST_ELASTI_GROUPS: {
        List<ElastiGroup> groups = spotInstHelperServiceDelegate.listAllElstiGroups(spotInstToken, spotInstAccountId);
        syncTaskResponse = SpotInstListElastigroupNamesResponse.builder().elastigroups(groups).build();
        break;
      }
      case SPOT_INST_GET_ELASTI_GROUP_JSON: {
        String elastigroupId = ((SpotInstGetElastigroupJsonParameters) spotInstTaskParameters).getElastigroupId();
        String json = spotInstHelperServiceDelegate.getElastigroupJson(spotInstToken, spotInstAccountId, elastigroupId);
        syncTaskResponse = SpotInstGetElastigroupJsonResponse.builder().elastigroupJson(json).build();
        break;
      }
      case SPOT_INST_LIST_ELASTI_GROUP_INSTANCES: {
        SpotInstListElastigroupInstancesParameters listInstancesParams =
            (SpotInstListElastigroupInstancesParameters) spotInstTaskParameters;
        List<Instance> instances = getAllEc2InstancesOfElastiGroup(awsConfig, listInstancesParams.getAwsRegion(),
            spotInstToken, spotInstAccountId, listInstancesParams.getElastigroupId());
        syncTaskResponse = SpotInstListElastigroupInstancesResponse.builder()
                               .elastigroupId(listInstancesParams.getElastigroupId())
                               .elastigroupInstances(instances)
                               .build();
        break;
      }
      default: {
        throw new InvalidRequestException(format("Unrecognized object of class: [%s] while executing sync task",
            spotInstTaskParameters.getCommandType().name()));
      }
    }
    return SpotInstTaskExecutionResponse.builder()
        .spotInstTaskResponse(syncTaskResponse)
        .commandExecutionStatus(SUCCESS)
        .build();
  }
}
