/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.SourceCodeManagerAuthentication;
import io.harness.delegate.beans.connector.scm.GitAuthType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// this is dummy connector dto for Gitness
@OwnedBy(HarnessTeam.DX)
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("HarnessAuthentication")
@Schema(
    name = "HarnessAuthentication", description = "This contains details of the information needed for Harness access")
public class HarnessAuthenticationDTO implements SourceCodeManagerAuthentication {
  @NotNull @JsonProperty("type") GitAuthType authType;
  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  @NotNull
  HarnessHttpCredentialsDTO credentials;

  @Builder
  public HarnessAuthenticationDTO(GitAuthType authType, HarnessHttpCredentialsDTO credentials) {
    this.authType = authType;
    this.credentials = credentials;
  }
}
