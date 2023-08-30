/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector.outcome;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureconnector.AzureConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@JsonTypeName(AzureConstants.MANUAL_CONFIG)
@RecasterAlias("io.harness.delegate.beans.connector.azureconnector.outcome.AzureManualDetailsOutcomeDTO")
public class AzureManualDetailsOutcomeDTO implements AzureCredentialSpecOutcomeDTO {
  @NotNull String applicationId;

  @NotNull String tenantId;

  @NotNull AzureAuthOutcomeDTO auth;
}
