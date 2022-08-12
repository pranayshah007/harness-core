/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.entities.CEReportSchedule.CEReportScheduleKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@OwnedBy(HarnessTeam.CE)
@Slf4j
public class CEReportScheduleDao {
  @Inject private HPersistence persistence;

  public CEReportSchedule save(String accountId, CEReportSchedule schedule) {
    log.info("Creating report schedule for viewId {} and accountId {}", schedule.getViewsId(), accountId);
    String key = persistence.save(schedule);
    Query<CEReportSchedule> query =
        persistence.createQuery(CEReportSchedule.class).field(CEReportScheduleKeys.uuid).equal(key);
    return query.get();
  }

  public CEReportSchedule get(String uuid, String accountId) {
    log.info("Retrieving report schedule for reportId {} and accountId {}", uuid, accountId);
    Query<CEReportSchedule> query = persistence.createQuery(CEReportSchedule.class)
                                        .field(CEReportScheduleKeys.accountId)
                                        .equal(accountId)
                                        .field(CEReportScheduleKeys.uuid)
                                        .equal(uuid);
    return query.get();
  }

  public List<CEReportSchedule> getReportSettingByView(String viewsId, String accountId) {
    log.info("Retrieving all report schedules for viewsId {} and accountId {}", viewsId, accountId);
    Query<CEReportSchedule> query = persistence.createQuery(CEReportSchedule.class)
                                        .field(CEReportScheduleKeys.accountId)
                                        .equal(accountId)
                                        .field(CEReportScheduleKeys.viewsId)
                                        .equal(viewsId);
    return query.asList();
  }

  public List<CEReportSchedule> getReportSettingByViewIds(List<String> viewsIds, String accountId) {
    log.info("Retrieving all report schedules for viewsIds {} and accountId {}", viewsIds.size(), accountId);
    Query<CEReportSchedule> query = persistence.createQuery(CEReportSchedule.class)
                                        .field(CEReportScheduleKeys.accountId)
                                        .equal(accountId)
                                        .field(CEReportScheduleKeys.viewsId)
                                        .in(viewsIds);
    return query.asList();
  }

  public List<CEReportSchedule> getAllByAccount(String accountId) {
    log.info("Retrieving all report schedules for  accountId", accountId);
    Query<CEReportSchedule> query =
        persistence.createQuery(CEReportSchedule.class).field(CEReportScheduleKeys.accountId).equal(accountId);
    return query.asList(new FindOptions());
  }

  public List<CEReportSchedule> update(String accountId, CEReportSchedule schedule) {
    log.info("Updating report schedules for reportId {}  and accountId {}", schedule.getUuid(), accountId);
    Query query = persistence.createQuery(CEReportSchedule.class)
                      .field(CEReportScheduleKeys.accountId)
                      .equal(accountId)
                      .field(CEReportScheduleKeys.uuid)
                      .equal(schedule.getUuid());
    UpdateOperations<CEReportSchedule> updateOperations =
        persistence.createUpdateOperations(CEReportSchedule.class)
            .set(CEReportScheduleKeys.enabled, schedule.isEnabled())
            .set(CEReportScheduleKeys.userCron, schedule.getUserCron())
            .set(CEReportScheduleKeys.name, schedule.getName())
            .set(CEReportScheduleKeys.description, schedule.getDescription())
            .set(CEReportScheduleKeys.recipients, schedule.getRecipients())
            .set(CEReportScheduleKeys.userCronTimeZone, schedule.getUserCronTimeZone())
            .set(CEReportScheduleKeys.nextExecution, schedule.getNextExecution());
    persistence.update(query, updateOperations);
    log.info(query.toString());
    return query.asList(new FindOptions());
  }

  public boolean delete(String uuid, String accountId) {
    log.info("Deleting report schedules for reportId {} and accountId {}", uuid, accountId);
    Query query = persistence.createQuery(CEReportSchedule.class)
                      .field(CEReportScheduleKeys.accountId)
                      .equal(accountId)
                      .field(CEReportScheduleKeys.uuid)
                      .equal(uuid);
    return persistence.delete(query);
  }

  public boolean deleteAllByView(String viewsId, String accountId) {
    Query query = persistence.createQuery(CEReportSchedule.class)
                      .field(CEReportScheduleKeys.accountId)
                      .equal(accountId)
                      .field(CEReportScheduleKeys.viewsId)
                      .equal(viewsId);
    return persistence.delete(query);
  }

  public List<CEReportSchedule> getAllMatchingSchedules(String accountId, Date timeUpto) {
    Query<CEReportSchedule> query = persistence.createQuery(CEReportSchedule.class)
                                        .field(CEReportScheduleKeys.enabled)
                                        .equal(true)
                                        .field(CEReportScheduleKeys.accountId)
                                        .equal(accountId)
                                        .field(CEReportScheduleKeys.nextExecution)
                                        .lessThanOrEq(timeUpto);
    log.info("Retrieving all report schedules <= this time {} for account {}", timeUpto.toString(), accountId);
    log.info(query.toString());
    return query.asList();
  }

  public List<CEReportSchedule> updateNextExecution(String accountId, CEReportSchedule schedule) {
    log.info("Updating next execution for reportId {}  and accountId {}", schedule.getUuid(), accountId);
    Query query = persistence.createQuery(CEReportSchedule.class)
                      .field(CEReportScheduleKeys.accountId)
                      .equal(accountId)
                      .field(CEReportScheduleKeys.uuid)
                      .equal(schedule.getUuid());
    UpdateOperations<CEReportSchedule> updateOperations =
        persistence.createUpdateOperations(CEReportSchedule.class)
            .set(CEReportScheduleKeys.nextExecution, schedule.getNextExecution());
    persistence.update(query, updateOperations);
    log.info(query.toString());
    return query.asList(new FindOptions());
  }
}
