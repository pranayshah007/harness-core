/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.maintenance.MaintenanceController;
import io.harness.scheduler.HQuartzScheduler;
import io.harness.scheduler.SchedulerConfig;

import software.wings.core.managerConfiguration.ConfigChangeEvent;
import software.wings.core.managerConfiguration.ConfigChangeListener;
import software.wings.core.managerConfiguration.ConfigurationController;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;

@Slf4j
public class JobScheduler extends HQuartzScheduler implements ConfigChangeListener {
  @Inject
  public JobScheduler(Injector injector, SchedulerConfig schedulerConfig, String defaultMongoUri) {
    super(injector, schedulerConfig, defaultMongoUri);
    if (!schedulerConfig.isEnabled()) {
      return;
    }

    try {
      if (schedulerConfig.getAutoStart().equals("true")) {
        injector.getInstance(MaintenanceController.class).register(this);
        scheduler = createScheduler(getDefaultProperties());

        ConfigurationController configurationController = injector.getInstance(Key.get(ConfigurationController.class));

        if (!getMaintenanceFlag() && configurationController.isPrimary()) {
          scheduler.start();
        }
      }
    } catch (SchedulerException exception) {
      throw new WingsException(ErrorCode.UNKNOWN_ERROR, exception)
          .addParam("message", "Could not initialize cron scheduler");
    }
  }

  @Override
  public void onConfigChange(List<ConfigChangeEvent> events) {
    log.info("onConfigChange {}", events);

    if (scheduler != null) {
      if (events.contains(ConfigChangeEvent.PrimaryChanged)) {
        ConfigurationController configurationController = injector.getInstance(Key.get(ConfigurationController.class));
        try {
          if (configurationController.isPrimary()) {
            scheduler.start();
          } else {
            scheduler.standby();
          }
        } catch (SchedulerException e) {
          log.error("Error updating scheduler.", e);
        }
      }
    }
  }
}
