/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.pms.execution.utils.AmbianceUtils.getAccountId;
import static io.harness.pms.execution.utils.AmbianceUtils.getOrgIdentifier;
import static io.harness.pms.execution.utils.AmbianceUtils.getProjectIdentifier;

import static java.lang.String.format;
import static java.util.function.Predicate.not;

import io.harness.beans.ScopeLevel;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.gitops.models.Cluster;
import io.harness.gitops.models.ClusterQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.beans.PageResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.executable.SyncExecutableWithRbac;
import io.harness.utils.RetryUtils;

import com.amazonaws.util.StringUtils;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import retrofit2.Response;

@Slf4j
public class GitopsClustersStep implements SyncExecutableWithRbac<ClusterStepParameters> {
  public static final String GITOPS_SWEEPING_OUTPUT = "gitops";
  private static final int UNLIMITED_SIZE = 100000;
  private static final int STRINGS_LOGGING_LIMIT = 500;

  public static final String GITOPS_ENV_OUTCOME = "gitopsEnvOutcome";

  @Inject private ClusterService clusterService;
  @Inject private EnvironmentGroupService environmentGroupService;
  @Inject private GitopsResourceClient gitopsResourceClient;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private EngineExpressionService engineExpressionService;

  private static final RetryPolicy<Object> retryPolicyForGitopsClustersFetch = RetryUtils.getRetryPolicy(
      "Error getting clusters from Harness Gitops..retrying", "Failed to fetch clusters from Harness Gitops",
      Collections.singletonList(IOException.class), Duration.ofMillis(10), 3, log);

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GITOPS_CLUSTERS.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public void validateResources(Ambiance ambiance, ClusterStepParameters stepParameters) {}

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, ClusterStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    final StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    CommandExecutionStatus status = FAILURE;

    final LogCallback logger = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);

    log.info("Starting execution for GitOpsClustersStep [{}]", stepParameters);

    // Get Service Variables from sweeping output
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(YAMLFieldNameConstants.SERVICE_VARIABLES));

    final Map<String, Object> svcVariables = optionalSweepingOutput != null && optionalSweepingOutput.isFound()
        ? ((VariablesSweepingOutput) optionalSweepingOutput.getOutput())
        : new HashMap<>();

    if (isNotEmpty(svcVariables)) {
      resolveVariables(ambiance, svcVariables);
    }

    // Fetch Environment Variables from GitOpsEnvOutcome
    OptionalSweepingOutput optionalSweepingOutputForEnvVars = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(GITOPS_ENV_OUTCOME));

    // Resolved environment variables for each environment
    final Map<String, Map<String, Object>> envVars =
        optionalSweepingOutputForEnvVars != null && optionalSweepingOutputForEnvVars.isFound()
        ? ((GitOpsEnvOutCome) optionalSweepingOutputForEnvVars.getOutput()).getEnvToEnvVariables()
        : new HashMap<>();

    if (isNotEmpty(envVars) && isNotEmpty(envVars.values())) {
      for (Map<String, Object> env : envVars.values()) {
        resolveVariables(ambiance, env);
      }
    }

    // Resolved environment variables for each environment
    final Map<String, Map<String, Object>> envSvcOverrideVars =
        optionalSweepingOutputForEnvVars != null && optionalSweepingOutputForEnvVars.isFound()
        ? ((GitOpsEnvOutCome) optionalSweepingOutputForEnvVars.getOutput()).getEnvToSvcVariables()
        : new HashMap<>();

    if (isNotEmpty(envSvcOverrideVars) && isNotEmpty(envSvcOverrideVars.values())) {
      for (Map<String, Object> svcEnvOverRide : envSvcOverrideVars.values()) {
        resolveVariables(ambiance, svcEnvOverRide);
      }
    }

    try {
      final Map<String, IndividualClusterInternal> validatedClusters =
          validatedClusters(ambiance, stepParameters, logger, envVars);

      final GitopsClustersOutcome outcome = toOutcome(validatedClusters, svcVariables, envSvcOverrideVars);

      executionSweepingOutputResolver.consume(ambiance, GITOPS_SWEEPING_OUTPUT, outcome, StepOutcomeGroup.STAGE.name());

      stepResponseBuilder.stepOutcome(StepOutcome.builder().name(GITOPS_SWEEPING_OUTPUT).outcome(outcome).build());
      status = SUCCESS;
    } finally {
      logger.saveExecutionLog("Completed", INFO, status);
    }

    return stepResponseBuilder.status(Status.SUCCEEDED).build();
  }

  @Override
  public Class<ClusterStepParameters> getStepParametersClass() {
    return ClusterStepParameters.class;
  }

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, null);
  }

  private Map<String, IndividualClusterInternal> validatedClusters(Ambiance ambiance, ClusterStepParameters params,
      LogCallback logger, Map<String, Map<String, Object>> envVarsMap) {
    final Collection<EnvClusterRefs> envClusterRefs;
    if (params.getEnvGroupRef() != null) {
      saveExecutionLog(format("Deploying to GitOps clusters in environment group %s", params.getEnvGroupRef()), logger);
    }

    envClusterRefs = params.getEnvClusterRefs();

    if (isEmpty(envClusterRefs)) {
      throw new InvalidRequestException("No GitOps Cluster is selected with the current environment configuration");
    }

    logEnvironments(envClusterRefs, logger);

    // clusterId -> IndividualClusterInternal
    final Map<String, IndividualClusterInternal> individualClusters = fetchClusterRefs(
        params.getEnvGroupRef(), params.getEnvGroupName(), ambiance, envClusterRefs, logger, envVarsMap);

    if (isEmpty(individualClusters)) {
      saveExecutionLog("No GitOps cluster is selected", logger);
      throw new InvalidRequestException("No GitOps Cluster is selected with the current environment configuration");
    }

    return filterClustersFromGitopsService(ambiance, individualClusters, logger);
  }

  @NotNull
  private Map<String, IndividualClusterInternal> filterClustersFromGitopsService(
      Ambiance ambiance, Map<String, IndividualClusterInternal> individualClusters, LogCallback logger) {
    final Set<String> accountLevelClustersIds = individualClusters.keySet()
                                                    .stream()
                                                    .filter(ref -> StringUtils.beginsWithIgnoreCase(ref, "account."))
                                                    .collect(Collectors.toSet());
    final Set<String> orgLevelClustersIds = individualClusters.keySet()
                                                .stream()
                                                .filter(ref -> StringUtils.beginsWithIgnoreCase(ref, "organization."))
                                                .collect(Collectors.toSet());

    final Map<String, IndividualClusterInternal> accountLevelClusters = new HashMap<>();
    final Map<String, IndividualClusterInternal> orgLevelClusters = new HashMap<>();
    final Map<String, IndividualClusterInternal> projectLevelClusters = new HashMap<>();

    for (Map.Entry<String, IndividualClusterInternal> clusterEntry : individualClusters.entrySet()) {
      final String key = clusterEntry.getKey();
      final IndividualClusterInternal value = clusterEntry.getValue();
      if (accountLevelClustersIds.contains(key)) {
        accountLevelClusters.putIfAbsent(key.split("\\.")[1], value);
      } else if (orgLevelClustersIds.contains(key)) {
        orgLevelClusters.putIfAbsent(key.split("\\.")[1], value);
      } else {
        projectLevelClusters.putIfAbsent(key, value);
      }
    }

    final Map<String, IndividualClusterInternal> projectLevelFilteredClusters =
        filterClustersFromGitopsService(getAccountId(ambiance), getOrgIdentifier(ambiance),
            getProjectIdentifier(ambiance), projectLevelClusters, logger);
    final Map<String, IndividualClusterInternal> orgLevelFilteredClusters = filterClustersFromGitopsService(
        getAccountId(ambiance), getOrgIdentifier(ambiance), "", orgLevelClusters, logger);
    final Map<String, IndividualClusterInternal> accountLevelFilteredClusters =
        filterClustersFromGitopsService(getAccountId(ambiance), "", "", accountLevelClusters, logger);

    return combine(projectLevelFilteredClusters, orgLevelFilteredClusters, accountLevelFilteredClusters);
  }

  private Map<String, IndividualClusterInternal> filterClustersFromGitopsService(String accountId, String orgId,
      String projectId, Map<String, IndividualClusterInternal> individualClusters, LogCallback logger) {
    if (isEmpty(individualClusters)) {
      return new HashMap<>();
    }
    saveExecutionLog("Processing clusters at scope " + ScopeLevel.of(accountId, orgId, projectId).toString(), logger);
    Map<String, Object> filter = ImmutableMap.of("identifier", ImmutableMap.of("$in", individualClusters.keySet()));
    try {
      final ClusterQuery query = ClusterQuery.builder()
                                     .accountId(accountId)
                                     .orgIdentifier(orgId)
                                     .projectIdentifier(projectId)
                                     .pageIndex(0)
                                     .pageSize(individualClusters.keySet().size())
                                     .filter(filter)
                                     .build();
      final Response<PageResponse<Cluster>> response =
          Failsafe.with(retryPolicyForGitopsClustersFetch)
              .get(() -> gitopsResourceClient.listClusters(query).execute());
      if (response.isSuccessful() && response.body() != null) {
        List<Cluster> content = CollectionUtils.emptyIfNull(response.body().getContent());

        logDataFromGitops(content, logger);

        content.forEach(c -> {
          if (individualClusters.containsKey(c.getIdentifier())) {
            individualClusters.get(c.getIdentifier()).setClusterName(c.name());
          }
        });

        logSkippedClusters(individualClusters.values()
                               .stream()
                               .filter(GitopsClustersStep::clusterNameNull)
                               .collect(Collectors.toList()),
            logger);
        individualClusters.values().removeIf(GitopsClustersStep::clusterNameNull);
        logFinalSelectedClusters(individualClusters, logger);
        return individualClusters;
      }
      throw new InvalidRequestException(format("Failed to fetch clusters from gitops. %s",
          response.errorBody() != null ? response.errorBody().string() : ""));
    } catch (Exception e) {
      log.error("Failed to fetch clusters from gitops", e);
      throw new InvalidRequestException("Failed to fetch clusters from gitops");
    }
  }

  private Map<String, IndividualClusterInternal> fetchClusterRefs(String envGroupRef, String envGroupName,
      Ambiance ambiance, Collection<EnvClusterRefs> envClusterRefs, LogCallback logger,
      Map<String, Map<String, Object>> envVarsMap) {
    final List<IndividualClusterInternal> clusterRefs =
        envClusterRefs.stream()
            .filter(not(EnvClusterRefs::isDeployToAll))
            .map(ec
                -> ec.getClusterRefs()
                       .stream()
                       .map(c
                           -> IndividualClusterInternal.builder()
                                  .envGroupRef(envGroupRef)
                                  .envGroupName(envGroupName)
                                  .envName(ec.getEnvName())
                                  .envRef(ec.getEnvRef())
                                  .clusterRef(c)
                                  .envVariables(envVarsMap.get(ec.getEnvRef()))
                                  .build())
                       .collect(Collectors.toList()))
            .flatMap(List::stream)
            .collect(Collectors.toList());

    // EnvRef -> EnvName
    final Map<String, EnvClusterRefs> envsWithAllClustersAsTarget =
        envClusterRefs.stream()
            .filter(EnvClusterRefs::isDeployToAll)
            .collect(Collectors.toMap(EnvClusterRefs::getEnvRef, Function.identity()));

    // Todo: Proper handling for large number of clusters
    if (isNotEmpty(envsWithAllClustersAsTarget)) {
      logIdentifiers(
          "Deploying to all GitOps clusters in environment(s)", envsWithAllClustersAsTarget.keySet(), logger);
      clusterRefs.addAll(clusterService
                             .listAcrossEnv(0, UNLIMITED_SIZE, getAccountId(ambiance), getOrgIdentifier(ambiance),
                                 getProjectIdentifier(ambiance), envsWithAllClustersAsTarget.keySet())
                             .stream()
                             .map(c
                                 -> IndividualClusterInternal.builder()
                                        .envGroupRef(envGroupRef)
                                        .envGroupName(envGroupName)
                                        .envRef(c.getEnvRef())
                                        .envName(envsWithAllClustersAsTarget.get(c.getEnvRef()) != null
                                                ? envsWithAllClustersAsTarget.get(c.getEnvRef()).getEnvName()
                                                : null)
                                        .clusterRef(c.getClusterRef())
                                        .envVariables(envVarsMap.get(c.getEnvRef()))
                                        .build())
                             .collect(Collectors.toSet()));
    }

    return clusterRefs.stream().collect(
        Collectors.toMap(IndividualClusterInternal::getClusterRef, Function.identity(), (k1, k2) -> k1));
  }

  protected GitopsClustersOutcome toOutcome(Map<String, IndividualClusterInternal> validatedClusters,
      Map<String, Object> svcVariables, Map<String, Map<String, Object>> envSvcOverrideVars) {
    final GitopsClustersOutcome outcome = new GitopsClustersOutcome(new ArrayList<>());

    for (String clusterId : validatedClusters.keySet()) {
      IndividualClusterInternal clusterInternal = validatedClusters.get(clusterId);

      Map<String, Object> mergedVars = new HashMap<>();

      // Merging of service variables, environment variables and service override config is happening here
      // Note, the order here is important.
      if (isNotEmpty(svcVariables)) {
        mergedVars.putAll(svcVariables);
      }
      if (isNotEmpty(clusterInternal.getEnvVariables())) {
        mergedVars.putAll(clusterInternal.getEnvVariables());
      }
      if (!envSvcOverrideVars.values().isEmpty() && envSvcOverrideVars.get(clusterInternal.getEnvRef()) != null) {
        mergedVars.putAll(envSvcOverrideVars.get(clusterInternal.getEnvRef()));
      }
      outcome.appendCluster(new Metadata(clusterInternal.getEnvGroupRef(), clusterInternal.getEnvGroupName()),
          new Metadata(clusterInternal.getEnvRef(), clusterInternal.getEnvName()),
          new Metadata(clusterInternal.getClusterRef(), clusterInternal.getClusterName()), mergedVars);
    }

    return outcome;
  }

  @Data
  @Builder
  static class IndividualClusterInternal {
    String envGroupRef;
    String envGroupName;
    String envRef;
    String envName;
    String clusterRef;
    String clusterName;
    Map<String, Object> envVariables;
  }

  private void resolveVariables(Ambiance ambiance, Map<String, Object> variables) {
    for (Object value : variables.values()) {
      if (value instanceof ParameterField) {
        ParameterField parameterFieldValue = (ParameterField) value;
        String resolvedValue = null;
        if (parameterFieldValue.isExpression()) {
          resolvedValue =
              engineExpressionService.renderExpression(ambiance, parameterFieldValue.getExpressionValue(), false);
        }
        if (resolvedValue != null) {
          if (!parameterFieldValue.isTypeString()) {
            parameterFieldValue.updateWithValue(Double.valueOf(resolvedValue));
          } else {
            parameterFieldValue.updateWithValue(resolvedValue);
          }
        }
      }
    }
  }

  private Map<String, IndividualClusterInternal> combine(Map<String, IndividualClusterInternal> m1,
      Map<String, IndividualClusterInternal> m2, Map<String, IndividualClusterInternal> m3) {
    Map<String, IndividualClusterInternal> combined = new HashMap<>();

    m1.keySet().forEach(k -> combined.putIfAbsent(k, m1.get(k)));
    m2.keySet().forEach(k -> combined.putIfAbsent(k, m2.get(k)));
    m3.keySet().forEach(k -> combined.putIfAbsent(k, m3.get(k)));

    return combined;
  }

  private void logDataFromGitops(List<Cluster> content, LogCallback logger) {
    saveExecutionLog(format("Following %d cluster(s) are present in Harness Gitops", content.size()), logger);
    logIdentifiers("Identifiers:", content.stream().map(Cluster::getIdentifier).collect(Collectors.toSet()), logger);
  }

  private void logFinalSelectedClusters(Map<String, IndividualClusterInternal> individualClusters, LogCallback logger) {
    saveExecutionLog(format("Following %d cluster(s) are selected after filtering", individualClusters.size()), logger);
    logIdentifiers("Identifiers:", individualClusters.keySet(), logger);
  }

  private void logEnvironments(Collection<EnvClusterRefs> envClusterRefs, LogCallback logger) {
    logIdentifiers(
        "Environment(s):", envClusterRefs.stream().map(EnvClusterRefs::getEnvRef).collect(Collectors.toSet()), logger);
  }

  private void logSkippedClusters(List<IndividualClusterInternal> clusterInternals, LogCallback logger) {
    if (isEmpty(clusterInternals)) {
      return;
    }

    saveExecutionLog(
        "Following clusters were skipped either because clusters were not linked to the environment or not present in harness GitOps",
        logger);
    Map<String, List<IndividualClusterInternal>> groupedClusters =
        clusterInternals.stream().collect(Collectors.groupingBy(IndividualClusterInternal::getEnvRef));
    groupedClusters.forEach(
        (key, value)
            -> logIdentifiers("Environment: " + key,
                value.stream().map(IndividualClusterInternal::getClusterRef).collect(Collectors.toList()), logger));
  }

  private void logIdentifiers(String logPrefix, Collection<String> strings, LogCallback logger) {
    int maxSize = STRINGS_LOGGING_LIMIT;
    saveExecutionLog(logPrefix + " " + strings.stream().limit(maxSize).collect(Collectors.joining(",", "{", "}")) + " "
            + (strings.size() > maxSize ? "..." : "\n\n"),
        logger);
  }

  private void saveExecutionLog(String log, LogCallback logger) {
    logger.saveExecutionLog(log);
  }

  private static boolean clusterNameNull(IndividualClusterInternal c) {
    return c.getClusterName() == null;
  }
}
