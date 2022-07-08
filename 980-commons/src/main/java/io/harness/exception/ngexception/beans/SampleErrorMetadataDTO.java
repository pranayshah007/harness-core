/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.ngexception.beans;

import io.harness.exception.ngexception.ErrorMetadataConstants;
import io.harness.exception.ngexception.ErrorMetadataDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "SampleErrorMetadata", description = "This has error messages.")
@JsonTypeName(ErrorMetadataConstants.SAMPLE_ERROR)
public class SampleErrorMetadataDTO implements ErrorMetadataDTO {
  private Map<String, String> sampleMap;

  @Override
  public String getType() {
    return ErrorMetadataConstants.SAMPLE_ERROR;
  }
}
