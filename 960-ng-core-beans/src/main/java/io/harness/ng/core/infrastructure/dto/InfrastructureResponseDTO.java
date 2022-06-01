/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.dto;

import io.harness.ng.core.infrastructure.InfrastructureType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@Schema(
    name = "InfrastructureResponseDTO", description = "This is the InfrastructureResponseDTO entity defined in Harness")
public class InfrastructureResponseDTO {
  String accountId;
  String identifier;
  String orgIdentifier;
  String projectIdentifier;
  String environmentRef;
  String name;
  String description;
  Map<String, String> tags;
  InfrastructureType type;
  String yaml;
}
