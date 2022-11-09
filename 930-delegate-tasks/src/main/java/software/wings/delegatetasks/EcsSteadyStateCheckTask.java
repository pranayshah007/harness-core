/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static com.amazonaws.util.CollectionUtils.isNullOrEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TimeoutException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.EcsSteadyStateCheckParams;
import software.wings.beans.container.EcsSteadyStateCheckResponse;
import software.wings.cloudprovider.UpdateServiceCountRequestData;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.service.impl.AwsHelperService;

import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.Service;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class EcsSteadyStateCheckTask extends AbstractDelegateRunnableTask {
  @Inject private AwsHelperService awsHelperService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private EcsContainerService ecsContainerService;

  public EcsSteadyStateCheckTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  private Service getService(String region, AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails,
      String clusterName, String serviceName) {
    List<Service> services = awsHelperService
                                 .describeServices(region, awsConfig, encryptedDataDetails,
                                     new DescribeServicesRequest().withCluster(clusterName).withServices(serviceName))
                                 .getServices();
    if (isNullOrEmpty(services)) {
      throw new InvalidRequestException(
          String.format("No service in cluster: %s with name: %s", clusterName, serviceName));
    }
    return services.get(0);
  }

  @Override
  public EcsSteadyStateCheckResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public EcsSteadyStateCheckResponse run(Object[] parameters) {
    EcsSteadyStateCheckParams params = (EcsSteadyStateCheckParams) parameters[0];
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback(
        delegateLogService, params.getAccountId(), params.getAppId(), params.getActivityId(), params.getCommandName());
    try {
      // First poll to see what is the service state right now.
      executionLogCallback.saveExecutionLog(String.format("Getting details about Ecs service: %s in Ecs cluster: %s.",
          params.getServiceName(), params.getClusterName()));
      Service service = getService(params.getRegion(), params.getAwsConfig(), params.getEncryptionDetails(),
          params.getClusterName(), params.getServiceName());

      // Now wait for runningCount == desiredCount
      executionLogCallback.saveExecutionLog(
          String.format("Waiting for running count to reach: %d for Ecs service: %s in Ecs cluster: %s.",
              service.getDesiredCount(), params.getServiceName(), params.getClusterName()));

      int timeoutInterval = (int) TimeUnit.MILLISECONDS.toMinutes(params.getTimeoutInMs());
      if (timeoutInterval < 10) {
        timeoutInterval = 10;
      }

      UpdateServiceCountRequestData updateCountRequestData =
          UpdateServiceCountRequestData.builder()
              .region(params.getRegion())
              .encryptedDataDetails(params.getEncryptionDetails())
              .cluster(params.getClusterName())
              .desiredCount(service.getDesiredCount())
              .executionLogCallback(executionLogCallback)
              .serviceName(params.getServiceName())
              .serviceEvents(ecsContainerService.getEventsFromService(service))
              .awsConfig(params.getAwsConfig())
              .timeOut(timeoutInterval)
              .build();
      ecsContainerService.waitForTasksToBeInRunningStateWithHandledExceptions(updateCountRequestData);

      // Now poll for events API to notify of steady state
      executionLogCallback.saveExecutionLog(
          String.format("Starting to wait for steady state for Ecs service: %s in Ecs cluster: %s.",
              params.getServiceName(), params.getClusterName()));
      ecsContainerService.waitForServiceToReachSteadyState(timeoutInterval, updateCountRequestData);
      executionLogCallback.saveExecutionLog(String.format(
          "Completed wait for steady state for Ecs service: %s in Ecs cluster: %s. Now getting all container infos.",
          params.getServiceName(), params.getClusterName()));

      // Now get all the container infos
      List<ContainerInfo> containerInfos = ecsContainerService.getContainerInfosAfterEcsWait(params.getRegion(),
          params.getAwsConfig(), params.getEncryptionDetails(), params.getClusterName(), params.getServiceName(),
          new ArrayList<>(), executionLogCallback);
      executionLogCallback.saveExecutionLog("Got all the container infos", LogLevel.INFO);
      return EcsSteadyStateCheckResponse.builder()
          .executionStatus(ExecutionStatus.SUCCESS)
          .containerInfoList(containerInfos)
          .build();
    } catch (TimeoutException ex) {
      Exception sanitiseException = ExceptionMessageSanitizer.sanitizeException(ex);
      String errorMessage = String.format("Timeout Exception: %s while waiting for ECS steady state for activity: %s",
          sanitiseException.getMessage(), params.getActivityId());
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
      log.error(errorMessage, sanitiseException);
      EcsSteadyStateCheckResponse response = EcsSteadyStateCheckResponse.builder()
                                                 .executionStatus(ExecutionStatus.FAILED)
                                                 .errorMessage(errorMessage)
                                                 .build();
      if (params.isTimeoutErrorSupported()) {
        response.setTimeoutFailure(true);
      }

      return response;
    } catch (Exception ex) {
      Exception sanitiseException = ExceptionMessageSanitizer.sanitizeException(ex);
      String errorMessage = String.format("Exception: %s while waiting for ECS steady state for activity: %s",
          sanitiseException.getMessage(), params.getActivityId());
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
      log.error(errorMessage, sanitiseException);
      return EcsSteadyStateCheckResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(errorMessage)
          .build();
    }
  }
}
