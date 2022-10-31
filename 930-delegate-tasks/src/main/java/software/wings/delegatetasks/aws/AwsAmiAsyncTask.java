/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.impl.aws.model.AwsAmiRequest;
import software.wings.service.impl.aws.model.AwsAmiRequest.AwsAmiRequestType;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupResponse;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbDeployRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesRequest;
import software.wings.service.impl.aws.model.AwsAmiTrafficShiftAlbSwitchRouteRequest;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.intfc.aws.delegate.AwsAmiHelperServiceDelegate;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsAmiAsyncTask extends AbstractDelegateRunnableTask {
  @Inject private DelegateLogService delegateLogService;
  @Inject private AwsAmiHelperServiceDelegate awsAmiHelperServiceDelegate;

  public AwsAmiAsyncTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AwsResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public AwsResponse run(Object[] parameters) {
    AwsAmiRequest request = (AwsAmiRequest) parameters[0];
    try {
      AwsAmiRequestType requestType = request.getRequestType();
      switch (requestType) {
        case EXECUTE_AMI_SERVICE_SETUP: {
          AwsAmiServiceSetupRequest awsAmiServiceSetupRequest = (AwsAmiServiceSetupRequest) request;
          ExecutionLogCallback logCallback = new ExecutionLogCallback(delegateLogService,
              awsAmiServiceSetupRequest.getAccountId(), awsAmiServiceSetupRequest.getAppId(),
              awsAmiServiceSetupRequest.getActivityId(), awsAmiServiceSetupRequest.getCommandName());
          return awsAmiHelperServiceDelegate.setUpAmiService(awsAmiServiceSetupRequest, logCallback);
        }
        case EXECUTE_AMI_SERVICE_DEPLOY: {
          AwsAmiServiceDeployRequest deployRequest = (AwsAmiServiceDeployRequest) request;
          ExecutionLogCallback logCallback = new ExecutionLogCallback(delegateLogService, deployRequest.getAccountId(),
              deployRequest.getAppId(), deployRequest.getActivityId(), deployRequest.getCommandName());
          return awsAmiHelperServiceDelegate.deployAmiService(deployRequest, logCallback);
        }
        case EXECUTE_AMI_SWITCH_ROUTE: {
          AwsAmiSwitchRoutesRequest switchRoutesRequest = (AwsAmiSwitchRoutesRequest) request;
          ExecutionLogCallback logCallback = new ExecutionLogCallback(delegateLogService,
              switchRoutesRequest.getAccountId(), switchRoutesRequest.getAppId(), switchRoutesRequest.getActivityId(),
              switchRoutesRequest.getCommandName());
          if (switchRoutesRequest.isRollback()) {
            return awsAmiHelperServiceDelegate.rollbackSwitchAmiRoutes(switchRoutesRequest, logCallback);
          } else {
            return awsAmiHelperServiceDelegate.switchAmiRoutes(switchRoutesRequest, logCallback);
          }
        }
        case EXECUTE_AMI_SERVICE_TRAFFIC_SHIFT_ALB_SETUP:
          return awsAmiHelperServiceDelegate.setUpAmiServiceTrafficShift(
              (AwsAmiServiceTrafficShiftAlbSetupRequest) request);

        case EXECUTE_AMI_SERVICE_TRAFFIC_SHIFT_ALB_DEPLOY:
          return awsAmiHelperServiceDelegate.deployAmiServiceTrafficShift(
              (AwsAmiServiceTrafficShiftAlbDeployRequest) request);

        case EXECUTE_AMI_SERVICE_TRAFFIC_SHIFT_ALB:
          return performAwsAmiTrafficShiftSwitchRoute((AwsAmiTrafficShiftAlbSwitchRouteRequest) request);

        default: {
          throw new InvalidRequestException("Invalid request type [" + requestType + "]", WingsException.USER);
        }
      }
    } catch (Exception ex) {
      return AwsAmiServiceSetupResponse.builder()
          .executionStatus(FAILED)
          .errorMessage(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)))
          .build();
    }
  }

  private AwsResponse performAwsAmiTrafficShiftSwitchRoute(AwsAmiTrafficShiftAlbSwitchRouteRequest request) {
    return request.isRollback() ? awsAmiHelperServiceDelegate.rollbackSwitchAmiRoutesTrafficShift(request)
                                : awsAmiHelperServiceDelegate.switchAmiRoutesTrafficShift(request);
  }
}
