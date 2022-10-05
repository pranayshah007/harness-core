package software.wings.service.impl.instance;

import static software.wings.utils.TimeUtils.isWeekend;

import software.wings.service.intfc.AuditService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.lifecycle.Managed;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AuditCleanupJob implements Managed {
  private static final long DELAY_IN_MINUTES = TimeUnit.HOURS.toMinutes(6);

  private static int retentionTimeInMonths = 18;
  @Inject private AuditService auditService;

  private ScheduledExecutorService executorService;

  @Override
  public void start() throws Exception {
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("audit-cleanup-job").build());
    executorService.scheduleWithFixedDelay(this::run, 30, DELAY_IN_MINUTES, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.warn("Audit Cleanup is stopped");
    executorService.shutdownNow();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  @VisibleForTesting
  public void run() {
    if (isWeekend()) {
      log.info("Audit Cleanup Job Started @ {}", Instant.now());
      long toBeDeletedTillTimestamp =
          LocalDateTime.now().minusMonths(retentionTimeInMonths).toInstant(ZoneOffset.UTC).toEpochMilli();
      auditService.deleteAuditRecords(toBeDeletedTillTimestamp);
    }
  }
}
