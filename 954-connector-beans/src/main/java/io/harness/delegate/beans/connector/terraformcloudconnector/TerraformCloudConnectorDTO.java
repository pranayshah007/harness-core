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
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.terraformcloudconnector.outcome.TerraformCloudConnectorOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.URL;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("TerraformCloudConnector")
@Schema(name = "TerraformCloudConnector", description = "This contains details of the Terraform Cloud connector")
public class TerraformCloudConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable, ManagerExecutable {
  @NotNull @URL String terraformCloudUrl;
  @NotNull @Valid TerraformCloudCredentialDTO credential;
  Set<String> delegateSelectors;
  @Builder.Default Boolean executeOnDelegate = true;
  ConnectorType connectorType;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (credential.getType() == TerraformCloudCredentialType.API_TOKEN) {
      return Collections.singletonList(credential.getSpec());
    }
    return null;
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return TerraformCloudConnectorOutcomeDTO.builder()
        .terraformCloudUrl(this.terraformCloudUrl)
        .credential(this.credential.toOutcome())
        .delegateSelectors(this.delegateSelectors)
        .executeOnDelegate(this.executeOnDelegate)
        .build();
  }
}
