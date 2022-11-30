/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.template.refresh;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.metadata.ErrorMetadataConstants;
import io.harness.exception.metadata.ErrorMetadataDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@JsonTypeName(ErrorMetadataConstants.TEMPLATE_INPUTS_VALIDATION_ERROR)
public class ValidateTemplateInputsResponseDTO implements ErrorMetadataDTO {
  boolean validYaml;
  ErrorNodeSummary errorNodeSummary;

  @Override
  public String getType() {
    return ErrorMetadataConstants.TEMPLATE_INPUTS_VALIDATION_ERROR;
  }
}
