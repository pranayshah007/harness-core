/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans.response;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.data.validator.Trimmed;
import io.harness.encryption.Scope;
import io.harness.freeze.beans.CurrentOrUpcomingWindow;
import io.harness.freeze.beans.FreezeEntityRule;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.FreezeWindow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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
@ApiModel("FreezeSummaryResponse")
@Schema(name = "FreezeSummaryResponse", description = "This contains summary of the Freeze Response")
@EqualsAndHashCode
@RecasterAlias("io.harness.freeze.beans.response.FreezeSummaryResponseDTO")
public class FreezeSummaryResponseDTO {
  @NotEmpty String accountId;

  FreezeType type;
  FreezeStatus status;

  @NotNull @EntityName String name;
  @Size(max = 1024) String description;
  Map<String, String> tags;

  @With @Trimmed String orgIdentifier;
  @With @Trimmed String projectIdentifier;

  List<FreezeWindow> windows;
  @JsonIgnore List<FreezeEntityRule> rules;

  CurrentOrUpcomingWindow currentOrUpcomingWindow;

  @With @NotEmpty @EntityIdentifier String identifier;

  long createdAt;
  long lastUpdatedAt;

  Scope freezeScope;

  String yaml;
}
