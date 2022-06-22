/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.ListUtils.trimStrings;
import static io.harness.exception.WingsException.USER;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.trim;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.IssueCommentWebhookEvent;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.gitops.steps.GitOpsStepHelper;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.gitapi.GitApiRequestType;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitOpsTaskType;
import io.harness.delegate.task.git.NGGitOpsResponse;
import io.harness.delegate.task.git.NGGitOpsTaskParams;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.ConnectorUtils;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import de.danielbechler.util.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.GITOPS)
@Slf4j
public class MergePRStep extends TaskChainExecutableWithRollbackAndRbac {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private GitOpsStepHelper gitOpsStepHelper;
  @Inject private ConnectorUtils connectorUtils;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GITOPS_MERGE_PR.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Nothing to validate
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    return null;
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();

    NGGitOpsResponse ngGitOpsResponse = (NGGitOpsResponse) responseData;

    if (TaskStatus.SUCCESS.equals(ngGitOpsResponse.getTaskStatus())) {
      return StepResponse.builder()
          .unitProgressList(ngGitOpsResponse.getUnitProgressData().getUnitProgresses())
          .status(Status.SUCCEEDED)
          .build();
    }

    return StepResponse.builder()
        .unitProgressList(ngGitOpsResponse.getUnitProgressData().getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(ngGitOpsResponse.getErrorMessage()).build())
        .build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    MergePRStepParams gitOpsSpecParams = (MergePRStepParams) stepParameters.getSpec();

    ManifestOutcome releaseRepoOutcome = gitOpsStepHelper.getReleaseRepoOutcome(ambiance);

    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.CREATE_PR_OUTCOME));
    String prLink = "https://github.com/wings-software/rohittest/pull/6";
    if (optionalSweepingOutput != null && optionalSweepingOutput.isFound()) {
      CreatePROutcome createPROutcome = (CreatePROutcome) optionalSweepingOutput.getOutput();
      prLink = createPROutcome.getPrLink();
    }

    GitStoreDelegateConfig gitStoreDelegateConfig = getGitStoreDelegateConfig(ambiance, releaseRepoOutcome);

    String accountId = AmbianceUtils.getAccountId(ambiance);

    ConnectorInfoDTO connectorInfoDTO =
        cdStepHelper.getConnector(releaseRepoOutcome.getStore().getConnectorReference().getValue(), ambiance);
    String scope = "";
    if (!isEmpty(connectorInfoDTO.getProjectIdentifier()) & !isEmpty(accountId)) {
      scope = "org.";
    } else {
      scope = "account.";
    }
    ConnectorDetails connectorDetails =
        connectorUtils.getConnectorDetails(IdentifierRef.builder()
                                               .accountIdentifier(accountId)
                                               .orgIdentifier(connectorInfoDTO.getOrgIdentifier())
                                               .projectIdentifier(connectorInfoDTO.getProjectIdentifier())
                                               .build(),
            scope + connectorInfoDTO.getIdentifier());

    //    ConnectorDetails connectorDetails = ConnectorDetails.builder()
    //            .connectorConfig(connectorInfoDTO.getConnectorConfig())
    //            .connectorType(connectorInfoDTO.getConnectorType())
    //            .identifier(connectorInfoDTO.getIdentifier())
    //            .orgIdentifier(connectorInfoDTO.getOrgIdentifier())
    //            .projectIdentifier(connectorInfoDTO.getProjectIdentifier())
    //            .encryptedDataDetails(gitStoreDelegateConfig.getEncryptedDataDetails())
    //            .build();

    GitApiTaskParams gitApiTaskParams = GitApiTaskParams.builder()
                                            .gitRepoType(GitRepoType.GITHUB)
                                            .requestType(GitApiRequestType.MERGE_PR)
                                            .connectorDetails(connectorDetails)
                                            .prNumber("6")
                                            .owner("wings-software")
                                            .repo("rohittest")
                                            .build();

    if (!Strings.isEmpty(prLink)) {
      NGGitOpsTaskParams ngGitOpsTaskParams = NGGitOpsTaskParams.builder()
                                                  .gitOpsTaskType(GitOpsTaskType.MERGE_PR)
                                                  .accountId(accountId)
                                                  .connectorInfoDTO(connectorInfoDTO)
                                                  .prLink(prLink)
                                                  .gitApiTaskParams(gitApiTaskParams)
                                                  .build();

      final TaskData taskData = TaskData.builder()
                                    .async(true)
                                    .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                    .taskType(TaskType.GITOPS_TASK_NG.name())
                                    .parameters(new Object[] {ngGitOpsTaskParams})
                                    .build();

      String taskName = TaskType.GITOPS_TASK_NG.getDisplayName();

      final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
          gitOpsSpecParams.getCommandUnits(), taskName,
          TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(gitOpsSpecParams.getDelegateSelectors()))),
          stepHelper.getEnvironmentType(ambiance));

      return TaskChainResponse.builder()
          .chainEnd(true)
          .passThroughData(MergePRPassThroughData.builder().prLink(prLink).build())
          .taskRequest(taskRequest)
          .build();
    } else {
      throw new InvalidRequestException("Pull Request Details are missing", USER);
    }
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
}
