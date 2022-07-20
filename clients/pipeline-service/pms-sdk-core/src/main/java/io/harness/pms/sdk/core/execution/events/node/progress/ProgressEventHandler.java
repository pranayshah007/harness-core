/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.events.node.progress;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.NodeExecutionEventType;
import io.harness.pms.contracts.progress.ProgressEvent;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.ExecutableProcessor;
import io.harness.pms.sdk.core.execution.ExecutableProcessorFactory;
import io.harness.pms.sdk.core.execution.ProgressPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ProgressData;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class ProgressEventHandler extends PmsBaseEventHandler<ProgressEvent> {
  @Inject private ExecutableProcessorFactory executableProcessorFactory;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  protected String getMetricPrefix(ProgressEvent message) {
    return "progress_event";
  }

  @Override
  @NonNull
  protected Map<String, String> extraLogProperties(ProgressEvent event) {
    return ImmutableMap.<String, String>builder().put("eventType", NodeExecutionEventType.PROGRESS.name()).build();
  }

  @Override
  protected Ambiance extractAmbiance(ProgressEvent event) {
    return event.getAmbiance();
  }

  @Override
  protected void handleEventWithContext(ProgressEvent event) {
    try {
      StepParameters stepParameters =
          RecastOrchestrationUtils.fromJson(event.getStepParameters().toStringUtf8(), StepParameters.class);
      ProgressData progressData =
          (ProgressData) kryoSerializer.asInflatedObject(event.getProgressBytes().toByteArray());

      ExecutableProcessor processor = executableProcessorFactory.obtainProcessor(event.getExecutionMode());
      ProgressPackage progressPackage = ProgressPackage.builder()
                                            .ambiance(event.getAmbiance())
                                            .stepParameters(stepParameters)
                                            .progressData(progressData)
                                            .build();
      processor.handleProgress(progressPackage);
    } catch (Exception ex) {
      log.error("Error while Handling progress NodeExecutionId [{}], PlanExecutionId [{}]",
          AmbianceUtils.obtainCurrentRuntimeId(event.getAmbiance()), event.getAmbiance().getPlanExecutionId(), ex);
    }
  }
}
