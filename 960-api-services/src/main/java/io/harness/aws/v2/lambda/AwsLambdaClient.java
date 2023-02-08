/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.v2.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;

import java.util.Optional;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

@OwnedBy(CDP)
public interface AwsLambdaClient {
  CreateFunctionResponse createFunction(
      AwsInternalConfig awsInternalConfig, CreateFunctionRequest createFunctionRequest);
  DeleteFunctionResponse deleteFunction(
      AwsInternalConfig awsInternalConfig, DeleteFunctionRequest deleteFunctionRequest);
  InvokeResponse invokeFunction(AwsInternalConfig awsInternalConfig, InvokeRequest invokeRequest);
  Optional<GetFunctionResponse> getFunction(AwsInternalConfig awsInternalConfig, GetFunctionRequest getFunctionRequest);
}
