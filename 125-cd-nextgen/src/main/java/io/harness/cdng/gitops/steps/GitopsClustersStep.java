/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_ENV_OUTCOME;
import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SWEEPING_OUTPUT;
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
import io.harness.cdng.environment.helper.EnvironmentInfraFilterHelper;
import io.harness.cdng.environment.helper.EnvironmentStepsUtils;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.CollectionUtils;
import io.harness.encryption.Scope;
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
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
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
  private static final int UNLIMITED_SIZE = 100000;
  private static final int STRINGS_LOGGING_LIMIT = 500;

  @Inject private ClusterService clusterService;
  @Inject private GitopsResourceClient gitopsResourceClient;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private EnvironmentInfraFilterHelper environmentInfraFilterHelper;
  @Inject private OutcomeService outcomeService;

  private static final RetryPolicy<Object> retryPolicyForGitopsClustersFetch = RetryUtils.getRetryPolicy(
      "Error getting clusters from Harness GitOps...retrying", "Failed to fetch clusters from Harness GitOps",
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

    performFiltering(stepParameters, ambiance);

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
      final Map<String, List<IndividualClusterInternal>> validatedClusters =
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

  private void performFiltering(ClusterStepParameters stepParameters, Ambiance ambiance) {
    if (stepParameters.getEnvironmentsYaml() == null && stepParameters.getEnvironmentGroupYaml() == null) {
      return;
    }
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    List<NGTag> serviceTags = EnvironmentInfraFilterHelper.getNGTags(serviceOutcome.getTags());

    List<EnvClusterRefs> envClusterRefs;
    if (stepParameters.getEnvironmentGroupYaml() != null) {
      envClusterRefs = environmentInfraFilterHelper.filterEnvGroupAndClusters(stepParameters.getEnvironmentGroupYaml(),
          serviceTags, getAccountId(ambiance), getOrgIdentifier(ambiance), getProjectIdentifier(ambiance));
      stepParameters.setEnvClusterRefs(envClusterRefs);
    }
    if (stepParameters.getEnvironmentsYaml() != null) {
      envClusterRefs = environmentInfraFilterHelper.filterEnvsAndClusters(stepParameters.getEnvironmentsYaml(),
          serviceTags, getAccountId(ambiance), getOrgIdentifier(ambiance), getProjectIdentifier(ambiance));
      stepParameters.setEnvClusterRefs(envClusterRefs);
    }
  }

  @Override
  public Class<ClusterStepParameters> getStepParametersClass() {
    return ClusterStepParameters.class;
  }

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, null);
  }

  private Map<String, List<IndividualClusterInternal>> validatedClusters(Ambiance ambiance,
      ClusterStepParameters params, LogCallback logger, Map<String, Map<String, Object>> envVarsMap) {
    final Collection<EnvClusterRefs> envClusterRefs;
    if (params.getEnvGroupRef() != null) {
      saveExecutionLog(format("Deploying to GitOps clusters in environment group %s", params.getEnvGroupRef()), logger);
    }

    envClusterRefs = params.getEnvClusterRefs();

    if (isEmpty(envClusterRefs)) {
      throw new InvalidRequestException("No GitOps Cluster is selected with the current environment configuration");
    }

    updateEnvRefsWithEnvGroupScope(envClusterRefs, params.getEnvGroupRef());

    logEnvironments(envClusterRefs, logger);

    // clusterId -> IndividualClusterInternal list, 1 cluster can be referenced by multiple environments
    final Map<String, List<IndividualClusterInternal>> individualClusters = fetchClusterRefs(
        params.getEnvGroupRef(), params.getEnvGroupName(), ambiance, envClusterRefs, logger, envVarsMap);

    if (isEmpty(individualClusters)) {
      saveExecutionLog("No GitOps cluster is selected", logger);
      throw new InvalidRequestException("No GitOps Cluster is selected with the current environment configuration");
    }

    return filterClustersFromGitopsService(ambiance, individualClusters, logger);
  }

  private void updateEnvRefsWithEnvGroupScope(Collection<EnvClusterRefs> envClusterRefs, String envGroupRef) {
    if (org.apache.commons.lang.StringUtils.isEmpty(envGroupRef)) {
      return;
    }
    Scope envGroupScope = EnvironmentStepsUtils.getScopeForRef(envGroupRef);
    for (EnvClusterRefs envClusterRef : envClusterRefs) {
      String envRefWithScope = EnvironmentStepsUtils.getEnvironmentRef(envClusterRef.getEnvRef(), envGroupScope);
      envClusterRef.setEnvRef(envRefWithScope);
    }
  }

  @NotNull
  private Map<String, List<IndividualClusterInternal>> filterClustersFromGitopsService(
      Ambiance ambiance, Map<String, List<IndividualClusterInternal>> individualClusters, LogCallback logger) {
    final Set<String> accountLevelClustersIds = individualClusters.keySet()
                                                    .stream()
                                                    .filter(ref -> StringUtils.beginsWithIgnoreCase(ref, "account."))
                                                    .collect(Collectors.toSet());
    final Set<String> orgLevelClustersIds = individualClusters.keySet()
                                                .stream()
                                                .filter(ref -> StringUtils.beginsWithIgnoreCase(ref, "organization."))
                                                .collect(Collectors.toSet());

    final Map<String, List<IndividualClusterInternal>> accountLevelClusters = new HashMap<>();
    final Map<String, List<IndividualClusterInternal>> orgLevelClusters = new HashMap<>();
    final Map<String, List<IndividualClusterInternal>> projectLevelClusters = new HashMap<>();

    for (Map.Entry<String, List<IndividualClusterInternal>> clusterEntry : individualClusters.entrySet()) {
      final String clusterId = clusterEntry.getKey();
      final List<IndividualClusterInternal> envsForCluster = clusterEntry.getValue();
      for (IndividualClusterInternal currentEnv : envsForCluster) {
        if (accountLevelClustersIds.contains(clusterId)) {
          putEnvDetailsForCluster(clusterId.split("\\.")[1], accountLevelClusters, currentEnv);
        } else if (orgLevelClustersIds.contains(clusterId)) {
          putEnvDetailsForCluster(clusterId.split("\\.")[1], orgLevelClusters, currentEnv);
        } else {
          putEnvDetailsForCluster(clusterId, projectLevelClusters, currentEnv);
        }
      }
    }

    final Map<String, List<IndividualClusterInternal>> projectLevelFilteredClusters =
        filterClustersFromGitopsService(getAccountId(ambiance), getOrgIdentifier(ambiance),
            getProjectIdentifier(ambiance), projectLevelClusters, logger);
    final Map<String, List<IndividualClusterInternal>> orgLevelFilteredClusters = filterClustersFromGitopsService(
        getAccountId(ambiance), getOrgIdentifier(ambiance), "", orgLevelClusters, logger);
    final Map<String, List<IndividualClusterInternal>> accountLevelFilteredClusters =
        filterClustersFromGitopsService(getAccountId(ambiance), "", "", accountLevelClusters, logger);

    return combine(projectLevelFilteredClusters, orgLevelFilteredClusters, accountLevelFilteredClusters);
  }

  private void putEnvDetailsForCluster(
      String clusterId, Map<String, List<IndividualClusterInternal>> clusters, IndividualClusterInternal currentEnv) {
    List<IndividualClusterInternal> existingEnvs = clusters.getOrDefault(clusterId, new ArrayList<>());
    existingEnvs.add(currentEnv);
    clusters.put(clusterId, existingEnvs);
  }

  private Map<String, List<IndividualClusterInternal>> filterClustersFromGitopsService(String accountId, String orgId,
      String projectId, Map<String, List<IndividualClusterInternal>> individualClusters, LogCallback logger) {
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
            individualClusters.get(c.getIdentifier()).forEach(envCluster -> {
              envCluster.setClusterName(c.name());
              envCluster.setAgentId(c.getAgentIdentifier());
            });
          }
        });

        logSkippedClusters(individualClusters.values()
                               .stream()
                               .flatMap(list -> list.stream())
                               .filter(GitopsClustersStep::clusterNameNull)
                               .collect(Collectors.toList()),
            logger);
        individualClusters.values().removeIf(value -> clusterNameNull(value.get(0)));
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

  private Map<String, List<IndividualClusterInternal>> fetchClusterRefs(String envGroupRef, String envGroupName,
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
                                  .envType(ec.getEnvType())
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
                                        .envType(envsWithAllClustersAsTarget.get(c.getEnvRef()) != null
                                                ? envsWithAllClustersAsTarget.get(c.getEnvRef()).getEnvType()
                                                : null)
                                        .clusterRef(c.getClusterRef())
                                        .envVariables(envVarsMap.get(c.getEnvRef()))
                                        .build())
                             .collect(Collectors.toSet()));
    }

    return clusterRefs.stream().collect(Collectors.groupingBy(IndividualClusterInternal::getClusterRef));
  }

  protected GitopsClustersOutcome toOutcome(Map<String, List<IndividualClusterInternal>> validatedClusters,
      Map<String, Object> svcVariables, Map<String, Map<String, Object>> envSvcOverrideVars) {
    final GitopsClustersOutcome outcome = new GitopsClustersOutcome(new ArrayList<>());

    validatedClusters.values().stream().flatMap(list -> list.stream()).forEach(clusterInternal -> {
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
          new Metadata(clusterInternal.getEnvRef(), clusterInternal.getEnvName()), clusterInternal.getEnvType(),
          new Metadata(clusterInternal.getClusterRef(), clusterInternal.getClusterName()), mergedVars,
          clusterInternal.getAgentId());
    });

    return outcome;
  }

  @Data
  @Builder
  static class IndividualClusterInternal {
    String envGroupRef;
    String envGroupName;
    String envRef;
    String envName;

    String envType;
    String clusterRef;
    String clusterName;
    String agentId;
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

  /* TODO what needs to be done if account and project level clusters have the same id, should we combine the results?
   Currently this scenario cannot be tested because of UI issue https://harness.atlassian.net/browse/CDS-49929. */
  private Map<String, List<IndividualClusterInternal>> combine(Map<String, List<IndividualClusterInternal>> m1,
      Map<String, List<IndividualClusterInternal>> m2, Map<String, List<IndividualClusterInternal>> m3) {
    Map<String, List<IndividualClusterInternal>> combined = new HashMap<>();

    m1.keySet().forEach(k -> combined.putIfAbsent(k, m1.get(k)));
    m2.keySet().forEach(k -> combined.putIfAbsent(k, m2.get(k)));
    m3.keySet().forEach(k -> combined.putIfAbsent(k, m3.get(k)));

    return combined;
  }

  private void logDataFromGitops(List<Cluster> content, LogCallback logger) {
    saveExecutionLog(format("Following %d cluster(s) are present in Harness Gitops", content.size()), logger);
    logIdentifiers("Identifiers:", content.stream().map(Cluster::getIdentifier).collect(Collectors.toSet()), logger);
  }

  private void logFinalSelectedClusters(
      Map<String, List<IndividualClusterInternal>> individualClusters, LogCallback logger) {
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
