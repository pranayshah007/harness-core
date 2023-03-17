/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.environment.dto;

import io.harness.beans.IdentifierRef;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "EnvironmentResponseDetails", description = "This is the Environment entity defined in Harness")
public class EnvironmentResponseDTO {
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String identifier;
  String name;
  String description;
  String color;
  EnvironmentType type;
  boolean deleted;
  Map<String, String> tags;
  @JsonIgnore Long version;
  String yaml;

  @JsonIgnore
  public String getFullyQualifiedIdentifier() {
    IdentifierRef envIdentifierRef = FullyQualifiedIdentifierHelper.getIdentifierRefWithScope(
        accountId, orgIdentifier, projectIdentifier, identifier);
    return envIdentifierRef.buildScopedIdentifier();
  }
}
