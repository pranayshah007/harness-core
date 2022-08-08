/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * Deprecated in CVNG
 */
@Value
@Builder
@Deprecated
public class SplunkValidationResponse {
  Histogram histogram;
  SplunkSampleResponse samples;
  String errorMessage;
  long queryDurationMillis;

  @Value
  @Builder
  public static class SplunkSampleResponse {
    List<SampleLog> rawSampleLogs;
    Map<String, String> sample;
    String splunkQuery;
    String errorMessage;
  }
  @Value
  @Builder
  public static class SampleLog {
    String raw;
    long timestamp;
  }
  @Value
  @Builder
  public static class Histogram {
    String query;
    long intervalMs;
    @Singular("addBar") List<Bar> bars;
    String errorMessage;
    String splunkQuery;
    long count;
    @Value
    @Builder
    public static class Bar {
      long timestamp;
      long count;
    }
  }
}
