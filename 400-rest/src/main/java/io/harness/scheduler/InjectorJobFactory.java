/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.scheduler;

import com.google.inject.Inject;
import com.google.inject.Injector;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

public class InjectorJobFactory implements JobFactory {
  @Inject private Injector injector;

  @Override
  public Job newJob(TriggerFiredBundle triggerFiredBundle, Scheduler scheduler) throws SchedulerException {
    JobDetail jobDetail = triggerFiredBundle.getJobDetail();
    Class jobClass = jobDetail.getJobClass();
    try {
      return (Job) injector.getInstance(jobClass);
    } catch (Exception e) {
      throw new UnsupportedOperationException(e);
    }
  }
}
