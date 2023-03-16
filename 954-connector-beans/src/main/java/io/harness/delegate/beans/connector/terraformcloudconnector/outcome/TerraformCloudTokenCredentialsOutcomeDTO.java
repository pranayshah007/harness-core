/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.terraformcloudconnector.outcome;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConstants;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@JsonTypeName(TerraformCloudConstants.API_TOKEN)
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudTokenCredentialsOutcomeDTO implements TerraformCloudCredentialSpecOutcomeDTO {
  @NotNull @SecretReference SecretRefData apiToken;
}
