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

import software.wings.service.impl.aws.model.AwsEc2ListInstancesRequest;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesResponse;
import software.wings.service.impl.aws.model.AwsEc2ListRegionsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListSGsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListSGsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListSubnetsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListSubnetsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListTagsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListTagsResponse;
import software.wings.service.impl.aws.model.AwsEc2ListVpcsRequest;
import software.wings.service.impl.aws.model.AwsEc2ListVpcsResponse;
import software.wings.service.impl.aws.model.AwsEc2Request;
import software.wings.service.impl.aws.model.AwsEc2Request.AwsEc2RequestType;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.impl.aws.model.AwsSecurityGroup;
import software.wings.service.impl.aws.model.AwsSubnet;
import software.wings.service.impl.aws.model.AwsVPC;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;

import com.amazonaws.services.ec2.model.Instance;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsEc2Task extends AbstractDelegateRunnableTask {
  @Inject private AwsEc2HelperServiceDelegate ec2ServiceDelegate;

  public AwsEc2Task(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AwsResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public AwsResponse run(Object[] parameters) {
    AwsEc2Request request = (AwsEc2Request) parameters[0];
    try {
      AwsEc2RequestType requestType = request.getRequestType();
      switch (requestType) {
        case VALIDATE_CREDENTIALS: {
          return ec2ServiceDelegate.validateAwsAccountCredential(
              request.getAwsConfig(), request.getEncryptionDetails());
        }
        case LIST_REGIONS: {
          List<String> regions = ec2ServiceDelegate.listRegions(request.getAwsConfig(), request.getEncryptionDetails());
          return AwsEc2ListRegionsResponse.builder().regions(regions).executionStatus(SUCCESS).build();
        }
        case LIST_VPCS: {
          List<AwsVPC> vpcs = ec2ServiceDelegate.listVPCs(
              request.getAwsConfig(), request.getEncryptionDetails(), ((AwsEc2ListVpcsRequest) request).getRegion());
          return AwsEc2ListVpcsResponse.builder().vpcs(vpcs).executionStatus(SUCCESS).build();
        }
        case LIST_SUBNETS: {
          List<AwsSubnet> subnets =
              ec2ServiceDelegate.listSubnets(request.getAwsConfig(), request.getEncryptionDetails(),
                  ((AwsEc2ListSubnetsRequest) request).getRegion(), ((AwsEc2ListSubnetsRequest) request).getVpcIds());
          return AwsEc2ListSubnetsResponse.builder().subnets(subnets).executionStatus(SUCCESS).build();
        }
        case LIST_SGS: {
          List<AwsSecurityGroup> securityGroups =
              ec2ServiceDelegate.listSGs(request.getAwsConfig(), request.getEncryptionDetails(),
                  ((AwsEc2ListSGsRequest) request).getRegion(), ((AwsEc2ListSGsRequest) request).getVpcIds());
          return AwsEc2ListSGsResponse.builder().securityGroups(securityGroups).executionStatus(SUCCESS).build();
        }
        case LIST_TAGS: {
          Set<String> tags = ec2ServiceDelegate.listTags(request.getAwsConfig(), request.getEncryptionDetails(),
              ((AwsEc2ListTagsRequest) request).getRegion(), ((AwsEc2ListTagsRequest) request).getResourceType());
          return AwsEc2ListTagsResponse.builder().tags(tags).executionStatus(SUCCESS).build();
        }
        case LIST_INSTANCES: {
          List<Instance> instances = ec2ServiceDelegate.listEc2Instances(request.getAwsConfig(),
              request.getEncryptionDetails(), ((AwsEc2ListInstancesRequest) request).getRegion(),
              ((AwsEc2ListInstancesRequest) request).getFilters(), false);
          return AwsEc2ListInstancesResponse.builder().instances(instances).executionStatus(SUCCESS).build();
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
