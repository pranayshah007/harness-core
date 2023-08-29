/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpsecretmanager;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.gcpsecretmanager.outcome.GcpSecretManagerConnectorOutcomeDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.BooleanUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"credentials"})
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("GcpSecretManager")
@OwnedBy(HarnessTeam.PL)
@Schema(name = "GcpSecretManager", description = "This contains details of GCP Secret Manager")
public class GcpSecretManagerConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable {
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;

  @ApiModelProperty(dataType = "string")
  @Schema(description = SecretManagerDescriptionConstants.GOOGLE_SECRET_MANAGER_CREDENTIALS)
  @SecretReference
  SecretRefData credentialsRef;

  @Schema(description = SecretManagerDescriptionConstants.DELEGATE_SELECTORS) Set<String> delegateSelectors;

  @Schema(description = SecretManagerDescriptionConstants.ASSUME_CREDENTIALS_ON_DELEGATE)
  Boolean assumeCredentialsOnDelegate;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return GcpSecretManagerConnectorOutcomeDTO.builder()
        .isDefault(isDefault)
        .credentialsRef(credentialsRef)
        .delegateSelectors(delegateSelectors)
        .assumeCredentialsOnDelegate(assumeCredentialsOnDelegate)
        .build();
  }

  @Override
  public void validate() {
    if (BooleanUtils.isTrue(assumeCredentialsOnDelegate)) {
      if (this.delegateSelectors == null) {
        throw new InvalidRequestException("delegateSelectors cannot be null");
      }
      if (this.delegateSelectors.isEmpty()) {
        throw new InvalidRequestException("delegateSelectors cannot be empty");
      }
    }
  }
}