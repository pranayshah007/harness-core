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
import io.harness.ng.core.models.BaseWinRmSpec;
import io.harness.ng.core.models.KerberosWinRmConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("Kerberos")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
@OwnedBy(CDP)
@RecasterAlias("io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO")
public class KerberosWinRmConfigDTO extends KerberosBaseConfigDTO implements BaseWinRmSpecDTO {
  @Schema(description = "This is the Kerberos either to use SSL/https .") private boolean useSSL = true;

  @Schema(description = "This is the Kerberos either to skip certificate checks .")
  private boolean skipCertChecks = true;

  @Schema(description = "This is the Kerberos powershell runs without loading profile .") private boolean useNoProfile;

  @Override
  public BaseWinRmSpec toEntity() {
    return KerberosWinRmConfig.builder()
        .useSSL(useSSL)
        .skipCertChecks(skipCertChecks)
        .useNoProfile(useNoProfile)
        .principal(getPrincipal())
        .realm(getRealm())
        .tgtGenerationMethod(getTgtGenerationMethod())
        .spec(Optional.ofNullable(getSpec()).map(TGTGenerationSpecDTO::toEntity).orElse(null))
        .build();
  }
}
