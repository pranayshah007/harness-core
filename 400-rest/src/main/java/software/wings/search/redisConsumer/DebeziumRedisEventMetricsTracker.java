/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.redisConsumer;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
@OwnedBy(CDC)
@Getter
@Slf4j
class DebeziumRedisEventMetricsTracker {
  private ConcurrentMap<String, Double> runningAverageTime = new ConcurrentHashMap<>();
  private ConcurrentMap<String, Long> numChangeEvents = new ConcurrentHashMap<>();

  void updateAverage(String key, double timeTaken) {
    double averageVal = runningAverageTime.getOrDefault(key, (double) 0);
    double n = numChangeEvents.getOrDefault(key, (long) 0);
    averageVal = (averageVal * (n / (n + 1))) + (timeTaken / (n + 1));
    runningAverageTime.put(key, averageVal);
    numChangeEvents.put(key, (long) n + 1);
  }
}
