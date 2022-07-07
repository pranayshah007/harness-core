/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset;

import io.harness.exception.ngexception.ErrorMetadataConstants;
import io.harness.exception.ngexception.ErrorMetadataDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("OverlayInputSetErrorWrapper")
@JsonTypeName(ErrorMetadataConstants.OVERLAY_INPUT_SET_ERROR)
public class OverlayInputSetErrorWrapperDTOPMS implements ErrorMetadataDTO {
  Map<String, String> invalidReferences;

  @Override
  public String getType() {
    return ErrorMetadataConstants.OVERLAY_INPUT_SET_ERROR;
  }
}
