/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import static io.harness.steps.StepUtils.buildAbstractions;
import static io.harness.steps.container.constants.ContainerStepExecutionConstants.CLEANUP_DETAILS;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.encryption.Scope;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.StepUtils;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.plugin.infrastructure.ContainerCleanupDetails;
import io.harness.steps.plugin.infrastructure.ContainerInfraYamlSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.utils.PmsFeatureFlagService;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepCleanupHelper {
  @Inject DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ConnectorUtils connectorUtils;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject OutcomeService outcomeService;
  @Inject LogStreamingStepClientFactory logStreamingClient;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

  private final int MAX_ATTEMPTS = 3;

  public void sendCleanupRequest(Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    try {
      RetryPolicy<Object> retryPolicy = getRetryPolicy(format("[Retrying failed call to clean pod attempt: {}"),
          format("Failed to clean pod after retrying {} times"));

      Failsafe.with(retryPolicy).run(() -> {
        Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
        closeLogStream(ambiance);
        ContainerCleanupDetails podCleanupDetails;
        OptionalSweepingOutput optionalCleanupSweepingOutput = executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                io.harness.steps.container.constants.ContainerStepExecutionConstants.CLEANUP_DETAILS));
        if (!optionalCleanupSweepingOutput.isFound()) {
          return;
        } else {
          podCleanupDetails = (ContainerCleanupDetails) executionSweepingOutputService.resolve(
              ambiance, RefObjectUtils.getSweepingOutputRefObject(CLEANUP_DETAILS));
        }

        if (podCleanupDetails == null) {
          return;
        }
        CICleanupTaskParams ciCleanupTaskParams = buildK8CleanupParameters(ambiance, podCleanupDetails);

        log.info("Received event to clean planExecutionId {}, level Id {}", ambiance.getPlanExecutionId(),
            level.getIdentifier());

        DelegateTaskRequest delegateTaskRequest =
            getDelegateCleanupTaskRequest(ambiance, ciCleanupTaskParams, accountId);

        String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
        log.info("Submitted cleanup request with taskId {} for planExecutionId {}, stage {}", taskId,
            ambiance.getPlanExecutionId(), level.getIdentifier());
      });
    } catch (Exception ex) {
      log.error("Failed to send cleanup call for plan {}", ambiance.getPlanExecutionId(), ex);
    }
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withBackoff(5, 60, ChronoUnit.SECONDS)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  public CIK8CleanupTaskParams buildK8CleanupParameters(Ambiance ambiance, ContainerCleanupDetails podDetails) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ContainerInfraYamlSpec containerInfraYamlSpec = ((ContainerK8sInfra) podDetails.getInfrastructure()).getSpec();
    String clusterConnectorRef = containerInfraYamlSpec.getConnectorRef().getValue();
    String namespace = (String) containerInfraYamlSpec.getNamespace().fetchFinalValue();
    final List<String> podNames = new ArrayList<>();
    podNames.add(podDetails.getPodName());

    boolean useSocketCapability = pmsFeatureFlagService.isEnabled(
        ngAccess.getAccountIdentifier(), FeatureName.CDS_K8S_SOCKET_CAPABILITY_CHECK_NG);

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, clusterConnectorRef);

    return CIK8CleanupTaskParams.builder()
        .k8sConnector(connectorDetails)
        .cleanupContainerNames(podDetails.getCleanUpContainerNames())
        .namespace(namespace)
        .podNameList(podNames)
        .serviceNameList(new ArrayList<>())
        .useSocketCapability(useSocketCapability)
        .build();
  }

  private DelegateTaskRequest getDelegateCleanupTaskRequest(
      Ambiance ambiance, CICleanupTaskParams ciCleanupTaskParams, String accountId) {
    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
    String taskType = TaskType.CONTAINER_CLEANUP.name();
    SerializationFormat serializationFormat = SerializationFormat.KRYO;

    return DelegateTaskRequest.builder()
        .accountId(accountId)
        .executeOnHarnessHostedDelegates(false)
        .eligibleToExecuteDelegateIds(new ArrayList<>())
        .taskSetupAbstractions(abstractions)
        .executionTimeout(java.time.Duration.ofSeconds(900))
        .taskType(taskType)
        .serializationFormat(serializationFormat)
        .taskParameters(ciCleanupTaskParams)
        .taskDescription("Cleanup pod task")
        .build();
  }

  public void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingClient.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeAllOpenStreamsWithPrefix(StepUtils.generateLogKeys(ambiance, emptyList()).get(0));
  }
}
