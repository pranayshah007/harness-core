/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.mapper.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.artifact.BambooArtifactInfo;
import io.harness.polling.contracts.ArtifactPathList;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.mapper.PollingInfoBuilder;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public class BambooArtifactInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    return BambooArtifactInfo.builder()
        .connectorRef(pollingPayloadData.getConnectorRef())
        .planKey(pollingPayloadData.getBambooPayload().getPlanKey())
        .artifactPaths(mapToString(pollingPayloadData.getBambooPayload().getArtifactPathList()))
        .build();
  }

  public List<String> mapToString(List<ArtifactPathList> ngVariableList) {
    List<String> inputs = new ArrayList<>();
    for (ArtifactPathList variable : ngVariableList) {
      inputs.add(variable.getArtifactPath());
    }
    return inputs;
  }
}
