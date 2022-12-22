/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.maintenance;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.observer.Subject;
import io.harness.threading.Schedulable;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Singleton;
import io.dropwizard.lifecycle.Managed;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
public class MaintenanceController implements Managed {
  private static final String MAINTENANCE_FILENAME = "maintenance";
  private static final String SHUTDOWN_FILENAME = "shutdown";

  private static Boolean forceMaintenance;
  private static final AtomicBoolean maintenance = new AtomicBoolean(true);
  private static final AtomicBoolean shutdown = new AtomicBoolean(false);

  public static void forceMaintenance(boolean force) {
    synchronized (log) {
      if (forceMaintenance == null || forceMaintenance != force) {
        log.info("Setting forced maintenance {}", force);
        forceMaintenance = force;
      }
    }
  }

  public static void resetForceMaintenance() {
    synchronized (log) {
      log.info("Un-setting forced maintenance");
      forceMaintenance = null;
    }
  }

  public static boolean getMaintenanceFlag() {
    return forceMaintenance != null ? forceMaintenance : maintenance.get();
  }

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final Subject<MaintenanceListener> maintenanceListenerSubject = new Subject<>();
  private ScheduledFuture scheduledFuture;

  public void register(MaintenanceListener listener) {
    maintenanceListenerSubject.register(listener);
  }

  /* (non-Javadoc)
   * @see io.dropwizard.lifecycle.Managed#start()
   */
  @Override
  public void start() {
    if (!running.getAndSet(true)) {
      scheduledFuture =
          Executors
              .newSingleThreadScheduledExecutor(
                  new ThreadFactoryBuilder().setNameFormat("maintenance-controller").build())
              .scheduleWithFixedDelay(
                  new Schedulable("Unexpected exception occurred while notifying for maintenance or shutdown",
                      this::checkForMaintenance),
                  1L, 1L, TimeUnit.SECONDS);
    }
  }

  private void checkForMaintenance() {
    boolean isShutdown = new File(SHUTDOWN_FILENAME).exists();
    if (shutdown.getAndSet(isShutdown) != isShutdown) {
      // We don't expect to ever leave shutdown mode, but log either way
      log.info("{} shutdown mode", isShutdown ? "Entering" : "Leaving");
      if (isShutdown) {
        maintenanceListenerSubject.fireInform(MaintenanceListener::onShutdown);
      }
    }
    boolean isMaintenance =
        forceMaintenance != null ? forceMaintenance : new File(MAINTENANCE_FILENAME).exists() || isShutdown;
    if (maintenance.getAndSet(isMaintenance) != isMaintenance) {
      log.info("{} maintenance mode", isMaintenance ? "Entering" : "Leaving");
      maintenanceListenerSubject.fireInform(
          isMaintenance ? MaintenanceListener::onEnterMaintenance : MaintenanceListener::onLeaveMaintenance);
    }
  }

  /* (non-Javadoc)
   * @see io.dropwizard.lifecycle.Managed#stop()
   */
  @Override
  public void stop() {
    if (running.getAndSet(false)) {
      if (scheduledFuture != null) {
        scheduledFuture.cancel(true);
      }
    }
  }
}
