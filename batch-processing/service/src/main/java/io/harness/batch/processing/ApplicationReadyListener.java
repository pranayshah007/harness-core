/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing;

import static io.harness.event.app.EventServiceApplication.EVENTS_STORE;

import static com.google.common.base.Verify.verify;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.concurrent.HTimeLimiter;
import io.harness.mongo.IndexManager;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.Morphia;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@Slf4j
public class ApplicationReadyListener {
  private final TimeScaleDBService timeScaleDBService;
  private final Morphia morphia;
  private final HPersistence hPersistence;
  private final IndexManager indexManager;
  private final Environment environment;
  private final TimeLimiter timeLimiter;

  public ApplicationReadyListener(TimeScaleDBService timeScaleDBService, HPersistence hPersistence, Morphia morphia,
      IndexManager indexManager, TimeLimiter timeLimiter, Environment environment) {
    this.timeScaleDBService = timeScaleDBService;
    this.hPersistence = hPersistence;
    this.morphia = morphia;
    this.indexManager = indexManager;
    this.timeLimiter = timeLimiter;
    this.environment = environment;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ensureTimescaleConnectivity() {
    log.info("Inside ensureTimescaleConnectivity");
    if (Boolean.TRUE.equals(environment.getProperty("ensure-timescale", Boolean.class, Boolean.TRUE))) {
      verify(timeScaleDBService.isValid(), "Unable to connect to timescale db");
    }
    log.info("End ensureTimescaleConnectivity");
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ensureIndexForEventsStore(ApplicationReadyEvent applicationReadyEvent) {
    AdvancedDatastore datastore = hPersistence.getDatastore(EVENTS_STORE);
    IndexManager.Mode indexManagerMode = applicationReadyEvent.getApplicationContext()
                                             .getBean(BatchMainConfig.class)
                                             .getEventsMongo()
                                             .getIndexManagerMode();
    indexManager.ensureIndexes(indexManagerMode, datastore, morphia, EVENTS_STORE);
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public void ensureMongoConnectivity() throws Exception {
    log.info("Inside ensureMongoConnectivity");
    try {
      HTimeLimiter.callInterruptible21(timeLimiter, hPersistence.healthExpectedResponseTimeout(), () -> {
        hPersistence.isHealthy();
        return null;
      });
    } catch (UncheckedTimeoutException e) {
      log.error("Timed out waiting for mongo connectivity");
      throw e;
    }
    log.info("End ensureMongoConnectivity");
  }

  @EventListener(ApplicationReadyEvent.class)
  public void createLivenessMarkerOnReadyEvent() throws IOException {
    createLivenessMarker();
  }

  public static void createLivenessMarker() throws IOException {
    File livenessMarker = new File("batch-processing-up");
    if (livenessMarker.exists()) {
      log.info("Liveness marker already exists at {}", livenessMarker.getAbsolutePath());
      return;
    }

    boolean created = livenessMarker.createNewFile();
    if (created) {
      log.info("Created liveness marker at: {}", livenessMarker.getAbsolutePath());
    } else {
      log.error("Failed to create liveness marker");
    }
  }
}
