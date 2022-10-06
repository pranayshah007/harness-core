/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.artifactoryconnector;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(CDC)
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("ArtifactoryAuthentication")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(using = ArtifactoryAuthDTODeserializer.class)
@Schema(
    name = "ArtifactoryAuthentication", description = "This entity contains the details for Artifactory Authentication")
public class ArtifactoryAuthenticationDTO {
  @NotNull @JsonProperty("type") ArtifactoryAuthType authType;

  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  ArtifactoryAuthCredentialsDTO credentials;

  @Builder
  public ArtifactoryAuthenticationDTO(ArtifactoryAuthType authType, ArtifactoryAuthCredentialsDTO credentials) {
    this.authType = authType;
    this.credentials = credentials;
  }
}
