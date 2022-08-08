/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
@JsonSubTypes({
  @JsonSubTypes.Type(value = AzureClientSecretKeyDTO.class, name = AzureConstants.SECRET_KEY)
  , @JsonSubTypes.Type(value = AzureClientKeyCertDTO.class, name = AzureConstants.KEY_CERT),
      @JsonSubTypes.Type(
          value = AzureUserAssignedMSIAuthDTO.class, name = AzureConstants.USER_ASSIGNED_MANAGED_IDENTITY),
      @JsonSubTypes.Type(
          value = AzureSystemAssignedMSIAuthDTO.class, name = AzureConstants.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
})
@Schema(name = "AzureAuthCredential", description = "This contains azure auth credentials")
public abstract class AzureAuthCredentialDTO implements DecryptableEntity {}