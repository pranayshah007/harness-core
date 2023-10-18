/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.customstage.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.stages.v1.PmsAbstractStageNodeV1;
import io.harness.steps.StepSpecTypeConstantsV1;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;

@Data
@JsonTypeName(StepSpecTypeConstantsV1.CUSTOM_STAGE)
@OwnedBy(PIPELINE)
public class CustomStageNodeV1 extends PmsAbstractStageNodeV1 {
  String type = StepSpecTypeConstantsV1.CUSTOM_STAGE;
  CustomStageConfigV1 spec;

  @Override
  public String getType() {
    return StepSpecTypeConstantsV1.CUSTOM_STAGE;
  }
}
