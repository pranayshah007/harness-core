/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.ngexception.beans.yamlschema;

import io.harness.exception.ngexception.ErrorMetadataConstants;
import io.harness.exception.ngexception.ErrorMetadataDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("YamlSchemaErrorWrapperDTO")
@JsonTypeName(ErrorMetadataConstants.YAML_SCHEMA_ERROR)
public class YamlSchemaErrorWrapperDTO implements ErrorMetadataDTO {
  List<YamlSchemaErrorDTO> schemaErrors;

  @Override
  public String getType() {
    return ErrorMetadataConstants.YAML_SCHEMA_ERROR;
  }
}
