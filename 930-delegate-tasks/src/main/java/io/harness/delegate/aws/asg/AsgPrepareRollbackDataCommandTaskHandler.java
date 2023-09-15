/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.aws.asg.manifest.AsgManifestType.AsgConfiguration;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgLaunchTemplate;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScalingPolicy;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScheduledUpdateGroupAction;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainFactory;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainState;
import io.harness.aws.asg.manifest.request.AsgConfigurationManifestRequest;
import io.harness.aws.asg.manifest.request.AsgLaunchTemplateManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScalingPolicyManifestRequest;
import io.harness.aws.asg.manifest.request.AsgScheduledActionManifestRequest;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.AsgNGException;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataRequest;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataResponse;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataResult;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AsgPrepareRollbackDataCommandTaskHandler extends AsgCommandTaskNGHandler {
  @Inject private AsgTaskHelper asgTaskHelper;

  @Override
  protected AsgCommandResponse executeTaskInternal(AsgCommandRequest asgCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress)
      throws AsgNGException {
    if (!(asgCommandRequest instanceof AsgPrepareRollbackDataRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("asgCommandRequest", "Must be instance of AsgPrepareRollbackDataRequest"));
    }

    AsgPrepareRollbackDataRequest asgPrepareRollbackDataRequest = (AsgPrepareRollbackDataRequest) asgCommandRequest;
    Map<String, List<String>> asgStoreManifestsContent = asgPrepareRollbackDataRequest.getAsgStoreManifestsContent();

    LogCallback logCallback = asgTaskHelper.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.prepareRollbackData.toString(), true, commandUnitsProgress);

    try {
      AsgSdkManager asgSdkManager = asgTaskHelper.getAsgSdkManager(asgCommandRequest, logCallback);
      String asgName = asgTaskHelper.getAsgName(asgPrepareRollbackDataRequest, asgStoreManifestsContent);

      Map<String, List<String>> asgManifestsDataForRollback =
          executePrepareRollbackData(asgSdkManager, logCallback, asgName);

      AsgPrepareRollbackDataResult asgPrepareRollbackDataResult =
          AsgPrepareRollbackDataResult.builder()
              .asgName(asgName)
              .asgManifestsDataForRollback(asgManifestsDataForRollback)
              .build();

      return AsgPrepareRollbackDataResponse.builder()
          .asgPrepareRollbackDataResult(asgPrepareRollbackDataResult)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();

    } catch (Exception e) {
      logCallback.saveExecutionLog(
          color(format("Prepare Rollback Data Failed with error: %s", asgTaskHelper.getExceptionMessage(e)),
              LogColor.Red, LogWeight.Bold),
          ERROR, CommandExecutionStatus.FAILURE);
      throw new AsgNGException(e);
    }
  }

  private Map<String, List<String>> executePrepareRollbackData(
      AsgSdkManager asgSdkManager, LogCallback logCallback, String asgName) {
    asgSdkManager.info("Preparing Rollback Data");
    if (isEmpty(asgName)) {
      throw new InvalidArgumentsException(Pair.of("AutoScalingGroup name", "Must not be empty"));
    }

    // Chain factory code to handle each manifest one by one in a chain
    AsgManifestHandlerChainState chainState =
        AsgManifestHandlerChainFactory.builder()
            .initialChainState(AsgManifestHandlerChainState.builder().asgName(asgName).build())
            .asgSdkManager(asgSdkManager)
            .build()
            .addHandler(AsgLaunchTemplate, AsgLaunchTemplateManifestRequest.builder().build())
            .addHandler(AsgConfiguration, AsgConfigurationManifestRequest.builder().build())
            .addHandler(AsgScalingPolicy, AsgScalingPolicyManifestRequest.builder().build())
            .addHandler(AsgScheduledUpdateGroupAction, AsgScheduledActionManifestRequest.builder().build())
            .getContent();

    if (chainState.getAutoScalingGroup() == null) {
      logCallback.saveExecutionLog(
          color(format("Asg %s doesn't exist. Skipping Prepare Rollback Data", asgName), White, LogWeight.Bold), INFO,
          CommandExecutionStatus.SUCCESS);
    } else {
      logCallback.saveExecutionLog(
          color("Prepare Rollback Data Finished Successfully", Green, Bold), INFO, CommandExecutionStatus.SUCCESS);
    }
    return chainState.getAsgManifestsDataForRollback();
  }
}
