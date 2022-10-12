/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.spotconnector;

import io.harness.beans.DecryptableEntity;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;
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
@JsonTypeName(SpotConstants.MANUAL_CONFIG)
@ApiModel("SpotManualConfigSpec")
@OneOfField(fields = {"accountId", "accountIdRef"})
@Schema(name = "SpotManualConfigSpec", description = "This contains Spot manual credentials connector spec")
public class SpotManualConfigSpecDTO implements SpotCredentialSpecDTO, DecryptableEntity {
  String accountId;
  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData accountIdRef;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData apiTokenRef;
}
