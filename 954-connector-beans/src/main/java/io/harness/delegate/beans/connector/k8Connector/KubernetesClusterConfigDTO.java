/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.k8Connector;

import static io.harness.ConnectorConstants.INHERIT_FROM_DELEGATE_TYPE_ERROR_MSG;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesAuthOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesClusterConfigOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesClusterDetailsOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesCredentialOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesCredentialSpecOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesDelegateDetailsOutcomeDTO;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "KubernetesClusterConfig", description = "This contains kubernetes cluster config details")
@RecasterAlias("io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO")
public class KubernetesClusterConfigDTO extends ConnectorConfigDTO implements DelegateSelectable {
  @Valid @NotNull KubernetesCredentialDTO credential;
  Set<String> delegateSelectors;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (credential.getKubernetesCredentialType() == KubernetesCredentialType.MANUAL_CREDENTIALS) {
      KubernetesClusterDetailsDTO k8sManualCreds = (KubernetesClusterDetailsDTO) credential.getConfig();
      return Collections.singletonList(k8sManualCreds.getAuth().getCredentials());
    }
    return null;
  }

  @Override
  public void validate() {
    if (KubernetesCredentialType.INHERIT_FROM_DELEGATE.equals(credential.getKubernetesCredentialType())
        && isEmpty(delegateSelectors)) {
      throw new InvalidRequestException(INHERIT_FROM_DELEGATE_TYPE_ERROR_MSG);
    }
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    KubernetesCredentialSpecOutcomeDTO spec = null;
    if (this.credential.getConfig() instanceof KubernetesClusterDetailsDTO) {
      KubernetesAuthDTO auth = ((KubernetesClusterDetailsDTO) this.credential.getConfig()).getAuth();
      KubernetesAuthOutcomeDTO authOutcomeDTO =
          KubernetesAuthOutcomeDTO.builder().authType(auth.getAuthType()).credentials(auth.getCredentials()).build();
      spec = KubernetesClusterDetailsOutcomeDTO.builder()
                 .masterUrl(((KubernetesClusterDetailsDTO) this.credential.getConfig()).getMasterUrl())
                 .auth(authOutcomeDTO)
                 .build();
    } else if (this.credential.getConfig() instanceof KubernetesDelegateDetailsDTO) {
      spec = KubernetesDelegateDetailsOutcomeDTO.builder().build();
    }

    return KubernetesClusterConfigOutcomeDTO.builder()
        .credential(KubernetesCredentialOutcomeDTO.builder()
                        .kubernetesCredentialType(this.credential.getKubernetesCredentialType())
                        .config(spec)
                        .build())
        .delegateSelectors(delegateSelectors)
        .build();
  }
}
