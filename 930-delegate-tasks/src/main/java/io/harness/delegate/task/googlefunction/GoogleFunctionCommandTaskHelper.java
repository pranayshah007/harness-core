package io.harness.delegate.task.googlefunction;

import com.google.api.core.ApiFuture;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.functions.v2.BuildConfig;
import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.DeleteFunctionRequest;
import com.google.cloud.functions.v2.Environment;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.GetFunctionRequest;
import com.google.cloud.functions.v2.OperationMetadata;
import com.google.cloud.functions.v2.ServiceConfig;
import com.google.cloud.functions.v2.Source;
import com.google.cloud.functions.v2.StorageSource;
import com.google.cloud.functions.v2.UpdateFunctionRequest;
import com.google.cloud.run.v2.DeleteRevisionRequest;
import com.google.cloud.run.v2.GetRevisionRequest;
import com.google.cloud.run.v2.GetServiceRequest;
import com.google.cloud.run.v2.Revision;
import com.google.cloud.run.v2.RevisionTemplate;
import com.google.cloud.run.v2.Service;
import com.google.cloud.run.v2.TrafficTarget;
import com.google.cloud.run.v2.TrafficTargetAllocationType;
import com.google.cloud.run.v2.TrafficTargetStatus;
import com.google.cloud.run.v2.UpdateServiceRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.googlefunctions.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctions.GoogleCloudStorageArtifactConfig;
import io.harness.delegate.task.googlefunctions.GoogleFunction;
import io.harness.delegate.task.googlefunctions.GoogleFunctionArtifactConfig;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SecretNotFoundRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.googlefunctions.GcpInternalConfig;
import io.harness.googlefunctions.GoogleCloudFunctionClient;
import io.harness.googlefunctions.GoogleCloudRunClient;
import io.harness.threading.Morpheus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.google.cloud.run.v2.TrafficTargetAllocationType.TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST;
import static com.google.common.collect.Lists.newArrayList;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static jodd.util.ThreadUtil.sleep;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class GoogleFunctionCommandTaskHelper {
    @Inject private GoogleCloudFunctionClient googleCloudFunctionClient;
    @Inject private GoogleCloudRunClient googleCloudRunClient;
    private static final int MAXIMUM_STEADY_STATE_CHECK_API_CALL = 300;
    private static final String CLOUD_RUN_SERVICE_TEMP_HARNESS_VERSION = "%s-harness-temp-version";

    public Function deployFunction(GcpGoogleFunctionInfraConfig googleFunctionInfraConfig,
                                   String googleFunctionDeployManifestContent, String updateFieldMaskContent,
                                   GoogleFunctionArtifactConfig googleFunctionArtifactConfig,
                                   boolean latestTrafficFlag) throws IOException,
            ExecutionException, InterruptedException {

        CreateFunctionRequest.Builder createFunctionRequestBuilder = CreateFunctionRequest.newBuilder();
        parseStringContentAsClassBuilder(googleFunctionDeployManifestContent,
                createFunctionRequestBuilder, "createFunctionRequest");

        // get function name
        String functionName = getFunctionName(googleFunctionInfraConfig.getProject(),
                googleFunctionInfraConfig.getRegion(), createFunctionRequestBuilder.getFunction().getName());

        createFunctionRequestBuilder.setParent(getFunctionParent(googleFunctionInfraConfig.getProject(),
                googleFunctionInfraConfig.getRegion()));

        Function.Builder functionBuilder = createFunctionRequestBuilder.getFunctionBuilder();
        BuildConfig.Builder buildConfigBuilder = functionBuilder.getBuildConfigBuilder();

        // set artifact source
        buildConfigBuilder.setSource(getArtifactSource(googleFunctionArtifactConfig));
        functionBuilder.setBuildConfig(buildConfigBuilder.build());
        functionBuilder.setName(functionName);

        // set 2nd Gen Environment
        functionBuilder.setEnvironment(Environment.GEN_2);

        ServiceConfig.Builder serviceConfigBuilder = functionBuilder.getServiceConfigBuilder();
        serviceConfigBuilder.setAllTrafficOnLatestRevision(latestTrafficFlag);
        createFunctionRequestBuilder.setFunction(functionBuilder.build());

        //check if function already exists
        Optional<Function> existingFunctionOptional = getFunction(functionName, googleFunctionInfraConfig.getGcpConnectorDTO(),
                googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());

        if(existingFunctionOptional.isEmpty()) {
            //create new function
            Function function = createFunction(createFunctionRequestBuilder.build(), googleFunctionInfraConfig.getGcpConnectorDTO(),
                    googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());
            if(!latestTrafficFlag) {
                updateFullTrafficToSingleRevision(function.getServiceConfig().getService(),
                        function.getServiceConfig().getRevision(),googleFunctionInfraConfig.getGcpConnectorDTO(),
                        googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());
            }
            return function;
        }
        else {
           //update existing function
            UpdateFunctionRequest.Builder updateFunctionRequestBuilder =
                    UpdateFunctionRequest.newBuilder()
                            .setFunction(createFunctionRequestBuilder.getFunction());
            if(StringUtils.isNotEmpty(updateFieldMaskContent)) {
                FieldMask.Builder fieldMaskBuilder = FieldMask.newBuilder();
                parseStringContentAsClassBuilder(updateFieldMaskContent, fieldMaskBuilder, "updateFieldMask");
                updateFunctionRequestBuilder.setUpdateMask(fieldMaskBuilder.build());
            }
            Function function = updateFunction(updateFunctionRequestBuilder.build(), googleFunctionInfraConfig.getGcpConnectorDTO(),
                    googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());
            deleteRevision(getTemporaryRevisionName(function.getServiceConfig().getService()),
                    googleFunctionInfraConfig.getGcpConnectorDTO(), googleFunctionInfraConfig.getProject(),
                    googleFunctionInfraConfig.getRegion());
            return function;
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
        OperationFuture<Function, OperationMetadata> operationFuture = googleCloudFunctionClient.createFunction(
                createFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region,
                project));
        validateOperationSnapshot(operationFuture.getInitialFuture(), "createFunction");
        return checkFunctionDeploymentSteadyState(createFunctionRequest.getFunction().getName(), gcpConnectorDTO, project, region);
    }

    public Function updateFunction(UpdateFunctionRequest updateFunctionRequest, GcpConnectorDTO gcpConnectorDTO,
                                   String project, String region) {
        OperationFuture<Function, OperationMetadata> operationFuture = googleCloudFunctionClient.updateFunction(
                updateFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region,
                        project));
        validateOperationSnapshot(operationFuture.getInitialFuture(), "updateFunction");
        return checkFunctionDeploymentSteadyState(updateFunctionRequest.getFunction().getName(), gcpConnectorDTO, project, region);
    }

    private void validateOperationSnapshot(ApiFuture<OperationSnapshot> operationSnapshot,
                                           String type) {
        try{
            operationSnapshot.get();
        }
        catch (Exception e) {
            Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
            if("createFunction".equals(type) || "updateFunction".equals(type)) {
                throw NestedExceptionUtils.hintWithExplanationException("Please check that input fields in Google Function" +
                                " Manifest yaml are valid",
                        format("Could not able to %s google cloud function due to below error", type),
                        new InvalidRequestException(sanitizedException.getMessage()));
            }
            else if("deleteFunction".equals(type)) {
                throw new InvalidRequestException("could not able to delete google cloud function" +
                        sanitizedException.getMessage());
            }
            else if("deleteRevision".equals(type)) {
                throw new InvalidRequestException("could not able to delete google cloud run revision" +
                        sanitizedException.getMessage());
            }
        }
    }

    private Function checkFunctionDeploymentSteadyState(String functionName, GcpConnectorDTO gcpConnectorDTO,
                                              String project, String region) {
        Function function = null;
        int currentApiCall = 0;
        do {
            currentApiCall++;
            GetFunctionRequest getFunctionRequest = GetFunctionRequest.newBuilder()
                    .setName(functionName)
                    .build();
            function = googleCloudFunctionClient.getFunction(getFunctionRequest, getGcpInternalConfig(gcpConnectorDTO,
                    region, project));
            if (function.getState() == Function.State.ACTIVE || function.getState() == Function.State.FAILED) {
                break;
            } else if (function.getState() == Function.State.DEPLOYING) {
                System.out.println("Deploying Function...");
            }
            Morpheus.sleep(ofSeconds(10));
        }
        while (currentApiCall<MAXIMUM_STEADY_STATE_CHECK_API_CALL);

        if (function.getState() == Function.State.ACTIVE) {
            System.out.println("Deployed Function Successfully...");
        } else {
            System.out.println("Deployed Failed" + function.getStateMessagesList());
        }
        return function;
    }

    private void checkFunctionDeletionSteadyState(String functionName, GcpConnectorDTO gcpConnectorDTO,
                                                        String project, String region) {
        Function function;
        int currentApiCall = 0;
        do {
            currentApiCall++;
            GetFunctionRequest getFunctionRequest = GetFunctionRequest.newBuilder()
                    .setName(functionName)
                    .build();
            try{
                function = googleCloudFunctionClient.getFunction(getFunctionRequest, getGcpInternalConfig(gcpConnectorDTO,
                        region, project));
                if (function.getState() == Function.State.DELETING) {
                    System.out.println("Deleting Function...");
                }
                Morpheus.sleep(ofSeconds(10));
            }
            catch (NotFoundException e) {
                System.out.println("Deleted Function Successfully...");
                return;
            }
        }
        while (currentApiCall<MAXIMUM_STEADY_STATE_CHECK_API_CALL);
    }

    private void checkRevisionDeletionSteadyState(String revisionName, GcpConnectorDTO gcpConnectorDTO,
                                                  String project, String region) {
        Revision revision;
        int currentApiCall = 0;
        do {
            currentApiCall++;
            GetRevisionRequest getRevisionRequest = GetRevisionRequest.newBuilder()
                    .setName(revisionName)
                    .build();
            try {
                revision = googleCloudRunClient.getRevision(getRevisionRequest, getGcpInternalConfig(gcpConnectorDTO,
                        region, project));
                System.out.println("Deleting Revision...");
                Morpheus.sleep(ofSeconds(10));
            }
            catch (Exception e) {
                if(e.getCause() instanceof NotFoundException) {
                    System.out.println("Deleted Revision Successfully...");
                    return;
                }
                throw e;
            }
        }
        while (currentApiCall<MAXIMUM_STEADY_STATE_CHECK_API_CALL);
    }

    private void checkTrafficShiftSteadyState(Integer targetTrafficPercent, String targetRevision, String existingRevision,
                                              String serviceName, GcpConnectorDTO gcpConnectorDTO, String project, String region) {
        Service service;
        int currentApiCall = 0;
        do {
            currentApiCall++;
            GetServiceRequest getServiceRequest = GetServiceRequest.newBuilder()
                    .setName(serviceName)
                    .build();
                service = googleCloudRunClient.getService(getServiceRequest, getGcpInternalConfig(gcpConnectorDTO,
                        region, project));
                if(existingRevision==null && matchRevisionTraffic(service.getTrafficStatuses(0),targetTrafficPercent, targetRevision)) {
                    System.out.println("Updated traffic Successfully...");
                    return;
                }
                else if(validateTrafficStatus(service.getTrafficStatusesList(), targetTrafficPercent, targetRevision,
                        existingRevision)) {
                    System.out.println("Updated traffic Successfully...");
                    return;
                }
                System.out.println("Updating traffic...");
                Morpheus.sleep(ofSeconds(10));
        }
        while (currentApiCall<MAXIMUM_STEADY_STATE_CHECK_API_CALL);
    }

    public Optional<Function> getFunction(String functionName, GcpConnectorDTO gcpConnectorDTO,
                                          String project, String region ) {
        GetFunctionRequest getFunctionRequest = GetFunctionRequest.newBuilder()
                .setName(functionName)
                .build();
        try {
            return Optional.of(googleCloudFunctionClient.getFunction(getFunctionRequest, getGcpInternalConfig(gcpConnectorDTO,
                    region, project)));
        }
        catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    private void deleteRevision(String revisionName, GcpConnectorDTO gcpConnectorDTO,
                                    String project, String region) {
        GetRevisionRequest getRevisionRequest = GetRevisionRequest.newBuilder()
                .setName(revisionName)
                .build();
        try {
            googleCloudRunClient.getRevision(getRevisionRequest, getGcpInternalConfig(gcpConnectorDTO,
                    region, project));
        }
        catch (Exception e) {
            if(e.getCause() instanceof NotFoundException) {
                return;
            }
            throw e;
        }
        DeleteRevisionRequest deleteRevisionRequest = DeleteRevisionRequest.newBuilder()
                .setName(revisionName)
                .build();
        OperationFuture<Revision, Revision> operationFuture = googleCloudRunClient.deleteRevision(deleteRevisionRequest,
                getGcpInternalConfig(gcpConnectorDTO, region, project));
        validateOperationSnapshot(operationFuture.getInitialFuture(), "deleteRevision");
        checkRevisionDeletionSteadyState(revisionName, gcpConnectorDTO, project, region);
    }

    public void deleteFunction(String functionName, GcpConnectorDTO gcpConnectorDTO,
                                   String project, String region) throws ExecutionException, InterruptedException {
        GetFunctionRequest getFunctionRequest = GetFunctionRequest.newBuilder()
                .setName(functionName)
                .build();
        try {
            googleCloudFunctionClient.getFunction(getFunctionRequest, getGcpInternalConfig(gcpConnectorDTO,
                    region, project));
        }
        catch (NotFoundException e) {
            return;
        }
        DeleteFunctionRequest deleteFunctionRequest = DeleteFunctionRequest.newBuilder()
                .setName(functionName)
                .build();
        OperationFuture<Empty, OperationMetadata> operationFuture = googleCloudFunctionClient.deleteFunction(
                deleteFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region,
                        project));
        validateOperationSnapshot(operationFuture.getInitialFuture(), "deleteFunction");
        checkFunctionDeletionSteadyState(functionName, gcpConnectorDTO, project, region);
    }

    public void updateTraffic(String serviceName, Integer targetTrafficPercent, String targetRevision, String existingRevision,
                               GcpConnectorDTO gcpConnectorDTO, String project, String region) {
        if(targetTrafficPercent<=0) {
            throw NestedExceptionUtils.hintWithExplanationException(
                    "Please make sure trafficPercent parameter should be greater than zero",
                    format("Current trafficPercent: %s is invalid",targetTrafficPercent),
                    new InvalidRequestException("Invalid Traffic Percent"));
        }
        if(targetTrafficPercent>100) {
            throw NestedExceptionUtils.hintWithExplanationException(
                    "Please make sure trafficPercent parameter should be less or equal to 100",
                    format("Current trafficPercent: %s is invalid",targetTrafficPercent),
                    new InvalidRequestException("Invalid Traffic Percent"));
        }
        GetServiceRequest getServiceRequest = GetServiceRequest.newBuilder()
                .setName(serviceName)
                .build();
        Service existingService = googleCloudRunClient.getService(getServiceRequest,
                getGcpInternalConfig(gcpConnectorDTO, region, project));

        RevisionTemplate.Builder revisionTemplateBuilder = existingService.getTemplate().toBuilder();
        revisionTemplateBuilder.setRevision(format(CLOUD_RUN_SERVICE_TEMP_HARNESS_VERSION,getResourceName(serviceName)));

        Service newService = Service.newBuilder()
                .setName(serviceName)
                .setTemplate(revisionTemplateBuilder.build())
                .addTraffic(getTrafficTarget(targetTrafficPercent, targetRevision))
                .addTraffic(getTrafficTarget(100-targetTrafficPercent, existingRevision))
                .build();

        UpdateServiceRequest updateServiceRequest = UpdateServiceRequest.newBuilder()
                .setService(newService)
                .build();

        OperationFuture<Service, Service> operationFuture =
         googleCloudRunClient.updateService(updateServiceRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
        validateOperationSnapshot(operationFuture.getInitialFuture(), "updateTraffic");
        checkTrafficShiftSteadyState(targetTrafficPercent, targetRevision, existingRevision, serviceName, gcpConnectorDTO,
                 project, region);
    }

    public void updateFullTrafficToSingleRevision(String serviceName, String revision,
                                                   GcpConnectorDTO gcpConnectorDTO, String project, String region) {
        GetServiceRequest getServiceRequest = GetServiceRequest.newBuilder()
                .setName(serviceName)
                .build();
        Service existingService = googleCloudRunClient.getService(getServiceRequest,
                getGcpInternalConfig(gcpConnectorDTO, region, project));

        RevisionTemplate.Builder revisionTemplateBuilder = existingService.getTemplate().toBuilder();
        revisionTemplateBuilder.setRevision(format(CLOUD_RUN_SERVICE_TEMP_HARNESS_VERSION,getResourceName(serviceName)));

        Service newService = Service.newBuilder()
                .setName(serviceName)
                .setTemplate(revisionTemplateBuilder.build())
                .addTraffic(getTrafficTarget(100, revision))
                .build();

        UpdateServiceRequest updateServiceRequest = UpdateServiceRequest.newBuilder()
                .setService(newService)
                .build();

        OperationFuture<Service, Service> operationFuture =
                googleCloudRunClient.updateService(updateServiceRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
        validateOperationSnapshot(operationFuture.getInitialFuture(), "updateTraffic");
        checkTrafficShiftSteadyState(100, revision, null, serviceName, gcpConnectorDTO,
                project, region);
    }

    private boolean validateTrafficStatus(List<TrafficTargetStatus> trafficTargetStatuses, Integer targetTrafficPercent,
                                          String targetRevision, String existingRevision) {
        for(TrafficTargetStatus trafficTargetStatus: trafficTargetStatuses) {
            if(!matchRevisionTraffic(trafficTargetStatus, targetTrafficPercent, targetRevision) &&
            !matchRevisionTraffic(trafficTargetStatus, 100-targetTrafficPercent, existingRevision)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchRevisionTraffic(TrafficTargetStatus trafficTargetStatus, Integer trafficPercent, String revision) {
        return trafficTargetStatus.getPercent()==trafficPercent && revision.equals(trafficTargetStatus.getRevision());
    }

    private TrafficTarget getTrafficTarget(int trafficPercent, String revision) {
        return TrafficTarget.newBuilder()
                .setRevision(revision)
                .setType(TrafficTargetAllocationType.TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION)
                .setPercent(trafficPercent)
                .build();
    }

    private String getResourceName(String name) {
        String[] values = name.split("/");
        if(values.length == 0){
            return "";
        }
        return values[values.length-1];
    }

    public String getFunctionName(String project, String region, String function) {
        return "projects/" + project + "/locations/" + region + "/functions/" + function;
    }

    private String getTemporaryRevisionName(String serviceName) {
        return serviceName+"/revisions/"+format(CLOUD_RUN_SERVICE_TEMP_HARNESS_VERSION,getResourceName(serviceName));
    }

    public Optional<String> getCloudRunServiceName(Function function) {
        return  StringUtils.isNotEmpty(function.getServiceConfig().getService())?
                Optional.of(function.getServiceConfig().getService()):Optional.empty();
    }

    private String getFunctionParent(String project, String region) {
        return "projects/" + project + "/locations/" + region;
    }

    public boolean validateTrafficInExistingRevisions(List<TrafficTargetStatus>  trafficTargetStatuses) {
        // only one of existing revisions should have 100% traffic before deployment
        for (TrafficTargetStatus trafficTargetStatus: trafficTargetStatuses) {
            if(trafficTargetStatus.getPercent()==100) {
                return true;
            }
        }
        return false;
    }

    public void parseStringContentAsClassBuilder(String content, Message.Builder builder, String type) {
        try{
            JsonFormat.parser().ignoringUnknownFields().merge(content,builder);
        }
        catch(Exception e) {
            Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
            if("createFunctionRequest".equals(type)) {
                throw NestedExceptionUtils.hintWithExplanationException(
                        "Please make sure Google Function manifest yaml should be of createFunctionRequest object type",
                        "Could not able to parse Google Function manifest yaml into object of createFunctionRequest",
                        new InvalidRequestException(sanitizedException.getMessage()));
            }
            else if("updateFieldMask".equals(type)) {
                throw NestedExceptionUtils.hintWithExplanationException(
                        "Please make sure updateFieldMask input in deploy step should be of FieldMask object type",
                        "Could not able to parse updateFieldMask input into object of FieldMask",
                        new InvalidRequestException(sanitizedException.getMessage()));
            }
            throw new InvalidRequestException(sanitizedException.getMessage());
        }
    }

    public String getCurrentRevision(Service service) {
        TrafficTargetStatus trafficTargetStatus = service.getTrafficStatuses(0);
        if(TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST.equals(trafficTargetStatus.getType())) {
            return getResourceName(service.getLatestReadyRevision());
        }
        return trafficTargetStatus.getRevision();
    }

    public Service getCloudRunService(String serviceName, GcpConnectorDTO gcpConnectorDTO,
                                      String project, String region) {
        GetServiceRequest getServiceRequest = GetServiceRequest.newBuilder()
                .setName(serviceName)
                .build();
        return googleCloudRunClient.getService(getServiceRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
    }

    public GoogleFunction getGoogleFunction(Function function, GcpGoogleFunctionInfraConfig googleFunctionInfraConfig){
        GoogleFunction.GoogleCloudRunService googleCloudRunService = GoogleFunction.GoogleCloudRunService.builder()
                .serviceName(function.getServiceConfig().getService())
                .memory(function.getServiceConfig().getAvailableMemory())
                .revision(function.getServiceConfig().getRevision())
                .build();

        Service cloudRunService = getCloudRunService(function.getServiceConfig().getService(),
                googleFunctionInfraConfig.getGcpConnectorDTO(), googleFunctionInfraConfig.getProject(),
                googleFunctionInfraConfig.getRegion());

        return GoogleFunction.builder()
                .functionName(function.getName())
                .state(function.getState().toString())
                .runtime(function.getBuildConfig().getRuntime())
                .environment(function.getEnvironment().name())
                .cloudRunService(googleCloudRunService)
                .activeCloudRunRevisions(getGoogleCloudRunRevisions(cloudRunService))
                .build();
    }

    private List<GoogleFunction.GoogleCloudRunRevision> getGoogleCloudRunRevisions(Service cloudRunService) {
        List<TrafficTargetStatus> trafficTargetStatuses = cloudRunService.getTrafficStatusesList();
        List<GoogleFunction.GoogleCloudRunRevision> revisions = newArrayList();
        trafficTargetStatuses.stream().filter(trafficTargetStatus -> trafficTargetStatus.getPercent()>0)
                .forEach(trafficTargetStatus -> {
            revisions.add(GoogleFunction.GoogleCloudRunRevision.builder()
                            .revision(trafficTargetStatus.getRevision())
                            .trafficPercent(trafficTargetStatus.getPercent())
                    .build());
        });
        return revisions;
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
