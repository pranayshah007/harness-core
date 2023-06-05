/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator;

import static io.harness.beans.execution.WebhookEvent.Type.BRANCH;
import static io.harness.beans.execution.WebhookEvent.Type.PR;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.INITIALIZE_EXECUTION;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_EXECUTION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.git.GitClientHelper.getGitRepo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.sweepingoutputs.InitializeExecutionSweepingOutput;
import io.harness.beans.sweepingoutputs.StageExecutionSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.commonconstants.CIExecutionConstants;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.pipeline.executions.beans.CIBuildAuthor;
import io.harness.ci.pipeline.executions.beans.CIBuildBranchHook;
import io.harness.ci.pipeline.executions.beans.CIBuildCommit;
import io.harness.ci.pipeline.executions.beans.CIBuildPRHook;
import io.harness.ci.pipeline.executions.beans.CIImageDetails;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.ci.pipeline.executions.beans.CIScmDetails;
import io.harness.ci.pipeline.executions.beans.CIWebhookInfoDTO;
import io.harness.ci.pipeline.executions.beans.TIBuildDetails;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo;
import io.harness.ci.plan.creator.execution.CIPipelineStageModuleInfo;
import io.harness.ci.plan.creator.execution.CIStageModuleInfo;
import io.harness.ci.states.InitializeTaskStep;
import io.harness.ci.states.IntegrationStageStepPMS;
import io.harness.ci.utils.WebhookTriggerProcessorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.execution.ExecutionSummaryModuleInfoProvider;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.pms.sdk.execution.beans.StageModuleInfo;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIModuleInfoProvider implements ExecutionSummaryModuleInfoProvider {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private CILicenseService ciLicenseService;

  String NULL_STR = "null";
  @Override
  public boolean shouldRun(OrchestrationEvent event) {
    StepType currentStepType = AmbianceUtils.getCurrentStepType(event.getAmbiance());
    return currentStepType != null && isWhitelistedNode(currentStepType);
  }

  @Override
  public PipelineModuleInfo getPipelineLevelModuleInfo(OrchestrationEvent event) {
    StepType currentStepType = AmbianceUtils.getCurrentStepType(event.getAmbiance());
    if (currentStepType != null && Objects.equals(currentStepType.getType(), InitializeTaskStep.STEP_TYPE.getType())) {
      String branch = null;
      String tag = null;
      String prNumber = null;
      String repoName = null;
      String buildType = null;
      String triggerRepoName = null;
      String url = null;
      String licenseType = null;
      String editionType = null;

      List<CIScmDetails> scmDetailsList = new ArrayList<>();
      List<CIInfraDetails> infraDetailsList = new ArrayList<>();
      List<CIImageDetails> imageDetailsList = new ArrayList<>();
      List<TIBuildDetails> tiBuildDetailsList = new ArrayList<>();

      CIBuildAuthor author = null;
      Boolean isPrivateRepo = false;
      List<CIBuildCommit> triggerCommits = null;
      ExecutionTriggerInfo executionTriggerInfo = event.getAmbiance().getMetadata().getTriggerInfo();
      Ambiance ambiance = event.getAmbiance();
      BaseNGAccess baseNGAccess = retrieveBaseNGAccess(ambiance);
      try {
        StepElementParameters stepElementParameters = (StepElementParameters) event.getResolvedStepParameters();
        if (stepElementParameters != null) {
          InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepElementParameters.getSpec();

          if (initializeStepInfo == null) {
            return null;
          }

          ParameterField<Build> buildParameterField = null;
          if (initializeStepInfo.getCiCodebase() != null) {
            buildParameterField = initializeStepInfo.getCiCodebase().getBuild();

            if (isNotEmpty(initializeStepInfo.getCiCodebase().getRepoName().getValue())) {
              repoName = initializeStepInfo.getCiCodebase().getRepoName().getValue();
            }
            if (StringUtils.isNotBlank(repoName)
                || initializeStepInfo.getCiCodebase().getConnectorRef().getValue() != null) {
              try {
                ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(
                    baseNGAccess, initializeStepInfo.getCiCodebase().getConnectorRef().getValue(), true);
                if (executionTriggerInfo.getTriggerType() == TriggerType.WEBHOOK) {
                  url = IntegrationStageUtils.getGitURLFromConnector(
                      connectorDetails, initializeStepInfo.getCiCodebase());
                }
                if (url == null) {
                  url = connectorUtils.retrieveURL(connectorDetails);
                }
                if (isEmpty(repoName) || repoName.equals(NULL_STR)) {
                  repoName = getGitRepo(url);
                }
                CIScmDetails scmDetails = IntegrationStageUtils.getCiScmDetails(connectorUtils, connectorDetails);
                scmDetails.setScmUrl(url);
                scmDetailsList.add(scmDetails);
              } catch (Exception exception) {
                log.warn("Failed to retrieve repo");
              }
            }
          }
          infraDetailsList.add(IntegrationStageUtils.getCiInfraDetails(initializeStepInfo.getInfrastructure()));
          imageDetailsList = IntegrationStageUtils.getCiImageDetails(initializeStepInfo);
          tiBuildDetailsList = IntegrationStageUtils.getTiBuildDetails(initializeStepInfo);

          isPrivateRepo = isPrivateRepo(url);
          Build build = RunTimeInputHandler.resolveBuild(buildParameterField);
          if (build != null) {
            buildType = build.getType().toString();
          }
          if (build != null && build.getType().equals(BuildType.BRANCH)) {
            branch = (String) ((BranchBuildSpec) build.getSpec()).getBranch().fetchFinalValue();
          }

          if (build != null && build.getType().equals(BuildType.PR)) {
            if (((PRBuildSpec) build.getSpec()).getNumber().isExpression() == false) {
              prNumber = (String) ((PRBuildSpec) build.getSpec()).getNumber().fetchFinalValue();
            }
          }

          if (build != null && build.getType().equals(BuildType.TAG)) {
            tag = (String) ((TagBuildSpec) build.getSpec()).getTag().fetchFinalValue();
          }
        }
      } catch (Exception ex) {
        log.error("Failed to retrieve branch and tag for filtering", ex);
      }
      ExecutionSource executionSource = null;
      try {
        executionSource = getWebhookExecutionSource(event.getAmbiance().getMetadata(), event.getTriggerPayload());
      } catch (Exception ex) {
        log.error("Failed to retrieve branch and tag for filtering", ex);
      }

      try {
        LicensesWithSummaryDTO licensesWithSummaryDTO =
            ciLicenseService.getLicenseSummary(baseNGAccess.getAccountIdentifier());

        if (licensesWithSummaryDTO == null) {
          throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
        }

        if (licensesWithSummaryDTO != null && licensesWithSummaryDTO.getLicenseType() != null) {
          licenseType = licensesWithSummaryDTO.getLicenseType() != null
              ? licensesWithSummaryDTO.getLicenseType().toString()
              : null;
          editionType =
              licensesWithSummaryDTO.getEdition() != null ? licensesWithSummaryDTO.getEdition().toString() : null;
        }
      } catch (Exception e) {
        log.error("Failed to retrieve licensing information", e);
      }

      if (executionSource != null && executionTriggerInfo.getTriggerType() == TriggerType.WEBHOOK) {
        WebhookExecutionSource webhookExecutionSource = (WebhookExecutionSource) executionSource;
        CIWebhookInfoDTO ciWebhookInfoDTO = CIModuleInfoMapper.getCIBuildResponseDTO(executionSource);
        OptionalSweepingOutput optionalSweepingOutput =
            executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
        CodebaseSweepingOutput codebaseSweepingOutput = null;
        triggerRepoName = fetchTriggerRepo(webhookExecutionSource);
        if (ciWebhookInfoDTO.getEvent().equals("branch")) {
          triggerCommits = ciWebhookInfoDTO.getBranch().getCommits();
        } else {
          triggerCommits = ciWebhookInfoDTO.getPullRequest().getCommits();
        }
        if (optionalSweepingOutput.isFound()) {
          codebaseSweepingOutput = (CodebaseSweepingOutput) optionalSweepingOutput.getOutput();
          ciWebhookInfoDTO =
              getCiExecutionInfoDTO(codebaseSweepingOutput, ciWebhookInfoDTO.getAuthor(), prNumber, triggerCommits);
        }

        author = ciWebhookInfoDTO.getAuthor();

        if (IntegrationStageUtils.isURLSame(webhookExecutionSource, url) && isNotEmpty(prNumber)) {
          return CIPipelineModuleInfo.builder()
              .triggerRepoName(triggerRepoName)
              .branch(branch)
              .tag(tag)
              .buildType(buildType)
              .prNumber(prNumber)
              .repoName(repoName)
              .ciExecutionInfoDTO(ciWebhookInfoDTO)
              .isPrivateRepo(isPrivateRepo)
              .scmDetailsList(scmDetailsList)
              .infraDetailsList(infraDetailsList)
              .imageDetailsList(imageDetailsList)
              .tiBuildDetailsList(tiBuildDetailsList)
              .ciLicenseType(licenseType)
              .ciEditionType(editionType)
              .build();
        }
      }

      // get codebase sweeping output
      OptionalSweepingOutput optionalSweepingOutput =
          executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
      CodebaseSweepingOutput codebaseSweepingOutput = null;
      if (optionalSweepingOutput.isFound()) {
        codebaseSweepingOutput = (CodebaseSweepingOutput) optionalSweepingOutput.getOutput();
      }
      if (codebaseSweepingOutput != null) {
        log.info("Codebase sweeping output {}", codebaseSweepingOutput);

        if (isEmpty(branch)) {
          branch = codebaseSweepingOutput.getBranch();
        }

        if (isEmpty(prNumber)) {
          prNumber = codebaseSweepingOutput.getPrNumber();
        }

        if (isEmpty(tag)) {
          tag = codebaseSweepingOutput.getTag();
        }

        if (isEmpty(repoName) && isNotEmpty(codebaseSweepingOutput.getRepoUrl())) {
          repoName = getGitRepo(codebaseSweepingOutput.getRepoUrl());
        }

        if (author == null) {
          author = CIBuildAuthor.builder()
                       .id(codebaseSweepingOutput.getGitUserId())
                       .name(codebaseSweepingOutput.getGitUser())
                       .avatar(codebaseSweepingOutput.getGitUserAvatar())
                       .email(codebaseSweepingOutput.getGitUserEmail())
                       .build();
        }
      }

      return CIPipelineModuleInfo.builder()
          .branch(branch)
          .triggerRepoName(triggerRepoName)
          .prNumber(prNumber)
          .buildType(buildType)
          .tag(tag)
          .repoName(repoName)
          .ciExecutionInfoDTO(getCiExecutionInfoDTO(codebaseSweepingOutput, author, prNumber, triggerCommits))
          .isPrivateRepo(isPrivateRepo)
          .scmDetailsList(scmDetailsList)
          .infraDetailsList(infraDetailsList)
          .imageDetailsList(imageDetailsList)
          .tiBuildDetailsList(tiBuildDetailsList)
          .ciLicenseType(licenseType)
          .ciEditionType(editionType)
          .build();
    } else if (currentStepType != null
        && Objects.equals(currentStepType.getType(), IntegrationStageStepPMS.STEP_TYPE.getType())) {
      return CIPipelineModuleInfo.builder().ciPipelineStageModuleInfo(getCIPipelineStageLevelInfo(event)).build();
    }
    return null;
  }

  private CIWebhookInfoDTO getCiExecutionInfoDTO(CodebaseSweepingOutput codebaseSweepingOutput,
      CIBuildAuthor ciBuildAuthor, String prNumber, List<CIBuildCommit> triggerCommits) {
    if (codebaseSweepingOutput == null) {
      return null;
    }

    List<CIBuildCommit> ciBuildCommits = new ArrayList<>();
    if (isNotEmpty(codebaseSweepingOutput.getCommits())) {
      for (CodebaseSweepingOutput.CodeBaseCommit commit : codebaseSweepingOutput.getCommits()) {
        ciBuildCommits.add(CIBuildCommit.builder()
                               .id(commit.getId())
                               .link(commit.getLink())
                               .message(commit.getMessage())
                               .ownerEmail(commit.getOwnerEmail())
                               .ownerId(commit.getOwnerId())
                               .ownerName(commit.getOwnerName())
                               .timeStamp(commit.getTimeStamp() * 1000)
                               .build());
      }
    }

    if (!displayTriggerCommits(ciBuildCommits, triggerCommits)) {
      triggerCommits = null;
    }

    String userSource = ciBuildAuthor != null && isNotEmpty(ciBuildAuthor.getId()) ? CIExecutionConstants.SOURCE_GIT
                                                                                   : CIExecutionConstants.SOURCE_MANUAL;

    if (isNotEmpty(prNumber)) {
      return CIWebhookInfoDTO.builder()
          .event("pullRequest")
          .author(ciBuildAuthor)
          .userSource(userSource)
          .pullRequest(CIBuildPRHook.builder()
                           .id(Long.valueOf(codebaseSweepingOutput.getPrNumber()))
                           .link(codebaseSweepingOutput.getPullRequestLink())
                           .title(codebaseSweepingOutput.getPrTitle())
                           .body(codebaseSweepingOutput.getPullRequestBody())
                           .sourceBranch(codebaseSweepingOutput.getSourceBranch())
                           .targetBranch(codebaseSweepingOutput.getTargetBranch())
                           .state(codebaseSweepingOutput.getState())
                           .commits(ciBuildCommits)
                           .triggerCommits(triggerCommits)
                           .build())
          .build();
    } else {
      return CIWebhookInfoDTO.builder()
          .event("branch")
          .userSource(userSource)
          .author(ciBuildAuthor)
          .branch(CIBuildBranchHook.builder().commits(ciBuildCommits).triggerCommits(triggerCommits).build())
          .build();
    }
  }

  public String fetchTriggerRepo(WebhookExecutionSource webhookExecutionSource) {
    if (webhookExecutionSource.getWebhookEvent().getType() == PR) {
      PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (prWebhookEvent == null || prWebhookEvent.getRepository() == null
          || prWebhookEvent.getRepository().getHttpURL() == null) {
        return null;
      }

      return getGitRepo(prWebhookEvent.getRepository().getHttpURL());

    } else if (webhookExecutionSource.getWebhookEvent().getType() == BRANCH) {
      BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();

      if (branchWebhookEvent == null || branchWebhookEvent.getRepository() == null
          || branchWebhookEvent.getRepository().getHttpURL() == null) {
        return null;
      }

      return getGitRepo(branchWebhookEvent.getRepository().getHttpURL());
    }

    return null;
  }

  public boolean displayTriggerCommits(List<CIBuildCommit> buildCommits, List<CIBuildCommit> triggerCommits) {
    if (isNotEmpty(triggerCommits) && isNotEmpty(buildCommits)) {
      return !buildCommits.stream()
                  .map(CIBuildCommit::getId)
                  .collect(Collectors.toSet())
                  .containsAll(triggerCommits.stream().map(CIBuildCommit::getId).collect(Collectors.toSet()));
    }

    return true;
  }

  private CIPipelineStageModuleInfo getCIPipelineStageLevelInfo(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    long startTime = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();
    String stageId;
    String stageName;
    String osType;
    String osArch;
    long initialiseBuildTime = 0;
    long totalStageBuildTime = 0;
    double buildMultiplier = 1;

    if (event.getResolvedStepParameters() != null) {
      StageElementParameters stageElementParameters = (StageElementParameters) event.getResolvedStepParameters();
      if (stageElementParameters != null) {
        stageId = stageElementParameters.getIdentifier();
        stageName = stageElementParameters.getName();
        IntegrationStageStepParametersPMS integrationStageStepParametersPMS =
            (IntegrationStageStepParametersPMS) stageElementParameters.getSpecConfig();
        if (integrationStageStepParametersPMS != null) {
          Infrastructure infrastructure = integrationStageStepParametersPMS.getInfrastructure();
          CIInfraDetails ciInfraDetails = IntegrationStageUtils.getCiInfraDetails(infrastructure);
          osType = ciInfraDetails.getInfraOSType();
          osArch = ciInfraDetails.getInfraArchType();
          buildMultiplier = IntegrationStageUtils.getBuildTimeMultiplierForHostedInfra(infrastructure);

          if (infrastructure.getType() == Infrastructure.Type.HOSTED_VM
              || infrastructure.getType() == Infrastructure.Type.KUBERNETES_HOSTED) {
            OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
                ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION));
            if (optionalSweepingOutput.isFound()) {
              initialiseBuildTime =
                  ((InitializeExecutionSweepingOutput) optionalSweepingOutput.getOutput()).getInitialiseExecutionTime();
            }

            optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
                ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION));
            if (optionalSweepingOutput.isFound()) {
              totalStageBuildTime =
                  ((StageExecutionSweepingOutput) optionalSweepingOutput.getOutput()).getStageExecutionTime();
            }

            return CIPipelineStageModuleInfo.builder()
                .stageExecutionId(stageExecutionId)
                .stageId(stageId)
                .stageName(stageName)
                .infraType(infrastructure.getType().getYamlName())
                .osType(osType)
                .osArch(osArch)
                .cpuTime((totalStageBuildTime > 0 && initialiseBuildTime > 0)
                        ? totalStageBuildTime - initialiseBuildTime
                        : 0)
                .stageBuildTime(totalStageBuildTime)
                .startTs(startTime)
                .buildMultiplier(buildMultiplier)
                .build();
          }
        }
      }
    }
    return null;
  }

  @Override
  public StageModuleInfo getStageLevelModuleInfo(OrchestrationEvent event) {
    return CIStageModuleInfo.builder().build();
  }

  private ExecutionSource getWebhookExecutionSource(
      ExecutionMetadata executionMetadata, TriggerPayload triggerPayload) {
    ExecutionTriggerInfo executionTriggerInfo = executionMetadata.getTriggerInfo();
    if (executionTriggerInfo.getTriggerType() == TriggerType.WEBHOOK) {
      if (triggerPayload != null) {
        ParsedPayload parsedPayload = triggerPayload.getParsedPayload();
        return WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);
      } else {
        throw new CIStageExecutionException("Parsed payload is empty for webhook execution");
      }
    }
    return null;
  }

  private boolean isWhitelistedNode(StepType stepType) {
    return Objects.equals(stepType.getType(), InitializeTaskStep.STEP_TYPE.getType())
        || Objects.equals(stepType.getType(), IntegrationStageStepPMS.STEP_TYPE.getType());
  }

  private BaseNGAccess retrieveBaseNGAccess(Ambiance ambiance) {
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String accountId = AmbianceUtils.getAccountId(ambiance);

    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private boolean isPrivateRepo(String urlString) {
    try {
      URL url = new URL(urlString);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(5000);
      connection.connect();
      int code = connection.getResponseCode();
      return !Response.Status.Family.familyOf(code).equals(Response.Status.Family.SUCCESSFUL);
    } catch (Exception e) {
      log.warn("Failed to get repo info, assuming private. url", e);
      return true;
    }
  }
}
