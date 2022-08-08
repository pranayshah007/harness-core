/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.request.HttpRequestInfo;
import io.harness.request.RequestMetadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@Schema(name = "AuditEvent", description = "This has the AuditEvent details defined in Harness.")
public class AuditEventDTO {
  @Schema(description = "Identifier of the Audit.") String auditId;
  @Schema(description = "Insert Identifier of the Audit.") @NotNull @NotBlank String insertId;
  @Valid @NotNull ResourceScopeDTO resourceScope;

  @Valid HttpRequestInfo httpRequestInfo;
  @Valid RequestMetadata requestMetadata;

  @NotNull Long timestamp;

  @NotNull @Valid AuthenticationInfoDTO authenticationInfo;

  @Schema(description = "Type of module associated with the Audit.") @NotNull ModuleType module;
  @Valid Environment environment;

  @NotNull @Valid ResourceDTO resource;

  @ApiModelProperty(hidden = true) @Valid YamlDiffRecordDTO yamlDiffRecord;

  @Schema(description = "Action type associated with the Audit.") @NotNull Action action;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
  @Valid
  AuditEventData auditEventData;

  @Schema(description = "Internal information.") @ApiModelProperty(hidden = true) Map<String, String> internalInfo;
}
