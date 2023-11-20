/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SLOGraphUtils {
  public static List<Instant> getMinutesExclusiveOfStartAndEndTime(
      Instant startTime, Instant endTime, long numOfPointsInBetween) {
    List<Instant> minutes = new ArrayList<>();
    long totalMinutes = Duration.between(startTime, endTime).toMinutes();
    long diff = totalMinutes;
    if (numOfPointsInBetween > 0) {
      diff = totalMinutes / numOfPointsInBetween;
    }
    if (diff == 0) {
      diff = 1L;
    }
    Duration diffDuration = Duration.ofMinutes(diff);
    for (Instant current = startTime.plus(diffDuration); current.isBefore(endTime);
         current = current.plus(diffDuration)) {
      minutes.add(current);
    }
    return minutes;
  }

  public static List<Instant> getBucketMinutesExclusiveOfStartAndEndTime(
      Instant startTime, Instant endTime, long numOfPointsRequiredInBetween, int bucketSize) {
    List<Instant> minutes = new ArrayList<>();
    long totalMinutes = Duration.between(startTime, endTime).toMinutes();
    Duration diffDuration = getDiffDuration(numOfPointsRequiredInBetween, bucketSize, totalMinutes);
    for (Instant current = startTime.plus(diffDuration); current.isBefore(endTime);
         current = current.plus(diffDuration)) {
      minutes.add(current);
    }
    return minutes;
  }

  public static List<Instant> getBucketMinutesInclusiveOfStartAndEndTime(
      Instant startTime, Instant endTime, long numOfPointsRequiredInBetween, int bucketSize) {
    List<Instant> minutes = new ArrayList<>();
    minutes.add(startTime);
    minutes.addAll(
        getBucketMinutesExclusiveOfStartAndEndTime(startTime, endTime, numOfPointsRequiredInBetween, bucketSize));
    minutes.add(endTime);
    return minutes;
  }

  private static Duration getDiffDuration(long numOfPointsRequiredInBetween, int bucketSize, long totalMinutes) {
    long bucketInterval = bucketSize;
    long currentNumOfPoints = totalMinutes / bucketInterval;
    while (currentNumOfPoints > numOfPointsRequiredInBetween) {
      bucketInterval += bucketSize;
      currentNumOfPoints = totalMinutes / bucketInterval;
    }
    return Duration.ofMinutes(bucketInterval);
  }
}
