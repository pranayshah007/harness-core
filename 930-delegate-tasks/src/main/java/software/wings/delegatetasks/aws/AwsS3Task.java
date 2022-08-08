/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SUCCESS;

import static software.wings.service.impl.aws.model.AwsS3Request.AwsS3RequestType.LIST_BUCKET_NAMES;

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

import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.impl.aws.model.AwsS3ListBucketNamesResponse;
import software.wings.service.impl.aws.model.AwsS3Request;
import software.wings.service.impl.aws.model.AwsS3Request.AwsS3RequestType;
import software.wings.service.intfc.aws.delegate.AwsS3HelperServiceDelegate;

import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsS3Task extends AbstractDelegateRunnableTask {
  @Inject private AwsS3HelperServiceDelegate s3HelperServiceDelegate;

  public AwsS3Task(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AwsResponse run(TaskParameters parameters) {
    AwsS3Request request = (AwsS3Request) parameters;
    try {
      AwsS3RequestType requestType = request.getRequestType();
      if (LIST_BUCKET_NAMES == requestType) {
        List<String> bucketNames =
            s3HelperServiceDelegate.listBucketNames(request.getAwsConfig(), request.getEncryptionDetails());
        return AwsS3ListBucketNamesResponse.builder().bucketNames(bucketNames).executionStatus(SUCCESS).build();
      } else {
        throw new InvalidRequestException("Invalid request type [" + requestType + "]", WingsException.USER);
      }
    } catch (WingsException exception) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(exception);
    } catch (Exception ex) {
      throw new InvalidRequestException(
          ExceptionMessageSanitizer.sanitizeException(ex).getMessage(), WingsException.USER);
    }
  }

  @Override
  public AwsResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }
}
