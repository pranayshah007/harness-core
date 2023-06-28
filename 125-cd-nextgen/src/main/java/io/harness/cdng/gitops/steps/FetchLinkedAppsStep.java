/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SWEEPING_OUTPUT;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeLevel;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.gitops.beans.FetchLinkedAppsStepParams;
import io.harness.cdng.gitops.beans.GitOpsLinkedAppsOutcome;
import io.harness.cdng.manifest.yaml.DeploymentRepoManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.gitops.GitOpsFetchAppTaskParams;
import io.harness.delegate.task.gitops.GitOpsFetchAppTaskResponse;
import io.harness.encryption.Scope;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogLine;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.BaseUrls;
import io.harness.ng.beans.PageResponse;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
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
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import retrofit2.Response;

@OwnedBy(HarnessTeam.GITOPS)
@Slf4j
public class FetchLinkedAppsStep extends CdTaskExecutable<GitOpsFetchAppTaskResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GITOPS_FETCH_LINKED_APPS.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String GITOPS_LINKED_APPS_OUTCOME = "GITOPS_LINKED_APPS_OUTCOME";
  public static final String LOG_KEY_SUFFIX = "EXECUTE";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private GitOpsStepHelper gitOpsStepHelper;
  @Inject private GitopsResourceClient gitopsResourceClient;

  @Inject private CDStepHelper cdStepHelper;
  @Inject private StepHelper stepHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private BaseUrls baseUrls;

  @Override
  public Class getStepParametersClass() {
    return FetchLinkedAppsStepParams.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<GitOpsFetchAppTaskResponse> responseDataSupplier) throws Exception {
    log.info("Started handling delegate task result");
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    try {
      GitOpsFetchAppTaskResponse gitOpsFetchAppTaskResponse = responseDataSupplier.get();

      if (gitOpsFetchAppTaskResponse.getTaskStatus() == TaskStatus.FAILURE) {
        return StepResponse.builder()
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder()
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(GENERAL_ERROR.name())
                                                 .setMessage(HarnessStringUtils.emptyIfNull(
                                                     gitOpsFetchAppTaskResponse.getErrorMessage()))
                                                 .build())
                             .build())
            .build();
      }

      OptionalSweepingOutput optionalGitOpsSweepingOutput = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getOutcomeRefObject(GITOPS_SWEEPING_OUTPUT));

      if (optionalGitOpsSweepingOutput == null || !optionalGitOpsSweepingOutput.isFound()) {
        throw new InvalidRequestException("GitOps Clusters Outcome Not Found.");
      }

      GitopsClustersOutcome gitopsClustersOutcome = (GitopsClustersOutcome) optionalGitOpsSweepingOutput.getOutput();
      List<String> scopedClusterIds = getScopedClusterIds(gitopsClustersOutcome);

      IdentifierRef identifierRef = IdentifierRef.builder()
                                        .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
                                        .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                                        .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                                        .build();

      List<Application> applications =
          fetchLinkedApps(gitOpsFetchAppTaskResponse.getAppName(), scopedClusterIds, identifierRef);
      populateAppUrls(applications, identifierRef);

      StepResponse.StepOutcome stepOutcome = null;

      if (EmptyPredicate.isEmpty(applications)) {
        logStreamingStepClient.writeLogLine(LogLine.builder()
                                                .message("No linked apps found in Harness System.")
                                                .level(LogLevel.INFO)
                                                .timestamp(Instant.now())
                                                .build(),
            LOG_KEY_SUFFIX);
      } else {
        for (Application application : applications) {
          logStreamingStepClient.writeLogLine(LogLine.builder()
                                                  .message(String.format("Found linked app: %s. Link - %s",
                                                      application.getName(), application.getUrl()))
                                                  .level(LogLevel.INFO)
                                                  .timestamp(Instant.now())
                                                  .build(),
              LOG_KEY_SUFFIX);
        }

        GitOpsLinkedAppsOutcome linkedAppsOutcome = GitOpsLinkedAppsOutcome.builder().apps(applications).build();
        stepOutcome =
            StepResponse.StepOutcome.builder().name(GITOPS_LINKED_APPS_OUTCOME).outcome(linkedAppsOutcome).build();
        executionSweepingOutputService.consume(
            ambiance, GITOPS_LINKED_APPS_OUTCOME, linkedAppsOutcome, StepOutcomeGroup.STAGE.name());
      }

      StepResponseBuilder stepResponse = StepResponse.builder().status(Status.SUCCEEDED);
      if (stepOutcome != null) {
        stepResponse.stepOutcome(stepOutcome);
      }
      return stepResponse.build();
    } catch (WingsException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new InvalidRequestException("Failed to execute Fetch Linked Apps step", ex);
    } finally {
      logStreamingStepClient.closeStream(LOG_KEY_SUFFIX);
    }
  }

  @VisibleForTesting
  @NotNull
  List<String> getScopedClusterIds(GitopsClustersOutcome gitopsClustersOutcome) {
    if (gitopsClustersOutcome == null || gitopsClustersOutcome.getClustersData() == null) {
      log.debug("No Gitops Clusters found");
      return Collections.emptyList();
    }
    List<IdentifierRef> clusterIds = gitopsClustersOutcome.getClustersData()
                                         .stream()
                                         .map(clusterdata
                                             -> IdentifierRef.builder()
                                                    .identifier(clusterdata.getClusterId())
                                                    .scope(getScope(clusterdata.getScope()))
                                                    .build())
                                         .collect(Collectors.toList());

    return clusterIds.stream().map(IdentifierRef::buildScopedIdentifier).collect(Collectors.toList());
  }

  // In commons, some places refer to org scope as ORGANIZATION and others refer it as ORG
  // This method takes care of both the mappings
  private Scope getScope(String scope) {
    return ScopeLevel.ORGANIZATION.toString().equalsIgnoreCase(scope) ? Scope.ORG : Scope.fromString(scope);
  }

  private void populateAppUrls(List<Application> applications, IdentifierRef identifierRef) {
    if (EmptyPredicate.isEmpty(applications)) {
      return;
    }
    for (Application application : applications) {
      String url = String.format("%saccount/%s/cd/orgs/%s/projects/%s/gitops/applications/%s?agentId=%s",
          baseUrls.getNextGenUiUrl(), identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
          identifierRef.getProjectIdentifier(), application.getName(), application.getAgentIdentifier());
      application.setUrl(url);
    }
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    try {
      log.info("Started executing Fetch Linked Apps Step");
      ILogStreamingStepClient logStreamingStepClient =
          logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      logStreamingStepClient.openStream(LOG_KEY_SUFFIX);

      FetchLinkedAppsStepParams gitOpsSpecParams = (FetchLinkedAppsStepParams) stepParameters.getSpec();
      DeploymentRepoManifestOutcome deploymentRepo =
          (DeploymentRepoManifestOutcome) gitOpsStepHelper.getDeploymentRepoOutcome(ambiance);

      List<GitFetchFilesConfig> gitFetchFilesConfig = new ArrayList<>();
      gitFetchFilesConfig.add(getGitFetchFilesConfig(ambiance, deploymentRepo));

      GitOpsFetchAppTaskParams fetchAppTaskParams = GitOpsFetchAppTaskParams.builder()
                                                        .gitFetchFilesConfig(gitFetchFilesConfig.get(0))
                                                        .accountId(AmbianceUtils.getAccountId(ambiance))
                                                        .build();

      final TaskData taskData = TaskData.builder()
                                    .async(true)
                                    .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                    .taskType(TaskType.GITOPS_FETCH_APP_TASK.name())
                                    .parameters(new Object[] {fetchAppTaskParams})
                                    .build();

      return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
          StepUtils.generateLogKeys(ambiance, Collections.singletonList(LOG_KEY_SUFFIX)), Collections.emptyList(),
          TaskType.GITOPS_FETCH_APP_TASK.getDisplayName(),
          TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(gitOpsSpecParams.getDelegateSelectors()))),
          stepHelper.getEnvironmentType(ambiance));

    } catch (WingsException ex) {
      throw ex;
    } catch (Exception e) {
      throw new InvalidRequestException("Failed to execute Fetch Linked Apps step", e);
    }
  }

  private List<Application> fetchLinkedApps(String appSetName, List<String> clusterIds, IdentifierRef identifierRef) {
    Map<String, Object> filters = new HashMap<>();
    filters.put("app.objectmeta.ownerreferences.0.name", appSetName);
    filters.put("clusterIdentifier", ImmutableMap.of("$in", clusterIds));
    ApplicationQuery applicationQuery = ApplicationQuery.builder()
                                            .accountId(identifierRef.getAccountIdentifier())
                                            .orgIdentifier(identifierRef.getOrgIdentifier())
                                            .projectIdentifier(identifierRef.getProjectIdentifier())
                                            .pageIndex(0)
                                            .pageSize(1000) // Assuming not more than 1000 entries
                                            .filter(filters)
                                            .build();
    try {
      Response<PageResponse<Application>> applicationsPageResponse =
          gitopsResourceClient.listApps(applicationQuery).execute();

      if (applicationsPageResponse.body() != null) {
        return CollectionUtils.emptyIfNull(applicationsPageResponse.body().getContent());
      } else {
        log.error("Failed to retrieve Linked Apps from Gitops Service, response :{}", applicationsPageResponse);
        throw new InvalidRequestException("Failed to retrieve Linked Apps from Gitops Service");
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Failed to retrieve Linked Apps from Gitops Service", e);
    }
  }

  public GitFetchFilesConfig getGitFetchFilesConfig(Ambiance ambiance, ManifestOutcome manifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) manifestOutcome.getStore();
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);

    GitStoreDelegateConfig gitStoreDelegateConfig = cdStepHelper.getGitStoreDelegateConfig(
        gitStoreConfig, connectorDTO, manifestOutcome, getParameterFieldValue(gitStoreConfig.getPaths()), ambiance);

    return GitFetchFilesConfig.builder()
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .succeedIfFileNotFound(false)
        .gitStoreDelegateConfig(gitStoreDelegateConfig)
        .build();
  }
}
