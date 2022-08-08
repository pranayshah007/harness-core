/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SUCCESS;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.AwsLoadBalancerDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.service.impl.aws.model.AwsElbListAppElbsResponse;
import software.wings.service.impl.aws.model.AwsElbListClassicElbsResponse;
import software.wings.service.impl.aws.model.AwsElbListListenerRequest;
import software.wings.service.impl.aws.model.AwsElbListListenerResponse;
import software.wings.service.impl.aws.model.AwsElbListTargetGroupsRequest;
import software.wings.service.impl.aws.model.AwsElbListTargetGroupsResponse;
import software.wings.service.impl.aws.model.AwsElbRequest;
import software.wings.service.impl.aws.model.AwsElbRequest.AwsElbRequestType;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

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
public class AwsElbTask extends AbstractDelegateRunnableTask {
  @Inject private AwsElbHelperServiceDelegate elbHelperServiceDelegate;

  public AwsElbTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AwsResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public AwsResponse run(Object[] parameters) {
    AwsElbRequest request = (AwsElbRequest) parameters[0];
    try {
      AwsElbRequestType requestType = request.getRequestType();
      switch (requestType) {
        case LIST_CLASSIC_ELBS: {
          List<String> classicLbs = elbHelperServiceDelegate.listClassicLoadBalancers(
              request.getAwsConfig(), request.getEncryptionDetails(), request.getRegion());
          return AwsElbListClassicElbsResponse.builder().classicElbs(classicLbs).executionStatus(SUCCESS).build();
        }
        case LIST_APPLICATION_LBS: {
          List<AwsLoadBalancerDetails> applicationLbs = elbHelperServiceDelegate.listApplicationLoadBalancerDetails(
              request.getAwsConfig(), request.getEncryptionDetails(), request.getRegion());
          return AwsElbListAppElbsResponse.builder().appElbs(applicationLbs).executionStatus(SUCCESS).build();
        }
        case LIST_NETWORK_LBS: {
          List<AwsLoadBalancerDetails> applicationLbs = elbHelperServiceDelegate.listNetworkLoadBalancerDetails(
              request.getAwsConfig(), request.getEncryptionDetails(), request.getRegion());
          return AwsElbListAppElbsResponse.builder().appElbs(applicationLbs).executionStatus(SUCCESS).build();
        }
        case LIST_ELB_LBS: {
          List<AwsLoadBalancerDetails> applicationLbs = elbHelperServiceDelegate.listElasticLoadBalancerDetails(
              request.getAwsConfig(), request.getEncryptionDetails(), request.getRegion());
          return AwsElbListAppElbsResponse.builder().appElbs(applicationLbs).executionStatus(SUCCESS).build();
        }
        case LIST_TARGET_GROUPS_FOR_ALBS: {
          Map<String, String> targetGroups =
              elbHelperServiceDelegate.listTargetGroupsForAlb(request.getAwsConfig(), request.getEncryptionDetails(),
                  request.getRegion(), ((AwsElbListTargetGroupsRequest) request).getLoadBalancerName());
          return AwsElbListTargetGroupsResponse.builder().targetGroups(targetGroups).executionStatus(SUCCESS).build();
        }
        case LIST_LISTENER_FOR_ELB: {
          List<AwsElbListener> listeners = elbHelperServiceDelegate.getElbListenersForLoadBalaner(
              request.getAwsConfig(), request.getEncryptionDetails(), request.getRegion(),
              ((AwsElbListListenerRequest) request).getLoadBalancerName());
          return AwsElbListListenerResponse.builder().awsElbListeners(listeners).executionStatus(SUCCESS).build();
        }
        default: {
          throw new InvalidRequestException("Invalid request type [" + requestType + "]", WingsException.USER);
        }
      }
    } catch (WingsException exception) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(exception);
    } catch (Exception exception) {
      throw new InvalidRequestException(
          ExceptionMessageSanitizer.sanitizeException(exception).getMessage(), WingsException.USER);
    }
  }

  private List<String> generateLoadBalancerNamesList(List<AwsLoadBalancerDetails> details) {
    return details.stream().map(AwsLoadBalancerDetails::getName).collect(toList());
  }
}
