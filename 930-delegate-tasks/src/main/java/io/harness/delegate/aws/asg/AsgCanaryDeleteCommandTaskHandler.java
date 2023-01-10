/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResponse;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResult;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.google.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgCanaryDeleteCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelper asgTaskHelper;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgCanaryDeleteRequest)) {
      throw new InvalidArgumentsException(Pair.of("asgCommandRequest", "Must be instance of AsgCanaryDeleteRequest"));
    }

    AsgCanaryDeleteRequest asgCanaryDeleteRequest = (AsgCanaryDeleteRequest) asgCommandRequest;

    LogCallback logCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.deleteService.toString(), true, commandUnitsProgress);

    try {
      AsgSdkManager asgSdkManager = asgTaskHelper.getAsgSdkManager(asgCommandRequest, logCallback);

      executeCanaryDelete(asgSdkManager, asgCanaryDeleteRequest.getCanaryAsgName());

      AsgCanaryDeleteResult asgCanaryDeleteResult = AsgCanaryDeleteResult.builder()
                                                        .canaryAsgName(asgCanaryDeleteRequest.getCanaryAsgName())
                                                        .canaryDeleted(true)
                                                        .build();

      logCallback.saveExecutionLog(
          color("Deletion Finished Successfully", Green, Bold), INFO, CommandExecutionStatus.SUCCESS);

      return AsgCanaryDeleteResponse.builder()
          .asgCanaryDeleteResult(asgCanaryDeleteResult)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();
    } catch (Exception e) {
      logCallback.saveExecutionLog(
          color(format("Deletion Failed."), LogColor.Red, LogWeight.Bold), ERROR, CommandExecutionStatus.FAILURE);
      throw new AsgNGException(e);
    }
  }

  private void executeCanaryDelete(AsgSdkManager asgSdkManager, String canaryAsgName) {
    if (isEmpty(canaryAsgName)) {
      throw new InvalidArgumentsException(Pair.of("AutoScalingGroup name", "Must not be empty"));
    }

    AutoScalingGroup autoScalingGroup = asgSdkManager.getASG(canaryAsgName);
    if (autoScalingGroup == null) {
      String msg = format("Asg with name `%s` does not exist", canaryAsgName);
      asgSdkManager.info(msg);
      throw new InvalidArgumentsException(msg);
    }

    asgSdkManager.deleteAsg(canaryAsgName);
  }
}
