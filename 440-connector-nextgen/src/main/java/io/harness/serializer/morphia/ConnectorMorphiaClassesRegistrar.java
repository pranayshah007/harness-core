/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.ConnectorFilterProperties;
import io.harness.connector.entities.embedded.appdynamicsconnector.AppDynamicsConnector;
import io.harness.connector.entities.embedded.artifactoryconnector.ArtifactoryConnector;
import io.harness.connector.entities.embedded.artifactoryconnector.ArtifactoryUserNamePasswordAuthentication;
import io.harness.connector.entities.embedded.awscodecommitconnector.AwsCodeCommitAuthentication;
import io.harness.connector.entities.embedded.awscodecommitconnector.AwsCodeCommitConfig;
import io.harness.connector.entities.embedded.awscodecommitconnector.AwsCodeCommitSecretKeyAccessKey;
import io.harness.connector.entities.embedded.awsconnector.AwsAccessKeyCredential;
import io.harness.connector.entities.embedded.awsconnector.AwsConfig;
import io.harness.connector.entities.embedded.awsconnector.AwsIamCredential;
import io.harness.connector.entities.embedded.awskmsconnector.AwsKmsConnector;
import io.harness.connector.entities.embedded.awskmsconnector.AwsKmsIamCredential;
import io.harness.connector.entities.embedded.awskmsconnector.AwsKmsManualCredential;
import io.harness.connector.entities.embedded.awskmsconnector.AwsKmsStsCredential;
import io.harness.connector.entities.embedded.awssecretmanager.AwsSecretManagerConnector;
import io.harness.connector.entities.embedded.azureartifacts.AzureArtifactsAuthentication;
import io.harness.connector.entities.embedded.azureartifacts.AzureArtifactsConnector;
import io.harness.connector.entities.embedded.azureartifacts.AzureArtifactsTokenCredentials;
import io.harness.connector.entities.embedded.azureconnector.AzureConfig;
import io.harness.connector.entities.embedded.azureconnector.AzureManagedIdentityCredential;
import io.harness.connector.entities.embedded.azureconnector.AzureManualCredential;
import io.harness.connector.entities.embedded.azurekeyvaultconnector.AzureKeyVaultConnector;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoApiAccess;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoConnector;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoHttpAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoSshAuthentication;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoTokenApiAccess;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoUsernameToken;
import io.harness.connector.entities.embedded.bamboo.BambooConnector;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketConnector;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketHttpAuthentication;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketSshAuthentication;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketUsernamePassword;
import io.harness.connector.entities.embedded.bitbucketconnector.BitbucketUsernamePasswordApiAccess;
import io.harness.connector.entities.embedded.ceawsconnector.CEAwsConfig;
import io.harness.connector.entities.embedded.ceawsconnector.CURAttributes;
import io.harness.connector.entities.embedded.ceawsconnector.S3BucketDetails;
import io.harness.connector.entities.embedded.ceazure.CEAzureConfig;
import io.harness.connector.entities.embedded.cek8s.CEK8sDetails;
import io.harness.connector.entities.embedded.customhealthconnector.CustomHealthConnector;
import io.harness.connector.entities.embedded.customsecretmanager.CustomSecretManagerConnector;
import io.harness.connector.entities.embedded.datadogconnector.DatadogConnector;
import io.harness.connector.entities.embedded.docker.DockerConnector;
import io.harness.connector.entities.embedded.docker.DockerUserNamePasswordAuthentication;
import io.harness.connector.entities.embedded.dynatraceconnector.DynatraceConnector;
import io.harness.connector.entities.embedded.elkconnector.ELKConnector;
import io.harness.connector.entities.embedded.errortrackingconnector.ErrorTrackingConnector;
import io.harness.connector.entities.embedded.gcpccm.GcpBillingExportDetails;
import io.harness.connector.entities.embedded.gcpccm.GcpCloudCostConfig;
import io.harness.connector.entities.embedded.gcpconnector.GcpConfig;
import io.harness.connector.entities.embedded.gcpconnector.GcpDelegateDetails;
import io.harness.connector.entities.embedded.gcpconnector.GcpServiceAccountKey;
import io.harness.connector.entities.embedded.gcpkmsconnector.GcpKmsConnector;
import io.harness.connector.entities.embedded.gcpsecretmanager.GcpSecretManagerConnector;
import io.harness.connector.entities.embedded.gitconnector.GitConfig;
import io.harness.connector.entities.embedded.gitconnector.GitSSHAuthentication;
import io.harness.connector.entities.embedded.gitconnector.GitUserNamePasswordAuthentication;
import io.harness.connector.entities.embedded.githubconnector.GithubApiAccess;
import io.harness.connector.entities.embedded.githubconnector.GithubConnector;
import io.harness.connector.entities.embedded.githubconnector.GithubHttpAuthentication;
import io.harness.connector.entities.embedded.githubconnector.GithubSshAuthentication;
import io.harness.connector.entities.embedded.githubconnector.GithubTokenApiAccess;
import io.harness.connector.entities.embedded.githubconnector.GithubUsernamePassword;
import io.harness.connector.entities.embedded.githubconnector.GithubUsernameToken;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabConnector;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabHttpAuthentication;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabKerberos;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabSshAuthentication;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabTokenApiAccess;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabUsernamePassword;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabUsernameToken;
import io.harness.connector.entities.embedded.helm.HttpHelmConnector;
import io.harness.connector.entities.embedded.helm.HttpHelmUsernamePasswordAuthentication;
import io.harness.connector.entities.embedded.helm.OciHelmConnector;
import io.harness.connector.entities.embedded.helm.OciHelmUsernamePasswordAuthentication;
import io.harness.connector.entities.embedded.jenkins.JenkinsConnector;
import io.harness.connector.entities.embedded.jira.JiraConnector;
import io.harness.connector.entities.embedded.jira.JiraPATAuthentication;
import io.harness.connector.entities.embedded.jira.JiraUserNamePasswordAuthentication;
import io.harness.connector.entities.embedded.kubernetescluster.K8sClientKeyCert;
import io.harness.connector.entities.embedded.kubernetescluster.K8sOpenIdConnect;
import io.harness.connector.entities.embedded.kubernetescluster.K8sServiceAccount;
import io.harness.connector.entities.embedded.kubernetescluster.K8sUserNamePassword;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesClusterConfig;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesClusterDetails;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesDelegateDetails;
import io.harness.connector.entities.embedded.localconnector.LocalConnector;
import io.harness.connector.entities.embedded.newrelicconnector.NewRelicConnector;
import io.harness.connector.entities.embedded.nexusconnector.NexusConnector;
import io.harness.connector.entities.embedded.nexusconnector.NexusUserNamePasswordAuthentication;
import io.harness.connector.entities.embedded.pagerduty.PagerDutyConnector;
import io.harness.connector.entities.embedded.pdcconnector.PhysicalDataCenterConnector;
import io.harness.connector.entities.embedded.prometheusconnector.PrometheusConnector;
import io.harness.connector.entities.embedded.servicenow.ServiceNowADFSAuthentication;
import io.harness.connector.entities.embedded.servicenow.ServiceNowConnector;
import io.harness.connector.entities.embedded.servicenow.ServiceNowUserNamePasswordAuthentication;
import io.harness.connector.entities.embedded.splunkconnector.SplunkConnector;
import io.harness.connector.entities.embedded.spotconnector.SpotConfig;
import io.harness.connector.entities.embedded.sumologic.SumoLogicConnector;
import io.harness.connector.entities.embedded.tasconnector.TasConfig;
import io.harness.connector.entities.embedded.terraformcloudconncetor.TerraformCloudConfig;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

@OwnedBy(DX)
public class ConnectorMorphiaClassesRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(Connector.class);
    set.add(KubernetesClusterConfig.class);
    set.add(GitConfig.class);
    set.add(VaultConnector.class);
    set.add(AwsKmsConnector.class);
    set.add(AwsSecretManagerConnector.class);
    set.add(AzureKeyVaultConnector.class);
    set.add(GcpKmsConnector.class);
    set.add(LocalConnector.class);
    set.add(AppDynamicsConnector.class);
    set.add(SplunkConnector.class);
    set.add(DockerConnector.class);
    set.add(PhysicalDataCenterConnector.class);
    set.add(GcpConfig.class);
    set.add(AwsConfig.class);
    set.add(SpotConfig.class);
    set.add(TasConfig.class);
    set.add(CEAwsConfig.class);
    set.add(ArtifactoryConnector.class);
    set.add(JiraConnector.class);
    set.add(NexusConnector.class);
    set.add(GithubConnector.class);
    set.add(GitlabConnector.class);
    set.add(BitbucketConnector.class);
    set.add(CEAzureConfig.class);
    set.add(CEK8sDetails.class);
    set.add(AwsCodeCommitConfig.class);
    set.add(HttpHelmConnector.class);
    set.add(NewRelicConnector.class);
    set.add(GcpCloudCostConfig.class);
    set.add(PrometheusConnector.class);
    set.add(DatadogConnector.class);
    set.add(SumoLogicConnector.class);
    set.add(DynatraceConnector.class);
    set.add(PagerDutyConnector.class);
    set.add(CustomHealthConnector.class);
    set.add(ServiceNowConnector.class);
    set.add(ErrorTrackingConnector.class);
    set.add(AzureConfig.class);
    set.add(AzureRepoConnector.class);
    set.add(JenkinsConnector.class);
    set.add(OciHelmConnector.class);
    set.add(CustomSecretManagerConnector.class);
    set.add(ELKConnector.class);
    set.add(GcpSecretManagerConnector.class);
    set.add(AzureArtifactsConnector.class);
    set.add(BambooConnector.class);
    set.add(TerraformCloudConfig.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("connector.entities.embedded.kubernetescluster.KubernetesDelegateDetails", KubernetesDelegateDetails.class);
    h.put("connector.entities.embedded.kubernetescluster.KubernetesClusterDetails", KubernetesClusterDetails.class);
    h.put("connector.entities.embedded.kubernetescluster.K8sClientKeyCert", K8sClientKeyCert.class);
    h.put("connector.entities.embedded.kubernetescluster.K8sOpenIdConnect", K8sOpenIdConnect.class);
    h.put("connector.entities.embedded.kubernetescluster.K8sServiceAccount", K8sServiceAccount.class);
    h.put("connector.entities.embedded.kubernetescluster.K8sUserNamePassword", K8sUserNamePassword.class);
    h.put("connector.entities.embedded.gitconnector.GitSSHAuthentication", GitSSHAuthentication.class);
    h.put("connector.entities.embedded.gitconnector.GitUserNamePasswordAuthentication",
        GitUserNamePasswordAuthentication.class);
    h.put("connector.entities.embedded.gcpconnector.GcpDelegateDetails", GcpDelegateDetails.class);
    h.put("connector.entities.embedded.gcpconnector.GcpServiceAccountKey", GcpServiceAccountKey.class);
    h.put("connector.entities.embedded.awsconnector.AwsIamCredential", AwsIamCredential.class);
    h.put("connector.entities.embedded.awsconnector.AwsAccessKeyCredential", AwsAccessKeyCredential.class);
    h.put("connector.entities.embedded.awskmsconnector.AwsKmsIamCredential", AwsKmsIamCredential.class);
    h.put("connector.entities.embedded.awskmsconnector.AwsKmsStsCredential", AwsKmsStsCredential.class);
    h.put("connector.entities.embedded.awskmsconnector.AwsKmsManualCredential", AwsKmsManualCredential.class);
    h.put("connector.entities.embedded.ceawsconnector.CURAttributes", CURAttributes.class);
    h.put("connector.entities.embedded.ceawsconnector.S3BucketDetails", S3BucketDetails.class);
    h.put("connector.entities.embedded.docker.DockerUserNamePasswordAuthentication",
        DockerUserNamePasswordAuthentication.class);
    h.put("connector.entities.embedded.artifactoryconnector.ArtifactoryUserNamePasswordAuthentication",
        ArtifactoryUserNamePasswordAuthentication.class);
    h.put("connector.entities.embedded.nexusconnector.NexusUserNamePasswordAuthentication",
        NexusUserNamePasswordAuthentication.class);
    h.put("connector.entities.embedded.githubconnector.GithubAppApiAccess", GithubApiAccess.class);
    h.put("connector.entities.embedded.githubconnector.GithubTokenApiAccess", GithubTokenApiAccess.class);
    h.put("connector.entities.embedded.githubconnector.GithubSshAuthentication", GithubSshAuthentication.class);
    h.put("connector.entities.embedded.githubconnector.GithubHttpAuthentication", GithubHttpAuthentication.class);
    h.put("connector.entities.embedded.githubconnector.GithubUsernamePassword", GithubUsernamePassword.class);
    h.put("connector.entities.embedded.githubconnector.GithubUsernameToken", GithubUsernameToken.class);
    h.put("connector.entities.embedded.gitlabconnector.GitlabTokenApiAccess", GitlabTokenApiAccess.class);
    h.put("connector.entities.embedded.gitlabconnector.GitlabSshAuthentication", GitlabSshAuthentication.class);
    h.put("connector.entities.embedded.gitlabconnector.GitlabHttpAuthentication", GitlabHttpAuthentication.class);
    h.put("connector.entities.embedded.gitlabconnector.GitlabUsernamePassword", GitlabUsernamePassword.class);
    h.put("connector.entities.embedded.gitlabconnector.GitlabUsernameToken", GitlabUsernameToken.class);
    h.put("connector.entities.embedded.gitlabconnector.GitlabKerberos", GitlabKerberos.class);
    h.put("connector.entities.ConnectorFilterProperties", ConnectorFilterProperties.class);
    h.put("connector.entities.embedded.bitbucketconnector.BitbucketUsernamePasswordApiAccess",
        BitbucketUsernamePasswordApiAccess.class);
    h.put(
        "connector.entities.embedded.bitbucketconnector.BitbucketSshAuthentication", BitbucketSshAuthentication.class);
    h.put("connector.entities.embedded.bitbucketconnector.BitbucketHttpAuthentication",
        BitbucketHttpAuthentication.class);
    h.put("connector.entities.embedded.bitbucketconnector.BitbucketUsernamePassword", BitbucketUsernamePassword.class);
    h.put("connector.entities.embedded.awscodecommitconnector.AwsCodeCommitAuthentication",
        AwsCodeCommitAuthentication.class);
    h.put("connector.entities.embedded.awscodecommitconnector.AwsCodeCommitSecretKeyAccessKey",
        AwsCodeCommitSecretKeyAccessKey.class);
    h.put("connector.entities.embedded.helm.HttpHelmUsernamePasswordAuthentication",
        HttpHelmUsernamePasswordAuthentication.class);
    h.put("connector.entities.embedded.helm.OciHelmUsernamePasswordAuthentication",
        OciHelmUsernamePasswordAuthentication.class);
    h.put("connector.entities.embedded.gcpccm.GcpBillingExportDetails", GcpBillingExportDetails.class);
    h.put("connector.entities.embedded.azureconnector.AzureManualCredential", AzureManualCredential.class);
    h.put("connector.entities.embedded.azureconnector.AzureManagedIdentityCredential",
        AzureManagedIdentityCredential.class);
    h.put("connector.entities.embedded.azurerepoconnector.AzureRepoAppApiAccess", AzureRepoApiAccess.class);
    h.put("connector.entities.embedded.azurerepoconnector.AzureRepoTokenApiAccess", AzureRepoTokenApiAccess.class);
    h.put(
        "connector.entities.embedded.azurerepoconnector.AzureRepoSshAuthentication", AzureRepoSshAuthentication.class);
    h.put("connector.entities.embedded.azurerepoconnector.AzureRepoHttpAuthentication",
        AzureRepoHttpAuthentication.class);
    h.put("connector.entities.embedded.azurerepoconnector.AzureRepoUsernameToken", AzureRepoUsernameToken.class);
    h.put("connector.entities.embedded.azureartifacts.AzureRepoHttpAuthentication", AzureArtifactsAuthentication.class);
    h.put("connector.entities.embedded.azureartifacts.AzureRepoUsernameToken", AzureArtifactsTokenCredentials.class);
    h.put("connector.entities.embedded.servicenow.ServiceNowUserNamePasswordAuthentication",
        ServiceNowUserNamePasswordAuthentication.class);
    h.put("connector.entities.embedded.servicenow.ServiceNowADFSAuthentication", ServiceNowADFSAuthentication.class);
    h.put("connector.entities.embedded.jira.JiraUserNamePasswordAuthentication",
        JiraUserNamePasswordAuthentication.class);
    h.put("connector.entities.embedded.jira.JiraPATAuthentication", JiraPATAuthentication.class);
  }
}
