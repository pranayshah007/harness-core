/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.spot;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import static java.util.Collections.emptyList;

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

import com.amazonaws.services.ec2.model.Instance;
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
      ElastigroupDeployTaskParameters elastigroupDeployTaskParameters = (ElastigroupDeployTaskParameters) parameters;

      ElastiGroup newElastigroup = elastigroupDeployTaskParameters.getNewElastigroup();
      ElastiGroup oldElastigroup = elastigroupDeployTaskParameters.getOldElastigroup();
      SpotConfig spotConfig = ngConfigMapper.mapSpotConfigWithDecryption(
          elastigroupDeployTaskParameters.getSpotConnector(), elastigroupDeployTaskParameters.getEncryptionDetails());
      String spotInstAccountId = spotConfig.getCredential().getSpotAccountId();
      String spotInstToken = spotConfig.getCredential().getAppTokenId();

      taskHelper.scaleElastigroup(newElastigroup, spotInstToken, spotInstAccountId, STEADY_STATE_TIME_OUT_IN_MINUTES,
          getLogStreamingTaskClient(), UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT,
          commandUnitsProgress);
      taskHelper.scaleElastigroup(oldElastigroup, spotInstToken, spotInstAccountId, STEADY_STATE_TIME_OUT_IN_MINUTES,
          getLogStreamingTaskClient(), DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT,
          commandUnitsProgress);

      //      List<Instance> newElastigroupInstances = newElastigroup != null
      //                                               ? getAllEc2InstancesOfElastiGroup(
      //          awsConfig, deployTaskParameters.getAwsRegion(), spotInstToken, spotInstAccountId,
      //          newElastigroup.getId())
      //                                               : emptyList();
      //
      //      List<Instance> ec2InstancesForOlderElastiGroup = oldElastigroup != null
      //                                                       ? getAllEc2InstancesOfElastiGroup(
      //          awsConfig, deployTaskParameters.getAwsRegion(), spotInstToken, spotInstAccountId,
      //          oldElastigroup.getId())
      //                                                       : emptyList();

      List<Instance> newElastigroupInstances = emptyList();
      List<Instance> ec2InstancesForOlderElastiGroup = emptyList();

      return ElastigroupDeployTaskResponse.builder()
          .status(CommandExecutionStatus.SUCCESS)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .errorMessage(getErrorMessage(CommandExecutionStatus.SUCCESS))
          .ec2InstancesAdded(newElastigroupInstances)
          .ec2InstancesExisting(ec2InstancesForOlderElastiGroup)
          .build();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in elastigroup deploy", sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    } finally {
      getLogStreamingTaskClient().dispatchLogs();
    }
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
