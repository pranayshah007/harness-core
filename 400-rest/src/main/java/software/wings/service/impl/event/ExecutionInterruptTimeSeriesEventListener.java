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
