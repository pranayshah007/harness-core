/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.awsconnector.outcome.AwsConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.gcpconnector.outcome.GcpConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesClusterConfigOutcomeDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.outcome.GitConfigOutcomeDTO;
import io.harness.delegate.beans.connector.scm.github.outcome.GithubConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.tasconnector.outcome.TasConnectorOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = KubernetesClusterConfigOutcomeDTO.class, name = "K8sClusterOutcome")
  , @JsonSubTypes.Type(value = GcpConnectorOutcomeDTO.class, name = "GcpOutcome"),
      @JsonSubTypes.Type(value = AwsConnectorOutcomeDTO.class, name = "AwsOutcome"),
      @JsonSubTypes.Type(value = AzureConnectorOutcomeDTO.class, name = "AzureOutcome"),
      @JsonSubTypes.Type(value = GitConfigOutcomeDTO.class, name = "GitOutcome"),
      @JsonSubTypes.Type(value = GithubConnectorOutcomeDTO.class, name = "GithubOutcome"),
      @JsonSubTypes.Type(value = GitlabConnectorOutcomeDTO.class, name = "GitlabOutcome"),
      @JsonSubTypes.Type(value = TasConnectorOutcomeDTO.class, name = "TasConnectorOutcome")
})
@OwnedBy(DX)
@Schema(
    name = "ConnectorConfigOutcome", description = "This is the view of the ConnectorConfig entity defined in Harness")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "connectorType", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
public abstract class ConnectorConfigOutcomeDTO implements DecryptableEntity {
  @JsonIgnore public abstract List<DecryptableEntity> getDecryptableEntities();
}
