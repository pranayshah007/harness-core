/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.googlefunction;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.CREATE_FUNCTION_GEN_ONE_FAILURE_HINT;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.CREATE_FUNCTION_PARSE_FAILURE_HINT;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.DELETE_FUNCTION_FAILURE_HINT;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.FIELD_MASK_PARSE_FAILURE_HINT;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.GET_FUNCTION_FAILURE_ERROR;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.GET_FUNCTION_FAILURE_EXPLAIN;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.GET_FUNCTION_FAILURE_HINT;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.GOOGLE_CLOUD_SOURCE_ARTIFACT_BRANCH_FORMAT;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.GOOGLE_CLOUD_SOURCE_ARTIFACT_COMMIT_FORMAT;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.GOOGLE_CLOUD_SOURCE_ARTIFACT_TAG_FORMAT;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.GOOGLE_CLOUD_STORAGE_ARTIFACT_FORMAT;
import static io.harness.delegate.task.googlefunction.GoogleFunctionUtils.UPDATE_FUNCTION_GEN_ONE_FAILURE_HINT;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Blue;
import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceFetchType;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleCloudSourceArtifactConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleCloudStorageArtifactConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionArtifactConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.googlefunctions.GoogleCloudFunctionGenOneClient;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.YamlUtils;
import io.harness.threading.Morpheus;

import software.wings.beans.LogColor;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.client.util.Lists;
import com.google.api.core.ApiFuture;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.functions.v1.CloudFunction;
import com.google.cloud.functions.v1.CloudFunctionStatus;
import com.google.cloud.functions.v1.CreateFunctionRequest;
import com.google.cloud.functions.v1.DeleteFunctionRequest;
import com.google.cloud.functions.v1.GetFunctionRequest;
import com.google.cloud.functions.v1.OperationMetadataV1;
import com.google.cloud.functions.v1.SourceRepository;
import com.google.cloud.functions.v1.UpdateFunctionRequest;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.longrunning.Operation;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class GoogleFunctionGenOneCommandTaskHelper {
  @Inject private TimeLimiter timeLimiter;
  @Inject private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;
  @Inject private GoogleCloudFunctionGenOneClient googleCloudFunctionGenOneClient;
  private YamlUtils yamlUtils = new YamlUtils();

  public CloudFunction deployFunction(GcpGoogleFunctionInfraConfig googleFunctionInfraConfig,
      String googleFunctionDeployManifestContent, String updateFieldMaskContent,
      GoogleFunctionArtifactConfig googleFunctionArtifactConfig, Long timeout, LogCallback logCallback,
      boolean isRollback) throws ExecutionException, InterruptedException {
    CreateFunctionRequest.Builder createFunctionRequestBuilder = CreateFunctionRequest.newBuilder();
    parseStringContentAsClassBuilder(
        googleFunctionDeployManifestContent, createFunctionRequestBuilder, logCallback, "createFunctionRequest");

    if (EmptyPredicate.isEmpty(createFunctionRequestBuilder.getFunction().getName())) {
      throw NestedExceptionUtils.hintWithExplanationException("Function Name should not be blank or null.",
          "Function name is null or blank.", new InvalidRequestException("Invalid Function Name"));
    }
    if (EmptyPredicate.isEmpty(googleFunctionInfraConfig.getRegion())) {
      throw NestedExceptionUtils.hintWithExplanationException("Region should not be blank or null.",
          "Region is null or blank.", new InvalidRequestException("Invalid Region Name"));
    }
    // get function name
    String functionName = googleFunctionCommandTaskHelper.getFunctionName(googleFunctionInfraConfig.getProject(),
        googleFunctionInfraConfig.getRegion(), createFunctionRequestBuilder.getFunction().getName());

    // set location
    createFunctionRequestBuilder.setLocation(googleFunctionCommandTaskHelper.getFunctionParent(
        googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()));

    CloudFunction.Builder cloudFunctionBuilder = createFunctionRequestBuilder.getFunctionBuilder();
    // set function name
    cloudFunctionBuilder.setName(functionName);

    if (!isRollback) {
      // set artifact source
      setArtifactSource(googleFunctionArtifactConfig, cloudFunctionBuilder);
    }

    // check if function already exists
    Optional<CloudFunction> existingFunctionOptional =
        getFunction(functionName, googleFunctionInfraConfig, logCallback);
    if (!isRollback) {
      googleFunctionCommandTaskHelper.printManifestContent(googleFunctionDeployManifestContent, logCallback);
    }
    if (existingFunctionOptional.isEmpty()) {
      // create new function
      logCallback.saveExecutionLog(format("Creating Function: %s in project: %s and region: %s %n", functionName,
                                       googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()),
          LogLevel.INFO);
      CloudFunction function =
          createFunction(createFunctionRequestBuilder.build(), googleFunctionInfraConfig, logCallback, timeout);
      logCallback.saveExecutionLog(format("Created Function: %s in project: %s and region: %s %n&n", functionName,
                                       googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()),
          LogLevel.INFO);
      return function;
    } else {
      // update existing function
      UpdateFunctionRequest.Builder updateFunctionRequestBuilder =
          UpdateFunctionRequest.newBuilder().setFunction(createFunctionRequestBuilder.getFunction());
      if (StringUtils.isNotEmpty(updateFieldMaskContent)) {
        FieldMask.Builder fieldMaskBuilder = FieldMask.newBuilder();
        parseStringContentAsClassBuilder(updateFieldMaskContent, fieldMaskBuilder, logCallback, "updateFieldMask");
        updateFunctionRequestBuilder.setUpdateMask(fieldMaskBuilder.build());
      }
      logCallback.saveExecutionLog(format("Updating Function: %s in project: %s and region: %s %n", functionName,
                                       googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()),
          LogLevel.INFO);
      CloudFunction function =
          updateFunction(updateFunctionRequestBuilder.build(), googleFunctionInfraConfig, logCallback, timeout);
      logCallback.saveExecutionLog(format("Updated Function: %s in project: %s and region: %s %n", functionName,
                                       googleFunctionInfraConfig.getProject(), googleFunctionInfraConfig.getRegion()),
          LogLevel.INFO);
      return function;
    }
  }

  private void setArtifactSource(
      GoogleFunctionArtifactConfig googleFunctionArtifactConfig, CloudFunction.Builder cloudFunctionBuilder) {
    if (googleFunctionArtifactConfig instanceof GoogleCloudStorageArtifactConfig) {
      GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
          (GoogleCloudStorageArtifactConfig) googleFunctionArtifactConfig;
      cloudFunctionBuilder.setSourceArchiveUrl(String.format(GOOGLE_CLOUD_STORAGE_ARTIFACT_FORMAT,
          googleCloudStorageArtifactConfig.getBucket(), googleCloudStorageArtifactConfig.getFilePath()));
    } else if (googleFunctionArtifactConfig instanceof GoogleCloudSourceArtifactConfig) {
      GoogleCloudSourceArtifactConfig googleCloudSourceArtifactConfig =
          (GoogleCloudSourceArtifactConfig) googleFunctionArtifactConfig;
      String url;
      if (GoogleCloudSourceFetchType.BRANCH.getName().equals(
              googleCloudSourceArtifactConfig.getGoogleCloudSourceFetchType().getName())) {
        url = String.format(GOOGLE_CLOUD_SOURCE_ARTIFACT_BRANCH_FORMAT, googleCloudSourceArtifactConfig.getProject(),
            googleCloudSourceArtifactConfig.getRepository(), googleCloudSourceArtifactConfig.getBranch(),
            googleCloudSourceArtifactConfig.getSourceDirectory());
      } else if (GoogleCloudSourceFetchType.TAG.getName().equals(
                     googleCloudSourceArtifactConfig.getGoogleCloudSourceFetchType().getName())) {
        url = String.format(GOOGLE_CLOUD_SOURCE_ARTIFACT_TAG_FORMAT, googleCloudSourceArtifactConfig.getProject(),
            googleCloudSourceArtifactConfig.getRepository(), googleCloudSourceArtifactConfig.getTag(),
            googleCloudSourceArtifactConfig.getSourceDirectory());
      } else {
        url = String.format(GOOGLE_CLOUD_SOURCE_ARTIFACT_COMMIT_FORMAT, googleCloudSourceArtifactConfig.getProject(),
            googleCloudSourceArtifactConfig.getRepository(), googleCloudSourceArtifactConfig.getCommitId(),
            googleCloudSourceArtifactConfig.getSourceDirectory());
      }
      SourceRepository sourceRepository = SourceRepository.newBuilder().setUrl(url).build();
      cloudFunctionBuilder.setSourceRepository(sourceRepository);
    } else {
      throw new InvalidRequestException("Invalid Artifact Source.");
    }
  }

  public CloudFunction createFunction(CreateFunctionRequest createFunctionRequest,
      GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig, LogCallback logCallback, Long timeout)
      throws ExecutionException, InterruptedException {
    validateFunctionStateBeforeDeployment(
        createFunctionRequest.getFunction().getName(), gcpGoogleFunctionInfraConfig, logCallback, timeout);
    OperationFuture<CloudFunction, OperationMetadataV1> operationFuture =
        googleCloudFunctionGenOneClient.createFunction(createFunctionRequest,
            googleFunctionCommandTaskHelper.getGcpInternalConfig(gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(),
                gcpGoogleFunctionInfraConfig.getRegion(), gcpGoogleFunctionInfraConfig.getProject()));
    validateOperationSnapshot(operationFuture.getInitialFuture(), logCallback, "createFunction");
    checkFunctionDeploymentOperationSteadyState(createFunctionRequest.getFunction().getName(),
        gcpGoogleFunctionInfraConfig, logCallback, timeout, operationFuture.getName());
    GetFunctionRequest getFunctionRequest =
        GetFunctionRequest.newBuilder().setName(createFunctionRequest.getFunction().getName()).build();
    return googleCloudFunctionGenOneClient.getFunction(getFunctionRequest,
        googleFunctionCommandTaskHelper.getGcpInternalConfig(gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(),
            gcpGoogleFunctionInfraConfig.getRegion(), gcpGoogleFunctionInfraConfig.getProject()));
  }

  public CloudFunction updateFunction(UpdateFunctionRequest updateFunctionRequest,
      GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig, LogCallback logCallback, Long timeout)
      throws ExecutionException, InterruptedException {
    validateFunctionStateBeforeDeployment(
        updateFunctionRequest.getFunction().getName(), gcpGoogleFunctionInfraConfig, logCallback, timeout);
    OperationFuture<CloudFunction, OperationMetadataV1> operationFuture =
        googleCloudFunctionGenOneClient.updateFunction(updateFunctionRequest,
            googleFunctionCommandTaskHelper.getGcpInternalConfig(gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(),
                gcpGoogleFunctionInfraConfig.getRegion(), gcpGoogleFunctionInfraConfig.getProject()));
    validateOperationSnapshot(operationFuture.getInitialFuture(), logCallback, "updateFunction");
    checkFunctionDeploymentOperationSteadyState(updateFunctionRequest.getFunction().getName(),
        gcpGoogleFunctionInfraConfig, logCallback, timeout, operationFuture.getName());
    GetFunctionRequest getFunctionRequest =
        GetFunctionRequest.newBuilder().setName(updateFunctionRequest.getFunction().getName()).build();
    return googleCloudFunctionGenOneClient.getFunction(getFunctionRequest,
        googleFunctionCommandTaskHelper.getGcpInternalConfig(gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(),
            gcpGoogleFunctionInfraConfig.getRegion(), gcpGoogleFunctionInfraConfig.getProject()));
  }

  public void deleteFunction(String functionName, GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig,
      LogCallback logCallback, Long timeout) throws ExecutionException, InterruptedException {
    GetFunctionRequest getFunctionRequest = GetFunctionRequest.newBuilder().setName(functionName).build();
    try {
      googleCloudFunctionGenOneClient.getFunction(getFunctionRequest,
          googleFunctionCommandTaskHelper.getGcpInternalConfig(gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(),
              gcpGoogleFunctionInfraConfig.getRegion(), gcpGoogleFunctionInfraConfig.getProject()));
    } catch (Exception e) {
      if (e.getCause() instanceof NotFoundException) {
        logCallback.saveExecutionLog(format("Skipping function: %s deletion as it doesn't exist",
            googleFunctionCommandTaskHelper.getResourceName(functionName)));
        return;
      }
      throwGetFunctionFailureException(e, logCallback);
    }
    DeleteFunctionRequest deleteFunctionRequest = DeleteFunctionRequest.newBuilder().setName(functionName).build();
    logCallback.saveExecutionLog(
        format("Deleting function: %s", googleFunctionCommandTaskHelper.getResourceName(functionName)));
    OperationFuture<Empty, OperationMetadataV1> operationFuture =
        googleCloudFunctionGenOneClient.deleteFunction(deleteFunctionRequest,
            googleFunctionCommandTaskHelper.getGcpInternalConfig(gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(),
                gcpGoogleFunctionInfraConfig.getRegion(), gcpGoogleFunctionInfraConfig.getProject()));
    validateOperationSnapshot(operationFuture.getInitialFuture(), logCallback, "deleteFunction");
    checkFunctionDeletionSteadyState(functionName, gcpGoogleFunctionInfraConfig, logCallback, timeout);
  }

  private void validateOperationSnapshot(
      ApiFuture<OperationSnapshot> operationSnapshot, LogCallback logCallback, String type) {
    try {
      operationSnapshot.get();
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      logCallback.saveExecutionLog(color(sanitizedException.getMessage(), Red), ERROR);
      if ("createFunction".equals(type)) {
        throw NestedExceptionUtils.hintWithExplanationException(CREATE_FUNCTION_GEN_ONE_FAILURE_HINT,
            "Create Cloud Function API call failed",
            new InvalidRequestException("Could not able to create cloud function"));
      } else if ("updateFunction".equals(type)) {
        throw NestedExceptionUtils.hintWithExplanationException(UPDATE_FUNCTION_GEN_ONE_FAILURE_HINT,
            "Update Cloud Function API call failed",
            new InvalidRequestException("Could not able to update cloud function"));
      } else if ("deleteFunction".equals(type)) {
        throw NestedExceptionUtils.hintWithExplanationException(DELETE_FUNCTION_FAILURE_HINT,
            "Delete Cloud Function API call failed",
            new InvalidRequestException("Could not able to delete cloud function"));
      }
    }
  }

  private void checkFunctionDeletionSteadyState(String functionName,
      GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig, LogCallback logCallback, Long timeout) {
    try {
      HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMillis(timeout), () -> {
        while (true) {
          logCallback.saveExecutionLog(
              format("Function deletion in progress: %s", color(functionName, LogColor.Yellow)));
          Optional<CloudFunction> optionalCloudFunction =
              getFunction(functionName, gcpGoogleFunctionInfraConfig, logCallback);
          if (optionalCloudFunction.isEmpty()) {
            logCallback.saveExecutionLog(color(format("Deleted Function successfully...%n%n"), LogColor.Green));
            return false;
          }
          if (CloudFunctionStatus.DELETE_IN_PROGRESS.equals(optionalCloudFunction.get().getStatus())) {
            logCallback.saveExecutionLog(format(
                "Function deletion in progress: %s", color(optionalCloudFunction.get().getName(), LogColor.Yellow)));
          }
          Morpheus.sleep(ofSeconds(10));
        }
      });
    } catch (UncheckedTimeoutException e) {
      throw new TimeoutException("Timed out waiting for function to achieve steady state before deployment", "Timeout",
          e, WingsException.EVERYBODY);
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(GET_FUNCTION_FAILURE_HINT, GET_FUNCTION_FAILURE_EXPLAIN,
          new InvalidRequestException(GET_FUNCTION_FAILURE_ERROR));
    }
  }

  private void validateFunctionStateBeforeDeployment(String functionName,
      GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig, LogCallback logCallback, Long timeout) {
    try {
      HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMillis(timeout), () -> {
        while (true) {
          Optional<CloudFunction> functionOptional =
              getFunction(functionName, gcpGoogleFunctionInfraConfig, logCallback);
          if (functionOptional.isEmpty() || CloudFunctionStatus.ACTIVE.equals(functionOptional.get().getStatus())) {
            return true;
          }
          logCallback.saveExecutionLog(
              format("Waiting for function to achieve steady state before deployment, current status is: "
                      + "%s",
                  color(functionOptional.get().getStatus().name(), LogColor.Yellow)));
          Morpheus.sleep(ofSeconds(10));
        }
      });
    } catch (UncheckedTimeoutException e) {
      throw new TimeoutException("Timed out waiting for function to achieve steady state before deployment", "Timeout",
          e, WingsException.EVERYBODY);
    } catch (Exception e) {
      throwGetFunctionFailureException(e, logCallback);
    }
  }

  private void checkFunctionDeploymentOperationSteadyState(String functionName,
      GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig, LogCallback logCallback, Long timeout,
      String operationName) {
    try {
      HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMillis(timeout), () -> {
        while (true) {
          Operation operation = googleCloudFunctionGenOneClient.getOperation(operationName,
              googleFunctionCommandTaskHelper.getGcpInternalConfig(gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(),
                  gcpGoogleFunctionInfraConfig.getRegion(), gcpGoogleFunctionInfraConfig.getProject()));
          if (operation.getDone()) {
            if (Operation.ResultCase.ERROR.equals(operation.getResultCase())) {
              logCallback.saveExecutionLog(color("Function Deployment failed...", LogColor.Red));
              logCallback.saveExecutionLog(color(operation.getError().getMessage(), LogColor.Red));
              throw new RuntimeException(operation.getError().getMessage());
            }
            return false;
          }
          logCallback.saveExecutionLog(
              format("Function deployment in progress: %s", color(functionName, LogColor.Yellow)));
          Morpheus.sleep(ofSeconds(10));
        }
      });
    } catch (UncheckedTimeoutException e) {
      throw new TimeoutException("Timed out waiting for function to achieve steady state before deployment", "Timeout",
          e, WingsException.EVERYBODY);
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException(CREATE_FUNCTION_GEN_ONE_FAILURE_HINT,
          "Cloud Function Deployment failed.",
          new InvalidRequestException("Function couldn't able to achieve steady state."));
    }
  }

  public Optional<CloudFunction> getFunction(
      String functionName, GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig, LogCallback logCallback) {
    GetFunctionRequest getFunctionRequest = GetFunctionRequest.newBuilder().setName(functionName).build();
    try {
      return Optional.of(googleCloudFunctionGenOneClient.getFunction(getFunctionRequest,
          googleFunctionCommandTaskHelper.getGcpInternalConfig(gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(),
              gcpGoogleFunctionInfraConfig.getRegion(), gcpGoogleFunctionInfraConfig.getProject())));
    } catch (Exception e) {
      if (e.getCause() instanceof NotFoundException) {
        return Optional.empty();
      }
      throwGetFunctionFailureException(e, logCallback);
    }
    return Optional.empty();
  }

  public void parseStringContentAsClassBuilder(
      String content, Message.Builder builder, LogCallback logCallback, String type) {
    try {
      JsonNode jsonNode = yamlUtils.read(content, JsonNode.class);
      String jsonContent = JsonUtils.asJson(jsonNode);
      JsonFormat.parser().ignoringUnknownFields().merge(jsonContent, builder);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      logCallback.saveExecutionLog(color(sanitizedException.getMessage(), Red), ERROR);
      if ("createFunctionRequest".equals(type)) {
        throw NestedExceptionUtils.hintWithExplanationException(CREATE_FUNCTION_PARSE_FAILURE_HINT,
            "Could not able to parse Google Function manifest into object of createFunctionRequest",
            new InvalidRequestException("Parsing of manifest content failed"));
      } else if ("updateFieldMask".equals(type)) {
        throw NestedExceptionUtils.hintWithExplanationException(FIELD_MASK_PARSE_FAILURE_HINT,
            "Could not able to parse updateFieldMask input into object of FieldMask",
            new InvalidRequestException("Parsing of updateFieldMask failed"));
      }
      throw new InvalidRequestException(sanitizedException.getMessage());
    }
  }

  public GoogleFunction getGoogleFunction(CloudFunction function, LogCallback logCallback)
      throws InvalidProtocolBufferException {
    saveLogs(logCallback, color("Updated Functions details: ", Blue, Bold), INFO);
    saveLogs(logCallback, JsonFormat.printer().print(function), INFO);
    GoogleFunction.GoogleCloudRunService googleCloudRunService = GoogleFunction.GoogleCloudRunService.builder().build();

    String source;
    if (EmptyPredicate.isNotEmpty(function.getSourceArchiveUrl())) {
      source = function.getSourceArchiveUrl();
    } else {
      source = function.getSourceRepository().getUrl();
    }
    String url = "";
    if (function.hasHttpsTrigger() && function.getHttpsTrigger() != null) {
      url = function.getHttpsTrigger().getUrl();
    }

    return GoogleFunction.builder()
        .functionName(function.getName())
        .state(function.getStatus().toString())
        .runtime(function.getRuntime())
        .source(source)
        .updatedTime(function.getUpdateTime().getSeconds())
        .environment(GoogleFunctionUtils.ENVIRONMENT_TYPE_GEN_ONE)
        .cloudRunService(googleCloudRunService)
        .activeCloudRunRevisions(Lists.newArrayList())
        .url(url)
        .build();
    // todo: change time format
    // todo: make env a constant
  }

  private void saveLogs(LogCallback executionLogCallback, String message, LogLevel logLevel) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message, logLevel);
    }
  }

  private void throwGetFunctionFailureException(Exception e, LogCallback logCallback) {
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
    if (logCallback != null) {
      logCallback.saveExecutionLog(color(sanitizedException.getMessage(), Red), ERROR);
    }
    throw NestedExceptionUtils.hintWithExplanationException(GET_FUNCTION_FAILURE_HINT, GET_FUNCTION_FAILURE_EXPLAIN,
        new InvalidRequestException(GET_FUNCTION_FAILURE_ERROR));
  }
}
