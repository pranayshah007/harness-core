/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResponse;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResult;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgCommandTaskNGHelper;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgInfraConfigHelper;
import io.harness.delegate.task.aws.asg.AsgTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgCanaryDeleteCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelperBase asgTaskHelperBase;
  @Inject private AsgInfraConfigHelper asgInfraConfigHelper;
  @Inject private AsgCommandTaskNGHelper asgCommandTaskHelper;

  private AsgInfraConfig asgInfraConfig;
  private long timeoutInMillis;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgCanaryDeleteRequest)) {
      throw new InvalidArgumentsException(Pair.of("asgCommandRequest", "Must be instance of AsgCanaryDeleteRequest"));
    }

    AsgCanaryDeleteRequest asgCanaryDeleteRequest = (AsgCanaryDeleteRequest) asgCommandRequest;
    timeoutInMillis = asgCanaryDeleteRequest.getTimeoutIntervalInMin() * 60000;
    asgInfraConfig = asgCanaryDeleteRequest.getAsgInfraConfig();

    LogCallback canaryDeleteLogCallback = asgTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.deleteService.toString(), true, commandUnitsProgress);

    String canaryAsgName = asgCanaryDeleteRequest.getCanaryAsgName();

    AsgCanaryDeleteResult asgCanaryDeleteResult = null;

    // TODO - delete asg part
    if (true) {
      asgCanaryDeleteResult = AsgCanaryDeleteResult.builder().canaryDeleted(true).canaryAsgName(canaryAsgName).build();

      canaryDeleteLogCallback.saveExecutionLog(
          format("Canary asg %s deleted", canaryAsgName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } else {
      canaryDeleteLogCallback.saveExecutionLog(
          format("Canary asg %s doesn't exist", canaryAsgName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      asgCanaryDeleteResult = AsgCanaryDeleteResult.builder().canaryDeleted(false).canaryAsgName(canaryAsgName).build();
    }
    return AsgCanaryDeleteResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .asgCanaryDeleteResult(asgCanaryDeleteResult)
        .build();
  }
}
