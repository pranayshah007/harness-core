/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;

import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegateNG;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class AwsLambdaCommandTaskHelper {
  @Inject private AwsLambdaHelperServiceDelegateNG awsLambdaHelperServiceDelegateNG;

  public void createFunction(String region, AwsInternalConfig awsInternalConfig) {
    getAwsLambdaClient(region, awsInternalConfig).createFunction(new CreateFunctionRequest());
  }

  public void deployFunction(String region, AwsInternalConfig awsInternalConfig) {
    getAwsLambdaClient(region, awsInternalConfig).invoke(new InvokeRequest());
  }

  public void deleteFunction(String region, AwsInternalConfig awsInternalConfig) {
    getAwsLambdaClient(region, awsInternalConfig).deleteFunction(new DeleteFunctionRequest());
  }

  public AWSLambda getAwsLambdaClient(String region, AwsInternalConfig awsInternalConfig) {
    return awsLambdaHelperServiceDelegateNG.getAmazonLambdaClient(region, awsInternalConfig);
  }
}
