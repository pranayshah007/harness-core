package io.harness.event.timeseries.processor;

import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ProcessorHelper {
  Long getLongValue(String key, TimeSeriesEventInfo eventInfo) {
    if (eventInfo != null && eventInfo.getLongData() != null && eventInfo.getLongData().get(key) != null) {
      return eventInfo.getLongData().get(key);
    }
    return 0L;
  }
}
