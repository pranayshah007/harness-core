/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.servicenow;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Schema(name = "ServiceNowAuthCredentials",
    description = "This contains details of credentials for Service Now Authentication")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ServiceNowUserNamePasswordDTO.class, name = ServiceNowConstants.USERNAME_PASSWORD)
  , @JsonSubTypes.Type(value = ServiceNowADFSDTO.class, name = ServiceNowConstants.ADFS),
      @JsonSubTypes.Type(value = ServiceNowRefreshTokenDTO.class, name = ServiceNowConstants.REFRESH_TOKEN)
})
public interface ServiceNowAuthCredentialsDTO extends DecryptableEntity {
  void validate();
}
