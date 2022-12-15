/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.util;

import io.harness.NGDateUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.activityhistory.dto.TimeGroupType;
import io.harness.ng.overview.dto.EntityStatusDetails;
import io.harness.ng.overview.dto.TimeValuePair;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.DX)
@UtilityClass
/**
 * Creates list of data points that represents total count of the entity at that timestamp
 * This class can be used to find growth trend for any type of entity that has given entityStatusDetails fields
 */
public class GrowthTrendEvaluator {
  /**
   * @param entities list of entity status details
   * @param startTimeInMs the starting time of the interval of growth
   * @param endTimeInMs end ending time of the interval of growth
   * @param timeGroupType required to determine the resultant number of data points
   * @return list of time-value pair sorted by time, with data points separated by timeGroupType interval
   */
  public List<TimeValuePair<Integer>> getGrowthTrend(List<io.harness.ng.overview.dto.EntityStatusDetails> entities,
      long startTimeInMs, long endTimeInMs, TimeGroupType timeGroupType) {
    // <startTime, endTime>
    Pair<Long, Long> queryInterval = tuneQueryInterval(startTimeInMs, endTimeInMs, timeGroupType);
    startTimeInMs = queryInterval.getLeft();
    endTimeInMs = queryInterval.getRight();

    Map<Long, Integer> timeValueMap = process(entities, startTimeInMs, endTimeInMs, timeGroupType.getDurationInMs());

    return prepareTrendResponse(timeValueMap, startTimeInMs, endTimeInMs, timeGroupType.getDurationInMs());
  }

  // --------------------------------- PRIVATE METHODS -------------------------------

  private Map<Long, Integer> process(List<io.harness.ng.overview.dto.EntityStatusDetails> entities, long startTimeInMs,
      long endTimeInMs, long intervalTimeInMs) {
    Map<Long, Integer> timeValueMap = new HashMap<>();

    for (EntityStatusDetails entity : entities) {
      long entityCreatedAtTime = entity.getCreatedAt();
      long entityDeletedAtTime = entity.getDeletedAt();

      if (entityCreatedAtTime > endTimeInMs) {
        // out of scope of interval
        continue;
      }

      // In case of creation, add 1 count to the corresponding window
      long windowTimestamp = getWindow(entityCreatedAtTime, intervalTimeInMs, startTimeInMs);
      updateCounter(timeValueMap, windowTimestamp, 1);

      // In case of deletion, add (-1) count to the corresponding window
      if (entity.isDeleted() && entity.getDeletedAt() > 0) {
        windowTimestamp = getWindow(entityDeletedAtTime, intervalTimeInMs, startTimeInMs);
        updateCounter(timeValueMap, windowTimestamp, -1);
      }
    }
    return timeValueMap;
  }

  /**
   * This method adjusts the dates and fixes different issues related to alignment to group size,
   * mutual alignment of the dates etc.
   * @param startTimeInMs
   * @param endTimeInMs
   * @param timeGroupType
   * @return pair {startTime, endTime}
   */
  @VisibleForTesting
  public Pair<Long, Long> tuneQueryInterval(long startTimeInMs, long endTimeInMs, TimeGroupType timeGroupType) {
    // Adjust the start time and end time to the same day's end time (midnight)
    endTimeInMs = NGDateUtils.getNextNearestWholeDayUTC(endTimeInMs);

    // Adjust startTime such that endTime - startTime is multiples of group by time
    // It should form complete intervals b/w start time and end time
    long intervalTimeInMs = timeGroupType.getDurationInMs();
    long diff = endTimeInMs - startTimeInMs;
    if (diff % intervalTimeInMs != 0) {
      startTimeInMs = endTimeInMs - (((diff / intervalTimeInMs) + 1) * intervalTimeInMs);
    }
    return new ImmutablePair<>(startTimeInMs, endTimeInMs);
  }

  @VisibleForTesting
  /**
   * Return the endTimestamp of the window where entityTime lies in
   * Window starts from startTimestamp with increments of intervalTimeInMs (groupByTime)
   * and ends at endTimeInMs (of the initial query interval)
   */
  public long getWindow(long entityTime, long intervalTimeInMs, long startTimeInMs) {
    if (entityTime <= startTimeInMs) {
      return startTimeInMs;
    }
    long diff = entityTime - startTimeInMs;
    long factor = diff / intervalTimeInMs + 1;
    if (diff % intervalTimeInMs == 0) {
      factor -= 1;
    }
    return startTimeInMs + factor * intervalTimeInMs;
  }

  private List<TimeValuePair<Integer>> prepareTrendResponse(
      Map<Long, Integer> timeValuePairMap, long startTimeInMs, long endTimeInMs, long intervalTimeInMs) {
    List<TimeValuePair<Integer>> timeValuePairList = new ArrayList<>();
    int currentCount = 0;
    long windowTimestampInMs = startTimeInMs;
    while (windowTimestampInMs <= endTimeInMs) {
      currentCount += timeValuePairMap.getOrDefault(windowTimestampInMs, 0);
      timeValuePairList.add(new TimeValuePair<>(windowTimestampInMs, currentCount));
      windowTimestampInMs += intervalTimeInMs;
    }

    return timeValuePairList;
  }

  private void updateCounter(Map<Long, Integer> timeValuePairMap, long windowTimestamp, int value) {
    int currentValue = timeValuePairMap.getOrDefault(windowTimestamp, 0);
    timeValuePairMap.put(windowTimestamp, value + currentValue);
  }
}
