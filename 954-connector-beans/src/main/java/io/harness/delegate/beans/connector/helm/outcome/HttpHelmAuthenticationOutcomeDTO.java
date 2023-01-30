/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.helm.outcome;

import io.harness.delegate.beans.connector.helm.HttpHelmAuthCredentialsDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpHelmAuthenticationOutcomeDTO {
  @NotNull HttpHelmAuthType type;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  HttpHelmAuthCredentialsDTO spec;

  @Builder
  public HttpHelmAuthenticationOutcomeDTO(HttpHelmAuthType type, HttpHelmAuthCredentialsDTO spec) {
    this.type = type;
    this.spec = spec;
  }
}
