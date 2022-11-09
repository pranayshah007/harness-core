/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.instance.dashboard;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Artifact information
 * @author rktummala on 08/13/17
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArtifactSummary extends EntitySummary {
  private String artifactSourceName;
  private String buildNo;
  private Map<String, Object> artifactParameters;
  private long lastDeployedAt;

  @Builder
  public ArtifactSummary(String id, String name, String type, String artifactSourceName, String buildNo,
      Map<String, Object> artifactParameters, long lastDeployedAt) {
    super(id, name, type);
    this.artifactSourceName = artifactSourceName;
    this.buildNo = buildNo;
    this.artifactParameters = artifactParameters;
    this.lastDeployedAt = lastDeployedAt;
  }

  public static class ArtifactSummaryBuilder extends EntitySummaryBuilder {
    ArtifactSummaryBuilder() {}
  }
}
