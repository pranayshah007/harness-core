/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.spotconnector;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.DecryptableEntity;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;
import io.harness.validation.OneOfField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonTypeName(SpotConstants.PERMANENT_TOKEN_CONFIG)
@ApiModel("SpotPermanentTokenConfigSpec")
@OneOfField(fields = {"spotAccountId", "spotAccountIdRef"})
@Schema(name = "SpotPermanentTokenConfigSpec", description = "This contains Spot permanent token connector spec")
@RecasterAlias("io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO")
public class SpotPermanentTokenConfigSpecDTO implements SpotCredentialSpecDTO, DecryptableEntity {
  String spotAccountId;
  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData spotAccountIdRef;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData apiTokenRef;
}
