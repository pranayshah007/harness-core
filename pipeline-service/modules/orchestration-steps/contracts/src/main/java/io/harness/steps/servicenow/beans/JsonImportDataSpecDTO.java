/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Objects.isNull;

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
@ApiModel("JsonImportDataSpec")
@Schema(name = "JsonImportDataSpec", description = "This contains details of Json Import Data specifications")
public class JsonImportDataSpecDTO implements ImportDataSpecDTO {
  @NotNull String importDataJson;

  public static JsonImportDataSpecDTO fromJsonImportDataSpec(JsonImportDataSpec jsonImportDataSpec) {
    if (ParameterField.isNull(jsonImportDataSpec.getJsonBody())) {
      throw new InvalidRequestException("Json body can't be null");
    }
    // don't apply json validation here as intermediate value for secrets is not Json safe.
    // empty json is also allowed in import sets
    String jsonBodyString = (String) jsonImportDataSpec.getJsonBody().fetchFinalValue();
    if (isNull(jsonBodyString)) {
      throw new InvalidRequestException("Runtime value of json body can't be null");
    }
    return JsonImportDataSpecDTO.builder().importDataJson(jsonBodyString).build();
  }
}
