/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.encryption.Scope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.With;

@OwnedBy(CDC)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("FreezeResponse")
@Schema(name = "FreezeResponse", description = "This contains details of the Freeze Response")
@EqualsAndHashCode
public class FreezeResponseDTO implements FreezeResponse {
  @NotEmpty String accountId;

  FreezeType type;
  FreezeStatus status;

  @NotNull @EntityName String name;
  @Size(max = 1024) String description;
  Map<String, String> tags;

  @With @Trimmed String orgIdentifier;
  @With @Trimmed String projectIdentifier;

  @With @NotEmpty @EntityIdentifier String identifier;
  @NotEmpty String yaml;

  long createdAt;
  long lastUpdatedAt;

  Scope freezeScope;

  private EmbeddedUser createdBy;
  private EmbeddedUser lastUpdatedBy;
}
