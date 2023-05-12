/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class AwsConnectorOutcomeDTO extends ConnectorConfigOutcomeDTO implements DelegateSelectable, ManagerExecutable {
  @Valid @NotNull AwsCredentialOutcomeDTO credential;
  @Valid AwsSdkClientBackoffStrategyOutcomeDTO awsSdkClientBackOffStrategyOverride;
  Set<String> delegateSelectors;
  @Builder.Default Boolean executeOnDelegate = true;
}
