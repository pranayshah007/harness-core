package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitOpsTaskType;
import io.harness.delegate.task.git.NGGitOpsResponse;
import io.harness.delegate.task.git.NGGitOpsTaskParams;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.expression.ExpressionEvaluatorUtils;
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
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class CreatePRStep extends TaskChainExecutableWithRollbackAndRbac {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GITOPS_CREATE_PR.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private EngineExpressionService engineExpressionService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private StepHelper stepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  private final Set<ConnectorType> validConnectorTypes =
      ImmutableSet.of(ConnectorType.GITHUB, ConnectorType.GITLAB, ConnectorType.BITBUCKET, ConnectorType.AZURE_REPO);

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

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
      CreatePROutcome createPROutcome = CreatePROutcome.builder()
                                            .changedFiles(((CreatePRPassThroughData) passThroughData).getFilePaths())
                                            .prLink(ngGitOpsResponse.getPrLink())
                                            .commitId(ngGitOpsResponse.getCommitId())
                                            .build();

      executionSweepingOutputService.consume(
          ambiance, OutcomeExpressionConstants.CREATE_PR_OUTCOME, createPROutcome, StepOutcomeGroup.STEP.name());

      return StepResponse.builder()
          .unitProgressList(ngGitOpsResponse.getUnitProgressData().getUnitProgresses())
          .status(Status.SUCCEEDED)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.CREATE_PR_OUTCOME)
                           .outcome(createPROutcome)
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
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    /*
    TODO:
     2. Handle the case when PR already exists
     Delegate side: (NgGitOpsCommandTask.java)
     5. Improve logging for commitAndPush, createPR, etc
     */
    CreatePRStepParams gitOpsSpecParams = (CreatePRStepParams) stepParameters.getSpec();
    StoreConfig store = gitOpsSpecParams.getStore().getValue().getSpec();
    Map<String, String> stringMap = gitOpsSpecParams.getStringMap().getValue();
    ExpressionEvaluatorUtils.updateExpressions(
        store, new CDExpressionResolveFunctor(engineExpressionService, ambiance));
    ExpressionEvaluatorUtils.updateExpressions(
        stringMap, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

    List<GitFetchFilesConfig> gitFetchFilesConfig = new ArrayList<>();
    // TODO: Should ManifestOutcome type be changed
    gitFetchFilesConfig.add(
        getGitFetchFilesConfig(ambiance, store, ValuesManifestOutcome.builder().identifier("dummy").build()));

    stringMap.remove("__uuid");

    NGGitOpsTaskParams ngGitOpsTaskParams =
        NGGitOpsTaskParams.builder()
            .gitOpsTaskType(GitOpsTaskType.CREATE_PR)
            .gitFetchFilesConfig(gitFetchFilesConfig.get(0))
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .stringMap(stringMap)
            .isNewBranch(gitOpsSpecParams.getIsNewBranch().getValue())
            .commitMessage(gitOpsSpecParams.getCommitMessage().getValue())
            .prTitle(gitOpsSpecParams.getPrTitle().getValue())
            .targetBranch(gitOpsSpecParams.getTargetBranch().getValue())
            .connectorInfoDTO(cdStepHelper.getConnector(store.getConnectorReference().getValue(), ambiance))
            .sourceBranch(gitFetchFilesConfig.get(0).getGitStoreDelegateConfig().getBranch())
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
        .taskRequest(taskRequest)
        .passThroughData(CreatePRPassThroughData.builder()
                             .filePaths(gitFetchFilesConfig.get(0).getGitStoreDelegateConfig().getPaths())
                             .stringMap(stringMap)
                             .build())
        .build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return null;
  }

  public GitFetchFilesConfig getGitFetchFilesConfig(
      Ambiance ambiance, StoreConfig store, ManifestOutcome manifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);

    List<String> gitFilePaths = new ArrayList<>();
    gitFilePaths.addAll(getParameterFieldValue(gitStoreConfig.getPaths()));

    GitStoreDelegateConfig gitStoreDelegateConfig =
        cdStepHelper.getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitFilePaths, ambiance);

    ScmConnector scmConnector = gitStoreDelegateConfig.getGitConfigDTO();

    if (!validConnectorTypes.contains(scmConnector.getConnectorType())) {
      // TODO: Handle in case this exception is thrown
      throw new UnsupportedOperationException(
          format("Create PR step is not supported for connector type: [%s]", scmConnector.getConnectorType()));
    }

    return GitFetchFilesConfig.builder()
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .succeedIfFileNotFound(false)
        .gitStoreDelegateConfig(gitStoreDelegateConfig)
        .build();
  }
}
