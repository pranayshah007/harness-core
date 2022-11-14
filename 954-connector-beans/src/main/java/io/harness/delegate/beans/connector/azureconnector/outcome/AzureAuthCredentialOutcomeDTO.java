/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector.outcome;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.azureconnector.AzureConstants;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
@JsonSubTypes({
  @JsonSubTypes.Type(value = AzureClientSecretKeyOutcomeDTO.class, name = AzureConstants.SECRET_KEY)
  , @JsonSubTypes.Type(value = AzureClientKeyCertOutcomeDTO.class, name = AzureConstants.KEY_CERT),
      @JsonSubTypes.Type(
          value = AzureUserAssignedMSIAuthOutcomeDTO.class, name = AzureConstants.USER_ASSIGNED_MANAGED_IDENTITY),
      @JsonSubTypes.Type(
          value = AzureSystemAssignedMSIAuthOutcomeDTO.class, name = AzureConstants.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
})
public abstract class AzureAuthCredentialOutcomeDTO implements DecryptableEntity {}