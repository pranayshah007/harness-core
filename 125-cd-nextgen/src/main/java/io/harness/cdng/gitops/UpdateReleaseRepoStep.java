/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SWEEPING_OUTPUT;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.ListUtils.trimStrings;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.trim;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.gitops.steps.GitOpsStepHelper;
import io.harness.cdng.gitops.steps.GitopsClustersOutcome;
import io.harness.cdng.k8s.K8sStepHelper;
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
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
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
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(GITOPS)
@Slf4j
public class UpdateReleaseRepoStep extends CdTaskExecutable<NGGitOpsResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GITOPS_UPDATE_RELEASE_REPO.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private EngineExpressionService engineExpressionService;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private StepHelper stepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject protected OutcomeService outcomeService;
  @Inject private K8sStepHelper k8sStepHelper;
  @Inject private GitOpsStepHelper gitOpsStepHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<NGGitOpsResponse> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();

    NGGitOpsResponse ngGitOpsResponse = (NGGitOpsResponse) responseData;

    if (TaskStatus.SUCCESS.equals(ngGitOpsResponse.getTaskStatus())) {
      UpdateReleaseRepoOutcome updateReleaseRepoOutcome = UpdateReleaseRepoOutcome.builder()
                                                              .prlink(ngGitOpsResponse.getPrLink())
                                                              .prNumber(ngGitOpsResponse.getPrNumber())
                                                              .commitId(ngGitOpsResponse.getCommitId())
                                                              .ref(ngGitOpsResponse.getRef())
                                                              .build();

      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.UPDATE_RELEASE_REPO_OUTCOME,
          updateReleaseRepoOutcome, StepOutcomeGroup.STAGE.name());

      return StepResponse.builder()
          .unitProgressList(ngGitOpsResponse.getUnitProgressData().getUnitProgresses())
          .status(Status.SUCCEEDED)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.UPDATE_RELEASE_REPO_OUTCOME)
                           .outcome(updateReleaseRepoOutcome)
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
    /*
  TODO:
   2. Handle the case when PR already exists
   Delegate side: (NgGitOpsCommandTask.java)
   */
    UpdateReleaseRepoStepParams gitOpsSpecParams = (UpdateReleaseRepoStepParams) stepParameters.getSpec();
    try {
      ManifestOutcome releaseRepoOutcome = gitOpsStepHelper.getReleaseRepoOutcome(ambiance);
      // Fetch files from releaseRepoOutcome and replace expressions if present with cluster name and environment
      Map<String, Map<String, String>> filesToVariablesMap =
          buildFilePathsToVariablesMap(releaseRepoOutcome, ambiance, gitOpsSpecParams.getVariables());

      List<GitFetchFilesConfig> gitFetchFilesConfig = new ArrayList<>();
      gitFetchFilesConfig.add(getGitFetchFilesConfig(ambiance, releaseRepoOutcome, filesToVariablesMap.keySet()));

      NGGitOpsTaskParams ngGitOpsTaskParams =
          NGGitOpsTaskParams.builder()
              .gitOpsTaskType(GitOpsTaskType.UPDATE_RELEASE_REPO)
              .gitFetchFilesConfig(gitFetchFilesConfig.get(0))
              .accountId(AmbianceUtils.getAccountId(ambiance))
              .connectorInfoDTO(
                  cdStepHelper.getConnector(releaseRepoOutcome.getStore().getConnectorReference().getValue(), ambiance))
              .filesToVariablesMap(filesToVariablesMap)
              .prTitle(gitOpsSpecParams.prTitle.getValue())
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
      log.error("Failed to execute Update Release Repo step", e);
      throw new InvalidRequestException(
          String.format("Failed to execute Update Release Repo step. %s", e.getMessage()));
    }
  }

  @VisibleForTesting
  public Map<String, Map<String, String>> buildFilePathsToVariablesMap(
      ManifestOutcome releaseRepoOutcome, Ambiance ambiance, Map<String, Object> variables) {
    // Get FilePath from release repo
    GitStoreConfig gitStoreConfig = (GitStoreConfig) releaseRepoOutcome.getStore();
    String filePath = gitStoreConfig.getPaths().getValue().get(0);

    // Read environment outcome and iterate over clusterData to replace the cluster and env name
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(GITOPS_SWEEPING_OUTPUT));

    Map<String, Map<String, String>> filePathsToVariables = new HashMap<>();

    if (optionalSweepingOutput != null && optionalSweepingOutput.isFound()) {
      GitopsClustersOutcome output = (GitopsClustersOutcome) optionalSweepingOutput.getOutput();
      List<GitopsClustersOutcome.ClusterData> clustersData = output.getClustersData();

      String file;

      for (GitopsClustersOutcome.ClusterData cluster : clustersData) {
        file = filePath;
        if (filePath.contains("<+envgroup.name>")) {
          file = filePath.replaceAll(
              "<\\+envgroup.name>/", cluster.getEnvGroupName() == null ? EMPTY : cluster.getEnvGroupName() + "/");
        }
        if (filePath.contains("<+env.name>")) {
          file = file.replaceAll("<\\+env.name>", cluster.getEnvName());
        }
        if (filePath.contains("<+cluster.name>")) {
          file = file.replaceAll("<\\+cluster.name>", cluster.getClusterName());
        }
        List<String> files = new ArrayList<>();
        files.add(file);
        // Resolve any other expressions in the filepaths. eg. service variables
        ExpressionEvaluatorUtils.updateExpressions(
            files, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

        file = files.get(0);

        ExpressionEvaluatorUtils.updateExpressions(
            cluster.getVariables(), new CDExpressionResolveFunctor(engineExpressionService, ambiance));

        Map<String, String> flattennedVariables = new HashMap<>();
        // Convert variables map from Map<String, Object> to Map<String, String>
        for (String key : cluster.getVariables().keySet()) {
          Object value = cluster.getVariables().get(key);
          if (value instanceof String && ((String) value).startsWith("${ngSecretManager.obtain")) {
            continue;
          }
          if (value.getClass() == ParameterField.class) {
            ParameterField<Object> p = (ParameterField) value;
            flattennedVariables.put(key, p.getValue().toString());
          } else {
            flattennedVariables.put(key, value.toString());
          }
        }
        // Convert variables from spec parameters
        for (Map.Entry<String, Object> variableEntry : variables.entrySet()) {
          ParameterField p = (ParameterField) variableEntry.getValue();
          ParameterField copyParameter = ParameterField.builder()
                                             .expression(p.isExpression())
                                             .expressionValue(p.getExpressionValue())
                                             .value(p.getValue())
                                             .build();
          if (copyParameter.isExpression()) {
            if (copyParameter.getExpressionValue().contains("<+envgroup.name>")) {
              copyParameter.setExpressionValue(copyParameter.getExpressionValue().replaceAll(
                  "<\\+envgroup.name>", cluster.getEnvGroupName() == null ? EMPTY : cluster.getEnvGroupName()));
            }
            if (copyParameter.getExpressionValue().contains("<+env.name>")) {
              copyParameter.setExpressionValue(
                  copyParameter.getExpressionValue().replaceAll("<\\+env.name>", cluster.getEnvName()));
            }
            if (copyParameter.getExpressionValue().contains("<+cluster.name>")) {
              copyParameter.setExpressionValue(
                  copyParameter.getExpressionValue().replaceAll("<\\+cluster.name>", cluster.getClusterName()));
            }
          }

          ExpressionEvaluatorUtils.updateExpressions(
              copyParameter, new CDExpressionResolveFunctor(engineExpressionService, ambiance));
          flattennedVariables.put(variableEntry.getKey(), copyParameter.getValue().toString());

          for (String key : flattennedVariables.keySet()) {
            String value = flattennedVariables.get(key);
            if (value.matches("[-+]?[0-9]*\\.0")) {
              flattennedVariables.put(key, value.split("\\.")[0]);
            }
          }
        }
        filePathsToVariables.put(file, flattennedVariables);
      }
    } else {
      throw new InvalidRequestException("No outcome published from GitOpsCluster Step");
    }
    return filePathsToVariables;
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return null;
  }

  public GitFetchFilesConfig getGitFetchFilesConfig(
      Ambiance ambiance, ManifestOutcome manifestOutcome, Set<String> resolvedFilePaths) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) manifestOutcome.getStore();
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);

    List<String> gitFilePaths = new ArrayList<>();
    gitFilePaths.addAll(resolvedFilePaths);

    GitStoreDelegateConfig gitStoreDelegateConfig =
        cdStepHelper.getGitStoreDelegateConfig(gitStoreConfig, connectorDTO, manifestOutcome, gitFilePaths, ambiance);

    ScmConnector scmConnector = (ScmConnector) connectorDTO.getConnectorConfig();

    // Overriding the gitStoreDelegateConfig to set the correct version of scmConnector that allows
    // to retain gitConnector metadata required for updating release repo
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
        .succeedIfFileNotFound(true)
        .gitStoreDelegateConfig(rebuiltGitStoreDelegateConfig)
        .build();
  }
}
