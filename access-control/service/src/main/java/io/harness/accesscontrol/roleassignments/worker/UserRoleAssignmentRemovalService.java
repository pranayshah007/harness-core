package io.harness.accesscontrol.roleassignments.worker;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class UserRoleAssignmentRemovalService implements Managed {
  private Future<?> userRoleAssignmentRemovalJobFuture;
  private final ScheduledExecutorService executorService;
  private static final String DEBUG_MESSAGE = "UserRoleAssignmentRemovalMigrationService: ";
  private final UserRoleAssignmentRemovalJob userRoleAssignmentRemovalMigrationJob;

  @Inject
  public UserRoleAssignmentRemovalService(UserRoleAssignmentRemovalJob userRoleAssignmentRemovalMigrationJob) {
    this.userRoleAssignmentRemovalMigrationJob = userRoleAssignmentRemovalMigrationJob;
    String threadName = "user-roleAssignment-removal-service-thread";
    this.executorService =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat(threadName).build());
  }

  @Override
  public void start() throws Exception {
    log.info(DEBUG_MESSAGE + "started...");
    userRoleAssignmentRemovalJobFuture =
        executorService.scheduleWithFixedDelay(userRoleAssignmentRemovalMigrationJob, 15, 1440, TimeUnit.DAYS);
  }

  @Override
  public void stop() throws Exception {
    log.info(DEBUG_MESSAGE + "stopping...");
    userRoleAssignmentRemovalJobFuture.cancel(false);
    executorService.shutdown();
  }
}
