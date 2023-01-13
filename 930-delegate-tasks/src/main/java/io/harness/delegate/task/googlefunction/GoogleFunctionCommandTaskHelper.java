package io.harness.delegate.task.googlefunction;

import com.google.api.client.util.Lists;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.cloud.functions.v2.BuildConfig;
import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.DeleteFunctionRequest;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.ListFunctionsRequest;
import com.google.cloud.functions.v2.ListFunctionsResponse;
import com.google.cloud.functions.v2.OperationMetadata;
import com.google.cloud.functions.v2.Source;
import com.google.cloud.functions.v2.StorageSource;
import com.google.cloud.functions.v2.UpdateFunctionRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.JsonFormat;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.googlefunctions.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctions.GoogleCloudStorageArtifactConfig;
import io.harness.delegate.task.googlefunctions.GoogleFunctionArtifactConfig;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionDeployRequest;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.runtime.SecretNotFoundRuntimeException;
import io.harness.googlefunctions.GcpInternalConfig;
import io.harness.googlefunctions.GoogleCloudFunctionClient;
import io.harness.serializer.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class GoogleFunctionCommandTaskHelper {
    @Inject private GoogleCloudFunctionClient googleCloudFunctionClient;

    public Function deployFunction(GoogleFunctionDeployRequest googleFunctionDeployRequest) throws IOException, ExecutionException, InterruptedException {
        GcpGoogleFunctionInfraConfig googleFunctionInfraConfig =
                (GcpGoogleFunctionInfraConfig) googleFunctionDeployRequest.getGoogleFunctionInfraConfig();

        CreateFunctionRequest.Builder createFunctionRequestBuilder = CreateFunctionRequest.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(googleFunctionDeployRequest.getGoogleFunctionDeployManifestContent(),
                createFunctionRequestBuilder);

        // get function name
        String functionName = getFunctionName(googleFunctionInfraConfig.getProject(),
                googleFunctionInfraConfig.getRegion(), createFunctionRequestBuilder.getFunction().getName());

        createFunctionRequestBuilder.setParent(getFunctionParent(googleFunctionInfraConfig.getProject(),
                googleFunctionInfraConfig.getRegion()));

        Function.Builder functionBuilder = createFunctionRequestBuilder.getFunctionBuilder();
        BuildConfig.Builder buildConfigBuilder = functionBuilder.getBuildConfigBuilder();

        // set artifact source
        buildConfigBuilder.setSource(getArtifactSource(googleFunctionDeployRequest.getGoogleFunctionArtifactConfig()));
        functionBuilder.setBuildConfig(buildConfigBuilder.build());
        functionBuilder.setName(functionName);
        createFunctionRequestBuilder.setFunction(functionBuilder.build());

        //check if function already exists
        Optional<Function> existingFunctionOptional = getFunction(functionName, googleFunctionInfraConfig.getGcpConnectorDTO(),
                googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());

        if(existingFunctionOptional.isEmpty()) {
            //create new function
            return createFunction(createFunctionRequestBuilder.build(), googleFunctionInfraConfig.getGcpConnectorDTO(),
                    googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());
        }
        else {
           //update existing function

            UpdateFunctionRequest.Builder updateFunctionRequestBuilder =
                    UpdateFunctionRequest.newBuilder()
                            .setFunction(createFunctionRequestBuilder.getFunction());
            if(StringUtils.isNotEmpty(googleFunctionDeployRequest.getUpdateFieldMaskContent())) {
                FieldMask.Builder fieldMaskBuilder = FieldMask.newBuilder();
                JsonFormat.parser().ignoringUnknownFields().merge(googleFunctionDeployRequest.getUpdateFieldMaskContent(),
                        createFunctionRequestBuilder);
                updateFunctionRequestBuilder.setUpdateMask(fieldMaskBuilder.build());
            }
            return updateFunction(updateFunctionRequestBuilder.build(), googleFunctionInfraConfig.getGcpConnectorDTO(),
                    googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());
        }

    }

    private Source getArtifactSource(GoogleFunctionArtifactConfig googleFunctionArtifactConfig) {
        if(googleFunctionArtifactConfig instanceof GoogleCloudStorageArtifactConfig) {
            GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
                    (GoogleCloudStorageArtifactConfig) googleFunctionArtifactConfig;
            StorageSource storageSource = StorageSource.newBuilder()
                    .setBucket(googleCloudStorageArtifactConfig.getBucket())
                    .setObject(googleCloudStorageArtifactConfig.getFilePath())
                    .build();
            return Source.newBuilder()
                    .setStorageSource(storageSource)
                    .build();
        }
        return null;
    }

    public Function createFunction(CreateFunctionRequest createFunctionRequest, GcpConnectorDTO gcpConnectorDTO,
                                   String project, String region) throws ExecutionException, InterruptedException {

        OperationFuture<Function, OperationMetadata> futureResponse = googleCloudFunctionClient.createFunction(
                createFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region,
                project));
        return futureResponse.get();
    }

    public Function updateFunction(UpdateFunctionRequest updateFunctionRequest, GcpConnectorDTO gcpConnectorDTO,
                                   String project, String region) throws ExecutionException, InterruptedException {
        OperationFuture<Function, OperationMetadata> futureResponse = googleCloudFunctionClient.updateFunction(
                updateFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region,
                project));
        OperationSnapshot operationSnapshot = futureResponse.getInitialFuture().get();
        return null;
    }

    public Optional<Function> getFunction(String functionName, GcpConnectorDTO gcpConnectorDTO,
                                          String project, String region ) {
        List<Function> functions = Lists.newArrayList();
        String pageToken = "";
                do{
                    ListFunctionsRequest.Builder listFunctionsRequestBuilder = ListFunctionsRequest.newBuilder()
                            .setParent(getFunctionParent(project, region))
                            .setPageSize(25);
                    if(!StringUtils.isEmpty(pageToken)) {
                        listFunctionsRequestBuilder.setPageToken(pageToken);
                    }
                    ListFunctionsResponse listFunctionsResponse =
                            googleCloudFunctionClient.listFunction(listFunctionsRequestBuilder.build(),
                                    getGcpInternalConfig(gcpConnectorDTO, region,
                                            project));
                    if(listFunctionsResponse==null) {
                        break;
                    }
                    if(!listFunctionsResponse.getFunctionsList().isEmpty()) {
                        functions.addAll(listFunctionsResponse.getFunctionsList());
                    }
                    pageToken = listFunctionsResponse.getNextPageToken();
                }
                while(!StringUtils.isEmpty(pageToken));
        for(Function function: functions) {
            if(function.getName().equals(functionName)) {
                return Optional.of(function);
            }
        }
        return Optional.empty();
    }

    public void deleteFunction(DeleteFunctionRequest deleteFunctionRequest, GcpConnectorDTO gcpConnectorDTO,
                                   String project, String region) throws ExecutionException, InterruptedException {
        OperationFuture<Empty, OperationMetadata> futureResponse = googleCloudFunctionClient.deleteFunction(
                deleteFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region,
                        project));
         futureResponse.get();
    }

    private String getFunctionName(String project, String region, String functionName) {
        return "projects/" + project + "/locations/" + region +
                "/functions/" + functionName;
    }

    private String getFunctionParent(String project, String region) {
        return "projects/" + project + "/locations/" + region;
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
