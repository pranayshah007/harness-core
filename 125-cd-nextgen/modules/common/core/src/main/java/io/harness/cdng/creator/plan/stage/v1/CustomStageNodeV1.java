/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.stages.stage.v1.AbstractStageNodeV1;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Value;

@Value
@JsonTypeName(YAMLFieldNameConstants.CUSTOM_V1)
@OwnedBy(PIPELINE)
public class CustomStageNodeV1 extends AbstractStageNodeV1 {
  String type = YAMLFieldNameConstants.CUSTOM_V1;
  CustomStageConfigV1 spec;
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) ParameterField<List<FailureConfigV1>> failure;

  @Override
  public String getType() {
    return YAMLFieldNameConstants.CUSTOM_V1;
  }
}
