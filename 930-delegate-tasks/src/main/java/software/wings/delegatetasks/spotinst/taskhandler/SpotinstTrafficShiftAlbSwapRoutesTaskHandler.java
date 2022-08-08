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
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.RENAME_NEW_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.RENAME_OLD_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.STAGE_ELASTI_GROUP_NAME_SUFFIX;
import static io.harness.spotinst.model.SpotInstConstants.SWAP_ROUTES_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import static software.wings.service.impl.aws.model.AwsConstants.MAX_TRAFFIC_SHIFT_WEIGHT;
import static software.wings.service.impl.aws.model.AwsConstants.MIN_TRAFFIC_SHIFT_WEIGHT;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotinstTrafficShiftAlbSwapRoutesParameters;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.spotinst.model.ElastiGroupRenameRequest;
import io.harness.spotinst.model.SpotInstConstants;

import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.command.ExecutionLogCallback;

import com.google.inject.Singleton;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SpotinstTrafficShiftAlbSwapRoutesTaskHandler extends SpotInstTaskHandler {
  @Override
  protected SpotInstTaskExecutionResponse executeTaskInternal(SpotInstTaskParameters spotinstTaskParameters,
      SpotInstConfig spotinstConfig, AwsConfig awsConfig) throws Exception {
    if (!(spotinstTaskParameters instanceof SpotinstTrafficShiftAlbSwapRoutesParameters)) {
      String message = format("Parameters of unrecognized class: [%s] found while executing setup step.",
          spotinstTaskParameters.getClass().getSimpleName());
      log.error(message);
      return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
    }
    String spotinstAccountId = spotinstConfig.getSpotInstAccountId();
    String spotinstToken = String.valueOf(spotinstConfig.getSpotInstToken());
    SpotinstTrafficShiftAlbSwapRoutesParameters parameters =
        (SpotinstTrafficShiftAlbSwapRoutesParameters) spotinstTaskParameters;
    if (parameters.isRollback()) {
      return executeRollback(spotinstAccountId, spotinstToken, awsConfig, parameters);
    } else {
      return executeDeploy(spotinstAccountId, spotinstToken, awsConfig, parameters);
    }
  }

  private SpotInstTaskExecutionResponse executeDeploy(String spotinstAccountId, String spotinstToken,
      AwsConfig awsConfig, SpotinstTrafficShiftAlbSwapRoutesParameters parameters) throws Exception {
    String prodElastigroupName = parameters.getElastigroupNamePrefix();
    String stageElastigroupName =
        format("%s__%s", parameters.getElastigroupNamePrefix(), STAGE_ELASTI_GROUP_NAME_SUFFIX);
    ElastiGroup oldElastigroup = parameters.getOldElastigroup();
    ElastiGroup newElastigroup = parameters.getNewElastigroup();
    ExecutionLogCallback logCallback = getLogCallBack(parameters, SWAP_ROUTES_COMMAND_UNIT);

    if (newElastigroup != null && parameters.getNewElastigroupWeight() >= MAX_TRAFFIC_SHIFT_WEIGHT) {
      logCallback.saveExecutionLog(
          format("Renaming Elastigroup with id: [%s] to [%s]", newElastigroup.getId(), prodElastigroupName));
      spotInstHelperServiceDelegate.updateElastiGroup(spotinstToken, spotinstAccountId, newElastigroup.getId(),
          ElastiGroupRenameRequest.builder().name(prodElastigroupName).build());
    }

    if (oldElastigroup != null && parameters.getNewElastigroupWeight() >= MAX_TRAFFIC_SHIFT_WEIGHT) {
      logCallback.saveExecutionLog(
          format("Renaming Elastigroup with id: [%s] to [%s]", oldElastigroup.getId(), stageElastigroupName));
      spotInstHelperServiceDelegate.updateElastiGroup(spotinstToken, spotinstAccountId, oldElastigroup.getId(),
          ElastiGroupRenameRequest.builder().name(stageElastigroupName).build());
    }

    awsElbHelperServiceDelegate.updateRulesForAlbTrafficShift(awsConfig, parameters.getAwsRegion(), emptyList(),
        parameters.getDetails(), logCallback, parameters.getNewElastigroupWeight(), SpotInstConstants.ELASTI_GROUP);
    logCallback.saveExecutionLog("Completed route updated successfully", INFO, SUCCESS);

    if (oldElastigroup != null && parameters.getNewElastigroupWeight() >= MAX_TRAFFIC_SHIFT_WEIGHT
        && parameters.isDownsizeOldElastigroup()) {
      ElastiGroup request = ElastiGroup.builder()
                                .id(oldElastigroup.getId())
                                .name(stageElastigroupName)
                                .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(0).target(0).build())
                                .build();
      updateElastiGroupAndWait(spotinstToken, spotinstAccountId, request, parameters.getTimeoutIntervalInMin(),
          parameters, DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
    } else {
      createAndFinishEmptyExecutionLog(parameters, DOWN_SCALE_COMMAND_UNIT, "No downsize action.");
      createAndFinishEmptyExecutionLog(parameters, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, "No downsize action.");
    }
    return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(SUCCESS).build();
  }

  private SpotInstTaskExecutionResponse executeRollback(String spotinstAccountId, String spotinstToken,
      AwsConfig awsConfig, SpotinstTrafficShiftAlbSwapRoutesParameters parameters) throws Exception {
    ElastiGroup newElastigroup = parameters.getNewElastigroup();
    ElastiGroup oldElastigroup = parameters.getOldElastigroup();
    String stageElastigroupName =
        format("%s__%s", parameters.getElastigroupNamePrefix(), STAGE_ELASTI_GROUP_NAME_SUFFIX);
    String prodElastigroupName = parameters.getElastigroupNamePrefix();
    ExecutionLogCallback logCallback;

    if (oldElastigroup != null) {
      ElastiGroup request = ElastiGroup.builder()
                                .id(oldElastigroup.getId())
                                .name(prodElastigroupName)
                                .capacity(oldElastigroup.getCapacity())
                                .build();
      updateElastiGroupAndWait(spotinstToken, spotinstAccountId, request, parameters.getTimeoutIntervalInMin(),
          parameters, UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
      logCallback = getLogCallBack(parameters, RENAME_OLD_COMMAND_UNIT);
      logCallback.saveExecutionLog(
          format("Renaming old Elastigroup with id: [%s] to name: [%s]", oldElastigroup.getId(), prodElastigroupName));
      spotInstHelperServiceDelegate.updateElastiGroup(spotinstToken, spotinstAccountId, oldElastigroup.getId(),
          ElastiGroupRenameRequest.builder().name(prodElastigroupName).build());
      logCallback.saveExecutionLog("Successfully renamed old Elastigroup", INFO, SUCCESS);
    } else {
      createAndFinishEmptyExecutionLog(parameters, UP_SCALE_COMMAND_UNIT, "No old Elastigroup found for upscaling");
      createAndFinishEmptyExecutionLog(
          parameters, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, "No old Elastigroup found for upscaling");
      createAndFinishEmptyExecutionLog(parameters, RENAME_OLD_COMMAND_UNIT, "No old Elastigroup found for renaming");
    }

    logCallback = getLogCallBack(parameters, SWAP_ROUTES_COMMAND_UNIT);
    awsElbHelperServiceDelegate.updateRulesForAlbTrafficShift(awsConfig, parameters.getAwsRegion(), emptyList(),
        parameters.getDetails(), logCallback, MIN_TRAFFIC_SHIFT_WEIGHT, SpotInstConstants.ELASTI_GROUP);
    logCallback.saveExecutionLog("Completed route updated successfully", INFO, SUCCESS);

    if (newElastigroup != null) {
      ElastiGroup request = ElastiGroup.builder()
                                .id(newElastigroup.getId())
                                .name(stageElastigroupName)
                                .capacity(ElastiGroupCapacity.builder().minimum(0).maximum(0).target(0).build())
                                .build();
      updateElastiGroupAndWait(spotinstToken, spotinstAccountId, request, parameters.getTimeoutIntervalInMin(),
          parameters, DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
      logCallback = getLogCallBack(parameters, RENAME_NEW_COMMAND_UNIT);
      logCallback.saveExecutionLog(
          format("Renaming Elastigroup with id: [%s] to name: [%s]", newElastigroup.getId(), stageElastigroupName));
      spotInstHelperServiceDelegate.updateElastiGroup(spotinstToken, spotinstAccountId, newElastigroup.getId(),
          ElastiGroupRenameRequest.builder().name(stageElastigroupName).build());
      logCallback.saveExecutionLog("Completed Rename", INFO, SUCCESS);
    } else {
      createAndFinishEmptyExecutionLog(
          parameters, DOWN_SCALE_COMMAND_UNIT, "No new Elastigroup found for downscaling.");
      createAndFinishEmptyExecutionLog(
          parameters, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, "No new Elastigroup found for downscaling.");
      createAndFinishEmptyExecutionLog(parameters, RENAME_NEW_COMMAND_UNIT, "No new Elastigroup found for renaming");
    }
    return SpotInstTaskExecutionResponse.builder().commandExecutionStatus(SUCCESS).build();
  }
}
