/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.models;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.secrets.BaseWinRmSpecDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonTypeName("NTLM")
@OwnedBy(CDP)
public class NTLMConfig implements BaseWinRmSpec {
  private String domain;
  private String username;
  private boolean useSSL;
  private boolean skipCertChecks;
  private boolean useNoProfile;
  private SecretRefData password;

  @Override
  public BaseWinRmSpecDTO toDTO() {
    return NTLMConfigDTO.builder()
        .domain(domain)
        .username(username)
        .password(password)
        .useSSL(useSSL)
        .skipCertChecks(skipCertChecks)
        .useNoProfile(useNoProfile)
        .build();
  }
}
