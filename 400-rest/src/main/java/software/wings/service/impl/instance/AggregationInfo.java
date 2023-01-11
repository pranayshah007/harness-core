/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.helm.HelmChartInfo;

import software.wings.beans.instance.dashboard.EntitySummary;

import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@OwnedBy(DX)
public final class AggregationInfo {
  @Id private ID _id;
  private long count;
  private EntitySummary appInfo;
  private EntitySummary serviceInfo;
  private EntitySummary infraMappingInfo;
  private EnvInfo envInfo;
  private ArtifactInfo artifactInfo;
  private HelmChartInfo helmChartInfo;
  private List<EntitySummary> instanceInfoList;

  @Data
  @NoArgsConstructor
  public static final class ID {
    private String serviceId;
    private String envId;
    private String lastArtifactId;
  }
}
