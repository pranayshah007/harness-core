/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.k8Connector.outcome;

import io.harness.delegate.beans.connector.k8Connector.KubernetesConfigConstants;

import com.fasterxml.jackson.annotation.JsonSubTypes;

@JsonSubTypes({
  @JsonSubTypes.
  Type(value = KubernetesClusterDetailsOutcomeDTO.class, name = KubernetesConfigConstants.MANUAL_CREDENTIALS)
  ,
      @JsonSubTypes.Type(
          value = KubernetesDelegateDetailsOutcomeDTO.class, name = KubernetesConfigConstants.INHERIT_FROM_DELEGATE)
})
public interface KubernetesCredentialSpecOutcomeDTO {}
