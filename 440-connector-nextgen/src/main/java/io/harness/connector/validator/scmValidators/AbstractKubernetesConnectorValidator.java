/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator.scmValidators;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.validator.AbstractConnectorValidator;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.rancher.RancherAuthType;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;

@OwnedBy(HarnessTeam.DX)
public abstract class AbstractKubernetesConnectorValidator extends AbstractConnectorValidator {
  protected List<EncryptedDataDetail> fetchEncryptionDetailsList(KubernetesClusterConfigDTO kubernetesClusterConfig,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (kubernetesClusterConfig.getCredential().getKubernetesCredentialType()
        == KubernetesCredentialType.MANUAL_CREDENTIALS) {
      KubernetesAuthCredentialDTO kubernetesAuthCredential = getKubernetesAuthCredential(
          (KubernetesClusterDetailsDTO) kubernetesClusterConfig.getCredential().getConfig());
      return super.getEncryptionDetail(kubernetesAuthCredential, accountIdentifier, orgIdentifier, projectIdentifier);
    }
    return null;
  }

  protected List<EncryptedDataDetail> fetchEncryptionDetailsList(RancherConnectorDTO rancherClusterConfig,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (rancherClusterConfig.getConfig().getConfig().getCredentials().getAuthType() == RancherAuthType.BEARER_TOKEN) {
      return super.getEncryptionDetail(rancherClusterConfig.getConfig().getConfig().getCredentials().getAuth(),
          accountIdentifier, orgIdentifier, projectIdentifier);
    }
    return null;
  }

  private KubernetesAuthCredentialDTO getKubernetesAuthCredential(
      KubernetesClusterDetailsDTO kubernetesClusterConfigDTO) {
    return kubernetesClusterConfigDTO.getAuth().getCredentials();
  }
}
