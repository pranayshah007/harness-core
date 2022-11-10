/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.mapper.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.artifact.CustomArtifactInfo;
import io.harness.polling.contracts.NGVariable;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.mapper.PollingInfoBuilder;
import io.harness.yaml.core.variables.NGVariableType;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public class CustomArtifactInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    return CustomArtifactInfo.builder()
        .script(pollingPayloadData.getCustomPayload().getScript())
        .artifactsArrayPath(pollingPayloadData.getCustomPayload().getArtifactsArrayPath())
        .versionPath(pollingPayloadData.getCustomPayload().getVersionPath())
        .inputs(mapToNgVariable(pollingPayloadData.getCustomPayload().getNgVariableList()))
        .build();
  }

  public List<io.harness.yaml.core.variables.NGVariable> mapToNgVariable(List<NGVariable> ngVariableList) {
    List<io.harness.yaml.core.variables.NGVariable> inputs = new ArrayList<>();
    for (NGVariable variable : ngVariableList) {
      inputs.add(io.harness.yaml.core.variables.StringNGVariable.builder()
                     .name(variable.getName())
                     .value(ParameterField.createValueField(variable.getValue()))
                     .type(NGVariableType.valueOf(variable.getType()))
                     .build());
    }
    return inputs;
  }
}
