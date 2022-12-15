/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.k8Connector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@JsonTypeName(KubernetesConfigConstants.CLIENT_KEY_CERT)
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDP)
@Schema(name = "KubernetesClientKeyCert", description = "This contains kubernetes client key certificate details")
public class KubernetesClientKeyCertDTO extends KubernetesAuthCredentialDTO {
  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData caCertRef;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData clientCertRef;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData clientKeyRef;
  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData clientKeyPassphraseRef;
  String clientKeyAlgo;
}
