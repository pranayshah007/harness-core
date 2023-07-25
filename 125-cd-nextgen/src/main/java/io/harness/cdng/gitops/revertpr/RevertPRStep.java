/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.revertpr;
import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.ListUtils.trimStrings;

import static org.apache.commons.lang3.StringUtils.trim;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.gitops.steps.GitOpsStepHelper;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
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
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.ConnectorUtils;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@OwnedBy(GITOPS)
@Slf4j
public class RevertPRStep extends CdTaskExecutable<NGGitOpsResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GITOPS_REVERT_PR.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private GitOpsStepHelper gitOpsStepHelper;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private ScmGitProviderHelper scmGitProviderHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Nothing to validate.
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<NGGitOpsResponse> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();

    NGGitOpsResponse ngGitOpsResponse = (NGGitOpsResponse) responseData;
    if (TaskStatus.SUCCESS.equals(ngGitOpsResponse.getTaskStatus())) {
      RevertPROutcome revertPROutcome = RevertPROutcome.builder()
                                            .prlink(ngGitOpsResponse.getPrLink())
                                            .prNumber(ngGitOpsResponse.getPrNumber())
                                            .commitId(ngGitOpsResponse.getCommitId())
                                            .ref(ngGitOpsResponse.getRef())
                                            .build();

      executionSweepingOutputService.consume(
          ambiance, OutcomeExpressionConstants.REVERT_PR_OUTCOME, revertPROutcome, StepOutcomeGroup.STAGE.name());

      return StepResponse.builder()
          .unitProgressList(ngGitOpsResponse.getUnitProgressData().getUnitProgresses())
          .status(Status.SUCCEEDED)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.REVERT_PR_OUTCOME)
                           .outcome(revertPROutcome)
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
    RevertPRStepParameters gitOpsSpecParams = (RevertPRStepParameters) stepParameters.getSpec();
    try {
      ManifestOutcome releaseRepoOutcome = gitOpsStepHelper.getReleaseRepoOutcome(ambiance);

      List<GitFetchFilesConfig> gitFetchFilesConfig = new ArrayList<>();
      gitFetchFilesConfig.add(getGitFetchFilesConfig(
          ambiance, releaseRepoOutcome, trim(getParameterFieldValue(gitOpsSpecParams.getCommitId()))));

      NGGitOpsTaskParams ngGitOpsTaskParams =
          NGGitOpsTaskParams.builder()
              .gitOpsTaskType(GitOpsTaskType.REVERT_PR)
              .gitFetchFilesConfig(gitFetchFilesConfig.get(0))
              .accountId(AmbianceUtils.getAccountId(ambiance))
              .connectorInfoDTO(
                  cdStepHelper.getConnector(releaseRepoOutcome.getStore().getConnectorReference().getValue(), ambiance))
              .prTitle(trim(getParameterFieldValue(gitOpsSpecParams.getPrTitle())))
              .build();

      final TaskData taskData = TaskData.builder()
                                    .async(true)
                                    .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                    .taskType(TaskType.GITOPS_TASK_NG.name())
                                    .parameters(new Object[] {ngGitOpsTaskParams})
                                    .build();

      return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
          gitOpsSpecParams.getCommandUnits(), TaskType.GITOPS_TASK_NG.getDisplayName(),
          TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(gitOpsSpecParams.getDelegateSelectors()))),
          stepHelper.getEnvironmentType(ambiance));

    } catch (Exception e) {
      log.error("Failed to execute Revert PR Repo step", e);
      throw new InvalidRequestException(String.format("Failed to execute Revert PR step. %s", e.getMessage()));
    }
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return null;
  }

  public GitFetchFilesConfig getGitFetchFilesConfig(
      Ambiance ambiance, ManifestOutcome manifestOutcome, String commitId) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) manifestOutcome.getStore();
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);

    List<String> gitFilePaths = new ArrayList<>();

    GitStoreDelegateConfig gitStoreDelegateConfig =
        cdStepHelper.getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitFilePaths, ambiance);

    ScmConnector scmConnector = (ScmConnector) connectorDTO.getConnectorConfig();

    // Overriding the gitStoreDelegateConfig to set the correct version of scmConnector that allows
    // to retain gitConnector metadata required for updating release repo and the commitId.
    GitStoreDelegateConfig rebuiltGitStoreDelegateConfig =
        GitStoreDelegateConfig.builder()
            .gitConfigDTO(scmConnector)
            .apiAuthEncryptedDataDetails(gitStoreDelegateConfig.getApiAuthEncryptedDataDetails())
            .sshKeySpecDTO(gitStoreDelegateConfig.getSshKeySpecDTO())
            .encryptedDataDetails(gitStoreDelegateConfig.getEncryptedDataDetails())
            .fetchType(gitStoreConfig.getGitFetchType())
            .branch(trim(getParameterFieldValue(gitStoreConfig.getBranch())))
            .commitId(commitId)
            .paths(trimStrings(gitFilePaths))
            .connectorName(connectorDTO.getName())
            .manifestType(manifestOutcome.getType())
            .manifestId(manifestOutcome.getIdentifier())
            .optimizedFilesFetch(gitStoreDelegateConfig.isOptimizedFilesFetch())
            .build();

    return GitFetchFilesConfig.builder()
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .succeedIfFileNotFound(true)
        .gitStoreDelegateConfig(rebuiltGitStoreDelegateConfig)
        .build();
  }
}