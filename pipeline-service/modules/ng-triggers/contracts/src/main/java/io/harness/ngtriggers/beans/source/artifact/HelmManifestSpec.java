/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.artifact;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.Constants.HELM_CHART;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.artifact.store.BuildStore;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(PIPELINE)
public class HelmManifestSpec implements ManifestTypeSpec {
  List<TriggerEventDataCondition> eventConditions;
  String chartName;
  String chartVersion;
  BuildStore store;

  @Override
  public List<TriggerEventDataCondition> fetchEventDataConditions() {
    return eventConditions;
  }

  @Override
  public String fetchBuildType() {
    return HELM_CHART;
  }
}
