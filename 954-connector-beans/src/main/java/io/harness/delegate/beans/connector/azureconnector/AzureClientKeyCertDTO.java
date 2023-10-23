/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureClientKeyCertOutcomeDTO;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@JsonTypeName(AzureConstants.KEY_CERT)
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDP)
@Schema(name = "AzureClientKeyCert", description = "This contains azure client key certificate details")
@RecasterAlias("io.harness.delegate.beans.ConnectorDisconnectHandler.azureconnector.AzureClientKeyCertDTO")
public class AzureClientKeyCertDTO extends AzureAuthCredentialDTO {
  @ApiModelProperty(dataType = "string")
  @JsonProperty("certificateRef")
  @NotNull
  @SecretReference
  SecretRefData clientCertRef;
  @Override
  public AzureClientKeyCertOutcomeDTO toOutcome() {
    return AzureClientKeyCertOutcomeDTO.builder().certificateRef(this.clientCertRef).build();
  }
}
