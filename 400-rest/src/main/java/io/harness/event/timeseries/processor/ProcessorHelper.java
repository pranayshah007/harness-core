package io.harness.event.timeseries.processor;

import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProcessorHelper {
  Long getLongValue(String key, TimeSeriesEventInfo eventInfo) {
    if (eventInfo != null && eventInfo.getLongData() != null && eventInfo.getLongData().get(key) != null) {
      return eventInfo.getLongData().get(key);
    }
    return 0L;
  }

  Boolean getBooleanValue(String key, TimeSeriesEventInfo eventInfo) {
    if (eventInfo != null && eventInfo.getBooleanData() != null && eventInfo.getBooleanData().get(key) != null) {
      return eventInfo.getBooleanData().get(key);
    }
    return false;
  }

  void setTimeStamp(String key, int index, TimeSeriesEventInfo eventInfo, PreparedStatement upsertStatement,
      DataFetcherUtils utils) throws SQLException {
    if (eventInfo != null && eventInfo.getLongData() != null && eventInfo.getLongData().get(key) != null) {
      upsertStatement.setTimestamp(index, new Timestamp(eventInfo.getLongData().get(key)), utils.getDefaultCalendar());
    } else {
      upsertStatement.setTimestamp(index, null);
    }
  }
}
