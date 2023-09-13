/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plugin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PluginMetadataRecordsJob {
  public static final int THIRTY_MINS = 1800;
  public static final long INITIAL_DELAY = 10;

  @Inject @Named("pluginMetadataPublishExecutor") protected ScheduledExecutorService executorService;
  @Inject PluginMetadataPublisher publisher;

  public void scheduleTasks() {
    try {
      log.info("PluginMetadataRecordsJob scheduler starting");
      executorService.scheduleAtFixedRate(() -> publisher.publish(), INITIAL_DELAY, THIRTY_MINS, TimeUnit.SECONDS);
      log.info("Plugin metadata publish scheduler started");
    } catch (Exception e) {
      log.error("Exception while creating the scheduled job to publish plugin metadata", e);
    }
  }
}
