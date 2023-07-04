/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.k8Connector;

import io.harness.annotation.RecasterAlias;
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
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(KubernetesConfigConstants.SERVICE_ACCOUNT)
@Schema(name = "KubernetesServiceAccount", description = "This contains kubernetes service account details")
@RecasterAlias("io.harness.delegate.beans.connector.k8Connector.KubernetesServiceAccountDTO")
public class KubernetesServiceAccountDTO extends KubernetesAuthCredentialDTO {
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData serviceAccountTokenRef;
  @ApiModelProperty(dataType = "string") @SecretReference SecretRefData caCertRef;
}
