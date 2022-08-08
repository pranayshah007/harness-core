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

import software.wings.service.impl.aws.model.AwsCFGetTemplateParamsRequest;
import software.wings.service.impl.aws.model.AwsCFGetTemplateParamsResponse;
import software.wings.service.impl.aws.model.AwsCFRequest;
import software.wings.service.impl.aws.model.AwsCFRequest.AwsCFRequestType;
import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.intfc.aws.delegate.AwsCFHelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.AwsConfigToInternalMapper;

import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsCFTask extends AbstractDelegateRunnableTask {
  @Inject private AwsCFHelperServiceDelegate awsCFHelperServiceDelegate;
  @Inject private EncryptionService encryptionService;

  public AwsCFTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AwsResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public AwsResponse run(Object[] parameters) {
    AwsCFRequest request = (AwsCFRequest) parameters[0];
    try {
      AwsCFRequestType requestType = request.getRequestType();
      switch (requestType) {
        case GET_TEMPLATE_PARAMETERS: {
          AwsCFGetTemplateParamsRequest paramsRequest = (AwsCFGetTemplateParamsRequest) request;
          encryptionService.decrypt(paramsRequest.getAwsConfig(), paramsRequest.getEncryptionDetails(), false);
          List<AwsCFTemplateParamsData> paramsData = awsCFHelperServiceDelegate.getParamsData(
              AwsConfigToInternalMapper.toAwsInternalConfig(paramsRequest.getAwsConfig()), paramsRequest.getRegion(),
              paramsRequest.getData(), paramsRequest.getType(), paramsRequest.getGitFileConfig(),
              paramsRequest.getGitConfig(), paramsRequest.getSourceRepoEncryptionDetails());
          return AwsCFGetTemplateParamsResponse.builder().executionStatus(SUCCESS).parameters(paramsData).build();
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
