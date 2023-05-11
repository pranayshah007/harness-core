/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.googlefunctions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.functions.v1.CloudFunction;
import com.google.cloud.functions.v1.CloudFunctionsServiceClient;
import com.google.cloud.functions.v1.CreateFunctionRequest;
import com.google.cloud.functions.v1.DeleteFunctionRequest;
import com.google.cloud.functions.v1.GetFunctionRequest;
import com.google.cloud.functions.v1.OperationMetadataV1;
import com.google.cloud.functions.v1.UpdateFunctionRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.longrunning.Operation;
import com.google.protobuf.Empty;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class GoogleCloudFunctionGenOneClientImpl implements GoogleCloudFunctionGenOneClient {
  @Inject private GoogleCloudClientHelper googleCloudClientHelper;

  private static final String CLIENT_NAME = "Google Cloud Function Gen One";
  @Override
  public CloudFunction getFunction(GetFunctionRequest getFunctionRequest, GcpInternalConfig gcpInternalConfig) {
    try (CloudFunctionsServiceClient client = googleCloudClientHelper.getFunctionGenOneClient(gcpInternalConfig)) {
      googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return client.getFunction(getFunctionRequest);
    } catch (Exception e) {
      googleCloudClientHelper.logError(
          CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      googleCloudClientHelper.handleException(e);
    }
    return CloudFunction.getDefaultInstance();
  }

  @Override
  public OperationFuture<CloudFunction, OperationMetadataV1> createFunction(
      CreateFunctionRequest createFunctionRequest, GcpInternalConfig gcpInternalConfig) {
    try (CloudFunctionsServiceClient client = googleCloudClientHelper.getFunctionGenOneClient(gcpInternalConfig)) {
      googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return client.createFunctionAsync(createFunctionRequest);
    } catch (Exception e) {
      googleCloudClientHelper.logError(
          CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      googleCloudClientHelper.handleException(e);
    }
    return null;
  }

  @Override
  public OperationFuture<CloudFunction, OperationMetadataV1> updateFunction(
      UpdateFunctionRequest updateFunctionRequest, GcpInternalConfig gcpInternalConfig) {
    try (CloudFunctionsServiceClient client = googleCloudClientHelper.getFunctionGenOneClient(gcpInternalConfig)) {
      googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return client.updateFunctionAsync(updateFunctionRequest);
    } catch (Exception e) {
      googleCloudClientHelper.logError(
          CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      googleCloudClientHelper.handleException(e);
    }
    return null;
  }

  @Override
  public OperationFuture<Empty, OperationMetadataV1> deleteFunction(
      DeleteFunctionRequest deleteFunctionRequest, GcpInternalConfig gcpInternalConfig) {
    try (CloudFunctionsServiceClient client = googleCloudClientHelper.getFunctionGenOneClient(gcpInternalConfig)) {
      googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return client.deleteFunctionAsync(deleteFunctionRequest);
    } catch (Exception e) {
      googleCloudClientHelper.logError(
          CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      googleCloudClientHelper.handleException(e);
    }
    return null;
  }

  @Override
  public Operation getOperation(String operationName, GcpInternalConfig gcpInternalConfig) {
    try (CloudFunctionsServiceClient client = googleCloudClientHelper.getFunctionGenOneClient(gcpInternalConfig)) {
      googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return client.getOperationsClient().getOperation(operationName);
    } catch (Exception e) {
      googleCloudClientHelper.logError(
          CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      googleCloudClientHelper.handleException(e);
    }
    return null;
  }
}
