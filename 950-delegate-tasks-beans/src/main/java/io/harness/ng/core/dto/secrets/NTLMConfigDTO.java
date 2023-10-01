/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.models.BaseWinRmSpec;
import io.harness.ng.core.models.NTLMConfig;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonTypeName("NTLM")
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "NTLMConfig", description = "This is the NTLM configuration details defined in Harness.")
@OwnedBy(CDP)
@RecasterAlias("io.harness.ng.core.dto.secrets.NTLMConfigDTO")
public class NTLMConfigDTO implements BaseWinRmSpecDTO, DecryptableEntity {
  @Schema(description = "This is the NTLM domain name.") @NotNull private String domain;

  @Schema(description = "This is the NTLM user name.") @NotNull private String username;

  @Schema(description = "This is the NTLM either to use SSL/https .") private boolean useSSL = true;

  @Schema(description = "This is the NTLM either to skip certificate checks .") private boolean skipCertChecks = true;

  @Schema(description = "This is the NTLM powershell runs without loading profile .") private boolean useNoProfile;

  @ApiModelProperty(dataType = "string") @NotNull @SecretReference private SecretRefData password;

  @Builder
  public NTLMConfigDTO(String domain, String username, SecretRefData password, boolean useSSL, boolean skipCertChecks,
      boolean useNoProfile) {
    this.domain = domain;
    this.username = username;
    this.password = password;
    this.useSSL = useSSL;
    this.skipCertChecks = skipCertChecks;
    this.useNoProfile = useNoProfile;
  }

  @Override
  public BaseWinRmSpec toEntity() {
    return NTLMConfig.builder()
        .domain(domain)
        .username(username)
        .password(password)
        .useSSL(useSSL)
        .skipCertChecks(skipCertChecks)
        .useNoProfile(useNoProfile)
        .build();
  }
}
