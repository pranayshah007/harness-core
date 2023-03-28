/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
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
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.gitapi.GitApiRequestType;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitOpsTaskType;
import io.harness.delegate.task.git.NGGitOpsResponse;
import io.harness.delegate.task.git.NGGitOpsTaskParams;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.impl.scm.ScmGitProviderHelper;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
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
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.ConnectorUtils;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.GITOPS)
@Slf4j
public class MergePRStep extends CdTaskExecutable<NGGitOpsResponse> {
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private GitOpsStepHelper gitOpsStepHelper;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ScmGitProviderHelper scmGitProviderHelper;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GITOPS_MERGE_PR.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Nothing to validate
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<NGGitOpsResponse> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();

    NGGitOpsResponse ngGitOpsResponse = (NGGitOpsResponse) responseData;

    if (TaskStatus.SUCCESS.equals(ngGitOpsResponse.getTaskStatus())) {
      MergePROutcome mergePROutcome = MergePROutcome.builder().commitId(ngGitOpsResponse.getCommitId()).build();

      executionSweepingOutputService.consume(
          ambiance, OutcomeExpressionConstants.MERGE_PR_OUTCOME, mergePROutcome, StepOutcomeGroup.STAGE.name());

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

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    MergePRStepParams gitOpsSpecParams = (MergePRStepParams) stepParameters.getSpec();

    ManifestOutcome releaseRepoOutcome = gitOpsStepHelper.getReleaseRepoOutcome(ambiance);

    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.CREATE_PR_OUTCOME));
    OptionalSweepingOutput optionalSweepingOutputUpdateReleaseRepo = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.UPDATE_RELEASE_REPO_OUTCOME));

    int prNumber;
    String prLink;
    String sha;
    String ref;
    if (optionalSweepingOutput != null && optionalSweepingOutput.isFound()) {
      CreatePROutcome createPROutcome = (CreatePROutcome) optionalSweepingOutput.getOutput();
      prNumber = createPROutcome.getPrNumber();
      prLink = createPROutcome.getPrlink();
      sha = createPROutcome.getCommitId();
      ref = createPROutcome.getRef();
    } else if (optionalSweepingOutputUpdateReleaseRepo != null && optionalSweepingOutputUpdateReleaseRepo.isFound()) {
      UpdateReleaseRepoOutcome updateReleaseRepoOutcome =
          (UpdateReleaseRepoOutcome) optionalSweepingOutputUpdateReleaseRepo.getOutput();
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
  }

  public GitStoreDelegateConfig getGitStoreDelegateConfig(Ambiance ambiance, ManifestOutcome manifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) manifestOutcome.getStore();
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);

    return cdStepHelper.getGitStoreDelegateConfig(
        gitStoreConfig, connectorDTO, manifestOutcome, new ArrayList<>(), ambiance);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return null;
  }

  private GitApiTaskParams getTaskParamsForBitbucket(BitbucketConnectorDTO bitbucketConnectorDTO,
      ConnectorDetails connectorDetails, int prNumber, String sha, String ref,
      ParameterField<Boolean> deleteSourceBranch, StepElementParameters stepParameters) {
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
}
