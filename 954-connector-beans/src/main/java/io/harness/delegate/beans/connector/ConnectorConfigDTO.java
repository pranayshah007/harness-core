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
import io.harness.connector.utils.TemplateDetails;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.delegate.beans.connector.cek8s.CEKubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerConnectorDTO;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.delegate.beans.connector.errortracking.ErrorTrackingConnectorDTO;
import io.harness.delegate.beans.connector.gcpccm.GcpCloudCostConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSecretManagerConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.localconnector.LocalConnectorDTO;
import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.pagerduty.PagerDutyConnectorDTO;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = KubernetesClusterConfigDTO.class, name = "K8sCluster")
  , @JsonSubTypes.Type(value = GitConfigDTO.class, name = "Git"),
      @JsonSubTypes.Type(value = SplunkConnectorDTO.class, name = "Splunk"),
      @JsonSubTypes.Type(value = AppDynamicsConnectorDTO.class, name = "AppDynamics"),
      @JsonSubTypes.Type(value = NewRelicConnectorDTO.class, name = "NewRelic"),
      @JsonSubTypes.Type(value = PrometheusConnectorDTO.class, name = "Prometheus"),
      @JsonSubTypes.Type(value = DatadogConnectorDTO.class, name = "Datadog"),
      @JsonSubTypes.Type(value = SumoLogicConnectorDTO.class, name = "SumoLogic"),
      @JsonSubTypes.Type(value = DynatraceConnectorDTO.class, name = "Dynatrace"),
      @JsonSubTypes.Type(value = VaultConnectorDTO.class, name = "Vault"),
      @JsonSubTypes.Type(value = DockerConnectorDTO.class, name = "DockerRegistry"),
      @JsonSubTypes.Type(value = LocalConnectorDTO.class, name = "Local"),
      @JsonSubTypes.Type(value = GcpKmsConnectorDTO.class, name = "GcpKms"),
      @JsonSubTypes.Type(value = AwsKmsConnectorDTO.class, name = "AwsKms"),
      @JsonSubTypes.Type(value = AwsSecretManagerDTO.class, name = "AwsSecretManager"),
      @JsonSubTypes.Type(value = AzureKeyVaultConnectorDTO.class, name = "AzureKeyVault"),
      @JsonSubTypes.Type(value = GcpConnectorDTO.class, name = "Gcp"),
      @JsonSubTypes.Type(value = AwsConnectorDTO.class, name = "Aws"),
      @JsonSubTypes.Type(value = SpotConnectorDTO.class, name = "Spot"),
      @JsonSubTypes.Type(value = AzureConnectorDTO.class, name = "Azure"),
      @JsonSubTypes.Type(value = CEAwsConnectorDTO.class, name = "CEAws"),
      @JsonSubTypes.Type(value = ArtifactoryConnectorDTO.class, name = "Artifactory"),
      @JsonSubTypes.Type(value = JiraConnectorDTO.class, name = "Jira"),
      @JsonSubTypes.Type(value = NexusConnectorDTO.class, name = "Nexus"),
      @JsonSubTypes.Type(value = GithubConnectorDTO.class, name = "Github"),
      @JsonSubTypes.Type(value = HarnessConnectorDTO.class, name = "Harness"),
      @JsonSubTypes.Type(value = GitlabConnectorDTO.class, name = "Gitlab"),
      @JsonSubTypes.Type(value = BitbucketConnectorDTO.class, name = "Bitbucket"),
      @JsonSubTypes.Type(value = AwsCodeCommitConnectorDTO.class, name = "Codecommit"),
      @JsonSubTypes.Type(value = CEAzureConnectorDTO.class, name = "CEAzure"),
      @JsonSubTypes.Type(value = CEKubernetesClusterConfigDTO.class, name = "CEK8sCluster"),
      @JsonSubTypes.Type(value = GcpCloudCostConnectorDTO.class, name = "GcpCloudCost"),
      @JsonSubTypes.Type(value = HttpHelmConnectorDTO.class, name = "HttpHelmRepo"),
      @JsonSubTypes.Type(value = PagerDutyConnectorDTO.class, name = "PagerDuty"),
      @JsonSubTypes.Type(value = CustomHealthConnectorDTO.class, name = "CustomHealth"),
      @JsonSubTypes.Type(value = ServiceNowConnectorDTO.class, name = "ServiceNow"),
      @JsonSubTypes.Type(value = ErrorTrackingConnectorDTO.class, name = "ErrorTracking"),
      @JsonSubTypes.Type(value = AzureRepoConnectorDTO.class, name = "AzureRepo"),
      @JsonSubTypes.Type(value = PhysicalDataCenterConnectorDTO.class, name = "Pdc"),
      @JsonSubTypes.Type(value = JenkinsConnectorDTO.class, name = "Jenkins"),
      @JsonSubTypes.Type(value = OciHelmConnectorDTO.class, name = "OciHelmRepo"),
      @JsonSubTypes.Type(value = CustomSecretManagerConnectorDTO.class, name = "CustomSecretManager"),
      @JsonSubTypes.Type(value = ELKConnectorDTO.class, name = "ElasticSearch"),
      @JsonSubTypes.Type(value = GcpSecretManagerConnectorDTO.class, name = "GcpSecretManager"),
      @JsonSubTypes.Type(value = AzureArtifactsConnectorDTO.class, name = "AzureArtifacts"),
      @JsonSubTypes.Type(value = TasConnectorDTO.class, name = "Tas"),
      @JsonSubTypes.Type(value = BambooConnectorDTO.class, name = "Bamboo"),
      @JsonSubTypes.Type(value = TerraformCloudConnectorDTO.class, name = "TerraformCloud"),
      @JsonSubTypes.Type(value = SignalFXConnectorDTO.class, name = "SignalFX"),
})
@OwnedBy(DX)
@Schema(name = "ConnectorConfig", description = "This is the view of the ConnectorConfig entity defined in Harness")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "connectorType", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
public abstract class ConnectorConfigDTO implements DecryptableEntity {
  @JsonIgnore public abstract List<DecryptableEntity> getDecryptableEntities();

  @JsonIgnore
  public List<TemplateDetails> getTemplateDetails() {
    return null;
  }

  public void validate() {
    // no op implementation which base classes can override
  }

  public ConnectorConfigOutcomeDTO toOutcome() {
    // no op implementation which base classes can override
    return null;
  }
}
