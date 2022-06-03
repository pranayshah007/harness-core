package io.harness.concurrency;

import io.harness.OrchestrationPublisherName;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.InitiateNodeHelper;
import io.harness.execution.NodeExecution;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.steps.io.ResponseDataMapper;
import io.harness.repositories.ConcurrentChildrenInstanceRepository;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutionStartCallback implements OldNotifyCallback {
  private static final String EXECUTION_START_PREFIX = "EXECUTION_START_CALLBACK_%s";
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;

  @Inject OrchestrationEngine engine;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject WaitNotifyEngine waitNotifyEngine;
  @Inject ConcurrentChildrenInstanceRepository concurrentChildrenInstanceRepository;
  @Inject PersistentLocker persistentLocker;
  @Inject ResponseDataMapper responseDataMapper;

  long maxConcurrency;
  String parentNodeExecutionId;
  Ambiance ambiance;

  @Override
  public void notify(Map<String, ResponseData> response) {
    String lockName = String.format(EXECUTION_START_PREFIX, parentNodeExecutionId);
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLock(lockName, Duration.ofSeconds(10), Duration.ofSeconds(30))) {
      Optional<ConcurrentChildInstance> concurrentChildInstance =
          concurrentChildrenInstanceRepository.findByParentNodeExecutionId(parentNodeExecutionId);
      if (!concurrentChildInstance.isPresent()) {
        // Todo: Error out execution
        return;
      }
      ConcurrentChildInstance childInstance = concurrentChildInstance.get();
      if (childInstance.getCursor() == childInstance.getChildrenNodeExecutionIds().size()) {
        return;
      }
      int cursor = childInstance.getCursor();
      String nodeExecutionToStart = childInstance.getChildrenNodeExecutionIds().get(cursor);
      NodeExecution nodeExecution =
          nodeExecutionService.getWithFieldsIncluded(nodeExecutionToStart, NodeProjectionUtils.withAmbianceAndStatus);
      engine.startNodeExecution(nodeExecution.getAmbiance());
      childInstance.setCursor(cursor + 1);
      childInstance.getCombinedResponse().putAll(responseDataMapper.toResponseDataProto(response));
      concurrentChildrenInstanceRepository.save(childInstance);
      ExecutionStartCallback executionStartCallback = ExecutionStartCallback.builder()
                                                          .parentNodeExecutionId(parentNodeExecutionId)
                                                          .ambiance(ambiance)
                                                          .maxConcurrency(maxConcurrency)
                                                          .build();
      waitNotifyEngine.waitForAllOn(publisherName, executionStartCallback, nodeExecution.getUuid());
    }
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {}
}
