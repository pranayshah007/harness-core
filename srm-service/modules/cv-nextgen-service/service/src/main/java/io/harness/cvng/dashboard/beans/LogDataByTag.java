/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.beans;

import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
public class LogDataByTag implements Comparable<LogDataByTag> {
  private Long timestamp;
  private List<CountByTag> countByTags;

  public void addCountByTag(CountByTag countByTag) {
    if (countByTags == null) {
      countByTags = new ArrayList<>();
    }
    countByTags.add(countByTag);
  }

  @Override
  public int compareTo(@NotNull LogDataByTag o) {
    return this.timestamp.compareTo(o.timestamp);
  }

  @Data
  @Builder
  public static class CountByTag {
    LogAnalysisTag tag;
    int count;
  }
}
