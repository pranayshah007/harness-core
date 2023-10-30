/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ccm.views.dao.CEReportScheduleDao;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.service.CEReportScheduleService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronSequenceGenerator;

@CodePulse(
    module = ProductModule.CCM, unitCoverageRequired = true, components = {HarnessModuleComponent.CCM_PERSPECTIVE})
@OwnedBy(HarnessTeam.CE)
@Slf4j
public class CEReportScheduleServiceImpl implements CEReportScheduleService {
  @Inject CEReportScheduleDao ceReportScheduleDao;

  @Override
  public CEReportSchedule get(String uuid, String accountId) {
    return ceReportScheduleDao.get(uuid, accountId);
  }

  @Override
  public List<CEReportSchedule> getReportSettingByView(String viewsId, String accountId) {
    return ceReportScheduleDao.getReportSettingByView(viewsId, accountId);
  }

  @Override
  public List<CEReportSchedule> getAllByAccount(String accountId) {
    return null;
  }

  @Override
  public CEReportSchedule createReportSetting(String accountId, CEReportSchedule schedule) {
    CEReportSchedule entry = get(schedule.getUuid(), accountId);
    // ID – the ID for a TimeZone, either an abbreviation such as "PST", a full name such as "America/Los_Angeles",
    // or a custom ID such as "GMT-8:00". Note that the support of abbreviations is for JDK 1.1.x compatibility only
    // and full names should be used.
    String cronTimeZone;
    if (schedule.getUserCronTimeZone() != null) {
      cronTimeZone = schedule.getUserCronTimeZone();
    } else {
      // Default to UTC
      cronTimeZone = "UTC";
    }
    CronSequenceGenerator cronTrigger =
        new CronSequenceGenerator(schedule.getUserCron(), TimeZone.getTimeZone(cronTimeZone));
    if (entry == null) {
      Date next = cronTrigger.next(new Date());
      log.info("Next Execution Time in user timezone: " + next);
      schedule.setNextExecution(next);
      schedule.setAccountId(accountId);
      schedule.setEnabled(true);
      schedule.setUserCronTimeZone(cronTimeZone);
      // Remove dupes from these lists
      schedule.setRecipients(new HashSet<>(Arrays.asList(schedule.getRecipients())).toArray(new String[0]));
      schedule.setViewsId(new HashSet<>(Arrays.asList(schedule.getViewsId())).toArray(new String[0]));
      return ceReportScheduleDao.save(accountId, schedule);
    }
    return null;
  }

  @Override
  public List<CEReportSchedule> update(String accountId, CEReportSchedule schedule) {
    // validate cron expression during updates as well
    String cronTimeZone;
    if (schedule.getUserCronTimeZone() != null) {
      cronTimeZone = schedule.getUserCronTimeZone();
    } else {
      // Default to UTC
      cronTimeZone = "UTC";
    }
    CronSequenceGenerator cronSequenceGenerator =
        new CronSequenceGenerator(schedule.getUserCron(), TimeZone.getTimeZone(cronTimeZone));
    Date next = cronSequenceGenerator.next(new Date());
    log.info("Updated next Execution Time: " + next);
    schedule.setNextExecution(next);
    if (schedule.getDescription() == null) {
      schedule.setDescription("");
    }
    // Remove dupes from these lists
    schedule.setRecipients(new HashSet<>(Arrays.asList(schedule.getRecipients())).toArray(new String[0]));
    schedule.setViewsId(new HashSet<>(Arrays.asList(schedule.getViewsId())).toArray(new String[0]));
    return ceReportScheduleDao.update(accountId, schedule);
  }

  @Override
  public void deleteAllByView(String viewsId, String accountId) {
    ceReportScheduleDao.deleteAllByView(viewsId, accountId);
  }

  @Override
  public void delete(String uuid, String accountId) {
    CEReportSchedule entry = get(uuid, accountId);
    if (entry != null) {
      ceReportScheduleDao.delete(entry.getUuid(), accountId);
    }
  }

  @Override
  public List<CEReportSchedule> getAllMatchingSchedules(String accountId, Date jobTime) {
    return ceReportScheduleDao.getAllMatchingSchedules(accountId, jobTime);
  }

  @Override
  public List<CEReportSchedule> updateNextExecution(String accountId, CEReportSchedule schedule) {
    String cronTimeZone;
    if (schedule.getUserCronTimeZone() != null) {
      cronTimeZone = schedule.getUserCronTimeZone();
    } else {
      // Default to UTC
      cronTimeZone = "UTC";
    }
    CronSequenceGenerator cronSequenceGenerator =
        new CronSequenceGenerator(schedule.getUserCron(), TimeZone.getTimeZone(cronTimeZone));
    Date next = cronSequenceGenerator.next(new Date());
    log.info("Updated next Execution Time: " + next);
    schedule.setNextExecution(next);
    return ceReportScheduleDao.updateNextExecution(accountId, schedule);
  }
}
