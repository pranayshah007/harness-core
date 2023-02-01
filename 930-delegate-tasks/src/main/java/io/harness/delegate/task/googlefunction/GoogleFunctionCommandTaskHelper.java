/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.googlefunction;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Blue;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static com.google.cloud.run.v2.TrafficTargetAllocationType.TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleCloudSourceArtifactConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleCloudStorageArtifactConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionArtifactConfig;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SecretNotFoundRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.googlefunctions.GcpInternalConfig;
import io.harness.googlefunctions.GoogleCloudFunctionClient;
import io.harness.googlefunctions.GoogleCloudRunClient;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.threading.Morpheus;

import software.wings.beans.LogColor;

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
import com.google.cloud.functions.v2.RepoSource;
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
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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
      GoogleFunctionArtifactConfig googleFunctionArtifactConfig, boolean latestTrafficFlag, LogCallback logCallback)
      throws IOException, ExecutionException, InterruptedException {
    CreateFunctionRequest.Builder createFunctionRequestBuilder = CreateFunctionRequest.newBuilder();
    parseStringContentAsClassBuilder(
        googleFunctionDeployManifestContent, createFunctionRequestBuilder, "createFunctionRequest");

    // get function name
    String functionName = getFunctionName(googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion(),
        createFunctionRequestBuilder.getFunction().getName());

    createFunctionRequestBuilder.setParent(
        getFunctionParent(googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()));

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

    // check if function already exists
    Optional<Function> existingFunctionOptional =
        getFunction(functionName, googleFunctionInfraConfig.getGcpConnectorDTO(),
            googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());
    printManifestContent(googleFunctionDeployManifestContent, logCallback);

    if (existingFunctionOptional.isEmpty()) {
      // create new function
      logCallback.saveExecutionLog(format("Creating Function: %s in project: %s and region: %s %n", functionName,
                                       googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()),
          LogLevel.INFO);
      Function function =
          createFunction(createFunctionRequestBuilder.build(), googleFunctionInfraConfig.getGcpConnectorDTO(),
              googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion(), logCallback);
      logCallback.saveExecutionLog(format("Created Function: %s in project: %s and region: %s %n&n", functionName,
                                       googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()),
          LogLevel.INFO);
      if (!latestTrafficFlag) {
        updateFullTrafficToSingleRevision(function.getServiceConfig().getService(),
            function.getServiceConfig().getRevision(), googleFunctionInfraConfig.getGcpConnectorDTO(),
            googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion(), logCallback);
      }
      return function;
    } else {
      // update existing function
      UpdateFunctionRequest.Builder updateFunctionRequestBuilder =
          UpdateFunctionRequest.newBuilder().setFunction(createFunctionRequestBuilder.getFunction());
      if (StringUtils.isNotEmpty(updateFieldMaskContent)) {
        FieldMask.Builder fieldMaskBuilder = FieldMask.newBuilder();
        parseStringContentAsClassBuilder(updateFieldMaskContent, fieldMaskBuilder, "updateFieldMask");
        updateFunctionRequestBuilder.setUpdateMask(fieldMaskBuilder.build());
      }
      logCallback.saveExecutionLog(format("Updating Function: %s in project: %s and region: %s %n", functionName,
                                       googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()),
          LogLevel.INFO);
      Function function =
          updateFunction(updateFunctionRequestBuilder.build(), googleFunctionInfraConfig.getGcpConnectorDTO(),
              googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion(), logCallback);
      logCallback.saveExecutionLog(format("Updated Function: %s in project: %s and region: %s %n", functionName,
                                       googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()),
          LogLevel.INFO);
      logCallback.saveExecutionLog(format("Deleting temporary revision: %s present in Cloud-Run service: %s",
          getResourceName(getTemporaryRevisionName(function.getServiceConfig().getService())),
          getResourceName(function.getServiceConfig().getService())));
      deleteRevision(getTemporaryRevisionName(function.getServiceConfig().getService()),
          googleFunctionInfraConfig.getGcpConnectorDTO(), googleFunctionInfraConfig.getProject(),
          googleFunctionInfraConfig.getRegion(), logCallback);
      return function;
    }
  }

  private Source getArtifactSource(GoogleFunctionArtifactConfig googleFunctionArtifactConfig) {
    if (googleFunctionArtifactConfig instanceof GoogleCloudStorageArtifactConfig) {
      GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
          (GoogleCloudStorageArtifactConfig) googleFunctionArtifactConfig;
      StorageSource storageSource = StorageSource.newBuilder()
                                        .setBucket(googleCloudStorageArtifactConfig.getBucket())
                                        .setObject(googleCloudStorageArtifactConfig.getFilePath())
                                        .build();
      return Source.newBuilder().setStorageSource(storageSource).build();
    } else if (googleFunctionArtifactConfig instanceof GoogleCloudSourceArtifactConfig) {
      GoogleCloudSourceArtifactConfig googleCloudSourceArtifactConfig =
          (GoogleCloudSourceArtifactConfig) googleFunctionArtifactConfig;
      RepoSource repoSource =
          RepoSource.newBuilder()
              .setProjectId(googleCloudSourceArtifactConfig.getProject())
              .setRepoName(getCloudSourceRepositoryName(
                  googleCloudSourceArtifactConfig.getProject(), googleCloudSourceArtifactConfig.getRepository()))
              .setDir(googleCloudSourceArtifactConfig.getSourceDirectory())
              .build();
      return Source.newBuilder().setRepoSource(repoSource).build();
    }
    throw new InvalidRequestException("Invalid Artifact Source.");
  }

  public Function createFunction(CreateFunctionRequest createFunctionRequest, GcpConnectorDTO gcpConnectorDTO,
      String project, String region, LogCallback logCallback) throws ExecutionException, InterruptedException {
    OperationFuture<Function, OperationMetadata> operationFuture = googleCloudFunctionClient.createFunction(
        createFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
    validateOperationSnapshot(operationFuture.getInitialFuture(), "createFunction");
    return checkFunctionDeploymentSteadyState(
        createFunctionRequest.getFunction().getName(), gcpConnectorDTO, project, region, logCallback);
  }

  public Function updateFunction(UpdateFunctionRequest updateFunctionRequest, GcpConnectorDTO gcpConnectorDTO,
      String project, String region, LogCallback logCallback) {
    OperationFuture<Function, OperationMetadata> operationFuture = googleCloudFunctionClient.updateFunction(
        updateFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
    validateOperationSnapshot(operationFuture.getInitialFuture(), "updateFunction");
    return checkFunctionDeploymentSteadyState(
        updateFunctionRequest.getFunction().getName(), gcpConnectorDTO, project, region, logCallback);
  }

  private void validateOperationSnapshot(ApiFuture<OperationSnapshot> operationSnapshot, String type) {
    try {
      operationSnapshot.get();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      if ("createFunction".equals(type) || "updateFunction".equals(type)) {
        throw NestedExceptionUtils.hintWithExplanationException("Please check that input fields in Google Function"
                + " Manifest yaml are valid",
            format("Could not able to %s google cloud function due to below error", type),
            new InvalidRequestException(sanitizedException.getMessage()));
      } else if ("deleteFunction".equals(type)) {
        throw new InvalidRequestException(
            "could not able to delete google cloud function" + sanitizedException.getMessage());
      } else if ("deleteRevision".equals(type)) {
        throw new InvalidRequestException(
            "could not able to delete google cloud run revision" + sanitizedException.getMessage());
      }
    }
  }

  private Function checkFunctionDeploymentSteadyState(
      String functionName, GcpConnectorDTO gcpConnectorDTO, String project, String region, LogCallback logCallback) {
    Function function = null;
    int currentApiCall = 0;
    do {
      currentApiCall++;
      GetFunctionRequest getFunctionRequest = GetFunctionRequest.newBuilder().setName(functionName).build();
      function = googleCloudFunctionClient.getFunction(
          getFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
      if (function.getState() == Function.State.ACTIVE || function.getState() == Function.State.FAILED) {
        break;
      } else if (function.getState() == Function.State.DEPLOYING) {
        logCallback.saveExecutionLog(
            format("Function deployment in progress: %s", color(function.getName(), LogColor.Yellow)));
      }
      Morpheus.sleep(ofSeconds(10));
    } while (currentApiCall < MAXIMUM_STEADY_STATE_CHECK_API_CALL);
    if (function.getState() == Function.State.ACTIVE) {
      logCallback.saveExecutionLog(color("Deployed Function successfully...", LogColor.Green));
      logCallback.saveExecutionLog(function.getStateMessagesList().toString());
    } else {
      logCallback.saveExecutionLog(color("Function Deployment failed...", LogColor.Red));
      logCallback.saveExecutionLog(color(function.getStateMessagesList().toString(), LogColor.Red));
      throw NestedExceptionUtils.hintWithExplanationException("Function didn't able to reach steady state",
          "Could not able to deploy google cloud function due to below error",
          new InvalidRequestException(function.getStateMessagesList().toString()));
    }
    return function;
  }

  private void checkFunctionDeletionSteadyState(
      String functionName, GcpConnectorDTO gcpConnectorDTO, String project, String region, LogCallback logCallback) {
    Function function;
    int currentApiCall = 0;
    do {
      currentApiCall++;
      GetFunctionRequest getFunctionRequest = GetFunctionRequest.newBuilder().setName(functionName).build();
      try {
        function = googleCloudFunctionClient.getFunction(
            getFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
        if (function.getState() == Function.State.DELETING) {
          logCallback.saveExecutionLog(
              format("Function deletion in progress: %s", color(function.getName(), LogColor.Yellow)));
        }
        Morpheus.sleep(ofSeconds(10));
      } catch (NotFoundException e) {
        logCallback.saveExecutionLog(color(format("Deleted Function successfully...%n%n"), LogColor.Green));
        return;
      }
    } while (currentApiCall < MAXIMUM_STEADY_STATE_CHECK_API_CALL);
  }

  private void checkRevisionDeletionSteadyState(
      String revisionName, GcpConnectorDTO gcpConnectorDTO, String project, String region, LogCallback logCallback) {
    Revision revision = null;
    int currentApiCall = 0;
    do {
      currentApiCall++;
      GetRevisionRequest getRevisionRequest = GetRevisionRequest.newBuilder().setName(revisionName).build();
      try {
        revision = googleCloudRunClient.getRevision(
            getRevisionRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
        logCallback.saveExecutionLog(
            format("Revision deletion in progress: %s", color(revision.getName(), LogColor.Yellow)));
        Morpheus.sleep(ofSeconds(10));
      } catch (Exception e) {
        if (e.getCause() instanceof NotFoundException) {
          logCallback.saveExecutionLog(color(format("Deleted Revision successfully...%n%n"), LogColor.Green));
          return;
        }
        throw e;
      }
    } while (currentApiCall < MAXIMUM_STEADY_STATE_CHECK_API_CALL);
  }

  private void checkTrafficShiftSteadyState(Integer targetTrafficPercent, String targetRevision,
      String existingRevision, String serviceName, GcpConnectorDTO gcpConnectorDTO, String project, String region,
      LogCallback logCallback) {
    Service service;
    int currentApiCall = 0;
    do {
      currentApiCall++;
      GetServiceRequest getServiceRequest = GetServiceRequest.newBuilder().setName(serviceName).build();
      service =
          googleCloudRunClient.getService(getServiceRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
      if (existingRevision == null
          && matchRevisionTraffic(service.getTrafficStatuses(0), targetTrafficPercent, targetRevision)) {
        logCallback.saveExecutionLog(color(format("Updated traffic Successfully...%n%n"), LogColor.Green));
        return;
      } else if (validateTrafficStatus(
                     service.getTrafficStatusesList(), targetTrafficPercent, targetRevision, existingRevision)) {
        logCallback.saveExecutionLog(color(format("Updated traffic Successfully...%n%n"), LogColor.Green));
        return;
      }
      logCallback.saveExecutionLog(color("Updating traffic...", LogColor.Yellow));
      Morpheus.sleep(ofSeconds(10));
    } while (currentApiCall < MAXIMUM_STEADY_STATE_CHECK_API_CALL);
  }

  public Optional<Function> getFunction(
      String functionName, GcpConnectorDTO gcpConnectorDTO, String project, String region) {
    GetFunctionRequest getFunctionRequest = GetFunctionRequest.newBuilder().setName(functionName).build();
    try {
      return Optional.of(googleCloudFunctionClient.getFunction(
          getFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region, project)));
    } catch (Exception e) {
      if (e.getCause() instanceof NotFoundException) {
        return Optional.empty();
      }
      throw e;
    }
  }

  private void deleteRevision(
      String revisionName, GcpConnectorDTO gcpConnectorDTO, String project, String region, LogCallback logCallback) {
    GetRevisionRequest getRevisionRequest = GetRevisionRequest.newBuilder().setName(revisionName).build();
    try {
      googleCloudRunClient.getRevision(getRevisionRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
    } catch (Exception e) {
      if (e.getCause() instanceof NotFoundException) {
        logCallback.saveExecutionLog(
            format("Skipping revision: %s deletion as it doesn't exist", getResourceName(revisionName)));
        return;
      }
      throw e;
    }
    DeleteRevisionRequest deleteRevisionRequest = DeleteRevisionRequest.newBuilder().setName(revisionName).build();
    logCallback.saveExecutionLog(format("Deleting revision: %s", getResourceName(revisionName)));
    OperationFuture<Revision, Revision> operationFuture = googleCloudRunClient.deleteRevision(
        deleteRevisionRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
    validateOperationSnapshot(operationFuture.getInitialFuture(), "deleteRevision");
    checkRevisionDeletionSteadyState(revisionName, gcpConnectorDTO, project, region, logCallback);
  }

  public void deleteFunction(String functionName, GcpConnectorDTO gcpConnectorDTO, String project, String region,
      LogCallback logCallback) throws ExecutionException, InterruptedException {
    GetFunctionRequest getFunctionRequest = GetFunctionRequest.newBuilder().setName(functionName).build();
    try {
      googleCloudFunctionClient.getFunction(getFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
    } catch (NotFoundException e) {
      logCallback.saveExecutionLog(
          format("Skipping function: %s deletion as it doesn't exist", getResourceName(functionName)));
      return;
    }
    DeleteFunctionRequest deleteFunctionRequest = DeleteFunctionRequest.newBuilder().setName(functionName).build();
    logCallback.saveExecutionLog(format("Deleting function: %s", getResourceName(functionName)));
    OperationFuture<Empty, OperationMetadata> operationFuture = googleCloudFunctionClient.deleteFunction(
        deleteFunctionRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
    validateOperationSnapshot(operationFuture.getInitialFuture(), "deleteFunction");
    checkFunctionDeletionSteadyState(functionName, gcpConnectorDTO, project, region, logCallback);
  }

  public void updateTraffic(String serviceName, Integer targetTrafficPercent, String targetRevision,
      String existingRevision, GcpConnectorDTO gcpConnectorDTO, String project, String region,
      LogCallback logCallback) {
    if (targetTrafficPercent <= 0) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please make sure trafficPercent parameter should be greater than zero",
          format("Current trafficPercent: %s is invalid", targetTrafficPercent),
          new InvalidRequestException("Invalid Traffic Percent"));
    }
    if (targetTrafficPercent > 100) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please make sure trafficPercent parameter should be less or equal to 100",
          format("Current trafficPercent: %s is invalid", targetTrafficPercent),
          new InvalidRequestException("Invalid Traffic Percent"));
    }
    GetServiceRequest getServiceRequest = GetServiceRequest.newBuilder().setName(serviceName).build();
    Service existingService =
        googleCloudRunClient.getService(getServiceRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));

    printExistingRevisionsTraffic(existingService.getTrafficStatusesList(), logCallback, existingService.getName());

    RevisionTemplate.Builder revisionTemplateBuilder = existingService.getTemplate().toBuilder();
    revisionTemplateBuilder.setRevision(format(CLOUD_RUN_SERVICE_TEMP_HARNESS_VERSION, getResourceName(serviceName)));

    Service newService = Service.newBuilder()
                             .setName(serviceName)
                             .setTemplate(revisionTemplateBuilder.build())
                             .addTraffic(getTrafficTarget(targetTrafficPercent, targetRevision))
                             .addTraffic(getTrafficTarget(100 - targetTrafficPercent, existingRevision))
                             .build();

    UpdateServiceRequest updateServiceRequest = UpdateServiceRequest.newBuilder().setService(newService).build();
    logCallback.saveExecutionLog(format("Updating %s percent traffic to Revision: %s in Cloud-Run Service: %s",
        targetTrafficPercent, getResourceName(targetRevision), getResourceName(serviceName)));

    OperationFuture<Service, Service> operationFuture = googleCloudRunClient.updateService(
        updateServiceRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
    validateOperationSnapshot(operationFuture.getInitialFuture(), "updateTraffic");
    checkTrafficShiftSteadyState(targetTrafficPercent, targetRevision, existingRevision, serviceName, gcpConnectorDTO,
        project, region, logCallback);
  }

  public void updateFullTrafficToSingleRevision(String serviceName, String revision, GcpConnectorDTO gcpConnectorDTO,
      String project, String region, LogCallback logCallback) {
    GetServiceRequest getServiceRequest = GetServiceRequest.newBuilder().setName(serviceName).build();
    Service existingService =
        googleCloudRunClient.getService(getServiceRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));

    RevisionTemplate.Builder revisionTemplateBuilder = existingService.getTemplate().toBuilder();
    revisionTemplateBuilder.setRevision(format(CLOUD_RUN_SERVICE_TEMP_HARNESS_VERSION, getResourceName(serviceName)));

    Service newService = Service.newBuilder()
                             .setName(serviceName)
                             .setTemplate(revisionTemplateBuilder.build())
                             .addTraffic(getTrafficTarget(100, revision))
                             .build();

    UpdateServiceRequest updateServiceRequest = UpdateServiceRequest.newBuilder().setService(newService).build();
    printExistingRevisionsTraffic(existingService.getTrafficStatusesList(), logCallback, existingService.getName());

    logCallback.saveExecutionLog(format("Updating full traffic to Revision: %s in Cloud-Run Service: %s",
        getResourceName(revision), getResourceName(serviceName)));

    OperationFuture<Service, Service> operationFuture = googleCloudRunClient.updateService(
        updateServiceRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
    validateOperationSnapshot(operationFuture.getInitialFuture(), "updateTraffic");
    checkTrafficShiftSteadyState(100, revision, null, serviceName, gcpConnectorDTO, project, region, logCallback);
  }

  public void printManifestContent(String manifestContent, LogCallback logCallback) {
    logCallback.saveExecutionLog(color(format("Google Function Manifest Content %n"), White, Bold));
    logCallback.saveExecutionLog(manifestContent);
  }

  private boolean validateTrafficStatus(List<TrafficTargetStatus> trafficTargetStatuses, Integer targetTrafficPercent,
      String targetRevision, String existingRevision) {
    for (TrafficTargetStatus trafficTargetStatus : trafficTargetStatuses) {
      if (!matchRevisionTraffic(trafficTargetStatus, targetTrafficPercent, targetRevision)
          && !matchRevisionTraffic(trafficTargetStatus, 100 - targetTrafficPercent, existingRevision)) {
        return false;
      }
    }
    return true;
  }

  private boolean matchRevisionTraffic(
      TrafficTargetStatus trafficTargetStatus, Integer trafficPercent, String revision) {
    return trafficTargetStatus.getPercent() == trafficPercent && revision.equals(trafficTargetStatus.getRevision());
  }

  private TrafficTarget getTrafficTarget(int trafficPercent, String revision) {
    return TrafficTarget.newBuilder()
        .setRevision(revision)
        .setType(TrafficTargetAllocationType.TRAFFIC_TARGET_ALLOCATION_TYPE_REVISION)
        .setPercent(trafficPercent)
        .build();
  }

  public String getResourceName(String name) {
    String[] values = name.split("/");
    if (values.length == 0) {
      return "";
    }
    return values[values.length - 1];
  }

  public String getFunctionName(String project, String region, String function) {
    return "projects/" + project + "/locations/" + region + "/functions/" + function;
  }

  public String getCloudSourceRepositoryName(String project, String repo) {
    return "projects/" + project + "/repositories/" + repo;
  }

  private String getTemporaryRevisionName(String serviceName) {
    return serviceName + "/revisions/" + format(CLOUD_RUN_SERVICE_TEMP_HARNESS_VERSION, getResourceName(serviceName));
  }

  public Optional<String> getCloudRunServiceName(Function function) {
    return StringUtils.isNotEmpty(function.getServiceConfig().getService())
        ? Optional.of(function.getServiceConfig().getService())
        : Optional.empty();
  }

  private String getFunctionParent(String project, String region) {
    return "projects/" + project + "/locations/" + region;
  }

  public boolean validateTrafficInExistingRevisions(List<TrafficTargetStatus> trafficTargetStatuses) {
    // only one of existing revisions should have 100% traffic before deployment
    for (TrafficTargetStatus trafficTargetStatus : trafficTargetStatuses) {
      if (trafficTargetStatus.getPercent() == 100) {
        return true;
      }
    }
    return false;
  }

  public void parseStringContentAsClassBuilder(String content, Message.Builder builder, String type) {
    try {
      JsonFormat.parser().ignoringUnknownFields().merge(content, builder);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      if ("createFunctionRequest".equals(type)) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Please make sure Google Function manifest yaml should be of createFunctionRequest object type",
            "Could not able to parse Google Function manifest yaml into object of createFunctionRequest",
            new InvalidRequestException(sanitizedException.getMessage()));
      } else if ("updateFieldMask".equals(type)) {
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
    if (TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST.equals(trafficTargetStatus.getType())) {
      return getResourceName(service.getLatestReadyRevision());
    }
    return trafficTargetStatus.getRevision();
  }

  public void printExistingRevisionsTraffic(
      List<TrafficTargetStatus> trafficTargetStatuses, LogCallback logCallback, String service) {
    logCallback.saveExecutionLog(
        format("Current active revisions in Cloud-Run service: %s", getResourceName(service)), LogLevel.INFO);
    for (TrafficTargetStatus trafficTargetStatus : trafficTargetStatuses) {
      if (trafficTargetStatus.getPercent() > 0) {
        logCallback.saveExecutionLog(color(format("Revision: %s in Cloud-Run service is serving: %s percent traffic..",
                                               trafficTargetStatus.getRevision(), trafficTargetStatus.getPercent()),
                                         LogColor.Yellow),
            LogLevel.INFO);
      }
    }
  }

  public Service getCloudRunService(
      String serviceName, GcpConnectorDTO gcpConnectorDTO, String project, String region) {
    GetServiceRequest getServiceRequest = GetServiceRequest.newBuilder().setName(serviceName).build();
    return googleCloudRunClient.getService(getServiceRequest, getGcpInternalConfig(gcpConnectorDTO, region, project));
  }

  public GoogleFunction getGoogleFunction(Function function, GcpGoogleFunctionInfraConfig googleFunctionInfraConfig,
      LogCallback logCallback) throws InvalidProtocolBufferException {
    logCallback.saveExecutionLog(color("Updated Functions details: ", Blue, Bold), INFO);
    logCallback.saveExecutionLog(JsonFormat.printer().print(function));
    GoogleFunction.GoogleCloudRunService googleCloudRunService =
        GoogleFunction.GoogleCloudRunService.builder()
            .serviceName(function.getServiceConfig().getService())
            .memory(function.getServiceConfig().getAvailableMemory())
            .revision(function.getServiceConfig().getRevision())
            .build();

    Service cloudRunService =
        getCloudRunService(function.getServiceConfig().getService(), googleFunctionInfraConfig.getGcpConnectorDTO(),
            googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion());
    logCallback.saveExecutionLog(color("Updated Cloud-Run Service details: ", Blue, Bold), INFO);
    logCallback.saveExecutionLog(JsonFormat.printer().print(cloudRunService));

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
    trafficTargetStatuses.stream()
        .filter(trafficTargetStatus -> trafficTargetStatus.getPercent() > 0)
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
