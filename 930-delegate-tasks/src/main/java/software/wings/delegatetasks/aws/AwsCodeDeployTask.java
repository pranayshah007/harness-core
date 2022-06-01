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

import software.wings.service.impl.aws.model.AwsCodeDeployListAppResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListAppRevisionRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListAppRevisionResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentConfigResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentGroupRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentGroupResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentInstancesRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentInstancesResponse;
import software.wings.service.impl.aws.model.AwsCodeDeployRequest;
import software.wings.service.impl.aws.model.AwsCodeDeployRequest.AwsCodeDeployRequestType;
import software.wings.service.impl.aws.model.AwsCodeDeployS3LocationData;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.intfc.aws.delegate.AwsCodeDeployHelperServiceDelegate;

import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsCodeDeployTask extends AbstractDelegateRunnableTask {
  @Inject private AwsCodeDeployHelperServiceDelegate awsCodeDeployHelperServiceDelegate;

  public AwsCodeDeployTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AwsResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public AwsResponse run(Object[] parameters) {
    AwsCodeDeployRequest request = (AwsCodeDeployRequest) parameters[0];
    AwsCodeDeployRequestType requestType = request.getRequestType();
    try {
      switch (requestType) {
        case LIST_APPLICATIONS: {
          List<String> applications = awsCodeDeployHelperServiceDelegate.listApplications(
              request.getAwsConfig(), request.getEncryptionDetails(), request.getRegion());
          return AwsCodeDeployListAppResponse.builder().applications(applications).executionStatus(SUCCESS).build();
        }
        case LIST_DEPLOYMENT_CONFIGURATION: {
          List<String> configs = awsCodeDeployHelperServiceDelegate.listDeploymentConfiguration(
              request.getAwsConfig(), request.getEncryptionDetails(), request.getRegion());
          return AwsCodeDeployListDeploymentConfigResponse.builder()
              .deploymentConfig(configs)
              .executionStatus(SUCCESS)
              .build();
        }
        case LIST_DEPLOYMENT_GROUP: {
          List<String> groups = awsCodeDeployHelperServiceDelegate.listDeploymentGroups(request.getAwsConfig(),
              request.getEncryptionDetails(), request.getRegion(),
              ((AwsCodeDeployListDeploymentGroupRequest) request).getAppName());
          return AwsCodeDeployListDeploymentGroupResponse.builder()
              .deploymentGroups(groups)
              .executionStatus(SUCCESS)
              .build();
        }
        case LIST_DEPLOYMENT_INSTANCES: {
          List<Instance> instances = awsCodeDeployHelperServiceDelegate.listDeploymentInstances(request.getAwsConfig(),
              request.getEncryptionDetails(), request.getRegion(),
              ((AwsCodeDeployListDeploymentInstancesRequest) request).getDeploymentId());
          return AwsCodeDeployListDeploymentInstancesResponse.builder()
              .instances(instances)
              .executionStatus(SUCCESS)
              .build();
        }
        case LIST_APP_REVISION: {
          AwsCodeDeployS3LocationData s3LocationData =
              awsCodeDeployHelperServiceDelegate.listAppRevision(request.getAwsConfig(), request.getEncryptionDetails(),
                  request.getRegion(), ((AwsCodeDeployListAppRevisionRequest) request).getAppName(),
                  ((AwsCodeDeployListAppRevisionRequest) request).getDeploymentGroupName());
          return AwsCodeDeployListAppRevisionResponse.builder()
              .s3LocationData(s3LocationData)
              .executionStatus(SUCCESS)
              .build();
        }
        default: {
          throw new InvalidRequestException("Invalid request type [" + requestType + "]", WingsException.USER);
        }
      }
    } catch (WingsException exception) {
      throw exception;
    } catch (Exception ex) {
      throw new InvalidRequestException(ex.getMessage(), WingsException.USER);
    }
  }
}
