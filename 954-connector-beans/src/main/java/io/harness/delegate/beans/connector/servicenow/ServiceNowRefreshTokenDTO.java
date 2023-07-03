/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@OwnedBy(CDC)
@Data
@Builder
@Schema(
    name = "ServiceNowRefreshToken", description = "This entity contains the details of the Service Now Refresh Token")
public class ServiceNowRefreshTokenDTO implements ServiceNowAuthCredentialsDTO {
  @URL @NotNull @NotBlank String tokenUrl;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData refreshTokenRef;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData clientIdRef;
  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData clientSecretRef;
  String scope;

  @Override
  public void validate() {
    //  nothing to validate here
  }
}
