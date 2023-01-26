/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static lombok.AccessLevel.PACKAGE;

import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.delegate.beans.ci.CIExecuteStepTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8ExecuteStepTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.task.citasks.CIExecuteStepTaskHandler;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.product.ci.engine.proto.ExecuteStepRequest;
import io.harness.product.ci.engine.proto.LiteEngineGrpc;
import io.harness.steps.plugin.ContainerCommandUnitConstants;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.internal.GrpcUtil;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
public class CIK8ExecuteStepTaskHandler implements CIExecuteStepTaskHandler {
  @NotNull private Type type = Type.K8;
  public static final String DELEGATE_NAMESPACE = "DELEGATE_NAMESPACE";
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;
  @Inject
  @Getter(value = PACKAGE, onMethod = @__({ @VisibleForTesting }))
  private DelegateConfiguration delegateConfiguration;
  private NGDelegateLogCallback ngDelegateLogCallback;
  @Override
  public Type getType() {
    return type;
  }

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(CIExecuteStepTaskParams ciExecuteStepTaskParams, String taskId) {
    CIK8ExecuteStepTaskParams cik8ExecuteStepTaskParams = (CIK8ExecuteStepTaskParams) ciExecuteStepTaskParams;
    CommandUnitsProgress commandUnitsProgress = cik8ExecuteStepTaskParams.getCommandUnitsProgress();
    log.info("commandUnitsProgress: {}", commandUnitsProgress);
    ILogStreamingTaskClient logStreamingTaskClient = cik8ExecuteStepTaskParams.getLogStreamingTaskClient();
    logCommandUnit(logStreamingTaskClient, "Starting executing container step", LogLevel.INFO,
        CommandExecutionStatus.RUNNING, commandUnitsProgress);

    ExecuteStepRequest executeStepRequest;
    try {
      executeStepRequest = ExecuteStepRequest.parseFrom(cik8ExecuteStepTaskParams.getSerializedStep());
      log.info("parsed call for execute step with id {} is successful ", executeStepRequest.getStep().getId());
    } catch (InvalidProtocolBufferException e) {
      log.error("Failed to parse serialized step with err: {}", e.getMessage());
      logCommandUnit(logStreamingTaskClient, "Failed executing container step", LogLevel.ERROR,
          CommandExecutionStatus.FAILURE, commandUnitsProgress);
      return K8sTaskExecutionResponse.builder()
          .errorMessage(e.getMessage())
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .build();
    }
    try {
      String namespacedDelegateSvcEndpoint =
          getNamespacedDelegateSvcEndpoint(cik8ExecuteStepTaskParams.getDelegateSvcEndpoint());
      log.info("Delegate service endpoint for step {}: {}", executeStepRequest.getStep().getId(),
          namespacedDelegateSvcEndpoint);
      if (isNotEmpty(namespacedDelegateSvcEndpoint)) {
        executeStepRequest =
            executeStepRequest.toBuilder().setDelegateSvcEndpoint(namespacedDelegateSvcEndpoint).build();
      }

      String accountKey = delegateConfiguration.getDelegateToken();
      String managerUrl = delegateConfiguration.getManagerUrl();
      String delegateID = DelegateAgentCommonVariables.getDelegateId();
      if (isNotEmpty(managerUrl)) {
        managerUrl = managerUrl.replace("/api/", "");
        executeStepRequest = executeStepRequest.toBuilder().setManagerSvcEndpoint(managerUrl).build();
      }
      if (isNotEmpty(accountKey)) {
        executeStepRequest = executeStepRequest.toBuilder().setAccountKey(accountKey).build();
      }
      if (isNotEmpty(delegateID)) {
        executeStepRequest = executeStepRequest.toBuilder().setDelegateId(delegateID).build();
      }

      final ExecuteStepRequest finalExecuteStepRequest = executeStepRequest;
      String target = format("%s:%d", cik8ExecuteStepTaskParams.getIp(), cik8ExecuteStepTaskParams.getPort());
      ManagedChannelBuilder managedChannelBuilder = ManagedChannelBuilder.forTarget(target).usePlaintext();
      if (!cik8ExecuteStepTaskParams.isLocal()) {
        managedChannelBuilder.proxyDetector(GrpcUtil.NOOP_PROXY_DETECTOR);
      }
      ManagedChannel channel = managedChannelBuilder.build();
      try {
        try {
          RetryPolicy<Object> retryPolicy =
              getRetryPolicy(format("[Retrying failed call to send execution call to pod %s: {}",
                                 ((CIK8ExecuteStepTaskParams) ciExecuteStepTaskParams).getIp()),
                  format("Failed to send execution to pod %s after retrying {} times",
                      ((CIK8ExecuteStepTaskParams) ciExecuteStepTaskParams).getIp()));

          return Failsafe.with(retryPolicy).get(() -> {
            LiteEngineGrpc.LiteEngineBlockingStub liteEngineBlockingStub = LiteEngineGrpc.newBlockingStub(channel);
            liteEngineBlockingStub.withDeadlineAfter(30, TimeUnit.SECONDS).executeStep(finalExecuteStepRequest);
            logCommandUnit(logStreamingTaskClient, "Completed executing container step", LogLevel.INFO,
                CommandExecutionStatus.SUCCESS, commandUnitsProgress);
            log.info("commandUnitsProgress: {}", commandUnitsProgress);
            return K8sTaskExecutionResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .commandUnitsProgress(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
                .build();
          });

        } finally {
          // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
          // resources the channel should be shut down when it will no longer be used. If it may be used
          // again leave it running.
          channel.shutdownNow();
        }
      } catch (Exception e) {
        log.error("Failed to execute step on lite engine target {} with err: {}", target, e);
        logCommandUnit(logStreamingTaskClient, "Failed executing container step", LogLevel.ERROR,
            CommandExecutionStatus.FAILURE, commandUnitsProgress);
        log.info("commandUnitsProgress: {}", commandUnitsProgress);

        return K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .commandUnitsProgress(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .errorMessage(e.getMessage())
            .build();
      }
    } catch (Exception e) {
      log.error("some error in ci step", e);
    }
    return null;
  }

  private String getNamespacedDelegateSvcEndpoint(String delegateSvcEndpoint) {
    String namespace = System.getenv(DELEGATE_NAMESPACE);
    if (isEmpty(namespace)) {
      return delegateSvcEndpoint;
    }

    String[] svcArr = delegateSvcEndpoint.split(":");
    if (svcArr.length != 2) {
      throw new InvalidArgumentsException(
          format("Delegate service endpoint provided is invalid: %s", delegateSvcEndpoint));
    }

    return format("%s.%s.svc.cluster.local:%s", svcArr[0], namespace, svcArr[1]);
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  private void logCommandUnit(ILogStreamingTaskClient logStreamingTaskClient, String message, LogLevel logLevel,
      CommandExecutionStatus status, CommandUnitsProgress commandUnitsProgress) {
    if (commandUnitsProgress == null || logStreamingTaskClient == null) {
      return;
    }
    if (ngDelegateLogCallback == null) {
      ngDelegateLogCallback = new NGDelegateLogCallback(
          logStreamingTaskClient, ContainerCommandUnitConstants.ContainerStep, true, commandUnitsProgress);
    }
    ngDelegateLogCallback.saveExecutionLog(message, logLevel, status);
  }
}
