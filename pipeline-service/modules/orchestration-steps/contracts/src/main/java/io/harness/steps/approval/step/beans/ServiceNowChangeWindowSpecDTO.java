/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("ServiceNowChangeWindowSpec")
@Schema(name = "ServiceNowChangeWindowSpec", description = "This contains details of the ServiceNow ChangeWindow")
public class ServiceNowChangeWindowSpecDTO implements ChangeWindowSpecDTO {
  @NotNull String startField;
  @NotNull String endField;

  public static ServiceNowChangeWindowSpecDTO fromServiceNowChangeWindowSpec(
      ServiceNowChangeWindowSpec serviceNowChangeWindowSpec) {
    if (serviceNowChangeWindowSpec == null) {
      return null;
    }
    if (ParameterField.isBlank(serviceNowChangeWindowSpec.getStartField())) {
      throw new InvalidRequestException("Start Field can't be empty");
    }
    String startField = (String) serviceNowChangeWindowSpec.getStartField().fetchFinalValue();
    if (ParameterField.isBlank(serviceNowChangeWindowSpec.getEndField())) {
      throw new InvalidRequestException("End Field can't be empty");
    }
    String endField = (String) serviceNowChangeWindowSpec.getEndField().fetchFinalValue();
    return ServiceNowChangeWindowSpecDTO.builder().startField(startField).endField(endField).build();
  }
}
