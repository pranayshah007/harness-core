/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.googlefunctions;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.DeleteFunctionRequest;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.GetFunctionRequest;
import com.google.cloud.functions.v2.ListFunctionsRequest;
import com.google.cloud.functions.v2.ListFunctionsResponse;
import com.google.cloud.functions.v2.OperationMetadata;
import com.google.cloud.functions.v2.UpdateFunctionRequest;
import com.google.longrunning.Operation;
import com.google.protobuf.Empty;

public interface GoogleCloudFunctionClient {
  Function getFunction(GetFunctionRequest getFunctionRequest, GcpInternalConfig gcpInternalConfig);

  OperationFuture<Function, OperationMetadata> createFunction(
      CreateFunctionRequest createFunctionRequest, GcpInternalConfig gcpInternalConfig);

  OperationFuture<Function, OperationMetadata> updateFunction(
      UpdateFunctionRequest updateFunctionRequest, GcpInternalConfig gcpInternalConfig);

  OperationFuture<Empty, OperationMetadata> deleteFunction(
      DeleteFunctionRequest deleteFunctionRequest, GcpInternalConfig gcpInternalConfig);

  ListFunctionsResponse listFunction(ListFunctionsRequest listFunctionsRequest, GcpInternalConfig gcpInternalConfig);

  Operation getOperation(String operationName, GcpInternalConfig gcpInternalConfig);
}
