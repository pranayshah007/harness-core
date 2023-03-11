/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.codebase;

import static io.harness.beans.execution.ExecutionSource.Type.MANUAL;
import static io.harness.beans.execution.ExecutionSource.Type.WEBHOOK;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import static software.wings.beans.TaskType.SCM_GIT_REF_TASK;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.CommitDetails;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.sweepingoutputs.Build;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput.CodeBaseCommit;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.ci.buildstate.CodebaseUtils;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.utils.WebhookTriggerProcessorUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.ScmGitRefTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.executables.TaskExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.FindPRResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CodeBaseTaskStep implements TaskExecutable<CodeBaseTaskStepParameters, ScmGitRefTaskResponseData>,
                                         SyncExecutable<CodeBaseTaskStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType("CI_CODEBASE_TASK").setStepCategory(StepCategory.STEP).build();

  @Inject private KryoSerializer kryoSerializer;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Inject private ScmGitRefManager scmGitRefManager;

  @Override
  public Class<CodeBaseTaskStepParameters> getStepParametersClass() {
    return CodeBaseTaskStepParameters.class;
  }

  @Override
  public TaskRequest obtainTask(
      Ambiance ambiance, CodeBaseTaskStepParameters stepParameters, StepInputPackage inputPackage) {
    ExecutionSource executionSource = getExecutionSource(ambiance, stepParameters.getExecutionSource());
    if (executionSource.getType() != MANUAL) {
      throw new CIStageExecutionException("{} type is not supported in codebase delegate task for scm api operation");
    }

    ManualExecutionSource manualExecutionSource = (ManualExecutionSource) executionSource;
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(AmbianceUtils.getNgAccess(ambiance),
        RunTimeInputHandler.resolveStringParameterV2("connectorRef", STEP_TYPE.getType(),
            ambiance.getStageExecutionId(), stepParameters.getConnectorRef(), false));

    ScmGitRefTaskParams scmGitRefTaskParams = obtainTaskParameters(manualExecutionSource, connectorDetails,
        RunTimeInputHandler.resolveStringParameterV2(
            "repoName", STEP_TYPE.getType(), ambiance.getStageExecutionId(), stepParameters.getRepoName(), false));

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(Duration.ofSeconds(30).toMillis())
                                  .taskType(SCM_GIT_REF_TASK.name())
                                  .parameters(new Object[] {scmGitRefTaskParams})
                                  .build();

    log.info("Created delegate task to fetch codebase info");
    return StepUtils.prepareTaskRequest(
        ambiance, taskData, kryoSerializer, false, Collections.emptyList(), false, null);
  }

  @Override
  public StepResponse handleTaskResult(Ambiance ambiance, CodeBaseTaskStepParameters stepParameters,
      ThrowingSupplier<ScmGitRefTaskResponseData> responseDataSupplier) throws Exception {
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = null;
    try {
      log.info("Retrieving codebase info from returned delegate response");
      scmGitRefTaskResponseData = responseDataSupplier.get();
      log.info("Successfully retrieved codebase info from returned delegate response");
    } catch (Exception ex) {
      ManualExecutionSource manualExecutionSource =
          (ManualExecutionSource) getExecutionSource(ambiance, stepParameters.getExecutionSource());
      String prNumber = manualExecutionSource.getPrNumber();
      if (scmGitRefTaskResponseData == null && isNotEmpty(prNumber)) {
        throw new CIStageExecutionException("Failed to retrieve PrNumber: " + prNumber + " details");
      }
      log.error("Failed to retrieve codebase info from returned delegate response");
    }

    saveScmResponseToSweepingOutput(ambiance, stepParameters, scmGitRefTaskResponseData);
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, CodeBaseTaskStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ExecutionSource executionSource = getExecutionSource(ambiance, stepParameters.getExecutionSource());
    CodebaseSweepingOutput codebaseSweepingOutput = null;
    String connectorRef = RunTimeInputHandler.resolveStringParameterV2(
        "connectorRef", STEP_TYPE.getType(), ambiance.getStageExecutionId(), stepParameters.getConnectorRef(), false);
    String repoName = RunTimeInputHandler.resolveStringParameterV2(
        "repoName", STEP_TYPE.getType(), ambiance.getStageExecutionId(), stepParameters.getRepoName(), false);
    if (executionSource.getType() == MANUAL) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
      ManualExecutionSource manualExecutionSource = (ManualExecutionSource) executionSource;
      // fetch scm details via manager
      if (connectorUtils.hasApiAccess(connectorDetails)) {
        String branch = manualExecutionSource.getBranch();
        String prNumber = manualExecutionSource.getPrNumber();
        String tag = manualExecutionSource.getTag();
        try {
          ScmConnector scmConnector =
              scmGitRefManager.getScmConnector(connectorDetails, ngAccess.getAccountIdentifier(), repoName);
          ScmGitRefTaskResponseData response = scmGitRefManager.fetchCodebaseMetadata(
              scmConnector, connectorDetails.getIdentifier(), branch, prNumber, tag);
          saveScmResponseToSweepingOutput(ambiance, stepParameters, response);
          return StepResponse.builder().status(Status.SUCCEEDED).build();
        } catch (Exception ex) {
          log.error("Failed to fetch codebase metadata", ex);
          return StepResponse.builder()
              .status(Status.FAILED)
              .failureInfo(FailureInfo.newBuilder().setErrorMessage(emptyIfNull(ex.getMessage())).build())
              .build();
        }
      } else {
        if (isNotEmpty(manualExecutionSource.getPrNumber())) {
          throw new CIStageExecutionException(
              "PR build type is not supported when api access is disabled in git connector or clone codebase is false");
        }
        String repoUrl = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, repoName);
        codebaseSweepingOutput = buildManualCodebaseSweepingOutput(manualExecutionSource, repoUrl);
      }
    } else if (executionSource.getType() == WEBHOOK) {
      codebaseSweepingOutput = buildWebhookCodebaseSweepingOutput((WebhookExecutionSource) executionSource);
    }
    saveCodebaseSweepingOutput(ambiance, codebaseSweepingOutput);

    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private void saveScmResponseToSweepingOutput(Ambiance ambiance, CodeBaseTaskStepParameters stepParameters,
      ScmGitRefTaskResponseData scmGitRefTaskResponseData) throws InvalidProtocolBufferException {
    CodebaseSweepingOutput codebaseSweepingOutput = null;
    if (scmGitRefTaskResponseData != null
        && scmGitRefTaskResponseData.getGitRefType() == GitRefType.PULL_REQUEST_WITH_COMMITS) {
      codebaseSweepingOutput = buildPRCodebaseSweepingOutput(scmGitRefTaskResponseData);
    } else if (scmGitRefTaskResponseData != null
        && scmGitRefTaskResponseData.getGitRefType() == GitRefType.LATEST_COMMIT_ID) {
      ManualExecutionSource manualExecutionSource =
          (ManualExecutionSource) getExecutionSource(ambiance, stepParameters.getExecutionSource());
      codebaseSweepingOutput =
          buildCommitShaCodebaseSweepingOutput(scmGitRefTaskResponseData, manualExecutionSource.getTag());
    }
    if (codebaseSweepingOutput != null) {
      saveCodebaseSweepingOutput(ambiance, codebaseSweepingOutput);
    }
  }

  @VisibleForTesting
  ScmGitRefTaskParams obtainTaskParameters(
      ManualExecutionSource manualExecutionSource, ConnectorDetails connectorDetails, String repoName) {
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
    String completeUrl = CodebaseUtils.getCompleteURLFromConnector(connectorDetails, repoName);
    scmConnector.setUrl(completeUrl);

    String branch = manualExecutionSource.getBranch();
    String prNumber = manualExecutionSource.getPrNumber();
    String tag = manualExecutionSource.getTag();
    if (isNotEmpty(branch)) {
      return ScmGitRefTaskParams.builder()
          .branch(branch)
          .gitRefType(GitRefType.LATEST_COMMIT_ID)
          .encryptedDataDetails(connectorDetails.getEncryptedDataDetails())
          .scmConnector(scmConnector)
          .build();
    } else if (isNotEmpty(prNumber)) {
      return ScmGitRefTaskParams.builder()
          .prNumber(Long.parseLong(prNumber))
          .gitRefType(GitRefType.PULL_REQUEST_WITH_COMMITS)
          .encryptedDataDetails(connectorDetails.getEncryptedDataDetails())
          .scmConnector((ScmConnector) connectorDetails.getConnectorConfig())
          .build();
    } else if (isNotEmpty(tag)) {
      return ScmGitRefTaskParams.builder()
          .ref(tag)
          .gitRefType(GitRefType.LATEST_COMMIT_ID)
          .encryptedDataDetails(connectorDetails.getEncryptedDataDetails())
          .scmConnector(scmConnector)
          .build();
    } else {
      throw new CIStageExecutionException("Manual codebase git task needs one of PR number, branch or tag");
    }
  }

  @VisibleForTesting
  CodebaseSweepingOutput buildCommitShaCodebaseSweepingOutput(
      ScmGitRefTaskResponseData scmGitRefTaskResponseData, String tag) throws InvalidProtocolBufferException {
    final byte[] getLatestCommitResponseByteArray = scmGitRefTaskResponseData.getGetLatestCommitResponse();
    if (isEmpty(getLatestCommitResponseByteArray)) {
      throw new CIStageExecutionException("Codebase git commit information can't be obtained");
    }
    GetLatestCommitResponse listCommitsResponse = GetLatestCommitResponse.parseFrom(getLatestCommitResponseByteArray);

    if (listCommitsResponse.getCommit() == null || isEmpty(listCommitsResponse.getCommit().getSha())) {
      return null;
    }

    Build build = new Build("branch");
    if (isNotEmpty(tag)) {
      build = new Build("tag");
    }

    String commitSha = listCommitsResponse.getCommit().getSha();
    String shortCommitSha = WebhookTriggerProcessorUtils.getShortCommitSha(commitSha);

    return CodebaseSweepingOutput.builder()
        .branch(scmGitRefTaskResponseData.getBranch())
        .tag(tag)
        .build(build)
        .commits(asList(CodeBaseCommit.builder()
                            .id(listCommitsResponse.getCommit().getSha())
                            .link(listCommitsResponse.getCommit().getLink())
                            .message(listCommitsResponse.getCommit().getMessage())
                            .ownerEmail(listCommitsResponse.getCommit().getAuthor().getEmail())
                            .ownerName(listCommitsResponse.getCommit().getAuthor().getName())
                            .ownerId(listCommitsResponse.getCommit().getAuthor().getLogin())
                            .timeStamp(listCommitsResponse.getCommit().getAuthor().getDate().getSeconds())
                            .build()))
        .commitSha(commitSha)
        .shortCommitSha(shortCommitSha)
        .repoUrl(scmGitRefTaskResponseData.getRepoUrl())
        .build();
  }

  @VisibleForTesting
  CodebaseSweepingOutput buildWebhookCodebaseSweepingOutput(WebhookExecutionSource webhookExecutionSource) {
    List<CodebaseSweepingOutput.CodeBaseCommit> codeBaseCommits = new ArrayList<>();
    if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.PR) {
      PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (isNotEmpty(prWebhookEvent.getCommitDetailsList())) {
        for (CommitDetails commit : prWebhookEvent.getCommitDetailsList()) {
          codeBaseCommits.add(CodebaseSweepingOutput.CodeBaseCommit.builder()
                                  .id(commit.getCommitId())
                                  .message(commit.getMessage())
                                  .link(commit.getLink())
                                  .timeStamp(commit.getTimeStamp())
                                  .ownerEmail(commit.getOwnerEmail())
                                  .ownerId(commit.getOwnerId())
                                  .ownerName(commit.getOwnerName())
                                  .build());
        }
      }

      String commitSha = prWebhookEvent.getBaseAttributes().getAfter();
      String shortCommitSha = WebhookTriggerProcessorUtils.getShortCommitSha(commitSha);
      String mergeCommitSha = prWebhookEvent.getBaseAttributes().getMergeSha();

      return CodebaseSweepingOutput.builder()
          .commits(codeBaseCommits)
          .state(getState(prWebhookEvent))
          .branch(prWebhookEvent.getTargetBranch())
          .targetBranch(prWebhookEvent.getTargetBranch())
          .sourceBranch(prWebhookEvent.getSourceBranch())
          .prNumber(String.valueOf(prWebhookEvent.getPullRequestId()))
          .prTitle(prWebhookEvent.getTitle())
          .build(new Build("PR"))
          .commitSha(commitSha)
          .mergeSha(mergeCommitSha)
          .shortCommitSha(shortCommitSha)
          .baseCommitSha(prWebhookEvent.getBaseAttributes().getBefore())
          .repoUrl(prWebhookEvent.getRepository().getLink())
          .pullRequestLink(prWebhookEvent.getPullRequestLink())
          .gitUser(prWebhookEvent.getBaseAttributes().getAuthorName())
          .gitUserEmail(prWebhookEvent.getBaseAttributes().getAuthorEmail())
          .gitUserAvatar(prWebhookEvent.getBaseAttributes().getAuthorAvatar())
          .gitUserId(prWebhookEvent.getBaseAttributes().getAuthorLogin())
          .build();
    } else if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.BRANCH) {
      BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (isNotEmpty(branchWebhookEvent.getCommitDetailsList())) {
        for (CommitDetails commit : branchWebhookEvent.getCommitDetailsList()) {
          codeBaseCommits.add(CodebaseSweepingOutput.CodeBaseCommit.builder()
                                  .id(commit.getCommitId())
                                  .message(commit.getMessage())
                                  .link(commit.getLink())
                                  .timeStamp(commit.getTimeStamp())
                                  .ownerEmail(commit.getOwnerEmail())
                                  .ownerId(commit.getOwnerId())
                                  .ownerName(commit.getOwnerName())
                                  .build());
        }
      }

      String commitSha = branchWebhookEvent.getBaseAttributes().getAfter();
      String shortCommitSha = WebhookTriggerProcessorUtils.getShortCommitSha(commitSha);

      return CodebaseSweepingOutput.builder()
          .branch(branchWebhookEvent.getBranchName())
          .commits(codeBaseCommits)
          .build(new Build("branch"))
          .targetBranch(branchWebhookEvent.getBranchName())
          .commitSha(commitSha)
          .shortCommitSha(shortCommitSha)
          .repoUrl(branchWebhookEvent.getRepository().getLink())
          .gitUser(branchWebhookEvent.getBaseAttributes().getAuthorName())
          .gitUserEmail(branchWebhookEvent.getBaseAttributes().getAuthorEmail())
          .gitUserAvatar(branchWebhookEvent.getBaseAttributes().getAuthorAvatar())
          .gitUserId(branchWebhookEvent.getBaseAttributes().getAuthorLogin())
          .build();
    }
    return CodebaseSweepingOutput.builder().build();
  }

  @VisibleForTesting
  CodebaseSweepingOutput buildManualCodebaseSweepingOutput(
      ManualExecutionSource manualExecutionSource, String repoUrl) {
    Build build = new Build("branch");
    if (isNotEmpty(manualExecutionSource.getTag())) {
      build = new Build("tag");
    }

    String commitSha = manualExecutionSource.getCommitSha();
    String shortCommitSha = WebhookTriggerProcessorUtils.getShortCommitSha(commitSha);

    return CodebaseSweepingOutput.builder()
        .build(build)
        .branch(manualExecutionSource.getBranch())
        .tag(manualExecutionSource.getTag())
        .commitSha(commitSha)
        .shortCommitSha(shortCommitSha)
        .repoUrl(repoUrl)
        .build();
  }

  @VisibleForTesting
  public CodebaseSweepingOutput buildPRCodebaseSweepingOutput(ScmGitRefTaskResponseData scmGitRefTaskResponseData)
      throws InvalidProtocolBufferException {
    CodebaseSweepingOutput codebaseSweepingOutput;
    final byte[] findPRResponseByteArray = scmGitRefTaskResponseData.getFindPRResponse();
    final byte[] listCommitsInPRResponseByteArray = scmGitRefTaskResponseData.getListCommitsInPRResponse();
    final String repoUrl = scmGitRefTaskResponseData.getRepoUrl();

    if (findPRResponseByteArray == null || listCommitsInPRResponseByteArray == null) {
      throw new CIStageExecutionException("Codebase git information can't be obtained");
    }

    FindPRResponse findPRResponse = FindPRResponse.parseFrom(findPRResponseByteArray);
    ListCommitsInPRResponse listCommitsInPRResponse =
        ListCommitsInPRResponse.parseFrom(listCommitsInPRResponseByteArray);
    PullRequest pr = findPRResponse.getPr();
    List<Commit> commits = listCommitsInPRResponse.getCommitsList();
    List<CodebaseSweepingOutput.CodeBaseCommit> codeBaseCommits = new ArrayList<>();
    for (Commit commit : commits) {
      codeBaseCommits.add(CodebaseSweepingOutput.CodeBaseCommit.builder()
                              .id(commit.getSha())
                              .message(commit.getMessage())
                              .link(commit.getLink())
                              .timeStamp(commit.getCommitter().getDate().getSeconds())
                              .ownerEmail(commit.getAuthor().getEmail())
                              .ownerId(commit.getAuthor().getLogin())
                              .ownerName(commit.getAuthor().getName())
                              .build());
    }

    String commitSha = pr.getSha();
    String shortCommitSha = WebhookTriggerProcessorUtils.getShortCommitSha(commitSha);

    codebaseSweepingOutput = CodebaseSweepingOutput.builder()
                                 .branch(pr.getTarget())
                                 .sourceBranch(pr.getSource())
                                 .targetBranch(pr.getTarget())
                                 .prNumber(String.valueOf(pr.getNumber()))
                                 .prTitle(pr.getTitle())
                                 .commitSha(commitSha)
                                 .mergeSha(pr.getMergeSha())
                                 .shortCommitSha(shortCommitSha)
                                 .build(new Build("PR"))
                                 .baseCommitSha(pr.getBase().getSha())
                                 .commitRef(pr.getRef())
                                 .repoUrl(repoUrl) // Add repo url to scm.PullRequest and get it from there
                                 .gitUser(pr.getAuthor().getName())
                                 .gitUserAvatar(pr.getAuthor().getAvatar())
                                 .gitUserEmail(pr.getAuthor().getEmail())
                                 .gitUserId(pr.getAuthor().getLogin())
                                 .pullRequestLink(pr.getLink())
                                 .commits(codeBaseCommits)
                                 .state(getState(pr))
                                 .build();
    return codebaseSweepingOutput;
  }

  private void saveCodebaseSweepingOutput(Ambiance ambiance, CodebaseSweepingOutput codebaseSweepingOutput) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
    if (!optionalSweepingOutput.isFound()) {
      try {
        executionSweepingOutputResolver.consume(
            ambiance, CODEBASE, codebaseSweepingOutput, StepOutcomeGroup.PIPELINE.name());
      } catch (Exception e) {
        log.error("Error while consuming codebase sweeping output", e);
      }
    }
  }

  @NotNull
  private String getState(PullRequest pr) {
    String state = "open";
    if (pr.getMerged()) {
      state = "merged";
    } else if (pr.getClosed()) {
      state = "closed";
    }
    return state;
  }

  @NotNull
  private String getState(PRWebhookEvent pr) {
    String state = "open";
    if (pr.isMerged()) {
      state = "merged";
    } else if (pr.isClosed()) {
      state = "closed";
    }
    return state;
  }

  private ExecutionSource getExecutionSource(Ambiance ambiance, ExecutionSource executionSource) {
    if (executionSource != null) {
      return executionSource;
    }
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage details sweeping output cannot be empty");
    }
    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();
    return stageDetails.getExecutionSource();
  }
}
