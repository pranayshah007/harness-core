/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.ListUtils.trimStrings;
import static io.harness.exception.WingsException.USER;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.trim;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.gitops.steps.GitopsClustersOutcome;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.EmptyPredicate;
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
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
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

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(GITOPS)
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
  @Inject protected OutcomeService outcomeService;
  @Inject private K8sStepHelper k8sStepHelper;

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

  public ManifestOutcome getReleaseRepoOutcome(Ambiance ambiance) {
    ManifestsOutcome manifestsOutcomes = k8sStepHelper.resolveManifestsOutcome(ambiance);

    List<ManifestOutcome> releaseRepoManifests =
        manifestsOutcomes.values()
            .stream()
            .filter(manifestOutcome -> ManifestType.ReleaseRepo.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());

    if (isEmpty(releaseRepoManifests)) {
      throw new InvalidRequestException("Release Repo Manifests are mandatory for Create PR step. Select one from "
              + String.join(", ", ManifestType.ReleaseRepo),
          USER);
    }

    if (releaseRepoManifests.size() > 1) {
      throw new InvalidRequestException("There can be only a single Release Repo manifest", USER);
    }
    return releaseRepoManifests.get(0);
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    /*
    TODO:
     2. Handle the case when PR already exists
     Delegate side: (NgGitOpsCommandTask.java)
     */
    CreatePRStepParams gitOpsSpecParams = (CreatePRStepParams) stepParameters.getSpec();

    Map<String, Object> variables = fetchVariablesForUpdate(ambiance);
    checkArgument(EmptyPredicate.isNotEmpty(variables), "no cluster variables found.");
    ExpressionEvaluatorUtils.updateExpressions(
        variables, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

    List<GitFetchFilesConfig> gitFetchFilesConfig = new ArrayList<>();

    ManifestOutcome releaseRepoOutcome = getReleaseRepoOutcome(ambiance);
    gitFetchFilesConfig.add(getGitFetchFilesConfig(ambiance, releaseRepoOutcome));

    NGGitOpsTaskParams ngGitOpsTaskParams =
        NGGitOpsTaskParams.builder()
            .gitOpsTaskType(GitOpsTaskType.CREATE_PR)
            .gitFetchFilesConfig(gitFetchFilesConfig.get(0))
            .overrideConfig(CDStepHelper.getParameterFieldBooleanValue(gitOpsSpecParams.getOverrideConfig(),
                CreatePRStepInfo.CreatePRBaseStepInfoKeys.overrideConfig, stepParameters))
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .connectorInfoDTO(
                cdStepHelper.getConnector(releaseRepoOutcome.getStore().getConnectorReference().getValue(), ambiance))
            .variables(variables)
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
                             .variables(variables)
                             .build())
        .build();
  }

  private Map<String, Object> fetchVariablesForUpdate(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(GitopsClustersStep.GITOPS_SWEEPING_OUTPUT));
    if (optionalSweepingOutput != null && optionalSweepingOutput.isFound()) {
      GitopsClustersOutcome output = (GitopsClustersOutcome) optionalSweepingOutput.getOutput();
      // Reading only 1 set of variables for now since all clusters have same variables
      List<GitopsClustersOutcome.ClusterData> clustersData = output.getClustersData();
      if (EmptyPredicate.isNotEmpty(clustersData)) {
        return clustersData.get(0).getVariables();
      }
    }
    return null;
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return null;
  }

  public GitFetchFilesConfig getGitFetchFilesConfig(Ambiance ambiance, ManifestOutcome manifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) manifestOutcome.getStore();
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);

    List<String> gitFilePaths = new ArrayList<>();
    gitFilePaths.addAll(getParameterFieldValue(gitStoreConfig.getPaths()));

    GitStoreDelegateConfig gitStoreDelegateConfig =
        cdStepHelper.getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitFilePaths, ambiance);

    ScmConnector scmConnector = (ScmConnector) connectorDTO.getConnectorConfig();

    // Overriding the gitStoreDelegateConfig to set the correct version of scmConnector that allows
    // to retain gitConnector metadata required for creating PR
    GitStoreDelegateConfig rebuiltGitStoreDelegateConfig =
        GitStoreDelegateConfig.builder()
            .gitConfigDTO(scmConnector)
            .apiAuthEncryptedDataDetails(gitStoreDelegateConfig.getApiAuthEncryptedDataDetails())
            .sshKeySpecDTO(gitStoreDelegateConfig.getSshKeySpecDTO())
            .encryptedDataDetails(gitStoreDelegateConfig.getEncryptedDataDetails())
            .fetchType(gitStoreConfig.getGitFetchType())
            .branch(trim(getParameterFieldValue(gitStoreConfig.getBranch())))
            .commitId(trim(getParameterFieldValue(gitStoreConfig.getCommitId())))
            .paths(trimStrings(gitFilePaths))
            .connectorName(connectorDTO.getName())
            .manifestType(manifestOutcome.getType())
            .manifestId(manifestOutcome.getIdentifier())
            .optimizedFilesFetch(gitStoreDelegateConfig.isOptimizedFilesFetch())
            .build();

    return GitFetchFilesConfig.builder()
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .succeedIfFileNotFound(false)
        .gitStoreDelegateConfig(rebuiltGitStoreDelegateConfig)
        .build();
  }
}
