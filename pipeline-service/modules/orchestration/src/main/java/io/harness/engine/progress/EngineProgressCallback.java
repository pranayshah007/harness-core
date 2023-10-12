/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.progress;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.progress.publisher.ProgressEventPublisher;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ProgressData;
import io.harness.waiter.ProgressCallback;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Transient;

@Value
@Builder
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class EngineProgressCallback implements ProgressCallback {
  @Inject @Transient NodeExecutionService nodeExecutionService;
  @Inject @Transient KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject @Transient ProgressEventPublisher progressEventPublisher;

  @Deprecated Ambiance ambiance;
  String nodeExecutionId;

  @Override
  public void notify(String correlationId, ProgressData progressData) {
    if (!(progressData instanceof BinaryResponseData)) {
      throw new UnsupportedOperationException("Progress updates are not supported for raw non Binary Response Data");
    }

    // This is the new way of managing progress updates below code is only to maintain backward compatibility
    BinaryResponseData binaryResponseData = (BinaryResponseData) progressData;
    progressEventPublisher.publishEvent(getNodeExecutionId(), binaryResponseData);

    try {
      // This code is only to maintain backward compatibility
      ProgressData data = (ProgressData) (binaryResponseData.isUsingKryoWithoutReference()
              ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
              : kryoSerializer.asInflatedObject(binaryResponseData.getData()));
      if (data instanceof UnitProgressData) {
        nodeExecutionService.updateV2(getNodeExecutionId(),
            ops -> ops.set(NodeExecutionKeys.unitProgresses, ((UnitProgressData) data).getUnitProgresses()));
      }
      log.info("Node Execution updated for progress data");
    } catch (Exception ex) {
      log.error("Failed to deserialize progress data via kryo");
    }
  }

  private String getNodeExecutionId() {
    return nodeExecutionId == null ? AmbianceUtils.obtainCurrentRuntimeId(ambiance) : nodeExecutionId;
  }
}
