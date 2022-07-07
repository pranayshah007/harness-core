/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.approval.stage;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@TypeAlias("approvalStageSpecParameters")
@RecasterAlias("io.harness.steps.approval.stage.ApprovalStageSpecParameters")
public class ApprovalStageSpecParameters implements SpecParameters {
  String childNodeID;

  public static ApprovalStageSpecParameters getStepParameters(String childNodeID) {
    return ApprovalStageSpecParameters.builder().childNodeID(childNodeID).build();
  }
}
