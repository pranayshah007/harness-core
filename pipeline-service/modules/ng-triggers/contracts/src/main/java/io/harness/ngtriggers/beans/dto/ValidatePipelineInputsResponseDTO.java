/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.ErrorMetadataDTO;
import io.harness.ngtriggers.Constants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@JsonTypeName(Constants.PIPELINE_INPUTS_VALIDATION_ERROR)
public class ValidatePipelineInputsResponseDTO implements ErrorMetadataDTO {
  Map<String, Map<String, String>> errorMap;
  boolean validYaml;

  @Override
  public String getType() {
    return Constants.PIPELINE_INPUTS_VALIDATION_ERROR;
  }
}
