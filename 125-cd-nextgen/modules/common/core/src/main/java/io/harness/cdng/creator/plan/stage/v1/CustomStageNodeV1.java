/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Value;

@Value
@JsonTypeName(YAMLFieldNameConstants.CUSTOM_V1)
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.cdng.creator.plan.stage.v1.CustomStageNodeV1")
public class CustomStageNodeV1 extends CustomAbstractStageNodeV1 {
  String type = YAMLFieldNameConstants.CUSTOM_V1;
  CustomStageConfigV1 spec;

  @Override
  public String getType() {
    return YAMLFieldNameConstants.CUSTOM_V1;
  }
}
