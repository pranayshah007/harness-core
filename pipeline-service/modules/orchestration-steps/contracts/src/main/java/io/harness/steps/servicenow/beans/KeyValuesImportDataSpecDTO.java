/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.servicenow.ServiceNowStepUtils;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("keyValuesImportDataSpec")
@Schema(name = "KeyValuesImportDataSpec", description = "This contains details of Key-Value Import Data specifications")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public class KeyValuesImportDataSpecDTO implements ImportDataSpecDTO {
  @NotNull String importDataJson;

  public static KeyValuesImportDataSpecDTO fromKeyValuesImportDataSpec(KeyValuesImportDataSpec keyValuesCriteriaSpec) {
    Map<String, ParameterField<String>> parameterizedFieldsMap =
        ServiceNowStepUtils.processServiceNowFieldsList(keyValuesCriteriaSpec.getFields());
    Map<String, String> fieldsMap = ServiceNowStepUtils.processServiceNowFieldsInSpec(parameterizedFieldsMap, null);
    if (isNull(fieldsMap)) {
      throw new InvalidRequestException("Fields can't be null");
    }
    if (fieldsMap.isEmpty()) {
      return KeyValuesImportDataSpecDTO.builder().importDataJson("").build();
    }
    // don't apply json validation here as intermediate value for secrets is not Json safe.
    String fieldJsonString = fieldsMap.keySet()
                                 .stream()
                                 .map(key
                                     -> "\"" + key + "\""
                                         + ":"
                                         + "\"" + fieldsMap.get(key) + "\"")
                                 .collect(Collectors.joining(", ", "{", "}"));
    return KeyValuesImportDataSpecDTO.builder().importDataJson(fieldJsonString).build();
  }
}
