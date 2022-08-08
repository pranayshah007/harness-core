/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.service.impl.aws.model.AwsAsgGetRunningCountData;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountRequest;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountResponse;
import software.wings.service.impl.aws.model.AwsAsgListAllNamesResponse;
import software.wings.service.impl.aws.model.AwsAsgListDesiredCapacitiesRequest;
import software.wings.service.impl.aws.model.AwsAsgListDesiredCapacitiesResponse;
import software.wings.service.impl.aws.model.AwsAsgListInstancesRequest;
import software.wings.service.impl.aws.model.AwsAsgListInstancesResponse;
import software.wings.service.impl.aws.model.AwsAsgRequest;
import software.wings.service.impl.aws.model.AwsAsgRequest.AwsAsgRequestType;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.intfc.aws.delegate.AwsAsgHelperServiceDelegate;

import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsAsgTask extends AbstractDelegateRunnableTask {
  @Inject private AwsAsgHelperServiceDelegate awsAsgHelperServiceDelegate;

  public AwsAsgTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AwsResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public AwsResponse run(TaskParameters parameters) {
    AwsAsgRequest request = (AwsAsgRequest) parameters;
    try {
      AwsAsgRequestType requestType = request.getRequestType();
      switch (requestType) {
        case LIST_ALL_ASG_NAMES: {
          List<String> aSgNames = awsAsgHelperServiceDelegate.listAutoScalingGroupNames(
              request.getAwsConfig(), request.getEncryptionDetails(), request.getRegion());
          return AwsAsgListAllNamesResponse.builder().aSgNames(aSgNames).executionStatus(SUCCESS).build();
        }
        case LIST_ASG_INSTANCES: {
          AwsAsgListInstancesRequest awsAsgListInstancesRequest = (AwsAsgListInstancesRequest) parameters;
          List<Instance> aSgInstances = awsAsgHelperServiceDelegate.listAutoScalingGroupInstances(
              request.getAwsConfig(), request.getEncryptionDetails(), request.getRegion(),
              awsAsgListInstancesRequest.getAutoScalingGroupName(), false);
          return AwsAsgListInstancesResponse.builder().instances(aSgInstances).executionStatus(SUCCESS).build();
        }
        case LIST_DESIRED_CAPACITIES: {
          AwsAsgListDesiredCapacitiesRequest awsAsgListDesiredCapacitiesRequest =
              (AwsAsgListDesiredCapacitiesRequest) parameters;
          Map<String, Integer> capacities =
              awsAsgHelperServiceDelegate.getDesiredCapacitiesOfAsgs(request.getAwsConfig(),
                  request.getEncryptionDetails(), request.getRegion(), awsAsgListDesiredCapacitiesRequest.getAsgs());
          return AwsAsgListDesiredCapacitiesResponse.builder().capacities(capacities).executionStatus(SUCCESS).build();
        }
        case GET_RUNNING_COUNT: {
          AwsAsgGetRunningCountRequest asgGetRunningCountRequest = (AwsAsgGetRunningCountRequest) parameters;
          AwsAsgGetRunningCountData data = awsAsgHelperServiceDelegate.getCurrentlyRunningInstanceCount(
              asgGetRunningCountRequest.getAwsConfig(), asgGetRunningCountRequest.getEncryptionDetails(),
              asgGetRunningCountRequest.getRegion(), asgGetRunningCountRequest.getInfraMappingId());
          return AwsAsgGetRunningCountResponse.builder().data(data).executionStatus(SUCCESS).build();
        }
        default: {
          throw new InvalidRequestException("Invalid request type [" + requestType + "]", WingsException.USER);
        }
      }
    } catch (WingsException exception) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(exception);
    } catch (Exception ex) {
      throw new InvalidRequestException(
          ExceptionMessageSanitizer.sanitizeException(ex).getMessage(), WingsException.USER);
    }
  }
}
