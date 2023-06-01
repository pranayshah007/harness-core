/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jenkins.jenkinsstep;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildOutcome.JenkinsBuildOutcomeBuilder;
import io.harness.common.NGTaskType;
import io.harness.common.NGTimeConversionHelper;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest.JenkinsArtifactDelegateRequestBuilder;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.delegate.task.jenkins.JenkinsBuildTaskNGResponse;
import io.harness.encryption.Scope;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.stream.Collectors;

public class JenkinsBuildStepHelperServiceImpl implements JenkinsBuildStepHelperService {
  private final ConnectorResourceClient connectorResourceClient;
  private final SecretManagerClientService secretManagerClientService;
  private final KryoSerializer referenceFalseKryoSerializer;
  private final LogStreamingStepClientFactory logStreamingStepClientFactory;
  public static final String COMMAND_UNIT = "Execute";
  public static final String IF_FAIL_MESSAGE = "Jenkins Get Job task failure due to error";
  public static final String JENKINS_QUEUE_TASK_NAME = "Jenkins Task: Queue Jenkins Build Task";
  public static final String JENKINS_POLL_TASK_NAME = "Jenkins Task: Poll Jenkins Build Task";
  @VisibleForTesting static final int TIMEOUT_IN_SECS = 120;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Inject
  public JenkinsBuildStepHelperServiceImpl(ConnectorResourceClient connectorResourceClient,
      @Named("PRIVILEGED") SecretManagerClientService secretManagerClientService,
      @Named("referenceFalseKryoSerializer") KryoSerializer kryoSerializer,
      LogStreamingStepClientFactory logStreamingStepClientFactory) {
    this.connectorResourceClient = connectorResourceClient;
    this.secretManagerClientService = secretManagerClientService;
    this.referenceFalseKryoSerializer = kryoSerializer;
    this.logStreamingStepClientFactory = logStreamingStepClientFactory;
  }

  @Override
  public TaskRequest prepareTaskRequest(JenkinsArtifactDelegateRequestBuilder paramsBuilder, Ambiance ambiance,
      String connectorRef, String timeStr, String taskName) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorRef, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorDTO> connectorDTOOptional = NGRestUtils.getResponse(
        connectorResourceClient.get(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()));
    if (!connectorDTOOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Connector not found for identifier: [%s]", connectorRef), WingsException.USER);
    }
    ConnectorConfigDTO configDTO = connectorDTOOptional.get().getConnectorInfo().getConnectorConfig();
    if (!(configDTO instanceof JenkinsConnectorDTO)) {
      throw new InvalidRequestException(
          String.format("Connector [%s] is not a jenkins connector", connectorRef), WingsException.USER);
    }

    JenkinsConnectorDTO connectorDTO = (JenkinsConnectorDTO) configDTO;
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    paramsBuilder.jenkinsConnectorDTO(connectorDTO);
    paramsBuilder.encryptedDataDetails(
        secretManagerClientService.getEncryptionDetails(ngAccess, connectorDTO.getAuth().getCredentials()));
    JenkinsArtifactDelegateRequest params = paramsBuilder.build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(
        params, ArtifactTaskType.JENKINS_BUILD, baseNGAccess, ambiance, "Jenkins Get Job task failure due to error");

    if (isNotEmpty(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getQueuedBuildUrl())) {
      paramsBuilder.queuedBuildUrl(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getQueuedBuildUrl());
      params = paramsBuilder.build();
      ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                          .accountId(ngAccess.getAccountIdentifier())
                                                          .artifactTaskType(ArtifactTaskType.JENKINS_POLL_TASK)
                                                          .attributes(params)
                                                          .build();
      TaskData taskData = TaskData.builder()
                              .async(true)
                              .timeout(NGTimeConversionHelper.convertTimeStringToMilliseconds(timeStr))
                              .taskType(NGTaskType.JENKINS_ARTIFACT_TASK_NG.name())
                              .parameters(new Object[] {artifactTaskParameters})
                              .build();
      return TaskRequestsUtils.prepareTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
          TaskCategory.DELEGATE_TASK_V2, Collections.singletonList(COMMAND_UNIT), true, taskName,
          params.getDelegateSelectors()
              .stream()
              .map(s -> TaskSelector.newBuilder().setSelector(s).build())
              .collect(Collectors.toList()),
          Scope.PROJECT, EnvironmentType.ALL, false, Collections.emptyList(), false, null);
    } else {
      ILogStreamingStepClient logStreamingStepClient =
          logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      logStreamingStepClient.closeStream(COMMAND_UNIT);
      throw new RuntimeException("Jenkins Queued Build URL is empty and could not start POLL_TASK");
    }
  }

  private static ArtifactTaskResponse getArtifactTaskResponse(ResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactServerException(IF_FAIL_MESSAGE + " - " + errorNotifyResponseData.getErrorMessage());
    } else if (responseData instanceof RemoteMethodReturnValueData) {
      RemoteMethodReturnValueData remoteMethodReturnValueData = (RemoteMethodReturnValueData) responseData;
      if (remoteMethodReturnValueData.getException() instanceof InvalidRequestException) {
        throw(InvalidRequestException)(remoteMethodReturnValueData.getException());
      } else {
        throw new ArtifactServerException(
            "Unexpected error during authentication to jenkins server " + remoteMethodReturnValueData.getReturnValue(),
            USER);
      }
    }
    return (ArtifactTaskResponse) responseData;
  }

  @Override
  public TaskChainResponse queueJenkinsBuildTask(JenkinsArtifactDelegateRequestBuilder paramsBuilder, Ambiance ambiance,
      StepElementParameters stepElementParameters) {
    TaskRequest taskRequest = queueDelegateTask(
        ambiance, stepElementParameters, paramsBuilder, ArtifactTaskType.JENKINS_BUILD, JENKINS_QUEUE_TASK_NAME);
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .passThroughData(JenkinsStepPassThoughData.builder().initStepStartTime(System.currentTimeMillis()).build())
        .chainEnd(false)
        .build();
  }

  @Override
  public TaskChainResponse pollJenkinsJob(JenkinsArtifactDelegateRequestBuilder paramsBuilder, Ambiance ambiance,
      StepElementParameters stepElementParameters, ResponseData responseData) {
    ArtifactTaskResponse artifactTaskResponse = getArtifactTaskResponse(responseData);
    if (artifactTaskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactServerException(IF_FAIL_MESSAGE + " - " + artifactTaskResponse.getErrorMessage()
          + " with error code: " + artifactTaskResponse.getErrorCode());
    }
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        artifactTaskResponse.getArtifactTaskExecutionResponse();

    if (isEmpty(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getQueuedBuildUrl())) {
      ILogStreamingStepClient logStreamingStepClient =
          logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      logStreamingStepClient.closeStream(COMMAND_UNIT);
      throw new ArtifactServerException("Jenkins Queued Build URL is empty and could not start POLL_TASK");
    }
    paramsBuilder.queuedBuildUrl(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getQueuedBuildUrl());
    TaskRequest taskRequest = queueDelegateTask(
        ambiance, stepElementParameters, paramsBuilder, ArtifactTaskType.JENKINS_POLL_TASK, JENKINS_POLL_TASK_NAME);
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .passThroughData(JenkinsStepPassThoughData.builder().initStepStartTime(System.currentTimeMillis()).build())
        .chainEnd(true)
        .build();
  }

  private TaskRequest queueDelegateTask(Ambiance ambiance, StepElementParameters stepParameters,
      JenkinsArtifactDelegateRequestBuilder paramsBuilder, ArtifactTaskType taskType, String jenkinsPollTaskName) {
    JenkinsBuildSpecParameters specParameters = (JenkinsBuildSpecParameters) stepParameters.getSpec();
    String connectorRef = specParameters.getConnectorRef().getValue();
    String timeStr = stepParameters.getTimeout().getValue();

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    JenkinsConnectorDTO connectorDTO = getConnectorDTO(ambiance, connectorRef);
    paramsBuilder.jenkinsConnectorDTO(connectorDTO);
    paramsBuilder.encryptedDataDetails(
        secretManagerClientService.getEncryptionDetails(ngAccess, connectorDTO.getAuth().getCredentials()));
    JenkinsArtifactDelegateRequest params = paramsBuilder.build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(taskType)
                                                        .attributes(params)
                                                        .build();
    TaskData taskData = TaskData.builder()
                            .async(true)
                            .timeout(NGTimeConversionHelper.convertTimeStringToMilliseconds(timeStr))
                            .taskType(NGTaskType.JENKINS_ARTIFACT_TASK_NG.name())
                            .parameters(new Object[] {artifactTaskParameters})
                            .build();
    return TaskRequestsUtils.prepareTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        TaskCategory.DELEGATE_TASK_V2, Collections.singletonList(COMMAND_UNIT), true, jenkinsPollTaskName,
        params.getDelegateSelectors()
            .stream()
            .map(s -> TaskSelector.newBuilder().setSelector(s).build())
            .collect(Collectors.toList()),
        Scope.PROJECT, EnvironmentType.ALL, false, Collections.emptyList(), false, null);
  }

  private JenkinsConnectorDTO getConnectorDTO(Ambiance ambiance, String connectorRef) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorRef, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorDTO> connectorDTOOptional = NGRestUtils.getResponse(
        connectorResourceClient.get(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()));
    if (!connectorDTOOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Connector not found for identifier: [%s]", connectorRef), WingsException.USER);
    }

    ConnectorConfigDTO configDTO = connectorDTOOptional.get().getConnectorInfo().getConnectorConfig();
    if (!(configDTO instanceof JenkinsConnectorDTO)) {
      throw new InvalidRequestException(
          String.format("Connector [%s] is not a jenkins connector", connectorRef), WingsException.USER);
    }
    return (JenkinsConnectorDTO) configDTO;
  }

  @Override
  public StepResponse prepareStepResponseV2(ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    return getStepResponse((ArtifactTaskResponse) responseSupplier.get());
  }

  @Override
  public StepResponse prepareStepResponse(ThrowingSupplier<ArtifactTaskResponse> responseSupplier) throws Exception {
    return getStepResponse(responseSupplier.get());
  }

  private StepResponse getStepResponse(ArtifactTaskResponse artifactTaskResponse) {
    JenkinsBuildTaskNGResponse jenkinsBuildTaskNGResponse =
        artifactTaskResponse.getArtifactTaskExecutionResponse().getJenkinsBuildTaskNGResponse();
    JenkinsBuildOutcomeBuilder jenkinsBuildOutcomeBuilder =
        JenkinsBuildOutcome.builder()
            .buildDisplayName(jenkinsBuildTaskNGResponse.getBuildDisplayName())
            .buildFullDisplayName(jenkinsBuildTaskNGResponse.getBuildFullDisplayName())
            .buildNumber(jenkinsBuildTaskNGResponse.getBuildNumber())
            .envVars(jenkinsBuildTaskNGResponse.getEnvVars())
            .jobUrl(jenkinsBuildTaskNGResponse.getJobUrl())
            .queuedBuildUrl(jenkinsBuildTaskNGResponse.getQueuedBuildUrl())
            .executionStatus(jenkinsBuildTaskNGResponse.getExecutionStatus())
            .description(jenkinsBuildTaskNGResponse.getDescription());
    Status status = Status.SUCCEEDED;
    if (!ExecutionStatus.SUCCESS.equals(jenkinsBuildTaskNGResponse.getExecutionStatus())) {
      status = Status.FAILED;
    }
    return StepResponse.builder()
        .status(status)
        .stepOutcome(StepOutcome.builder().name("build").outcome(jenkinsBuildOutcomeBuilder.build()).build())
        .build();
  }

  public ArtifactTaskExecutionResponse executeSyncTask(JenkinsArtifactDelegateRequest jenkinsRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, Ambiance ambiance, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, jenkinsRequest, taskType, ambiance);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(BaseNGAccess ngAccess, JenkinsArtifactDelegateRequest delegateRequest,
      ArtifactTaskType artifactTaskType, Ambiance ambiance) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(delegateRequest)
                                                        .build();
    LinkedHashMap<String, String> logAbstractionMap = StepUtils.generateLogAbstractions(ambiance);
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.JENKINS_ARTIFACT_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(TIMEOUT_IN_SECS))
            .taskSetupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
            .taskSetupAbstraction("ng", "true")
            .taskSetupAbstraction("owner", ngAccess.getOrgIdentifier() + "/" + ngAccess.getProjectIdentifier())
            .taskSetupAbstraction("projectIdentifier", ngAccess.getProjectIdentifier())
            .taskSelectors(delegateRequest.getDelegateSelectors())
            .logStreamingAbstractions(logAbstractionMap)
            .build();
    return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
  }

  private ArtifactTaskExecutionResponse getTaskExecutionResponse(
      DelegateResponseData responseData, String ifFailedMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactServerException(ifFailedMessage + " - " + errorNotifyResponseData.getErrorMessage());
    } else if (responseData instanceof RemoteMethodReturnValueData) {
      RemoteMethodReturnValueData remoteMethodReturnValueData = (RemoteMethodReturnValueData) responseData;
      if (remoteMethodReturnValueData.getException() instanceof InvalidRequestException) {
        throw(InvalidRequestException)(remoteMethodReturnValueData.getException());
      } else {
        throw new ArtifactServerException(
            "Unexpected error during authentication to docker server " + remoteMethodReturnValueData.getReturnValue(),
            USER);
      }
    }
    ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) responseData;
    if (artifactTaskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactServerException(ifFailedMessage + " - " + artifactTaskResponse.getErrorMessage()
          + " with error code: " + artifactTaskResponse.getErrorCode());
    }
    return artifactTaskResponse.getArtifactTaskExecutionResponse();
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
