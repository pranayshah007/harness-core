package io.harness.googlefunctions;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.DeleteFunctionRequest;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.FunctionServiceClient;
import com.google.cloud.functions.v2.GetFunctionRequest;
import com.google.cloud.functions.v2.OperationMetadata;
import com.google.cloud.functions.v2.UpdateFunctionRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Empty;
import io.harness.annotations.dev.OwnedBy;
import lombok.extern.slf4j.Slf4j;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class GoogleCloudFunctionClientImpl implements GoogleCloudFunctionClient {

    @Inject private GoogleCloudClientHelper googleCloudClientHelper;

    private static final String CLIENT_NAME = "Google Cloud Function";

    @Override
    public Function getFunction(GetFunctionRequest getFunctionRequest, GcpInternalConfig gcpInternalConfig) {
        try(FunctionServiceClient client = googleCloudClientHelper.getFunctionsClient(gcpInternalConfig)){
            googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
            return client.getFunction(getFunctionRequest);

        } catch (Exception e) {
            googleCloudClientHelper.logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
            googleCloudClientHelper.handleException(e);
        }
        return Function.getDefaultInstance();
    }

    @Override
    public OperationFuture<Function, OperationMetadata> createFunction(CreateFunctionRequest createFunctionRequest, GcpInternalConfig gcpInternalConfig) {
        try(FunctionServiceClient client = googleCloudClientHelper.getFunctionsClient(gcpInternalConfig)){
            googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
            return client.createFunctionAsync(createFunctionRequest);

        } catch (Exception e) {
            googleCloudClientHelper.logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
            googleCloudClientHelper.handleException(e);
        }
        return null;
    }

    @Override
    public OperationFuture<Function, OperationMetadata> updateFunction(UpdateFunctionRequest updateFunctionRequest, GcpInternalConfig gcpInternalConfig) {
        try(FunctionServiceClient client = googleCloudClientHelper.getFunctionsClient(gcpInternalConfig)){
            googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
            return client.updateFunctionAsync(updateFunctionRequest);

        } catch (Exception e) {
            googleCloudClientHelper.logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
            googleCloudClientHelper.handleException(e);
        }
        return null;
    }

    @Override
    public OperationFuture<Empty, OperationMetadata> deleteFunction(DeleteFunctionRequest deleteFunctionRequest, GcpInternalConfig gcpInternalConfig) {
        try(FunctionServiceClient client = googleCloudClientHelper.getFunctionsClient(gcpInternalConfig)){
            googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
             client.deleteFunctionAsync(deleteFunctionRequest);

        } catch (Exception e) {
            googleCloudClientHelper.logError(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
            googleCloudClientHelper.handleException(e);
        }
        return null;
    }

}
