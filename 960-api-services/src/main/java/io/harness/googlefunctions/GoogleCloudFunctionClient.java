package io.harness.googlefunctions;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.DeleteFunctionRequest;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.GetFunctionRequest;
import com.google.cloud.functions.v2.OperationMetadata;
import com.google.cloud.functions.v2.UpdateFunctionRequest;
import com.google.protobuf.Empty;

public interface GoogleCloudFunctionClient {

    Function getFunction(GetFunctionRequest getFunctionRequest, GcpInternalConfig gcpInternalConfig);

    OperationFuture<Function, OperationMetadata> createFunction(CreateFunctionRequest createFunctionRequest, GcpInternalConfig gcpInternalConfig);

    OperationFuture<Function, OperationMetadata> updateFunction(UpdateFunctionRequest updateFunctionRequest, GcpInternalConfig gcpInternalConfig);

    OperationFuture<Empty, OperationMetadata> deleteFunction(DeleteFunctionRequest deleteFunctionRequest, GcpInternalConfig gcpInternalConfig);
}
