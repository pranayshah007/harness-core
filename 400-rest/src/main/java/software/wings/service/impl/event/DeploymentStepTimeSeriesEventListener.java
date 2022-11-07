package software.wings.service.impl.event;

import io.harness.event.timeseries.processor.DeploymentStepEventProcessor;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;

import software.wings.api.DeploymentStepTimeSeriesEvent;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeploymentStepTimeSeriesEventListener extends QueueListener<DeploymentStepTimeSeriesEvent> {
  @Inject private DeploymentStepEventProcessor deploymentStepEventProcessor;

  @Inject
  public DeploymentStepTimeSeriesEventListener(QueueConsumer<DeploymentStepTimeSeriesEvent> queueConsumer) {
    super(queueConsumer, false);
  }

  @Override
  public void onMessage(DeploymentStepTimeSeriesEvent deploymentStepTimeSeriesEvent) {
    try {
      deploymentStepEventProcessor.processEvent(deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo());
    } catch (Exception ex) {
      log.error("Failed to process DeploymentStepTimeSeriesEvent : [{}]",
          deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo().toString(), ex);
    }
  }
}
