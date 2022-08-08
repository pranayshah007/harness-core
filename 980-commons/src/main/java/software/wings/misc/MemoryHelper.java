/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.misc;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import lombok.extern.slf4j.Slf4j;

/**
 * The Class MemoryUtil.
 */
@Slf4j
public class MemoryHelper {
  public static long getProcessMemoryBytes() {
    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    return memoryMXBean.getHeapMemoryUsage().getUsed() + memoryMXBean.getNonHeapMemoryUsage().getUsed();
  }

  public static long getProcessMemoryMB() {
    // Performing right shift by 20, is similar to division by 2^20. Prefer using shift operator,
    // since its faster than division.
    return getProcessMemoryBytes() >> 20;
  }

  public static long getProcessMaxMemoryBytes() {
    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    return memoryMXBean.getHeapMemoryUsage().getMax();
  }

  public static long getProcessMaxMemoryMB() {
    // Performing right shift by 20, is similar to division by 2^20. Prefer using shift operator,
    // since its faster than division.
    return getProcessMaxMemoryBytes() >> 20;
  }

  public static Long getPodRSSFromCgroupBytes() {
    String line;
    final String rssKey = "total_rss";
    final String filePath = "/sys/fs/cgroup/memory/memory.stat";
    try {
      java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(filePath));
      while ((line = reader.readLine()) != null) {
        String[] keyValuePair = line.split(" ");
        if (keyValuePair.length > 1) {
          String key = keyValuePair[0];
          String value = keyValuePair[1];
          if (key.equals(rssKey)) {
            return Long.valueOf(value);
          }
        }
      }
    } catch (Exception ex) {
      log.error("Error while fetching pod's rss from cgroup ", ex);
      throw new RuntimeException("Unable to get pod's rss from cgroup");
    }
    // Throw exception if we didn't able to find Pod's rss yet.
    throw new RuntimeException("Unable to get pod's rss from cgroup");
  }

  public static long getPodRSSFromCgroupMB() {
    final long podRss = getPodRSSFromCgroupBytes();
    if (podRss < 1) {
      return podRss;
    }
    // Performing right shift by 20, is similar to division by 2^20. Prefer using shift operator,
    // since it is faster than division.
    return podRss >> 20;
  }

  public static long getPodMaxMemoryMB() {
    try {
      // Read Memory limits from cgroup.
      java.io.File configFile = new java.io.File("/sys/fs/cgroup/memory/memory.limit_in_bytes");
      java.util.List<String> memoryLines =
          org.apache.commons.io.FileUtils.readLines(configFile, com.google.common.base.Charsets.UTF_8);
      if (memoryLines.size() == 1) {
        Long memoryLimit = Long.valueOf(memoryLines.get(0));
        // Performing right shift by 20, is similar to division by 2^20. Prefer using shift operator,
        // since it is faster than division.
        return memoryLimit >> 20;
      }
    } catch (Exception ex) {
      log.error("Error while fetching pod's limit from cgroup ", ex);
    }
    throw new RuntimeException("Unable to get pod's limit from cgroup");
  }
}