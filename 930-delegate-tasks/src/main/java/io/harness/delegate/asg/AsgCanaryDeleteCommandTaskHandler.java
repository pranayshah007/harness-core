/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.asg;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.delegate.beans.aws.asg.AsgCanaryDeleteResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.AsgInfraConfigHelper;
import io.harness.delegate.task.aws.AsgTaskHelperBase;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResponse;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;

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
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(asgCommandRequest instanceof AsgCanaryDeleteRequest)) {
      throw new InvalidArgumentsException(Pair.of("asgCommandRequest", "Must be instance of AsgCanaryDeleteRequest"));
    }

    AsgCanaryDeleteRequest asgCanaryDeleteRequest = (AsgCanaryDeleteRequest) asgCommandRequest;
    timeoutInMillis = asgCanaryDeleteRequest.getTimeoutIntervalInMin() * 60000;
    asgInfraConfig = asgCanaryDeleteRequest.getAsgInfraConfig();

    LogCallback canaryDeleteLogCallback = asgTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, AsgCommandUnitConstants.deleteService.toString(), true, commandUnitsProgress);

    String asgLaunchTemplateContent = asgCanaryDeleteRequest.getAsgLaunchTemplateContent();

    CreateServiceRequest.Builder createServiceRequestBuilder = asgCommandTaskHelper.parseYamlAsObject(
            asgLaunchTemplateContent, CreateServiceRequest.serializableBuilderClass());

    CreateServiceRequest createServiceRequest = createServiceRequestBuilder.build();

    String canaryServiceName = createServiceRequest.serviceName() + asgCanaryDeleteRequest.getAsgServiceNameSuffix();

    Optional<Service> optionalService = asgCommandTaskHelper.describeService(
        canaryServiceName, asgInfraConfig.getRegion(), asgInfraConfig.getAwsConnectorDTO());

    AsgCanaryDeleteResult asgCanaryDeleteResult = null;

    if (optionalService.isPresent() && asgCommandTaskHelper.isServiceActive(optionalService.get())) {
      canaryDeleteLogCallback.saveExecutionLog(format("Deleting service %s..", canaryServiceName), LogLevel.INFO);

      asgCommandTaskHelper.deleteService(
          canaryServiceName, asgInfraConfig.getRegion(), asgInfraConfig.getAwsConnectorDTO());

      asgCommandTaskHelper.asgServiceInactiveStateCheck(canaryDeleteLogCallback, asgInfraConfig.getAwsConnectorDTO(),
          canaryServiceName, asgInfraConfig.getRegion(), (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));

      asgCanaryDeleteResult =
          AsgCanaryDeleteResult.builder().canaryDeleted(true).canaryServiceName(canaryServiceName).build();

      canaryDeleteLogCallback.saveExecutionLog(
          format("Canary service %s deleted", canaryServiceName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } else {
      canaryDeleteLogCallback.saveExecutionLog(
          format("Canary service %s doesn't exist", canaryServiceName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      asgCanaryDeleteResult =
          AsgCanaryDeleteResult.builder().canaryDeleted(false).canaryServiceName(canaryServiceName).build();
    }
    return AsgCanaryDeleteResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .asgCanaryDeleteResult(asgCanaryDeleteResult)
        .build();
  }
}
