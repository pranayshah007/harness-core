/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.ci.commonconstants.CIExecutionConstants.PATH_SEPARATOR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;
import static io.harness.govern.Switch.unhandled;
import static io.harness.pms.execution.utils.StatusUtils.isFinalStatus;
import static io.harness.steps.StepUtils.buildAbstractions;

import io.harness.PipelineUtils;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.ci.buildstate.CodebaseUtils;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.ci.states.codebase.CodeBaseTaskStepParameters;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.task.ci.CIBuildStatusPushParameters;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.encryption.Scope;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.git.checks.GitStatusCheckHelper;
import io.harness.git.checks.GitStatusCheckParams;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.remote.client.CGRestUtils;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class GitBuildStatusUtility {
  private static final String UNSUPPORTED = "UNSUPPORTED";
  private static final String GITHUB_ERROR = "error";
  private static final String GITHUB_SUCCESS = "success";
  private static final String GITHUB_FAILED = "failure";
  private static final String GITHUB_PENDING = "pending";
  private static final String BITBUCKET_FAILED = "FAILED";
  private static final String BITBUCKET_SUCCESS = "SUCCESSFUL";
  private static final String BITBUCKET_PENDING = "INPROGRESS";
  private static final String GITLAB_FAILED = "failed";
  private static final String GITLAB_CANCELED = "canceled";
  private static final String GITLAB_PENDING = "pending";
  private static final String GITLAB_SUCCESS = "success";
  private static final String AZURE_REPO_ERROR = "error";
  private static final String AZURE_REPO_FAILED = "failed";
  private static final String AZURE_REPO_PENDING = "pending";
  private static final String AZURE_REPO_SUCCESS = "succeeded";
  private static final String DASH = "-";
  private static final int IDENTIFIER_LENGTH = 30;
  private static final int IDENTIFIER_LENGTH_BB_SAAS = 19;
  private static final List<ConnectorType> validConnectors = Arrays.asList(GITHUB, GITLAB, BITBUCKET, AZURE_REPO);

  @Inject private ConnectorUtils connectorUtils;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject @Named("ngBaseUrl") private String ngBaseUrl;
  @Inject private PipelineUtils pipelineUtils;
  @Inject private AccountClient accountClient;
  @Inject private GitStatusCheckHelper gitStatusCheckHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public boolean shouldSendStatus(StepCategory stepCategory) {
    return stepCategory == StepCategory.STAGE;
  }

  public boolean isCodeBaseStepSucceeded(Level level, Status status) {
    return (level.getStepType().getType().equals(CodeBaseTaskStep.STEP_TYPE.getType())) && status == Status.SUCCEEDED;
  }

  /**
   * Status, ResolvedStepParameters
   * @param nodeExecution
   * @param ambiance
   * @param accountId
   */
  public void sendStatusToGit(Status status, StepParameters stepParameters, Ambiance ambiance, String accountId) {
    String commitSha = null;
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
    CodebaseSweepingOutput codebaseSweepingOutput = null;
    if (optionalSweepingOutput.isFound()) {
      codebaseSweepingOutput = (CodebaseSweepingOutput) optionalSweepingOutput.getOutput();
      if (codebaseSweepingOutput != null) {
        commitSha = codebaseSweepingOutput.getCommitSha();
      }
    }
    BuildStatusUpdateParameter buildStatusUpdateParameter = fetchBuildStatusUpdateParameter(stepParameters, ambiance);

    if (commitSha == null && buildStatusUpdateParameter != null) {
      /* This will be used only in case of sending running state of stage build

      Running state status will not work for manual executed builds
       */
      commitSha = buildStatusUpdateParameter.getSha();
    }

    if (buildStatusUpdateParameter != null && isNotEmpty(commitSha)) {
      CIBuildStatusPushParameters ciBuildStatusPushParameters =
          getCIBuildStatusPushParams(ambiance, buildStatusUpdateParameter, status, commitSha);

      /* This check is require because delegate is not honouring the ordering and
         there are instances where we are overriding final status with prev state status i.e running specially in case
         time interval is minuscule
      */

      if (isFinalStatus(status)) {
        try {
          Thread.sleep(5000);
        } catch (InterruptedException exception) {
          // Ignore
        }
      }

      if (ciBuildStatusPushParameters.getState() != UNSUPPORTED) {
        ConnectorDetails connectorDetails = ciBuildStatusPushParameters.getConnectorDetails();
        boolean executeOnDelegate =
            connectorDetails.getExecuteOnDelegate() == null || connectorDetails.getExecuteOnDelegate();
        if (executeOnDelegate) {
          sendStatusViaDelegate(
              ambiance, ciBuildStatusPushParameters, accountId, buildStatusUpdateParameter.getIdentifier());
        } else {
          sendStatus(ambiance, ciBuildStatusPushParameters, accountId, buildStatusUpdateParameter.getIdentifier());
        }
      } else {
        log.info("Skipping git status update request for stage {}, planId {}, commitId {}, status {}, scm type {}",
            buildStatusUpdateParameter.getIdentifier(), ambiance.getPlanExecutionId(), commitSha,
            ciBuildStatusPushParameters.getState(), ciBuildStatusPushParameters.getGitSCMType());
      }
    }
  }

  private void sendStatus(
      Ambiance ambiance, CIBuildStatusPushParameters ciBuildStatusPushParameters, String accountId, String stageId) {
    GitStatusCheckParams gitStatusCheckParams = convertParams(ciBuildStatusPushParameters);
    log.info("Sending git status update request for stage {}, planId {}, commitId {}, status {}", stageId,
        ambiance.getPlanExecutionId(), ciBuildStatusPushParameters.getSha(), ciBuildStatusPushParameters.getState());
    gitStatusCheckHelper.sendStatus(gitStatusCheckParams);
  }

  private void sendStatusViaDelegate(
      Ambiance ambiance, CIBuildStatusPushParameters ciBuildStatusPushParameters, String accountId, String stageId) {
    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
    DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                  .accountId(accountId)
                                                  .taskSetupAbstractions(abstractions)
                                                  .executionTimeout(java.time.Duration.ofSeconds(60))
                                                  .taskType("BUILD_STATUS")
                                                  .taskParameters(ciBuildStatusPushParameters)
                                                  .taskDescription("CI git build status task")
                                                  .build();

    String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
    log.info("Submitted git status update request for stage {}, planId {}, commitId {}, status {} with taskId {}",
        stageId, ambiance.getPlanExecutionId(), ciBuildStatusPushParameters.getSha(),
        ciBuildStatusPushParameters.getState(), taskId);
  }

  private GitStatusCheckParams convertParams(CIBuildStatusPushParameters params) {
    return GitStatusCheckParams.builder()
        .title(params.getTitle())
        .desc(params.getDesc())
        .state(params.getState())
        .buildNumber(params.getBuildNumber())
        .detailsUrl(params.getDetailsUrl())
        .repo(params.getRepo())
        .owner(params.getOwner())
        .sha(params.getSha())
        .identifier(params.getIdentifier())
        .target_url(params.getTarget_url())
        .userName(params.getUserName())
        .connectorDetails(params.getConnectorDetails())
        .gitSCMType(params.getGitSCMType())
        .build();
  }

  private GitRepositoryDTO getRepositoryFromApiUrl(ConnectorDetails gitConnector, String url) {
    if (gitConnector.getConnectorConfig() instanceof GitlabConnectorDTO) {
      GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();

      if (!GitAuthType.HTTP.equals(gitlabConnectorDTO.getAuthentication().getAuthType())
          || gitlabConnectorDTO.getApiAccess() == null
          || !(gitlabConnectorDTO.getApiAccess().getSpec() instanceof GitlabTokenSpecDTO)) {
        return null;
      }
      GitlabTokenSpecDTO gitlabTokenSpecDTO = (GitlabTokenSpecDTO) gitlabConnectorDTO.getApiAccess().getSpec();
      String apiUrl = gitlabTokenSpecDTO.getApiUrl();
      if (StringUtils.isBlank(apiUrl)) {
        return null;
      }
      url = StringUtils.removeEnd(url, PATH_SEPARATOR);
      apiUrl = StringUtils.removeEnd(apiUrl, PATH_SEPARATOR) + PATH_SEPARATOR;
      String ownerAndRepo = StringUtils.removeStart(url, apiUrl);
      ownerAndRepo = StringUtils.removeEnd(ownerAndRepo, ".git");
      if (ownerAndRepo.contains("/")) {
        String[] parts = ownerAndRepo.split("/");
        String repo = parts[parts.length - 1];
        String owner = StringUtils.removeEnd(ownerAndRepo, "/" + repo);
        return GitRepositoryDTO.builder().name(repo).org(owner).build();
      }
    }
    return null;
  }

  public CIBuildStatusPushParameters getCIBuildStatusPushParams(
      Ambiance ambiance, BuildStatusUpdateParameter buildStatusUpdateParameter, Status status, String commitSha) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorDetails gitConnector = getGitConnector(ngAccess, buildStatusUpdateParameter.getConnectorIdentifier());
    validateSCMType(gitConnector.getConnectorType());

    String repoName = buildStatusUpdateParameter.getRepoName();
    String url = CodebaseUtils.getCompleteURLFromConnector(gitConnector, repoName);

    url = StringUtils.join(StringUtils.stripEnd(url, PATH_SEPARATOR), PATH_SEPARATOR);

    String finalRepo = GitClientHelper.getGitRepo(url);
    String ownerName = GitClientHelper.getGitOwner(url, false);

    GitRepositoryDTO gitRepositoryDTO = getRepositoryFromApiUrl(gitConnector, url);
    if (gitRepositoryDTO != null) {
      finalRepo = gitRepositoryDTO.getName();
      ownerName = gitRepositoryDTO.getOrg();
    }

    GitSCMType gitSCMType = retrieveSCMType(gitConnector);

    String detailsUrl = getBuildDetailsUrl(ambiance);

    return CIBuildStatusPushParameters.builder()
        .detailsUrl(detailsUrl)
        .desc(generateDesc(ambiance.getMetadata().getPipelineIdentifier(), ambiance.getMetadata().getExecutionUuid(),
            buildStatusUpdateParameter.getName(), status.name()))
        .sha(commitSha)
        .gitSCMType(gitSCMType)
        .connectorDetails(gitConnector)
        .userName(connectorUtils.fetchUserName(gitConnector))
        .owner(ownerName)
        .repo(finalRepo)
        .identifier(generateIdentifier(
            url, ambiance.getMetadata().getPipelineIdentifier(), buildStatusUpdateParameter.getIdentifier()))
        .state(retrieveBuildStatusState(gitSCMType, status))
        .build();
  }

  private String generateDesc(String pipeline, String executionId, String stage, String status) {
    return String.format(
        "Execution status of Pipeline - %s (%s) Stage - %s was %s", pipeline, executionId, stage, status);
  }

  private String generateIdentifier(String url, String pipelineIdentifer, String stageIdentifer) {
    // Since bitbucket saas only allows max 40 characters in the key, https://harness.atlassian.net/browse/CI-5411
    if (GitClientHelper.isBitBucketSAAS(url)) {
      return String.join(DASH, StringUtils.abbreviate(pipelineIdentifer, IDENTIFIER_LENGTH_BB_SAAS),
          StringUtils.abbreviate(stageIdentifer, IDENTIFIER_LENGTH_BB_SAAS));
    }

    return String.join(DASH, StringUtils.abbreviate(pipelineIdentifer, IDENTIFIER_LENGTH),
        StringUtils.abbreviate(stageIdentifer, IDENTIFIER_LENGTH));
  }

  private GitSCMType retrieveSCMType(ConnectorDetails gitConnector) {
    if (gitConnector.getConnectorType() == GITHUB) {
      return GitSCMType.GITHUB;
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      return GitSCMType.BITBUCKET;
    } else if (gitConnector.getConnectorType() == GITLAB) {
      return GitSCMType.GITLAB;
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      return GitSCMType.AZURE_REPO;
    } else {
      throw new CIStageExecutionException("scmType " + gitConnector.getConnectorType() + "is not supported");
    }
  }

  private void validateSCMType(ConnectorType connectorType) {
    if (!validConnectors.contains(connectorType)) {
      throw new CIStageExecutionException("scmType " + connectorType + "is not supported");
    }
  }

  private String retrieveBuildStatusState(GitSCMType gitSCMType, Status status) {
    switch (gitSCMType) {
      case GITHUB:
        return getGitHubStatus(status);
      case GITLAB:
        return getGitLabStatus(status);
      case BITBUCKET:
        return getBitBucketStatus(status);
      case AZURE_REPO:
        return getAzureRepoStatus(status);
      default:
        unhandled(gitSCMType);
        return UNSUPPORTED;
    }
  }

  private String getGitHubStatus(Status status) {
    if (status == Status.ERRORED) {
      return GITHUB_ERROR;
    }
    if (status == Status.ABORTED || status == Status.FAILED || status == Status.EXPIRED) {
      return GITHUB_FAILED;
    }
    if (status == Status.SUCCEEDED || status == Status.IGNORE_FAILED) {
      return GITHUB_SUCCESS;
    }
    if (status == Status.RUNNING) {
      return GITHUB_PENDING;
    }
    if (status == Status.QUEUED) {
      return GITHUB_PENDING;
    }

    return UNSUPPORTED;
  }

  private String getAzureRepoStatus(Status status) {
    switch (status) {
      case ERRORED:
        return AZURE_REPO_ERROR;
      case ABORTED:
      case FAILED:
      case EXPIRED:
        return AZURE_REPO_FAILED;
      case SUCCEEDED:
      case IGNORE_FAILED:
        return AZURE_REPO_SUCCESS;
      case RUNNING:
      case QUEUED:
        return AZURE_REPO_PENDING;
      default:
        return UNSUPPORTED;
    }
  }

  private String getGitLabStatus(Status status) {
    if (status == Status.ERRORED || status == Status.FAILED || status == Status.EXPIRED) {
      return GITLAB_FAILED;
    }
    if (status == Status.ABORTED) {
      return GITLAB_CANCELED;
    }
    if (status == Status.SUCCEEDED || status == Status.IGNORE_FAILED) {
      return GITLAB_SUCCESS;
    }
    if (status == Status.RUNNING) {
      return GITLAB_PENDING;
    }
    if (status == Status.QUEUED) {
      return GITLAB_PENDING;
    }

    return UNSUPPORTED;
  }

  private String getBitBucketStatus(Status status) {
    if (status == Status.ERRORED) {
      return BITBUCKET_FAILED;
    }
    if (status == Status.ABORTED || status == Status.FAILED || status == Status.EXPIRED) {
      return BITBUCKET_FAILED;
    }
    if (status == Status.SUCCEEDED || status == Status.IGNORE_FAILED) {
      return BITBUCKET_SUCCESS;
    }
    if (status == Status.RUNNING) {
      return BITBUCKET_PENDING;
    }
    if (status == Status.QUEUED) {
      return BITBUCKET_PENDING;
    }

    return UNSUPPORTED;
  }

  private BuildStatusUpdateParameter fetchBuildStatusUpdateParameter(StepParameters stepParameters, Ambiance ambiance) {
    if (stepParameters instanceof StageElementParameters) {
      StageElementParameters stageElementParameters = (StageElementParameters) stepParameters;
      IntegrationStageStepParametersPMS integrationStageStepParameters =
          (IntegrationStageStepParametersPMS) stageElementParameters.getSpecConfig();
      return integrationStageStepParameters.getBuildStatusUpdateParameter() != null
          ? integrationStageStepParameters.getBuildStatusUpdateParameter()
          : fetchBuildStatusUpdateParameterFromStageDetails(ambiance);
    } else if (stepParameters instanceof CodeBaseTaskStepParameters) {
      return fetchBuildStatusUpdateParameterFromStageDetails(ambiance);
    }

    return null;
  }

  private BuildStatusUpdateParameter fetchBuildStatusUpdateParameterFromStageDetails(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutputStageDetails = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (optionalSweepingOutputStageDetails.isFound()) {
      StageDetails stageDetails = (StageDetails) optionalSweepingOutputStageDetails.getOutput();
      return stageDetails.getBuildStatusUpdateParameter();
    }
    return null;
  }

  private ConnectorDetails getGitConnector(NGAccess ngAccess, String connectorRef) {
    return connectorUtils.getConnectorDetails(ngAccess, connectorRef);
  }

  public String getBuildDetailsUrl(Ambiance ambiance) {
    String stageSetupId = AmbianceUtils.getStageSetupIdAmbiance(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String baseUrl = getNgBaseUrl(getVanityUrl(ngAccess.getAccountIdentifier()), ngBaseUrl);
    String pipelineId = ambiance.getMetadata().getPipelineIdentifier();
    String executionId = ambiance.getMetadata().getExecutionUuid();
    return pipelineUtils.getBuildDetailsUrl(ngAccess, pipelineId, executionId, baseUrl, stageSetupId, stageExecutionId);
  }

  private String getVanityUrl(String accountID) {
    return CGRestUtils.getResponse(accountClient.getVanityUrl(accountID));
  }

  private String getNgBaseUrl(String vanityUrl, String defaultBaseUrl) {
    if (isEmpty(vanityUrl)) {
      return defaultBaseUrl;
    }

    String newBaseUrl = StringUtils.stripEnd(vanityUrl, PATH_SEPARATOR);
    try {
      URL url = new URL(defaultBaseUrl);
      String hostUrl = String.format("%s://%s", url.getProtocol(), url.getHost());
      return StringUtils.join(newBaseUrl, defaultBaseUrl.substring(hostUrl.length()));
    } catch (Exception e) {
      log.warn("There was error while generating vanity URL", e);
      return defaultBaseUrl;
    }
  }
}
