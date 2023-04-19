/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.googlefunctions;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.functions.v1.CloudFunction;
import com.google.cloud.functions.v1.CreateFunctionRequest;
import com.google.cloud.functions.v1.DeleteFunctionRequest;
import com.google.cloud.functions.v1.GetFunctionRequest;
import com.google.cloud.functions.v1.OperationMetadataV1;
import com.google.cloud.functions.v1.UpdateFunctionRequest;
import com.google.longrunning.Operation;
import com.google.protobuf.Empty;

public interface GoogleCloudFunctionGenOneClient {
  CloudFunction getFunction(GetFunctionRequest getFunctionRequest, GcpInternalConfig gcpInternalConfig);

  OperationFuture<CloudFunction, OperationMetadataV1> createFunction(
      CreateFunctionRequest createFunctionRequest, GcpInternalConfig gcpInternalConfig);

  OperationFuture<CloudFunction, OperationMetadataV1> updateFunction(
      UpdateFunctionRequest updateFunctionRequest, GcpInternalConfig gcpInternalConfig);

  OperationFuture<Empty, OperationMetadataV1> deleteFunction(
      DeleteFunctionRequest deleteFunctionRequest, GcpInternalConfig gcpInternalConfig);

  Operation getOperation(String operationName, GcpInternalConfig gcpInternalConfig);
}
