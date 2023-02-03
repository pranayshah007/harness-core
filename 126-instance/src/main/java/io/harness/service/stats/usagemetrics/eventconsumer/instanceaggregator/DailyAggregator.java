/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.stats.usagemetrics.eventconsumer.instanceaggregator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.event.timeseries.processor.instanceeventprocessor.InstanceEventAggregator;
import io.harness.event.timeseries.processor.utils.DateUtils;
import io.harness.eventsframework.schemas.instancestatstimeseriesevent.TimeseriesBatchEventInfo;
import io.harness.models.constants.TimescaleConstants;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;

@OwnedBy(HarnessTeam.DX)
public class DailyAggregator extends InstanceAggregator {
  private static final String FETCH_CHILD_DATA_POINTS_SQL =
      "SELECT PERCENTILE_DISC(0.50) WITHIN GROUP (ORDER BY INSTANCECOUNT) AS INSTANCECOUNT, BOOL_AND(SANITYSTATUS) AS SANITYSTATUS, COUNT(*) AS NUM_OF_RECORDS "
      + "FROM NG_INSTANCE_STATS_HOUR "
      + "WHERE REPORTEDAT >= ? AND REPORTEDAT < ? "
      + "AND ACCOUNTID=? AND ORGID=? AND PROJECTID=? AND SERVICEID=? AND ENVID=? AND CLOUDPROVIDERID=? AND INSTANCETYPE=?";

  private static final String UPSERT_PARENT_TABLE_SQL =
      "INSERT INTO NG_INSTANCE_STATS_DAY (REPORTEDAT,ACCOUNTID,ORGID,PROJECTID,SERVICEID,ENVID,CLOUDPROVIDERID,INSTANCETYPE,INSTANCECOUNT,WEEKTIMESTAMP,MONTHTIMESTAMP,SANITYSTATUS) "
      + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?) "
      + "ON CONFLICT(ACCOUNTID,ORGID,PROJECTID,SERVICEID,ENVID,CLOUDPROVIDERID,INSTANCETYPE,REPORTEDAT) "
      + "DO UPDATE SET INSTANCECOUNT=EXCLUDED.INSTANCECOUNT, SANITYSTATUS=EXCLUDED.SANITYSTATUS "
      + "WHERE NG_INSTANCE_STATS_DAY.SANITYSTATUS=FALSE "
      + "AND (NG_INSTANCE_STATS_DAY.INSTANCECOUNT!=EXCLUDED.INSTANCECOUNT OR NG_INSTANCE_STATS_DAY.SANITYSTATUS!=EXCLUDED.SANITYSTATUS)";

  public DailyAggregator(TimeseriesBatchEventInfo eventInfo) {
    super(eventInfo, FETCH_CHILD_DATA_POINTS_SQL, UPSERT_PARENT_TABLE_SQL, 24, "DAILY AGGREGATOR");
  }

  @Override
  public Date getWindowBeginTimestamp() {
    Date windowEndTimestamp = getWindowEndTimestamp();
    return DateUtils.addDays(windowEndTimestamp.getTime(), -1);
  }

  @Override
  public Date getWindowEndTimestamp() {
    long eventTimestamp = this.getEventInfo().getTimestamp();
    return DateUtils.getNextNearestWholeDayUTC(eventTimestamp);
  }

  @Override
  public InstanceAggregator getParentAggregatorObj() {
    //    return new WeeklyAggregator(this.getEventInfo());
    return null;
  }

  @Override
  public void prepareUpsertQuery(PreparedStatement statement, Map<String, Object> params) throws SQLException {
    Map<String, String> dataMap = (Map<String, String>) params.get(InstanceEventAggregator.DATA_MAP_KEY);

    statement.setTimestamp(1, new Timestamp(this.getWindowBeginTimestamp().getTime()), DateUtils.getDefaultCalendar());
    statement.setString(2, this.getEventInfo().getAccountId());
    statement.setString(3, dataMap.get(TimescaleConstants.ORG_ID.getKey()));
    statement.setString(4, dataMap.get(TimescaleConstants.PROJECT_ID.getKey()));
    statement.setString(5, dataMap.get(TimescaleConstants.SERVICE_ID.getKey()));
    statement.setString(6, dataMap.get(TimescaleConstants.ENV_ID.getKey()));
    statement.setString(7, dataMap.get(TimescaleConstants.CLOUDPROVIDER_ID.getKey()));
    statement.setString(8, dataMap.get(TimescaleConstants.INSTANCE_TYPE.getKey()));
    statement.setInt(9, (Integer) params.get(TimescaleConstants.INSTANCECOUNT.getKey()));
    statement.setTimestamp(
        10, new Timestamp(this.getWeeklyWindowTimestamp().getTime()), DateUtils.getDefaultCalendar());
    statement.setTimestamp(
        11, new Timestamp(this.getMonthlyWindowTimestamp().getTime()), DateUtils.getDefaultCalendar());
    statement.setBoolean(12, (Boolean) params.get(TimescaleConstants.SANITYSTATUS.getKey()));
  }

  // Calculate the month window to which event belongs (starting timestamp of the month)
  private Date getMonthlyWindowTimestamp() {
    long eventTimestamp = this.getEventInfo().getTimestamp();
    return DateUtils.getBeginningTimestampOfMonth(eventTimestamp);
  }

  // Calculate the week window to which event belongs (starting timestamp of the week)
  // Week starts from Sunday
  private Date getWeeklyWindowTimestamp() {
    long eventTimestamp = this.getEventInfo().getTimestamp();
    Date prevWholeDayTimestamp = DateUtils.getPrevNearestWholeDayUTC(eventTimestamp);

    Integer dayOfWeek = DateUtils.getDayOfWeek(prevWholeDayTimestamp.getTime());

    return DateUtils.addDays(prevWholeDayTimestamp.getTime(), 1 - dayOfWeek);
  }
}
