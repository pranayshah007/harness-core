/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.k8Connector.outcome;

import io.harness.delegate.beans.connector.k8Connector.KubernetesConfigConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

@Data
@Builder
@JsonTypeName(KubernetesConfigConstants.MANUAL_CREDENTIALS)
@JsonIgnoreProperties(ignoreUnknown = true)
public class KubernetesClusterDetailsOutcomeDTO implements KubernetesCredentialSpecOutcomeDTO {
  @NotBlank @NotNull String masterUrl;
  @NotNull @Valid KubernetesAuthOutcomeDTO auth;
}
