package io.harness.delegate.task.googlefunction;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.DeleteFunctionRequest;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.GetFunctionRequest;
import com.google.cloud.functions.v2.OperationMetadata;
import com.google.cloud.functions.v2.UpdateFunctionRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Empty;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.runtime.SecretNotFoundRuntimeException;
import io.harness.googlefunctions.GcpInternalConfig;
import io.harness.googlefunctions.GoogleCloudFunctionClient;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class GoogleFunctionCommandTaskHelper {
    @Inject private GoogleCloudFunctionClient googleCloudFunctionClient;


    public Function createFunction(CreateFunctionRequest createFunctionRequest, GcpConnectorDTO gcpConnectorDTO,
                                   String project, String region) throws ExecutionException, InterruptedException {
        OperationFuture<Function, OperationMetadata> futureResponse = googleCloudFunctionClient.createFunction(createFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region,
                project));
        return futureResponse.get();
    }

    public Function updateFunction(UpdateFunctionRequest updateFunctionRequest, GcpConnectorDTO gcpConnectorDTO,
                                   String project, String region) throws ExecutionException, InterruptedException {
        OperationFuture<Function, OperationMetadata> futureResponse = googleCloudFunctionClient.updateFunction(
                updateFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region,
                project));
        return futureResponse.get();
    }

    public Function getFunction(String functionName, GcpConnectorDTO gcpConnectorDTO,
                                String project, String region) {
        return googleCloudFunctionClient.getFunction(
                GetFunctionRequest.newBuilder()
                        .setName("projects/"+project+"/locations/"+region+"/functions/"+functionName)
                        .build(),
                getGcpInternalConfig(gcpConnectorDTO, region,
                        project));
    }

    public void deleteFunction(DeleteFunctionRequest deleteFunctionRequest, GcpConnectorDTO gcpConnectorDTO,
                                   String project, String region) throws ExecutionException, InterruptedException {
        OperationFuture<Empty, OperationMetadata> futureResponse = googleCloudFunctionClient.deleteFunction(
                deleteFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region,
                        project));
         futureResponse.get();
    }


    private GcpInternalConfig getGcpInternalConfig(GcpConnectorDTO gcpConnectorDTO, String region, String project) {
        if (gcpConnectorDTO == null) {
            throw new InvalidArgumentsException("GCP Connector cannot be null");
        }
        boolean isUseDelegate = false;
        char[] serviceAccountKeyFileContent = new char[0];
        GcpConnectorCredentialDTO credential = gcpConnectorDTO.getCredential();
        if (credential == null) {
            throw new InvalidArgumentsException("GCP Connector credential cannot be null");
        }
        if (INHERIT_FROM_DELEGATE == credential.getGcpCredentialType()) {
            isUseDelegate = true;
        } else {
            SecretRefData secretRef = ((GcpManualDetailsDTO) credential.getConfig()).getSecretKeyRef();
            if (secretRef.getDecryptedValue() == null) {
                throw new SecretNotFoundRuntimeException("Could not find secret " + secretRef.getIdentifier());
            }
            serviceAccountKeyFileContent = secretRef.getDecryptedValue();
        }
        return GcpInternalConfig.builder()
                .serviceAccountKeyFileContent(serviceAccountKeyFileContent)
                .isUseDelegate(isUseDelegate)
                .region(region)
                .project(project)
                .build();
    }
}
