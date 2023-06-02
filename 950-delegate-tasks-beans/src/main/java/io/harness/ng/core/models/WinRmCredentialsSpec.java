/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.models;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmCommandParameter;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;

import software.wings.stencils.DefaultValue;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("WinRmCredentials")
@OwnedBy(CDP)
public class WinRmCredentialsSpec extends SecretSpec {
  @DefaultValue("5986") int port;
  WinRmAuth auth;
  @Builder.Default List<WinRmCommandParameter> parameters = Collections.emptyList();

  @Override
  public SecretSpecDTO toDTO() {
    return WinRmCredentialsSpecDTO.builder()
        .port(getPort())
        .auth(Optional.ofNullable(auth).map(WinRmAuth::toDTO).orElse(null))
        .parameters(parameters)
        .build();
  }
}
