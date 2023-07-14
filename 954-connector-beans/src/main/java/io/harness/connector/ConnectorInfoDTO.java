/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.ConnectorConstants.CONNECTOR_TYPES;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.ConnectorConstants;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(DX)
@Schema(name = "ConnectorInfo", description = "This has the Connector details defined in Harness")
public class ConnectorInfoDTO {
  @NotNull @NotBlank @NGEntityName @Schema(description = ConnectorConstants.CONNECTOR_NAME) String name;
  @NotNull
  @NotBlank
  @EntityIdentifier
  @Schema(description = ConnectorConstants.CONNECTOR_IDENTIFIER_MSG)
  String identifier;
  @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) String accountIdentifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = NGCommonEntityConstants.TAGS) Map<String, String> tags;

  @NotNull
  @JsonProperty(CONNECTOR_TYPES)
  @Schema(description = ConnectorConstants.CONNECTOR_TYPE)
  io.harness.delegate.beans.connector.ConnectorType connectorType;

  @JsonProperty("spec")
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = CONNECTOR_TYPES, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      visible = true)
  @Valid
  @NotNull
  io.harness.delegate.beans.connector.ConnectorConfigDTO connectorConfig;

  // Adding custom setters for Jackson to set empty string as null
  public void setAccountIdentifier(String accountIdentifier) {
    this.accountIdentifier = isEmpty(accountIdentifier) ? null : accountIdentifier;
  }
  public void setOrgIdentifier(String orgIdentifier) {
    this.orgIdentifier = isEmpty(orgIdentifier) ? null : orgIdentifier;
  }

  public void setProjectIdentifier(String projectIdentifier) {
    this.projectIdentifier = isEmpty(projectIdentifier) ? null : projectIdentifier;
  }

  @Builder
  public ConnectorInfoDTO(String name, String identifier, String description, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, Map<String, String> tags, ConnectorType connectorType,
      ConnectorConfigDTO connectorConfig) {
    this.name = name;
    this.identifier = identifier;
    this.description = description;
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = isEmpty(orgIdentifier) ? null : orgIdentifier;
    this.projectIdentifier = isEmpty(projectIdentifier) ? null : projectIdentifier;
    this.tags = tags;
    this.connectorType = connectorType;
    this.connectorConfig = connectorConfig;
  }

  public ConnectorInfoOutcomeDTO toOutcome() {
    return ConnectorInfoOutcomeDTO.builder()
        .identifier(this.identifier)
        .name(this.name)
        .description(this.description)
        .orgIdentifier(this.orgIdentifier)
        .projectIdentifier(this.projectIdentifier)
        .tags(this.tags)
        .connectorType(this.connectorType)
        .connectorConfigOutcome(this.connectorConfig.toOutcome())
        .build();
  }
}
