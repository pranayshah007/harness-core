package io.harness.metrics;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.security.SecureRandom;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PipelineTelemetryRecordsJob {
  public static final int METRICS_RECORD_PERIOD_HOURS = 24;

  @Inject private Injector injector;
  @Inject @Named("telemetryPublisherExecutor") protected ScheduledExecutorService executorService;
  @Inject PipelineTelemetryPublisher publisher;

  public void scheduleTasks() {
    long initialDelay = new SecureRandom().nextInt(1);

    try {
      executorService.scheduleAtFixedRate(
          () -> publisher.recordTelemetry(), initialDelay, METRICS_RECORD_PERIOD_HOURS, TimeUnit.HOURS);
    } catch (Exception e) {
      log.error("Exception while creating the scheduled job to track pipelines", e);
    }
  }
}
