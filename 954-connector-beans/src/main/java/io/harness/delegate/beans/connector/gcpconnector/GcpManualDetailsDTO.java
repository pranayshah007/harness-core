/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpconnector;

import io.harness.annotation.RecasterAlias;
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

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(GcpConstants.MANUAL_CONFIG)
@ApiModel("GcpManualDetails")
@Schema(name = "GcpManualDetails", description = "This contains GCP manual credentials details")
@RecasterAlias("io.harness.delegate.beans.ConnectorDisconnectHandler.gcpconnector.GcpManualDetailsDTO")
public class GcpManualDetailsDTO implements GcpCredentialSpecDTO {
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData secretKeyRef;
}
