/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import static io.harness.ConnectorConstants.INHERIT_FROM_DELEGATE_TYPE_ERROR_MSG;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.MANUAL_CREDENTIALS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.awsconnector.outcome.AwsConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.awsconnector.outcome.AwsCredentialOutcomeDTO;
import io.harness.delegate.beans.connector.awsconnector.outcome.AwsSdkClientBackoffStrategyOutcomeDTO;
import io.harness.exception.InvalidRequestException;

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

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("AwsConnector")
@Schema(name = "AwsConnector", description = "This contains details of the AWS connector")
public class AwsConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable, ManagerExecutable {
  @Valid @NotNull AwsCredentialDTO credential;
  @Valid AwsSdkClientBackoffStrategyDTO awsSdkClientBackOffStrategyOverride;
  Set<String> delegateSelectors;
  @Builder.Default Boolean executeOnDelegate = true;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (credential.getAwsCredentialType() == MANUAL_CREDENTIALS) {
      AwsManualConfigSpecDTO awsManualCredentials = (AwsManualConfigSpecDTO) credential.getConfig();
      return Collections.singletonList(awsManualCredentials);
    }
    return null;
  }

  @Override
  public void validate() {
    if ((AwsCredentialType.INHERIT_FROM_DELEGATE.equals(credential.getAwsCredentialType())
            || AwsCredentialType.IRSA.equals(credential.getAwsCredentialType()))
        && isEmpty(delegateSelectors)) {
      throw new InvalidRequestException(INHERIT_FROM_DELEGATE_TYPE_ERROR_MSG);
    }
  }
  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return AwsConnectorOutcomeDTO.builder()
        .credential(AwsCredentialOutcomeDTO.builder()
                        .type(this.credential.getAwsCredentialType())
                        .crossAccountAccess(this.credential.getCrossAccountAccess())
                        .config(this.credential.getConfig())
                        .region(this.credential.getTestRegion())
                        .build())
        .awsSdkClientBackOffStrategyOverride(
            AwsSdkClientBackoffStrategyOutcomeDTO.builder()
                .type(this.awsSdkClientBackOffStrategyOverride.getAwsSdkClientBackoffStrategyType())
                .spec(this.awsSdkClientBackOffStrategyOverride.getBackoffStrategyConfig())
                .build())
        .delegateSelectors(this.delegateSelectors)
        .executeOnDelegate(this.executeOnDelegate)
        .build();
  }
}
