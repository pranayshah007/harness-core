/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.models.SecretSpec;
import io.harness.ng.core.models.WinRmCredentialsSpec;

import software.wings.stencils.DefaultValue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.Attributes;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("WinRmCredentials")
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "WinRmCredentialsSpec", description = "This is the WinRm authentication details defined in Harness.")
@OwnedBy(CDP)
public class WinRmCredentialsSpecDTO extends SecretSpecDTO {
  @Schema(description = "WinRm port") @DefaultValue("5986") int port = 5986;
  @Valid @NotNull WinRmAuthDTO auth;
  @Attributes(title = "Parameters") private List<WinRmCommandParameter> parameters = Collections.emptyList();

  @Override
  @JsonIgnore
  public Optional<String> getErrorMessageForInvalidYaml() {
    return Optional.empty();
  }

  @Override
  public SecretSpec toEntity() {
    return WinRmCredentialsSpec.builder().port(getPort()).auth(this.auth.toEntity()).parameters(parameters).build();
  }

  @Builder
  public WinRmCredentialsSpecDTO(int port, WinRmAuthDTO auth, List<WinRmCommandParameter> parameters) {
    this.port = port;
    this.auth = auth;
    this.parameters = parameters;
  }
}
