/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.change;

import io.harness.cvng.beans.change.ChangeCategory;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class ChangeTimeline {
  @Singular("categoryTimeline") Map<ChangeCategory, List<TimeRangeDetail>> categoryTimeline;

  @Data
  @Builder
  public static class TimeRangeDetail {
    Long count;
    Long startTime;
    Long endTime;

    public Long incrementCount(int count) {
      return this.count = this.count + count;
    }
  }
}
