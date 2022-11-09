/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.servicenow.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@OwnedBy(CDC)
@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes(value =
    {
      @JsonSubTypes.Type(value = JsonImportDataSpec.class, name = ImportDataSpecTypeConstants.JSON)
      , @JsonSubTypes.Type(value = KeyValuesImportDataSpec.class, name = ImportDataSpecTypeConstants.KEY_VALUES)
    })
public interface ImportDataSpec {
  @JsonIgnore ImportDataSpecType getType();
  @JsonIgnore ImportDataSpecDTO toImportDataSpecDTO();
}
