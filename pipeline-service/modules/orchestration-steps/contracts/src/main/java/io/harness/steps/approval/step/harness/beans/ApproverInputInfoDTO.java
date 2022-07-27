/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "ApproverInputInfo", description = "This contains details of Approver Inputs")
public class ApproverInputInfoDTO {
  @NotEmpty String name;
  String defaultValue;

  public static ApproverInputInfoDTO fromApproverInputInfo(ApproverInputInfo approverInput) {
    if (approverInput == null) {
      return null;
    }

    return ApproverInputInfoDTO.builder()
        .name(approverInput.getName())
        .defaultValue((String) approverInput.getDefaultValue().fetchFinalValue())
        .build();
  }
}
