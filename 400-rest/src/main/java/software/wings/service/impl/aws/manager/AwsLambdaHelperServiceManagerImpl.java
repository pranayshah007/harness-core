/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.manager;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.TaskType;
import software.wings.service.impl.aws.model.AwsLambdaFunctionRequest;
import software.wings.service.impl.aws.model.AwsLambdaFunctionResponse;
import software.wings.service.impl.aws.model.AwsLambdaRequest;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.impl.aws.model.embed.AwsLambdaDetails;
import software.wings.service.impl.aws.model.request.AwsLambdaDetailsRequest;
import software.wings.service.impl.aws.model.response.AwsLambdaDetailsResponse;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.aws.manager.AwsLambdaHelperServiceManager;

import com.google.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Pranjal on 01/29/2019
 */
@Slf4j
@OwnedBy(CDP)
public class AwsLambdaHelperServiceManagerImpl implements AwsLambdaHelperServiceManager {
  private static final long TIME_OUT_IN_MINUTES = 2;
  @Inject private DelegateService delegateService;

  @Override
  public List<String> listLambdaFunctions(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    AwsResponse response = executeTask(awsConfig.getAccountId(),
        AwsLambdaFunctionRequest.builder()
            .awsConfig(awsConfig)
            .encryptionDetails(encryptionDetails)
            .region(region)
            .build());
    log.info("Listing Lambda functions for region {}", region);
    return ((AwsLambdaFunctionResponse) response).getLambdaFunctions();
  }

  @Override
  public AwsLambdaDetails getFunctionDetails(AwsLambdaDetailsRequest request) {
    AwsResponse awsResponse = executeTask(request.getAwsConfig().getAccountId(), request);
    return ((AwsLambdaDetailsResponse) awsResponse).getLambdaDetails();
  }

  private AwsResponse executeTask(String accountId, AwsLambdaRequest request) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AWS_LAMBDA_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(TimeUnit.MINUTES.toMillis(TIME_OUT_IN_MINUTES))
                      .build())
            .build();
    try {
      DelegateResponseData notifyResponseData = delegateService.executeTaskV2(delegateTask);
      if (notifyResponseData instanceof ErrorNotifyResponseData) {
        throw new GeneralException(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage());
      } else if (notifyResponseData instanceof AwsResponse
          && ((AwsResponse) notifyResponseData).getExecutionStatus() == ExecutionStatus.FAILED) {
        throw new GeneralException(((AwsResponse) notifyResponseData).getErrorMessage());
      }

      return (AwsResponse) notifyResponseData;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(ex.getMessage(), WingsException.USER);
    }
  }
}
