/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.k8Connector;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

@Data
@Builder
@JsonTypeName(KubernetesConfigConstants.MANUAL_CREDENTIALS)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "KubernetesClusterDetails", description = "This contains kubernetes cluster details")
@RecasterAlias("io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO")
public class KubernetesClusterDetailsDTO implements KubernetesCredentialSpecDTO {
  @NotBlank @NotNull String masterUrl;
  @JsonProperty("auth") @NotNull @Valid KubernetesAuthDTO auth;
}
