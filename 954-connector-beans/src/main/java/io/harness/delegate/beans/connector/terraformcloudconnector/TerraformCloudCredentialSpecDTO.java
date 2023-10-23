/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.terraformcloudconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.terraformcloudconnector.outcome.TerraformCloudCredentialSpecOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(HarnessTeam.CDP)
@JsonSubTypes(
    { @JsonSubTypes.Type(value = TerraformCloudTokenCredentialsDTO.class, name = TerraformCloudConstants.API_TOKEN) })
@ApiModel("TerraformCloudCredentialSpec")
@Schema(name = "TerraformCloudCredentialSpec", description = "This contains Terraform Cloud ConnectorDisconnectHandler credentials spec")
public interface TerraformCloudCredentialSpecDTO extends DecryptableEntity {
  default TerraformCloudCredentialSpecOutcomeDTO toOutcome() {
    return null;
  }
}
