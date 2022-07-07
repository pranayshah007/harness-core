/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.ngexception.beans.templateservice;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ngexception.ErrorMetadataConstants;
import io.harness.exception.ngexception.ErrorMetadataDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CDC)
@JsonTypeName(ErrorMetadataConstants.TEMPLATE_INPUTS_ERROR)
public class TemplateInputsErrorMetadataDTO implements ErrorMetadataDTO {
  String errorYaml;
  Map<String, TemplateInputsErrorDTO> errorMap;

  @Builder
  public TemplateInputsErrorMetadataDTO(String errorYaml, Map<String, TemplateInputsErrorDTO> errorMap) {
    this.errorMap = errorMap;
    this.errorYaml = errorYaml;
  }

  @Override
  public String getType() {
    return ErrorMetadataConstants.TEMPLATE_INPUTS_ERROR;
  }
}
