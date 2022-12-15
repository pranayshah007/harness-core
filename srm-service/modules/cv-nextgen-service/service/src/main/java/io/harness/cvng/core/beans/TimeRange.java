/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TimeRange {
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC") Instant startTime;
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC") Instant endTime;

  @JsonIgnore
  public Duration getDuration() {
    return Duration.between(startTime, endTime);
  }
}
