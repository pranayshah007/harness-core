/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.k8Connector;

import static io.harness.ConnectorConstants.INHERIT_FROM_DELEGATE_TYPE_ERROR_MSG;
import static io.harness.ConnectorConstants.INVALID_URL_MSG;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.ConnectorConstants;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "KubernetesClusterConfig", description = "This contains kubernetes cluster config details")
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
    KubernetesCredentialSpecDTO config = credential.getConfig();
    if (config != null) {
      String masterUrl = ((KubernetesClusterDetailsDTO) config).getMasterUrl();
      if (StringUtils.isNotEmpty(masterUrl) && !checkIfStringIsValidUrl(masterUrl)) {
        throw new InvalidRequestException(INVALID_URL_MSG);
      }
    }
  }

  private boolean checkIfStringIsValidUrl(String value) {
    UrlValidator urlValidator = new UrlValidator();
    return urlValidator.isValid(value);
  }

}
