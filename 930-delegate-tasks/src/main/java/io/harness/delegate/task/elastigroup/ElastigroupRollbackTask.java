/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.spotinst.model.SpotInstConstants.DELETE_NEW_ELASTI_GROUP;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.RENAME_OLD_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.connector.task.spot.SpotConfig;
import io.harness.connector.task.spot.SpotNgConfigMapper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.spot.elastigroup.rollback.ElastigroupRollbackTaskParameters;
import io.harness.delegate.task.spot.elastigroup.rollback.ElastigroupRollbackTaskResponse;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(CDP)
public class ElastigroupRollbackTask extends AbstractDelegateRunnableTask {
  public static final int STEADY_STATE_TIME_OUT_IN_MINUTES = 5;

  @Inject private SpotNgConfigMapper ngConfigMapper;

  @Inject private ElastigroupDeployTaskHelper taskHelper;
  @Inject SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;

  @Inject private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;

  public ElastigroupRollbackTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    if (!(parameters instanceof ElastigroupRollbackTaskParameters)) {
      throw new IllegalArgumentException(String.format("Invalid parameters type provide %s", parameters.getClass()));
    }

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    try {
      final ElastigroupRollbackTaskParameters rollbackParameters = (ElastigroupRollbackTaskParameters) parameters;
      if (rollbackParameters.isBlueGreen()) {
        if (rollbackParameters.isSetupSuccessful()) {
          return executeBlueGreenSwapRollback(rollbackParameters, commandUnitsProgress);
        } else {
          return executeBlueGreenSetupRollback(rollbackParameters, commandUnitsProgress);
        }
      } else {
        return executeBasicAndCanaryRollback(rollbackParameters, commandUnitsProgress);
      }
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in elastigroup rollback", sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    } finally {
      getLogStreamingTaskClient().dispatchLogs();
    }
  }

  private DelegateResponseData executeBlueGreenSetupRollback(
      ElastigroupRollbackTaskParameters parameters, CommandUnitsProgress commandUnitsProgress) throws Exception {
    SpotConfig spotConfig =
        ngConfigMapper.mapSpotConfigWithDecryption(parameters.getSpotConnector(), parameters.getEncryptionDetails());
    String spotInstAccountId = spotConfig.getCredential().getSpotAccountId();
    String spotInstToken = spotConfig.getCredential().getAppTokenId();
    int timeoutInMinutes = parameters.getTimeout() > 0 ? parameters.getTimeout() : STEADY_STATE_TIME_OUT_IN_MINUTES;

    rollbackNew(parameters, spotInstAccountId, spotInstToken, timeoutInMinutes, commandUnitsProgress);

    return ElastigroupRollbackTaskResponse.builder()
        .status(CommandExecutionStatus.SUCCESS)
        .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
        .errorMessage(getErrorMessage(CommandExecutionStatus.SUCCESS))
        .build();
  }

  private DelegateResponseData executeBlueGreenSwapRollback(
      ElastigroupRollbackTaskParameters parameters, CommandUnitsProgress commandUnitsProgress) throws Exception {
    SpotConfig spotConfig =
        ngConfigMapper.mapSpotConfigWithDecryption(parameters.getSpotConnector(), parameters.getEncryptionDetails());
    String spotInstAccountId = spotConfig.getCredential().getSpotAccountId();
    String spotInstToken = spotConfig.getCredential().getAppTokenId();
    int timeoutInMinutes = parameters.getTimeout() > 0 ? parameters.getTimeout() : STEADY_STATE_TIME_OUT_IN_MINUTES;

    restoreOld(parameters, spotInstAccountId, spotInstToken, timeoutInMinutes, commandUnitsProgress);
    rollbackRoutes(parameters, commandUnitsProgress);
    rollbackNew(parameters, spotInstAccountId, spotInstToken, timeoutInMinutes, commandUnitsProgress);

    return ElastigroupRollbackTaskResponse.builder()
        .status(CommandExecutionStatus.SUCCESS)
        .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
        .errorMessage(getErrorMessage(CommandExecutionStatus.SUCCESS))
        .build();
  }

  private void restoreOld(ElastigroupRollbackTaskParameters parameters, String spotInstAccountId, String spotInstToken,
      int timeoutInMinutes, CommandUnitsProgress commandUnitsProgress) throws Exception {
    taskHelper.scaleElastigroup(parameters.getOldElastigroup(), spotInstToken, spotInstAccountId, timeoutInMinutes,
        getLogStreamingTaskClient(), UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT,
        commandUnitsProgress);

    taskHelper.renameElastigroup(parameters.getOldElastigroup(), parameters.getElastigroupNamePrefix(),
        spotInstAccountId, spotInstToken, getLogStreamingTaskClient(), RENAME_OLD_COMMAND_UNIT, commandUnitsProgress);
  }

  private void rollbackRoutes(ElastigroupRollbackTaskParameters parameters, CommandUnitsProgress commandUnitsProgress) {
    elastigroupCommandTaskNGHelper.decryptAwsCredentialDTO(
        parameters.getAwsConnectorInfo().getConnectorConfig(), parameters.getAwsEncryptedDetails());

    AwsInternalConfig awsInternalConfig = elastigroupCommandTaskNGHelper.getAwsInternalConfig(
        (AwsConnectorDTO) parameters.getAwsConnectorInfo().getConnectorConfig(), parameters.getAwsRegion());

    taskHelper.restoreLoadBalancerRoutesIfNeeded(parameters.getLoadBalancerDetailsForBGDeployments(), awsInternalConfig,
        parameters.getAwsRegion(), getLogStreamingTaskClient(), commandUnitsProgress);
  }

  private LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, true, commandUnitsProgress);
  }

  private void rollbackNew(ElastigroupRollbackTaskParameters parameters, String spotInstAccountId, String spotInstToken,
      int timeoutInMinutes, CommandUnitsProgress commandUnitsProgress) throws Exception {
    ElastiGroup elastigroup = parameters.getNewElastigroup();

    if (elastigroup != null) {
      elastigroup = elastigroup.clone();
      elastigroup.setCapacity(ElastiGroupCapacity.builder().minimum(0).maximum(0).target(0).build());
    }

    taskHelper.scaleElastigroup(elastigroup, spotInstToken, spotInstAccountId, timeoutInMinutes,
        getLogStreamingTaskClient(), DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT,
        commandUnitsProgress);
    final LogCallback deleteLogCallback =
        getLogCallback(getLogStreamingTaskClient(), DELETE_NEW_ELASTI_GROUP, commandUnitsProgress);
    try {
      taskHelper.deleteElastigroup(elastigroup, spotInstToken, spotInstAccountId, deleteLogCallback);
      taskHelper.cleanupNewElastigroups(true, parameters, spotInstAccountId, spotInstToken, deleteLogCallback);
      deleteLogCallback.close(CommandExecutionStatus.SUCCESS);
    } catch (Exception e) {
      deleteLogCallback.close(CommandExecutionStatus.FAILURE);
      throw e;
    }
  }

  private ElastigroupRollbackTaskResponse executeBasicAndCanaryRollback(
      ElastigroupRollbackTaskParameters parameters, CommandUnitsProgress commandUnitsProgress) throws Exception {
    SpotConfig spotConfig =
        ngConfigMapper.mapSpotConfigWithDecryption(parameters.getSpotConnector(), parameters.getEncryptionDetails());
    String spotInstAccountId = spotConfig.getCredential().getSpotAccountId();
    String spotInstToken = spotConfig.getCredential().getAppTokenId();
    int timeoutInMinutes = parameters.getTimeout() > 0 ? parameters.getTimeout() : STEADY_STATE_TIME_OUT_IN_MINUTES;

    List<String> olderElastigroupInstanceIds = new ArrayList<>();
    LogCallback logCallback = null;
    try {
      if (!parameters.isSetupSuccessful()) {
        logCallback = getLogCallback(getLogStreamingTaskClient(), DELETE_NEW_ELASTI_GROUP, commandUnitsProgress);
        taskHelper.cleanupNewElastigroups(false, parameters, spotInstAccountId, spotInstToken, logCallback);
        logCallback.close(CommandExecutionStatus.SUCCESS);
      } else {
        ElastiGroup newElastigroup = parameters.getNewElastigroup();
        ElastiGroup oldElastigroup = parameters.getOldElastigroup();

        taskHelper.scaleElastigroup(oldElastigroup, spotInstToken, spotInstAccountId, timeoutInMinutes,
            getLogStreamingTaskClient(), UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT,
            commandUnitsProgress);
        taskHelper.scaleElastigroup(newElastigroup, spotInstToken, spotInstAccountId, timeoutInMinutes,
            getLogStreamingTaskClient(), DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT,
            commandUnitsProgress);
        logCallback = getLogCallback(getLogStreamingTaskClient(), DELETE_NEW_ELASTI_GROUP, commandUnitsProgress);
        taskHelper.deleteElastigroup(newElastigroup, spotInstToken, spotInstAccountId, logCallback);
        logCallback.close(CommandExecutionStatus.SUCCESS);
        olderElastigroupInstanceIds = elastigroupCommandTaskNGHelper.getAllEc2InstanceIdsOfElastigroup(
            spotInstToken, spotInstAccountId, oldElastigroup);
      }
    } catch (Exception e) {
      if (logCallback != null) {
        logCallback.close(CommandExecutionStatus.FAILURE);
      }
      throw e;
    }

    return ElastigroupRollbackTaskResponse.builder()
        .status(CommandExecutionStatus.SUCCESS)
        .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
        .errorMessage(getErrorMessage(CommandExecutionStatus.SUCCESS))
        .ec2InstanceIdsExisting(olderElastigroupInstanceIds)
        .build();
  }

  private String getErrorMessage(CommandExecutionStatus status) {
    switch (status) {
      case QUEUED:
        return "Elastigroup Rollback execution queued.";
      case FAILURE:
        return "Elastigroup Rollback execution failed. Please check execution logs.";
      case RUNNING:
        return "Elastigroup Rollback execution running.";
      case SKIPPED:
        return "Elastigroup Rollback execution skipped.";
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
