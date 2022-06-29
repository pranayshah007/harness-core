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
// public class WeeklyAggregator extends InstanceAggregator {
//  private static final String FETCH_CHILD_DATA_POINTS_SQL = "SELECT INSTANCECOUNT, DATASANITY "
//      + "FROM INSTANCE_STATS_DAY "
//      + "WHERE REPORTEDAT > ? AND REPORTEDAT <= ? "
//      + "AND ACCOUNTID=? AND APPID=? AND SERVICEID=? AND ENVID=? AND CLOUDPROVIDERID=? AND INSTANCETYPE=?";
//
//  private static final String UPSERT_PARENT_TABLE_SQL =
//      "INSERT INTO INSTANCE_STATS_WEEK (REPORTEDAT, ACCOUNTID, APPID, SERVICEID, ENVID, CLOUDPROVIDERID, INSTANCETYPE,
//      INSTANCECOUNT, ARTIFACTID, DATASANITY) "
//      + "VALUES(?,?,?,?,?,?,?,?,?,?)"
//      + "ON DUPLICATE KEY UPDATE INSTANCECOUNT=VALUES('INSTANCECOUNT'), DATASANITY=VALUES('DATASANITY')"
//      + "WHERE DATASANITY=FALSE";
//
//  public WeeklyAggregator(TimeSeriesBatchEventInfo eventInfo) {
//    super(eventInfo, FETCH_CHILD_DATA_POINTS_SQL, UPSERT_PARENT_TABLE_SQL, 7);
//  }
//
//  @Override
//  public Date getWindowBeginTimestamp() {
//    Date windowEndTimestamp = getWindowEndTimestamp();
//    return DateUtils.addDays(windowEndTimestamp.getTime(), -7);
//  }
//
//  @Override
//  public Date getWindowEndTimestamp() {
//    long eventTimestamp = this.getEventInfo().getTimestamp();
//    Date currWholeDayTimestamp = DateUtils.getNextNearestWholeDayUTC(eventTimestamp);
//    // Now, if day on current day-end timestamp is Monday
//    // means that this timestamp is itself end timestamp for current week window
//    Integer dayOfWeek = DateUtils.getDayOfWeek(currWholeDayTimestamp.getTime());
//    if (dayOfWeek == 1) {
//      return currWholeDayTimestamp;
//    }
//
//    // Else just add remaining days to curr day timestamp to make it nearest Sunday
//    return DateUtils.addDays(currWholeDayTimestamp.getTime(), 7 - dayOfWeek + 1);
//  }
//
//  @Override
//  public InstanceAggregator getParentAggregatorObj() {
//    return null;
//  }
//
//  @Override
//  public void prepareUpsertQuery(PreparedStatement statement, Map<String, Object> params) {
//    // TODO right now not required, not using this aggregator
//  }
//}
