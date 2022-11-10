/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "ImportDataSpecWrapper",
    description = "This contract contains details of Import Data Specifications such as Import Data Format")
public class ImportDataSpecWrapperDTO {
  @NotNull ImportDataSpecType type;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  @NotNull
  ImportDataSpecDTO importDataSpecDTO;

  public static ImportDataSpecWrapperDTO fromImportDataSpecWrapper(ImportDataSpecWrapper importDataSpecWrapper) {
    if (importDataSpecWrapper == null) {
      return null;
    }

    ImportDataSpec importDataSpec = importDataSpecWrapper.getImportDataSpec();
    if (importDataSpec == null) {
      throw new InvalidRequestException("Import Data Spec can't be null");
    }

    return ImportDataSpecWrapperDTO.builder()
        .type(importDataSpecWrapper.getType())
        .importDataSpecDTO(importDataSpec.toImportDataSpecDTO())
        .build();
  }
}
