/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

// package io.harness.event.timeseries.processor.instanceeventprocessor.instanceaggregator;
//
// import io.harness.event.timeseries.processor.utils.DateUtils;
//
// import software.wings.service.impl.event.timeseries.TimeSeriesBatchEventInfo;
//
// import java.sql.PreparedStatement;
// import java.util.Date;
// import java.util.Map;
//
// public class MonthlyAggregator extends InstanceAggregator {
//  private static final String FETCH_CHILD_DATA_POINTS_SQL = "SELECT INSTANCECOUNT, SANITYSTATUS "
//      + "FROM INSTANCE_STATS_DAY "
//      + "WHERE REPORTEDAT > ? AND REPORTEDAT <= ? "
//      + "AND ACCOUNTID=? AND APPID=? AND SERVICEID=? AND ENVID=? AND CLOUDPROVIDERID=? AND INSTANCETYPE=?";
//
//  private static final String UPSERT_PARENT_TABLE_SQL =
//      "INSERT INTO INSTANCE_STATS_MONTH (REPORTEDAT, ACCOUNTID, APPID, SERVICEID, ENVID, CLOUDPROVIDERID,
//      INSTANCETYPE, INSTANCECOUNT, ARTIFACTID, DATASANITY) "
//      + "VALUES(?,?,?,?,?,?,?,?,?,?)"
//      + "ON DUPLICATE KEY UPDATE INSTANCECOUNT=VALUES('INSTANCECOUNT'), DATASANITY=VALUES('DATASANITY')"
//      + "WHERE DATASANITY=FALSE";
//
//  public MonthlyAggregator(TimeSeriesBatchEventInfo eventInfo) {
//    super(eventInfo, FETCH_CHILD_DATA_POINTS_SQL, UPSERT_PARENT_TABLE_SQL, 0);
//    setWindowSize(getDaysInCurrentMonth(eventInfo.getTimestamp()));
//  }
//
//  @Override
//  public Date getWindowBeginTimestamp() {
//    Date windowEndTimestamp = getWindowEndTimestamp();
//    return DateUtils.addDays(windowEndTimestamp.getTime(), (-1) * this.getWindowSize());
//  }
//
//  @Override
//  public Date getWindowEndTimestamp() {
//    long eventTimestamp = this.getEventInfo().getTimestamp();
//    Date currWholeDayTimestamp = DateUtils.getNextNearestWholeDayUTC(eventTimestamp);
//    // If its last date of the month and converting to nearest whole day will make it first timestamp of next month
//    // Also, if eventTimestamp itself is first timestamp of next month
//    // Then it is the end of the current window
//    if (DateUtils.isMonthStartTimestamp(eventTimestamp)) {
//      return currWholeDayTimestamp;
//    }
//
//    // Else just add remaining days of current month to curr day timestamp to make it end of window timestamp
//    return DateUtils.addDays(currWholeDayTimestamp.getTime(),
//        this.getWindowSize() - DateUtils.getDayOfMonth(currWholeDayTimestamp.getTime()) + 1);
//  }
//
//  @Override
//  public InstanceAggregator getParentAggregatorObj() {
//    return null;
//  }
//
//  @Override
//  public void prepareUpsertQuery(PreparedStatement statement, Map<String, Object> params) {
//    // TODO right now not required, fill in later
//  }
//
//  private Integer getDaysInCurrentMonth(long eventTimestamp) {
//    if (DateUtils.isMonthStartTimestamp(eventTimestamp)) {
//      Date lastMonthTimestamp = DateUtils.addMonths(eventTimestamp, -1);
//      return DateUtils.getDaysInCurrentMonth(lastMonthTimestamp.getTime());
//    } else {
//      return DateUtils.getDaysInCurrentMonth(eventTimestamp);
//    }
//  }
//}
