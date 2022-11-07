/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.event;

import io.harness.event.timeseries.processor.ExecutionInterruptProcessor;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;

import software.wings.api.ExecutionInterruptTimeSeriesEvent;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecutionInterruptTimeSeriesEventListener extends QueueListener<ExecutionInterruptTimeSeriesEvent> {
  @Inject private ExecutionInterruptProcessor executionInterruptProcessor;

  @Inject
  public ExecutionInterruptTimeSeriesEventListener(QueueConsumer<ExecutionInterruptTimeSeriesEvent> queueConsumer) {
    super(queueConsumer, false);
  }

  @Override
  public void onMessage(ExecutionInterruptTimeSeriesEvent executionInterruptTimeSeriesEvent) {
    try {
      executionInterruptProcessor.processEvent(executionInterruptTimeSeriesEvent.getTimeSeriesEventInfo());
    } catch (Exception ex) {
      log.error("Failed to process ExecutionInterruptTimeSeriesEvent : [{}]",
          executionInterruptTimeSeriesEvent.getTimeSeriesEventInfo().toString(), ex);
    }
  }
}
