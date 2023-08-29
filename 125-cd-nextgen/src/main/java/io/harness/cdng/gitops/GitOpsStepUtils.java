/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import static io.harness.cdng.gitops.constants.GitopsConstants.GITOPS_SWEEPING_OUTPUT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.ScopeLevel;
import io.harness.cdng.gitops.steps.GitopsClustersOutcome;
import io.harness.cdng.gitops.syncstep.EnvironmentClusterListing;
import io.harness.cdng.gitops.syncstep.EnvironmentClusterListing.EnvironmentClusterListingBuilder;
import io.harness.cdng.gitops.syncstep.SyncStepHelper;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.encryption.Scope;
import io.harness.gitops.models.ApplicationResource;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.utils.RetryUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
public class GitOpsStepUtils {
  public static final String SERVICE = "service";
  public static final String LOG_SUFFIX = "Execute";

  public static RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return RetryUtils.getRetryPolicy(failedAttemptMessage, failureMessage, Collections.singletonList(IOException.class),
        Duration.ofMillis(SyncStepHelper.NETWORK_CALL_RETRY_SLEEP_DURATION_MILLIS),
        SyncStepHelper.NETWORK_CALL_MAX_RETRY_ATTEMPTS, log);
  }

  public static Set<String> getServiceIdsInPipelineExecution(
      Ambiance ambiance, ExecutionSweepingOutputService executionSweepingOutputResolver) {
    OptionalSweepingOutput optionalSweepingOutputForService =
        executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(SERVICE));
    return optionalSweepingOutputForService != null && optionalSweepingOutputForService.isFound()
        ? Stream.of(((ServiceStepOutcome) optionalSweepingOutputForService.getOutput()).getIdentifier())
              .collect(Collectors.toSet())
        : new HashSet<>();
  }

  public static EnvironmentClusterListing getEnvAndClusterIdsInPipelineExecution(
      Ambiance ambiance, ExecutionSweepingOutputService executionSweepingOutputResolver) {
    OptionalSweepingOutput optionalSweepingOutputForEnv = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(GITOPS_SWEEPING_OUTPUT));

    EnvironmentClusterListingBuilder environmentClusterListing = EnvironmentClusterListing.builder();
    if (optionalSweepingOutputForEnv != null && optionalSweepingOutputForEnv.isFound()) {
      GitopsClustersOutcome gitopsClustersOutcome = (GitopsClustersOutcome) optionalSweepingOutputForEnv.getOutput();
      // ideally, the gitops clusters step should fail when no cluster is present, this is an extra check
      if (gitopsClustersOutcome == null || isEmpty(gitopsClustersOutcome.getClustersData())) {
        log.debug("No GitOps Clusters found");
      } else {
        environmentClusterListing.clusterIds(getScopedClusterIdsInPipelineExecution(gitopsClustersOutcome))
            .environmentIds(getEnvIdsInPipelineExecution(gitopsClustersOutcome));
      }
    }
    return environmentClusterListing.build();
  }

  static Map<String, Set<String>> getScopedClusterIdsInPipelineExecution(GitopsClustersOutcome gitopsClustersOutcome) {
    return gitopsClustersOutcome.getClustersData().stream().collect(
        Collectors.groupingBy(GitopsClustersOutcome.ClusterData::getAgentId, Collectors.mapping(cluster -> {
          String scope = cluster.getScope().toLowerCase();
          String clusterId = cluster.getClusterId();
          if (ScopeLevel.PROJECT.toString().equalsIgnoreCase(scope)) {
            return clusterId;
          } else if (ScopeLevel.ORGANIZATION.toString().equalsIgnoreCase(scope)) {
            return Scope.ORG.getYamlRepresentation() + "." + clusterId;
          } else {
            return scope + "." + clusterId;
          }
        }, Collectors.toSet())));
  }

  private static Set<String> getEnvIdsInPipelineExecution(GitopsClustersOutcome outcome) {
    return outcome.getClustersData()
        .stream()
        .map(GitopsClustersOutcome.ClusterData::getEnvId)
        .collect(Collectors.toSet());
  }

  public static boolean isApplicationCorrespondsToClusterInExecution(
      ApplicationResource fetchedApplication, Map<String, Set<String>> clusterIdsInPipelineExecution) {
    String agentIdentifier = fetchedApplication.getAgentIdentifier();
    Set<String> clustersForAgent = clusterIdsInPipelineExecution.get(agentIdentifier);
    return clustersForAgent != null && clustersForAgent.contains(fetchedApplication.getClusterIdentifier());
  }

  public static void logExecutionInfo(String logMessage, LogCallback logger) {
    log.info(logMessage);
    saveExecutionLog(logMessage, logger, LogLevel.INFO);
  }

  public static void logExecutionError(String logMessage, LogCallback logger) {
    log.error(logMessage);
    saveExecutionLog(logMessage, logger, LogLevel.ERROR);
  }

  public static void saveExecutionLog(String log, LogCallback logger, LogLevel logLevel) {
    logger.saveExecutionLog(log, logLevel);
  }

  public static void closeLogStream(Ambiance ambiance, LogStreamingStepClientFactory logStreamingStepClientFactory) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(LOG_SUFFIX);
  }
}
