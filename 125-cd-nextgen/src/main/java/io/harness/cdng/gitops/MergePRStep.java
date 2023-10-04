/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.executions.steps.ExecutionNodeType.GITOPS_MERGE_PR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static java.util.Collections.emptySet;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.gitops.githubrestraint.services.GithubRestraintInstanceService;
import io.harness.cdng.gitops.revertpr.RevertPROutcome;
import io.harness.cdng.gitops.steps.GitOpsStepHelper;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.gitapi.GitApiRequestType;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitOpsTaskType;
import io.harness.delegate.task.git.NGGitOpsResponse;
import io.harness.delegate.task.git.NGGitOpsTaskParams;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintUnit;
import io.harness.distribution.constraint.Consumer;
import io.harness.distribution.constraint.ConsumerId;
import io.harness.distribution.constraint.InvalidPermitsException;
import io.harness.distribution.constraint.PermanentlyBlockedConsumerException;
import io.harness.distribution.constraint.UnableToRegisterConsumerException;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitopsprovider.entity.GithubRestraintInstance.GithubRestraintInstanceKeys;
import io.harness.impl.scm.ScmGitProviderHelper;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.security.PmsSecurityContextEventGuard;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.executable.AsyncChainExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.ConnectorUtils;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@OwnedBy(HarnessTeam.GITOPS)
@Slf4j
public class MergePRStep implements AsyncChainExecutableWithRbac<StepElementParameters>,
                                    TaskExecutable<StepElementParameters, NGGitOpsResponse> {
  private static final String CONSTRAINT_OPERATION = "MERGE_PR";
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private GitOpsStepHelper gitOpsStepHelper;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ScmGitProviderHelper scmGitProviderHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private GithubRestraintInstanceService githubRestraintInstanceService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private StepHelper stepHelper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(GITOPS_MERGE_PR.getYamlType()).setStepCategory(StepCategory.STEP).build();

  public GitStoreDelegateConfig getGitStoreDelegateConfig(Ambiance ambiance, ManifestOutcome manifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) manifestOutcome.getStore();
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);

    return cdStepHelper.getGitStoreDelegateConfigWithApiAccess(
        gitStoreConfig, connectorDTO, new ArrayList<>(), ambiance, manifestOutcome);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private NGLogCallback getLogCallback(Ambiance ambiance, boolean shouldOpenStream) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, null, shouldOpenStream);
  }

  private GitApiTaskParams getTaskParamsForBitbucket(BitbucketConnectorDTO bitbucketConnectorDTO,
      ConnectorDetails connectorDetails, int prNumber, String sha, String ref,
      ParameterField<Boolean> deleteSourceBranch, StepBaseParameters stepParameters) {
    return GitApiTaskParams.builder()
        .gitRepoType(GitRepoType.BITBUCKET)
        .requestType(GitApiRequestType.MERGE_PR)
        .connectorDetails(connectorDetails)
        .prNumber(String.valueOf(prNumber))
        .sha(sha)
        .ref(ref)
        .owner(bitbucketConnectorDTO.getGitRepositoryDetails().getOrg())
        .repo(bitbucketConnectorDTO.getGitRepositoryDetails().getName())
        .deleteSourceBranch(CDStepHelper.getParameterFieldBooleanValue(
            deleteSourceBranch, MergePRStepInfo.MergePRBaseStepInfoKeys.deleteSourceBranch, stepParameters))
        .build();
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  @SneakyThrows
  public AsyncChainExecutableResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    NGLogCallback logCallback = getLogCallback(ambiance, true);
    MergePRStepParams gitOpsSpecParams = (MergePRStepParams) stepParameters.getSpec();

    ManifestOutcome releaseRepoOutcome = gitOpsStepHelper.getReleaseRepoOutcome(ambiance);
    ConnectorInfoDTO connectorInfoDTO =
        cdStepHelper.getConnector(releaseRepoOutcome.getStore().getConnectorReference().getValue(), ambiance);
    String tokenRefIdentifier = extractToken(connectorInfoDTO);
    String constraintUnitIdentifier =
        GITOPS_MERGE_PR.getName() + AmbianceUtils.getAccountId(ambiance) + tokenRefIdentifier;

    Constraint constraint = githubRestraintInstanceService.createAbstraction(constraintUnitIdentifier);
    String releaseEntityId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String consumerId = generateUuid();
    ConstraintUnit constraintUnit = new ConstraintUnit(constraintUnitIdentifier);

    Map<String, Object> constraintContext = populateConstraintContext(constraintUnit, releaseEntityId);
    logCallback.saveExecutionLog(
        String.format("Trying to acquire lock on token for %s operation", CONSTRAINT_OPERATION));

    if (!cdFeatureFlagHelper.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.GITOPS_GITHUB_RESTRAINT_FOR_STEPS)) {
      String taskId =
          queueDelegateTask(ambiance, gitOpsSpecParams, releaseRepoOutcome, connectorInfoDTO, stepParameters);
      return AsyncChainExecutableResponse.newBuilder()
          .addAllLogKeys(getLogKeys(ambiance))
          .setCallbackId(taskId)
          .addAllUnits(gitOpsSpecParams.getCommandUnits())
          .setChainEnd(true)
          .build();
    }

    try {
      Consumer.State state = constraint.registerConsumer(
          constraintUnit, new ConsumerId(consumerId), 1, constraintContext, githubRestraintInstanceService);
      switch (state) {
        case BLOCKED:
          logCallback.saveExecutionLog("Running instances were found, step queued.");
          return AsyncChainExecutableResponse.newBuilder()
              .addAllUnits(gitOpsSpecParams.getCommandUnits())
              .addAllLogKeys(getLogKeys(ambiance))
              .setCallbackId(consumerId)
              .build();
        case ACTIVE:
          try {
            logCallback.saveExecutionLog("Lock acquired, proceeding with delegate task.", INFO, SUCCESS);
            String taskId =
                queueDelegateTask(ambiance, gitOpsSpecParams, releaseRepoOutcome, connectorInfoDTO, stepParameters);
            return AsyncChainExecutableResponse.newBuilder()
                .addAllUnits(gitOpsSpecParams.getCommandUnits())
                .addAllLogKeys(getLogKeys(ambiance))
                .setCallbackId(taskId)
                .setChainEnd(true)
                .build();

          } catch (Exception e) {
            log.error("Failed to execute MergePR step", e);
            throw new InvalidRequestException(String.format("Failed to execute MergePR step. %s", e.getMessage()));
          }
        case REJECTED:
          logCallback.saveExecutionLog(
              "Constraint acquire rejected. Please try again after few minutes.", ERROR, FAILURE);
          throw new GeneralException("Found already running resourceConstrains, marking this execution as failed");
        default:
          throw new IllegalStateException("This should never happen");
      }
    } catch (InvalidPermitsException | UnableToRegisterConsumerException | PermanentlyBlockedConsumerException e) {
      log.error("Exception on MergePR for id [{}]", AmbianceUtils.obtainCurrentRuntimeId(ambiance), e);
      throw e;
    }
  }

  private List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  }

  @Override
  public AsyncChainExecutableResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepParameters, StepInputPackage inputPackage,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    try {
      NGLogCallback logCallback = getLogCallback(ambiance, false);
      logCallback.saveExecutionLog("Lock acquired, proceeding with delegate task.", INFO, SUCCESS);
      MergePRStepParams gitOpsSpecParams = (MergePRStepParams) stepParameters.getSpec();

      ManifestOutcome releaseRepoOutcome = gitOpsStepHelper.getReleaseRepoOutcome(ambiance);
      ConnectorInfoDTO connectorInfoDTO =
          cdStepHelper.getConnector(releaseRepoOutcome.getStore().getConnectorReference().getValue(), ambiance);

      String taskId =
          queueDelegateTask(ambiance, gitOpsSpecParams, releaseRepoOutcome, connectorInfoDTO, stepParameters);
      return AsyncChainExecutableResponse.newBuilder()
          .addAllUnits(gitOpsSpecParams.getCommandUnits())
          .addAllLogKeys(getLogKeys(ambiance))
          .setCallbackId(taskId)
          .setChainEnd(true)
          .build();
    } catch (Exception e) {
      log.error("Failed to execute MergePR step", e);
      throw new InvalidRequestException(String.format("Failed to execute MergePR step. %s", e.getMessage()));
    }
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    NGGitOpsResponse ngGitOpsResponse = (NGGitOpsResponse) responseDataSupplier.get();
    return calculateStepResponse(ambiance, ngGitOpsResponse);
  }

  private StepResponse calculateStepResponse(Ambiance ambiance, NGGitOpsResponse ngGitOpsResponse) {
    NGLogCallback logCallback = getLogCallback(ambiance, false);

    if (TaskStatus.SUCCESS.equals(ngGitOpsResponse.getTaskStatus())) {
      logCallback.saveExecutionLog("MergePR step finished.", INFO, SUCCESS);
      MergePROutcome mergePROutcome = MergePROutcome.builder().commitId(ngGitOpsResponse.getCommitId()).build();

      String outcomeName = ngGitOpsResponse.isRevertPR() ? OutcomeExpressionConstants.MERGE_REVERT_PR_OUTCOME
                                                         : OutcomeExpressionConstants.MERGE_PR_OUTCOME;
      executionSweepingOutputService.consume(ambiance, outcomeName, mergePROutcome, StepOutcomeGroup.STAGE.name());

      return StepResponse.builder()
          .unitProgressList(ngGitOpsResponse.getUnitProgressData().getUnitProgresses())
          .status(Status.SUCCEEDED)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.MERGE_PR_OUTCOME)
                           .outcome(mergePROutcome)
                           .build())
          .build();
    }

    return StepResponse.builder()
        .unitProgressList(ngGitOpsResponse.getUnitProgressData().getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(ngGitOpsResponse.getErrorMessage()).build())
        .build();
  }

  private String queueDelegateTask(Ambiance ambiance, MergePRStepParams gitOpsSpecParams,
      ManifestOutcome releaseRepoOutcome, ConnectorInfoDTO connectorInfoDTO, StepElementParameters stepParameters) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.UPDATE_RELEASE_REPO_OUTCOME));
    OptionalSweepingOutput optionalSweepingOutputRevertPR = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.REVERT_PR_OUTCOME));

    int prNumber;
    String prLink;
    String sha;
    String ref;
    boolean isRevertPR = false;
    if (optionalSweepingOutputRevertPR != null && optionalSweepingOutputRevertPR.isFound()) {
      RevertPROutcome revertPROutcome = (RevertPROutcome) optionalSweepingOutputRevertPR.getOutput();
      prNumber = revertPROutcome.getPrNumber();
      prLink = revertPROutcome.getPrlink();
      sha = revertPROutcome.getCommitId();
      ref = revertPROutcome.getRef();
      isRevertPR = true;
    } else if (optionalSweepingOutput != null && optionalSweepingOutput.isFound()) {
      UpdateReleaseRepoOutcome updateReleaseRepoOutcome = (UpdateReleaseRepoOutcome) optionalSweepingOutput.getOutput();
      prNumber = updateReleaseRepoOutcome.getPrNumber();
      prLink = updateReleaseRepoOutcome.getPrlink();
      sha = updateReleaseRepoOutcome.getCommitId();
      ref = updateReleaseRepoOutcome.getRef();
    } else {
      throw new InvalidRequestException("Pull Request Details are missing", USER);
    }

    Map<String, Object> apiParamOptions = gitOpsSpecParams.getVariables();

    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(connectorInfoDTO.getIdentifier(), accountId,
            connectorInfoDTO.getOrgIdentifier(), connectorInfoDTO.getProjectIdentifier());

    ConnectorDetails connectorDetails =
        connectorUtils.getConnectorDetails(identifierRef, identifierRef.buildScopedIdentifier());

    GitStoreDelegateConfig gitStoreDelegateConfig = getGitStoreDelegateConfig(ambiance, releaseRepoOutcome);
    GitApiTaskParams gitApiTaskParams;
    switch (gitStoreDelegateConfig.getGitConfigDTO().getConnectorType()) {
      case GITHUB:
        GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) gitStoreDelegateConfig.getGitConfigDTO();
        gitApiTaskParams =
            GitApiTaskParams.builder()
                .gitRepoType(GitRepoType.GITHUB)
                .requestType(GitApiRequestType.MERGE_PR)
                .connectorDetails(connectorDetails)
                .prNumber(String.valueOf(prNumber))
                .owner(githubConnectorDTO.getGitRepositoryDetails().getOrg())
                .repo(githubConnectorDTO.getGitRepositoryDetails().getName())
                .sha(sha)
                .deleteSourceBranch(CDStepHelper.getParameterFieldBooleanValue(gitOpsSpecParams.getDeleteSourceBranch(),
                    MergePRStepInfo.MergePRBaseStepInfoKeys.deleteSourceBranch, stepParameters))
                .ref(ref)
                .build();
        break;
      case AZURE_REPO:
        AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) gitStoreDelegateConfig.getGitConfigDTO();
        gitApiTaskParams =
            GitApiTaskParams.builder()
                .gitRepoType(GitRepoType.AZURE_REPO)
                .requestType(GitApiRequestType.MERGE_PR)
                .connectorDetails(connectorDetails)
                .prNumber(String.valueOf(prNumber))
                .owner(azureRepoConnectorDTO.getGitRepositoryDetails().getOrg())
                .repo(azureRepoConnectorDTO.getGitRepositoryDetails().getName())
                .sha(sha)
                .deleteSourceBranch(CDStepHelper.getParameterFieldBooleanValue(gitOpsSpecParams.getDeleteSourceBranch(),
                    MergePRStepInfo.MergePRBaseStepInfoKeys.deleteSourceBranch, stepParameters))
                .apiParamOptions(emptyIfNull(apiParamOptions))
                .build();
        break;
      case GITLAB:
        GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) gitStoreDelegateConfig.getGitConfigDTO();
        String slug = scmGitProviderHelper.getSlug(gitlabConnectorDTO);
        gitApiTaskParams =
            GitApiTaskParams.builder()
                .gitRepoType(GitRepoType.GITLAB)
                .requestType(GitApiRequestType.MERGE_PR)
                .connectorDetails(connectorDetails)
                .prNumber(String.valueOf(prNumber))
                .slug(slug)
                .sha(sha)
                .deleteSourceBranch(CDStepHelper.getParameterFieldBooleanValue(gitOpsSpecParams.getDeleteSourceBranch(),
                    MergePRStepInfo.MergePRBaseStepInfoKeys.deleteSourceBranch, stepParameters))
                .build();
        break;
      case BITBUCKET:
        gitApiTaskParams = getTaskParamsForBitbucket((BitbucketConnectorDTO) gitStoreDelegateConfig.getGitConfigDTO(),
            connectorDetails, prNumber, sha, ref, gitOpsSpecParams.getDeleteSourceBranch(), stepParameters);
        break;
      default:
        throw new InvalidRequestException("Failed to run MergePR Step. Connector not supported", USER);
    }

    NGGitOpsTaskParams ngGitOpsTaskParams = NGGitOpsTaskParams.builder()
                                                .gitOpsTaskType(GitOpsTaskType.MERGE_PR)
                                                .accountId(accountId)
                                                .connectorInfoDTO(connectorInfoDTO)
                                                .gitApiTaskParams(gitApiTaskParams)
                                                .prLink(prLink)
                                                .isRevertPR(isRevertPR)
                                                .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                  .taskType(TaskType.GITOPS_TASK_NG.name())
                                  .parameters(new Object[] {ngGitOpsTaskParams})
                                  .build();

    TaskRequest taskRequest = TaskRequestsUtils.prepareTaskRequestWithTaskSelector(ambiance, taskData,
        referenceFalseKryoSerializer, TaskCategory.DELEGATE_TASK_V2, gitOpsSpecParams.getCommandUnits(), true,
        taskData.getTaskType(),
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(gitOpsSpecParams.getDelegateSelectors()))));

    DelegateTaskRequest delegateTaskRequest =
        cdStepHelper.mapTaskRequestToDelegateTaskRequest(taskRequest, taskData, emptySet(), "", true);

    return delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
  }

  private String extractToken(ConnectorInfoDTO connectorInfoDTO) {
    String tokenRefIdentifier = null;

    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubCredentialsDTO githubCredentialsDTO = githubConnectorDTO.getAuthentication().getCredentials();

    if (githubCredentialsDTO instanceof GithubHttpCredentialsDTO) {
      GithubUsernameTokenDTO githubUsernameTokenDTO =
          (GithubUsernameTokenDTO) ((GithubHttpCredentialsDTO) githubCredentialsDTO).getHttpCredentialsSpec();
      tokenRefIdentifier = githubUsernameTokenDTO.getTokenRef().getIdentifier();
    } else if (githubCredentialsDTO instanceof GithubSshCredentialsDTO) {
      tokenRefIdentifier = ((GithubSshCredentialsDTO) githubCredentialsDTO).getSshKeyRef().getIdentifier();
    }
    return tokenRefIdentifier;
  }

  private Map<String, Object> populateConstraintContext(ConstraintUnit constraintUnit, String releaseEntityId) {
    Map<String, Object> constraintContext = new HashMap<>();
    constraintContext.put(GithubRestraintInstanceKeys.releaseEntityId, releaseEntityId);
    constraintContext.put(
        GithubRestraintInstanceKeys.order, githubRestraintInstanceService.getMaxOrder(constraintUnit.getValue()) + 1);

    return constraintContext;
  }

  @Override
  public TaskRequest obtainTask(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    try (PmsSecurityContextEventGuard securityContextEventGuard = new PmsSecurityContextEventGuard(ambiance)) {
      validateResources(ambiance, stepParameters);
      MergePRStepParams gitOpsSpecParams = (MergePRStepParams) stepParameters.getSpec();

      ManifestOutcome releaseRepoOutcome = gitOpsStepHelper.getReleaseRepoOutcome(ambiance);

      OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.UPDATE_RELEASE_REPO_OUTCOME));
      OptionalSweepingOutput optionalSweepingOutputRevertPR = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.REVERT_PR_OUTCOME));

      int prNumber;
      String prLink;
      String sha;
      String ref;
      boolean isRevertPR = false;
      if (optionalSweepingOutputRevertPR != null && optionalSweepingOutputRevertPR.isFound()) {
        RevertPROutcome revertPROutcome = (RevertPROutcome) optionalSweepingOutputRevertPR.getOutput();
        prNumber = revertPROutcome.getPrNumber();
        prLink = revertPROutcome.getPrlink();
        sha = revertPROutcome.getCommitId();
        ref = revertPROutcome.getRef();
        isRevertPR = true;
      } else if (optionalSweepingOutput != null && optionalSweepingOutput.isFound()) {
        UpdateReleaseRepoOutcome updateReleaseRepoOutcome =
            (UpdateReleaseRepoOutcome) optionalSweepingOutput.getOutput();
        prNumber = updateReleaseRepoOutcome.getPrNumber();
        prLink = updateReleaseRepoOutcome.getPrlink();
        sha = updateReleaseRepoOutcome.getCommitId();
        ref = updateReleaseRepoOutcome.getRef();
      } else {
        throw new InvalidRequestException("Pull Request Details are missing", USER);
      }

      ConnectorInfoDTO connectorInfoDTO =
          cdStepHelper.getConnector(releaseRepoOutcome.getStore().getConnectorReference().getValue(), ambiance);

      String accountId = AmbianceUtils.getAccountId(ambiance);

      Map<String, Object> apiParamOptions = null;

      apiParamOptions = gitOpsSpecParams.getVariables();

      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(connectorInfoDTO.getIdentifier(), accountId,
              connectorInfoDTO.getOrgIdentifier(), connectorInfoDTO.getProjectIdentifier());

      ConnectorDetails connectorDetails =
          connectorUtils.getConnectorDetails(identifierRef, identifierRef.buildScopedIdentifier());

      GitStoreDelegateConfig gitStoreDelegateConfig = getGitStoreDelegateConfig(ambiance, releaseRepoOutcome);
      GitApiTaskParams gitApiTaskParams;
      switch (gitStoreDelegateConfig.getGitConfigDTO().getConnectorType()) {
        case GITHUB:
          GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) gitStoreDelegateConfig.getGitConfigDTO();
          gitApiTaskParams = GitApiTaskParams.builder()
                                 .gitRepoType(GitRepoType.GITHUB)
                                 .requestType(GitApiRequestType.MERGE_PR)
                                 .connectorDetails(connectorDetails)
                                 .prNumber(String.valueOf(prNumber))
                                 .owner(githubConnectorDTO.getGitRepositoryDetails().getOrg())
                                 .repo(githubConnectorDTO.getGitRepositoryDetails().getName())
                                 .sha(sha)
                                 .deleteSourceBranch(CDStepHelper.getParameterFieldBooleanValue(
                                     gitOpsSpecParams.getDeleteSourceBranch(),
                                     MergePRStepInfo.MergePRBaseStepInfoKeys.deleteSourceBranch, stepParameters))
                                 .ref(ref)
                                 .build();
          break;
        case AZURE_REPO:
          AzureRepoConnectorDTO azureRepoConnectorDTO =
              (AzureRepoConnectorDTO) gitStoreDelegateConfig.getGitConfigDTO();
          gitApiTaskParams = GitApiTaskParams.builder()
                                 .gitRepoType(GitRepoType.AZURE_REPO)
                                 .requestType(GitApiRequestType.MERGE_PR)
                                 .connectorDetails(connectorDetails)
                                 .prNumber(String.valueOf(prNumber))
                                 .owner(azureRepoConnectorDTO.getGitRepositoryDetails().getOrg())
                                 .repo(azureRepoConnectorDTO.getGitRepositoryDetails().getName())
                                 .sha(sha)
                                 .deleteSourceBranch(CDStepHelper.getParameterFieldBooleanValue(
                                     gitOpsSpecParams.getDeleteSourceBranch(),
                                     MergePRStepInfo.MergePRBaseStepInfoKeys.deleteSourceBranch, stepParameters))
                                 .apiParamOptions(emptyIfNull(apiParamOptions))
                                 .build();
          break;
        case GITLAB:
          GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) gitStoreDelegateConfig.getGitConfigDTO();
          String slug = scmGitProviderHelper.getSlug(gitlabConnectorDTO);
          gitApiTaskParams = GitApiTaskParams.builder()
                                 .gitRepoType(GitRepoType.GITLAB)
                                 .requestType(GitApiRequestType.MERGE_PR)
                                 .connectorDetails(connectorDetails)
                                 .prNumber(String.valueOf(prNumber))
                                 .slug(slug)
                                 .sha(sha)
                                 .deleteSourceBranch(CDStepHelper.getParameterFieldBooleanValue(
                                     gitOpsSpecParams.getDeleteSourceBranch(),
                                     MergePRStepInfo.MergePRBaseStepInfoKeys.deleteSourceBranch, stepParameters))
                                 .build();
          break;
        case BITBUCKET:
          gitApiTaskParams = getTaskParamsForBitbucket((BitbucketConnectorDTO) gitStoreDelegateConfig.getGitConfigDTO(),
              connectorDetails, prNumber, sha, ref, gitOpsSpecParams.getDeleteSourceBranch(), stepParameters);
          break;
        default:
          throw new InvalidRequestException("Failed to run MergePR Step. Connector not supported", USER);
      }

      NGGitOpsTaskParams ngGitOpsTaskParams = NGGitOpsTaskParams.builder()
                                                  .gitOpsTaskType(GitOpsTaskType.MERGE_PR)
                                                  .accountId(accountId)
                                                  .connectorInfoDTO(connectorInfoDTO)
                                                  .gitApiTaskParams(gitApiTaskParams)
                                                  .prLink(prLink)
                                                  .isRevertPR(isRevertPR)
                                                  .build();

      final TaskData taskData = TaskData.builder()
                                    .async(true)
                                    .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                    .taskType(TaskType.GITOPS_TASK_NG.name())
                                    .parameters(new Object[] {ngGitOpsTaskParams})
                                    .build();

      String taskName = TaskType.GITOPS_TASK_NG.getDisplayName();

      return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
          gitOpsSpecParams.getCommandUnits(), taskName,
          TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(gitOpsSpecParams.getDelegateSelectors()))),
          stepHelper.getEnvironmentType(ambiance));
    } catch (Exception e) {
      log.error("Failed to execute Update Release Repo step", e);
      throw new InvalidRequestException(
          String.format("Failed to execute Update Release Repo step. %s", e.getMessage()));
    }
  }

  @Override
  public StepResponse handleTaskResult(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<NGGitOpsResponse> responseDataSupplier) throws Exception {
    try (PmsSecurityContextEventGuard securityContextEventGuard = new PmsSecurityContextEventGuard(ambiance)) {
      NGGitOpsResponse response = responseDataSupplier.get();
      return calculateStepResponse(ambiance, response);
    }
  }
}
