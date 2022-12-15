/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpconnector.outcome;

import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialSpecDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GcpConnectorCredentialOutcomeDTO {
  @NotNull GcpCredentialType type;
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  GcpCredentialSpecDTO spec;

  @Builder
  public GcpConnectorCredentialOutcomeDTO(GcpCredentialType gcpCredentialType, GcpCredentialSpecDTO config) {
    this.type = gcpCredentialType;
    this.spec = config;
  }
}
