/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import io.harness.engine.OrchestrationEngine;
import io.harness.engine.pms.execution.SdkResponseProcessorFactory;
import io.harness.event.handlers.SdkResponseProcessor;
import io.harness.execution.NodeExecution;
import io.harness.execution.PmsNodeExecutionMetadata;
import io.harness.logging.AutoLogContext;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractNodeExecutionStrategy<P extends Node, M extends PmsNodeExecutionMetadata>
    implements NodeExecutionStrategy<P, NodeExecution, M> {
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private SdkResponseProcessorFactory sdkResponseProcessorFactory;
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  @Override
  public NodeExecution runNode(@NonNull Ambiance ambiance, @NonNull P node, M metadata) {
    return runNode(ambiance, node, metadata, InitiateMode.CREATE_AND_START);
  }

  @Override
  public NodeExecution runNode(@NonNull Ambiance ambiance, @NonNull P node, M metadata, InitiateMode initiateMode) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String parentId = AmbianceUtils.obtainParentRuntimeId(ambiance);
      String notifyId = parentId == null ? null : AmbianceUtils.obtainCurrentRuntimeId(ambiance);
      if (initiateMode == InitiateMode.CREATE) {
        return createNodeExecution(ambiance, node, metadata, notifyId, parentId, null);
      }
      return createAndRunNodeExecution(ambiance, node, metadata, notifyId, parentId, null);
    } catch (Exception ex) {
      log.error("Exception happened while running Node", ex);
      handleError(ambiance, ex);
      return null;
    }
  }

  @Override
  // PrevExecution doesn't contain fields for nextId and endTs, if needed handle for projection in NextStepHandler
  public NodeExecution runNextNode(
      @NonNull Ambiance ambiance, @NonNull P node, NodeExecution prevExecution, M metadata) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      return createAndRunNodeExecution(
          ambiance, node, metadata, prevExecution.getNotifyId(), prevExecution.getParentId(), prevExecution.getUuid());
    } catch (Exception ex) {
      log.error("Exception happened while running next Node", ex);
      handleError(ambiance, ex);
      return null;
    }
  }

  @VisibleForTesting
  NodeExecution createAndRunNodeExecution(
      Ambiance ambiance, P node, M metadata, String notifyId, String parentId, String previousId) {
    NodeExecution savedExecution = createNodeExecution(ambiance, node, metadata, notifyId, parentId, previousId);
    executorService.submit(() -> orchestrationEngine.startNodeExecution(savedExecution.getAmbiance()));
    return savedExecution;
  }

  @Override
  public void handleSdkResponseEvent(SdkResponseEventProto event) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(event.getAmbiance(), event.getSdkResponseEventType())) {
      log.info("Event for SdkResponseEvent received for eventType {}", event.getSdkResponseEventType());
      SdkResponseProcessor handler = sdkResponseProcessorFactory.getHandler(event.getSdkResponseEventType());
      handler.handleEvent(event);
      log.info("Event for SdkResponseEvent for event type {} completed successfully", event.getSdkResponseEventType());
    } catch (Exception ex) {
      handleError(event.getAmbiance(), ex);
    }
  }

  public abstract NodeExecution createNodeExecution(
      Ambiance ambiance, P node, M metadata, String notifyId, String parentId, String previousId);
}
