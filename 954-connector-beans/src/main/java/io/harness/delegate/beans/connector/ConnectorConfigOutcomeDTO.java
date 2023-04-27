/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.artifactoryconnector.outcome.ArtifactoryConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.awsconnector.outcome.AwsConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.awskmsconnector.outcome.AwsKmsConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.awssecretmanager.outcome.AwsSecretManagerOutcomeDTO;
import io.harness.delegate.beans.connector.azureartifacts.outcome.AzureArtifactsConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.outcome.AzureKeyVaultConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.customsecretmanager.outcome.CustomSecretManagerConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.docker.outcome.DockerConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.gcpconnector.outcome.GcpConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.gcpkmsconnector.outcome.GcpKmsConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.gcpsecretmanager.outcome.GcpSecretManagerConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.helm.outcome.HttpHelmConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.helm.outcome.OciHelmConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.jenkins.outcome.JenkinsConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesClusterConfigOutcomeDTO;
import io.harness.delegate.beans.connector.nexusconnector.outcome.NexusConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.pdcconnector.outcome.PhysicalDataCenterConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.outcome.AwsCodeCommitConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.outcome.AzureRepoConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.outcome.BitbucketConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.outcome.GitConfigOutcomeDTO;
import io.harness.delegate.beans.connector.scm.github.outcome.GithubConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.tasconnector.outcome.TasConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.vaultconnector.outcome.VaultConnectorOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = KubernetesClusterConfigOutcomeDTO.class, name = "K8sClusterOutcome")
  , @JsonSubTypes.Type(value = GcpConnectorOutcomeDTO.class, name = "GcpOutcome"),
      @JsonSubTypes.Type(value = AwsConnectorOutcomeDTO.class, name = "AwsOutcome"),
      @JsonSubTypes.Type(value = AzureConnectorOutcomeDTO.class, name = "AzureOutcome"),
      @JsonSubTypes.Type(value = GitConfigOutcomeDTO.class, name = "GitOutcome"),
      @JsonSubTypes.Type(value = GithubConnectorOutcomeDTO.class, name = "GithubOutcome"),
      @JsonSubTypes.Type(value = GitlabConnectorOutcomeDTO.class, name = "GitlabOutcome"),
      @JsonSubTypes.Type(value = PhysicalDataCenterConnectorOutcomeDTO.class, name = "PdcOutcome"),
      @JsonSubTypes.Type(value = TasConnectorOutcomeDTO.class, name = "TasConnectorOutcome"),
      @JsonSubTypes.Type(value = JenkinsConnectorOutcomeDTO.class, name = "JenkinsOutcome"),
      @JsonSubTypes.Type(value = ArtifactoryConnectorOutcomeDTO.class, name = "ArtifactoryOutcome"),
      @JsonSubTypes.Type(value = AzureArtifactsConnectorOutcomeDTO.class, name = "AzureArtifactsOutcome"),
      @JsonSubTypes.Type(value = DockerConnectorOutcomeDTO.class, name = "DockerRegistryOutcome"),
      @JsonSubTypes.Type(value = NexusConnectorOutcomeDTO.class, name = "NexusOutcome"),
      @JsonSubTypes.Type(value = OciHelmConnectorOutcomeDTO.class, name = "OciHelmRepoOutcome"),
      @JsonSubTypes.Type(value = HttpHelmConnectorOutcomeDTO.class, name = "HttpHelmRepoOutcome"),
      @JsonSubTypes.Type(value = AwsCodeCommitConnectorOutcomeDTO.class, name = "CodecommitOutcome"),
      @JsonSubTypes.Type(value = BitbucketConnectorOutcomeDTO.class, name = "BitbucketOutcome"),
      @JsonSubTypes.Type(value = AzureRepoConnectorOutcomeDTO.class, name = "AzureRepoOutcome"),
      @JsonSubTypes.Type(value = AwsKmsConnectorOutcomeDTO.class, name = "AwsKmsConnectorOutcome"),
      @JsonSubTypes.Type(value = AwsSecretManagerOutcomeDTO.class, name = "AwsSecretManagerOutcome"),
      @JsonSubTypes.Type(value = AzureKeyVaultConnectorOutcomeDTO.class, name = "AzureKeyVaultConnectorOutcome"),
      @JsonSubTypes.Type(
          value = CustomSecretManagerConnectorOutcomeDTO.class, name = "CustomSecretManagerConnectorOutcome"),
      @JsonSubTypes.Type(value = GcpKmsConnectorOutcomeDTO.class, name = " GcpKmsConnectorOutcome"),
      @JsonSubTypes.Type(value = GcpSecretManagerConnectorOutcomeDTO.class, name = "GcpSecretManagerConnectorOutcome"),
      @JsonSubTypes.Type(value = VaultConnectorOutcomeDTO.class, name = "VaultConnectorOutcome")
})
@OwnedBy(DX)
@Schema(
    name = "ConnectorConfigOutcome", description = "This is the view of the ConnectorConfig entity defined in Harness")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "connectorType", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
public abstract class ConnectorConfigOutcomeDTO {}
