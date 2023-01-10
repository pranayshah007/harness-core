/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.spot.SpotConfig;
import io.harness.connector.task.spot.SpotNgConfigMapper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.spot.elastigroup.deploy.ElastigroupDeployTaskParameters;
import io.harness.delegate.task.spot.elastigroup.deploy.ElastigroupDeployTaskResponse;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.spotinst.model.ElastiGroup;

import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(CDP)
public class ElastigroupDeployTask extends AbstractDelegateRunnableTask {
  public static final int STEADY_STATE_TIME_OUT_IN_MINUTES = 5;

  @Inject private SpotNgConfigMapper ngConfigMapper;

  @Inject private ElastigroupDeployTaskHelper taskHelper;
  @Inject private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;

  public ElastigroupDeployTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    if (!(parameters instanceof ElastigroupDeployTaskParameters)) {
      throw new IllegalArgumentException(String.format("Invalid parameters type provide %s", parameters.getClass()));
    }

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    try {
      return elastigroupDeploy((ElastigroupDeployTaskParameters) parameters, commandUnitsProgress);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in elastigroup deploy", sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    } finally {
      getLogStreamingTaskClient().dispatchLogs();
    }
  }

  private ElastigroupDeployTaskResponse elastigroupDeploy(
      ElastigroupDeployTaskParameters parameters, CommandUnitsProgress commandUnitsProgress) throws Exception {
    ElastiGroup newElastigroup = parameters.getNewElastigroup();
    ElastiGroup oldElastigroup = parameters.getOldElastigroup();
    SpotConfig spotConfig =
        ngConfigMapper.mapSpotConfigWithDecryption(parameters.getSpotConnector(), parameters.getEncryptionDetails());
    String spotInstAccountId = spotConfig.getCredential().getSpotAccountId();
    String spotInstToken = spotConfig.getCredential().getAppTokenId();
    int timeoutInMinutes = parameters.getTimeout() > 0 ? parameters.getTimeout() : STEADY_STATE_TIME_OUT_IN_MINUTES;

    taskHelper.scaleElastigroup(newElastigroup, spotInstToken, spotInstAccountId, timeoutInMinutes,
        getLogStreamingTaskClient(), UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT,
        commandUnitsProgress);
    taskHelper.scaleElastigroup(oldElastigroup, spotInstToken, spotInstAccountId, timeoutInMinutes,
        getLogStreamingTaskClient(), DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT,
        commandUnitsProgress);

    List<String> newElastigroupInstanceIds = elastigroupCommandTaskNGHelper.getAllEc2InstanceIdsOfElastigroup(
        spotInstToken, spotInstAccountId, newElastigroup);
    List<String> olderElastigroupInstanceIds = elastigroupCommandTaskNGHelper.getAllEc2InstanceIdsOfElastigroup(
        spotInstToken, spotInstAccountId, oldElastigroup);

    return ElastigroupDeployTaskResponse.builder()
        .status(CommandExecutionStatus.SUCCESS)
        .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
        .errorMessage(getErrorMessage(CommandExecutionStatus.SUCCESS))
        .ec2InstanceIdsAdded(newElastigroupInstanceIds)
        .ec2InstanceIdsExisting(olderElastigroupInstanceIds)
        .build();
  }

  private String getErrorMessage(CommandExecutionStatus status) {
    switch (status) {
      case QUEUED:
        return "Elastigroup Deploy execution queued.";
      case FAILURE:
        return "Elastigroup Deploy execution failed. Please check execution logs.";
      case RUNNING:
        return "Elastigroup Deploy execution running.";
      case SKIPPED:
        return "Elastigroup Deploy execution skipped.";
      case SUCCESS:
      default:
        return "";
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
