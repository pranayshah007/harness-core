/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.appmanifest;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDC)
public class ManifestSummary {
  public static final String CHART_URL = "url";

  private String uuid;
  private String versionNo;
  private String name;
  private String source;
  private String appManifestName;
  private String appManifestId;

  public static ManifestSummary prepareSummaryFromHelmChart(HelmChart helmChart) {
    if (helmChart == null) {
      return null;
    }
    ManifestSummaryBuilder manifestSummaryBuilder = ManifestSummary.builder()
                                                        .uuid(helmChart.getUuid())
                                                        .versionNo(helmChart.getVersion())
                                                        .appManifestId(helmChart.getApplicationManifestId())
                                                        .appManifestName(helmChart.getAppManifestName())
                                                        .name(helmChart.getName());
    Map<String, String> metadata = helmChart.getMetadata();
    if (EmptyPredicate.isNotEmpty(helmChart.getMetadata())) {
      manifestSummaryBuilder.source(metadata.get(CHART_URL));
    }
    return manifestSummaryBuilder.build();
  }
}
