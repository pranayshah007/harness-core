/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.params.filterParams;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;

import java.util.List;
import lombok.AccessLevel;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Value
public class LiveMonitoringLogAnalysisFilter extends LogAnalysisFilter {
  List<LogAnalysisTag> clusterTypes;

  public boolean filterByClusterTypes() {
    return isNotEmpty(clusterTypes);
  }
}
