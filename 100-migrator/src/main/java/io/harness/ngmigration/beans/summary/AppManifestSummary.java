/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.beans.summary;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.CDC)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppManifestSummary extends BaseSummary {
  Map<String, Long> kindSummary;
  Map<String, Long> storeSummary;

  @Builder
  public AppManifestSummary(int count, Map<String, Long> kindSummary, Map<String, Long> storeSummary) {
    super(count);
    this.kindSummary = kindSummary;
    this.storeSummary = storeSummary;
  }
}
