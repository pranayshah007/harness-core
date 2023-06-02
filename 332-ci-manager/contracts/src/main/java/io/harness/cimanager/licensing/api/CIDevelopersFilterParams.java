/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.licensing.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.licensing.usage.params.filter.FilterParams;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.CI)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("CIDevelopersFilterParams")
@Schema(name = "CIDevelopersFilterParams", description = "CI Developer Usage Filter Params")
public class CIDevelopersFilterParams implements FilterParams {
  @Schema(description = "Organization identifier of the Entity.")
  @EntityIdentifier(allowBlank = true)
  String orgIdentifier;

  @Schema(description = "Project identifier of the Entity.")
  @EntityIdentifier(allowBlank = true)
  String projectIdentifier;

  @Schema(description = "Developer identifier or name of the Entity.")
  @EntityIdentifier(allowBlank = true)
  String developer;
}
