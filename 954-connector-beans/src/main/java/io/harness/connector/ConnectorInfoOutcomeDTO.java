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

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.ConnectorType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
@RecasterAlias("io.harness.connector.ConnectorInfoOutcomeDTO")
public class ConnectorInfoOutcomeDTO {
  @NotNull @NotBlank @NGEntityName String name;
  @NotNull @NotBlank @EntityIdentifier String identifier;
  String description;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  Map<String, String> tags;

  @NotNull ConnectorType type;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = CONNECTOR_TYPES, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      visible = true)
  @Valid
  @NotNull
  ConnectorConfigOutcomeDTO spec;

  // Adding custom setters for Jackson to set empty string as null
  public void setOrgIdentifier(String orgIdentifier) {
    this.orgIdentifier = isEmpty(orgIdentifier) ? null : orgIdentifier;
  }

  public void setProjectIdentifier(String projectIdentifier) {
    this.projectIdentifier = isEmpty(projectIdentifier) ? null : projectIdentifier;
  }

  @Builder
  public ConnectorInfoOutcomeDTO(String name, String identifier, String description, String orgIdentifier,
      String projectIdentifier, Map<String, String> tags, ConnectorType connectorType,
      ConnectorConfigOutcomeDTO connectorConfigOutcome) {
    this.name = name;
    this.identifier = identifier;
    this.description = description;
    this.orgIdentifier = isEmpty(orgIdentifier) ? null : orgIdentifier;
    this.projectIdentifier = isEmpty(projectIdentifier) ? null : projectIdentifier;
    this.tags = tags;
    this.type = connectorType;
    this.spec = connectorConfigOutcome;
  }
}
