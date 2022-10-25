/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import io.harness.event.timeseries.TimeseriesLogContext;
import io.harness.event.timeseries.processor.instanceeventprocessor.InstanceEventProcessor;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;

import software.wings.api.InstanceEvent;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * The instance event that has info about the instance changes, which needs to be pushed
 * @author rktummala on 08/04/19
 *
 */
@Slf4j
public class InstanceEventListener extends QueueListener<InstanceEvent> {
  @Inject private InstanceEventProcessor instanceEventProcessor;

  @Inject
  public InstanceEventListener(QueueConsumer<InstanceEvent> queueConsumer) {
    super(queueConsumer, false);
  }

  /* (non-Javadoc)
   * @see software.wings.core.queue.QueueListener#onMessage(software.wings.core.queue.Queuable)
   */
  @SneakyThrows
  @Override
  public void onMessage(InstanceEvent instanceEvent) {
    try (AutoLogContext ignore = new TimeseriesLogContext(AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      //      log.info("InstanceTimeSeriesEvent received : {}", instanceEvent.getTimeSeriesBatchEventInfo().getLog());
      instanceEventProcessor.processEvent(instanceEvent.getTimeSeriesBatchEventInfo());
    } catch (Exception ex) {
      log.error("Failed to process InstanceEvent : [{}]", instanceEvent.toString(), ex);
      // Throw exception back for retry via queue
      throw ex;
    }
  }
}
