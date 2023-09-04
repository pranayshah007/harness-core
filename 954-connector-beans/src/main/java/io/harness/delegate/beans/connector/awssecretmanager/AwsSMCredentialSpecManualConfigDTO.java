/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awssecretmanager;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PL)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(AwsSecretManagerConstants.MANUAL_CONFIG)
@ApiModel("AwsSMCredentialSpecManualConfig")
@Schema(name = "AwsSMCredentialSpecManualConfig",
    description = "Returns secret reference access key and secret key of AWS Secret Manager.")
@RecasterAlias("io.harness.delegate.beans.connector.awssecretmanager.AwsSMCredentialSpecManualConfigDTO")
public class AwsSMCredentialSpecManualConfigDTO implements AwsSecretManagerCredentialSpecDTO {
  @SecretReference
  @ApiModelProperty(dataType = "string")
  @Schema(description = SecretManagerDescriptionConstants.ACCESS_KEY)
  @NotNull
  private SecretRefData accessKey;
  @SecretReference
  @ApiModelProperty(dataType = "string")
  @NotNull
  @Schema(description = SecretManagerDescriptionConstants.SECRET_KEY)
  private SecretRefData secretKey;
}
