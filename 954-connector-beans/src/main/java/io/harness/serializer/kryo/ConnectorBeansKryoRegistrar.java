/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsAuthType;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthCredentialsDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConstants;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConstants;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsInheritFromDelegateSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorCredentialDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConstants;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialSpecAssumeIAMDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialSpecAssumeSTSDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialSpecDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialSpecManualConfigDTO;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsCredentialType;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSMCredentialSpecAssumeIAMDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSMCredentialSpecAssumeSTSDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSMCredentialSpecManualConfigDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerConstants;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerCredentialDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerCredentialSpecDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerCredentialType;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConstants;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialSpecDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.azureconnector.AzureSystemAssignedMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.connector.ceawsconnector.AwsCurAttributesDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.delegate.beans.connector.ceazure.BillingExportSpecDTO;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.delegate.beans.connector.cek8s.CEKubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthKeyAndValue;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerConnectorDTO;
import io.harness.delegate.beans.connector.customsecretmanager.TemplateLinkConfigForCustomSecretManager;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthCredentialsDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;
import io.harness.delegate.beans.connector.elkconnector.ELKAuthType;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.delegate.beans.connector.errortracking.ErrorTrackingConnectorDTO;
import io.harness.delegate.beans.connector.gcpccm.GcpCloudCostConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConstants;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpDelegateDetailsDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSecretManagerConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthCredentialsDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmUsernamePasswordDTO;
import io.harness.delegate.beans.connector.helm.OciHelmAuthCredentialsDTO;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthCredentialsDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClientKeyCertDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialSpecDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesDelegateDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesOpenIdConnectDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesServiceAccountDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.delegate.beans.connector.localconnector.LocalConnectorDTO;
import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthCredentialsDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConstants;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.pagerduty.PagerDutyConnectorDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsCredentialsDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitSecretKeyAccessKeyDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.CustomCommitAttributes;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabKerberosDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthCredentialsDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthenticationDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowUserNamePasswordDTO;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialSpecDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialType;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;
import java.util.LinkedHashSet;

@OwnedBy(HarnessTeam.PL)
public class ConnectorBeansKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(ConnectorDTO.class, 26001);
    kryo.register(ConnectorInfoDTO.class, 26002);
    kryo.register(AppDynamicsConnectorDTO.class, 19105);
    kryo.register(CustomCommitAttributes.class, 19070);
    kryo.register(GitAuthenticationDTO.class, 19063);
    kryo.register(GitAuthType.class, 19066);
    kryo.register(GitConfigDTO.class, 19060);
    kryo.register(GitConnectionType.class, 19068);
    kryo.register(GitHTTPAuthenticationDTO.class, 19064);
    kryo.register(GitSSHAuthenticationDTO.class, 19065);
    kryo.register(KubernetesAuthCredentialDTO.class, 19058);
    kryo.register(KubernetesAuthDTO.class, 19050);
    kryo.register(KubernetesAuthType.class, 19051);
    kryo.register(KubernetesClientKeyCertDTO.class, 19053);
    kryo.register(KubernetesClusterConfigDTO.class, 19045);
    kryo.register(KubernetesClusterDetailsDTO.class, 19049);
    kryo.register(KubernetesCredentialSpecDTO.class, 19047);
    kryo.register(KubernetesCredentialType.class, 19046);
    kryo.register(KubernetesDelegateDetailsDTO.class, 19048);
    kryo.register(KubernetesOpenIdConnectDTO.class, 19055);
    kryo.register(KubernetesServiceAccountDTO.class, 19054);
    kryo.register(KubernetesUserNamePasswordDTO.class, 19052);
    kryo.register(SplunkConnectorDTO.class, 19111);
    kryo.register(DockerAuthCredentialsDTO.class, 19112);
    kryo.register(DockerAuthenticationDTO.class, 19113);
    kryo.register(DockerAuthType.class, 19114);
    kryo.register(DockerConnectorDTO.class, 19115);
    kryo.register(DockerUserNamePasswordDTO.class, 19116);
    kryo.register(KubernetesCredentialDTO.class, 19342);
    kryo.register(JiraConnectorDTO.class, 19344);
    kryo.register(GcpConnectorDTO.class, 19345);
    kryo.register(GcpConnectorCredentialDTO.class, 19346);
    kryo.register(GcpCredentialType.class, 19347);
    kryo.register(GcpConstants.class, 19348);
    kryo.register(GcpDelegateDetailsDTO.class, 19349);
    kryo.register(GcpManualDetailsDTO.class, 19350);
    kryo.register(AwsConnectorDTO.class, 19351);
    kryo.register(AwsConstants.class, 19352);
    kryo.register(AwsCredentialDTO.class, 19353);
    kryo.register(AwsCredentialSpecDTO.class, 19354);
    kryo.register(AwsCredentialType.class, 19355);
    kryo.register(AwsInheritFromDelegateSpecDTO.class, 19357);
    kryo.register(AwsManualConfigSpecDTO.class, 19358);
    kryo.register(CrossAccountAccessDTO.class, 19362);
    kryo.register(ConnectorType.class, 19372);
    kryo.register(GithubHttpCredentialsDTO.class, 19440);
    kryo.register(GithubHttpAuthenticationType.class, 19441);
    kryo.register(GithubUsernamePasswordDTO.class, 19442);
    kryo.register(GitlabConnectorDTO.class, 19443);
    kryo.register(GithubConnectorDTO.class, 19444);
    kryo.register(GithubApiAccessDTO.class, 19445);
    kryo.register(GithubSshCredentialsDTO.class, 19446);
    kryo.register(GithubApiAccessSpecDTO.class, 19447);
    kryo.register(GithubAppSpecDTO.class, 19449);
    kryo.register(GithubTokenSpecDTO.class, 19450);
    kryo.register(GithubApiAccessType.class, 19451);
    kryo.register(GithubAuthenticationDTO.class, 19452);
    kryo.register(GithubCredentialsDTO.class, 19453);
    kryo.register(CEAwsConnectorDTO.class, 19454);
    kryo.register(AwsCurAttributesDTO.class, 19455);
    kryo.register(ArtifactoryConnectorDTO.class, 19487);
    kryo.register(ArtifactoryAuthCredentialsDTO.class, 19488);
    kryo.register(ArtifactoryAuthenticationDTO.class, 19489);
    kryo.register(ArtifactoryAuthType.class, 19490);
    kryo.register(ArtifactoryConstants.class, 19491);
    kryo.register(ArtifactoryUsernamePasswordAuthDTO.class, 19492);
    kryo.register(NexusConnectorDTO.class, 19498);
    kryo.register(NexusAuthenticationDTO.class, 19499);
    kryo.register(NexusAuthType.class, 19500);
    kryo.register(NexusUsernamePasswordAuthDTO.class, 19501);
    kryo.register(NexusAuthCredentialsDTO.class, 19502);
    kryo.register(NexusConstants.class, 19503);
    kryo.register(VaultConnectorDTO.class, 19506);
    kryo.register(GithubUsernameTokenDTO.class, 19511);
    kryo.register(GitlabUsernameTokenDTO.class, 19512);
    kryo.register(GitlabAuthenticationDTO.class, 19520);
    kryo.register(BitbucketConnectorDTO.class, 19521);
    kryo.register(GithubHttpCredentialsSpecDTO.class, 19522);
    kryo.register(GitlabHttpCredentialsDTO.class, 19523);
    kryo.register(BitbucketAuthenticationDTO.class, 19524);
    kryo.register(GitlabUsernamePasswordDTO.class, 19525);
    kryo.register(BitbucketHttpCredentialsDTO.class, 19526);
    kryo.register(GitlabKerberosDTO.class, 19527);
    kryo.register(GitlabHttpAuthenticationType.class, 19528);
    kryo.register(BitbucketHttpAuthenticationType.class, 19529);
    kryo.register(BitbucketUsernamePasswordDTO.class, 19530);
    kryo.register(GithubOauthDTO.class, 19552);
    kryo.register(GitlabOauthDTO.class, 19553);
    kryo.register(LocalConnectorDTO.class, 543237);
    kryo.register(GcpKmsConnectorDTO.class, 543238);

    kryo.register(AwsKmsConnectorDTO.class, 543286);
    kryo.register(AwsKmsConnectorCredentialDTO.class, 543288);
    kryo.register(AwsKmsCredentialType.class, 543289);
    kryo.register(AwsKmsCredentialSpecDTO.class, 543290);
    kryo.register(AwsKmsConstants.class, 543291);
    kryo.register(AwsKmsCredentialSpecManualConfigDTO.class, 543292);
    kryo.register(AwsKmsCredentialSpecAssumeIAMDTO.class, 543293);
    kryo.register(AwsKmsCredentialSpecAssumeSTSDTO.class, 543294);

    kryo.register(AwsSecretManagerDTO.class, 643295);
    kryo.register(AwsSecretManagerCredentialDTO.class, 643296);
    kryo.register(AwsSecretManagerCredentialType.class, 643297);
    kryo.register(AwsSecretManagerCredentialSpecDTO.class, 643298);
    kryo.register(AwsSecretManagerConstants.class, 643299);
    kryo.register(AwsSMCredentialSpecManualConfigDTO.class, 643308);
    kryo.register(AwsSMCredentialSpecAssumeIAMDTO.class, 643310);
    kryo.register(AwsSMCredentialSpecAssumeSTSDTO.class, 643311);

    kryo.register(CEAzureConnectorDTO.class, 19540);
    kryo.register(BillingExportSpecDTO.class, 19542);
    kryo.register(LinkedHashSet.class, 100030);
    kryo.register(CEKubernetesClusterConfigDTO.class, 19543);
    kryo.register(GitlabSshCredentialsDTO.class, 19643);
    kryo.register(GitlabTokenSpecDTO.class, 19644);
    kryo.register(GitlabApiAccessDTO.class, 19645);
    kryo.register(GitlabApiAccessType.class, 19646);
    kryo.register(GitlabApiAccessSpecDTO.class, 19647);

    kryo.register(AwsCodeCommitConnectorDTO.class, 19648);
    kryo.register(AwsCodeCommitAuthenticationDTO.class, 19649);
    kryo.register(AwsCodeCommitHttpsCredentialsDTO.class, 19650);
    kryo.register(AwsCodeCommitSecretKeyAccessKeyDTO.class, 19651);
    kryo.register(AwsCodeCommitUrlType.class, 19652);
    kryo.register(AwsCodeCommitHttpsAuthType.class, 19653);
    kryo.register(AwsCodeCommitAuthType.class, 19654);

    kryo.register(HttpHelmAuthCredentialsDTO.class, 19655);
    kryo.register(HttpHelmAuthenticationDTO.class, 19656);
    kryo.register(HttpHelmAuthType.class, 19657);
    kryo.register(HttpHelmConnectorDTO.class, 19658);
    kryo.register(HttpHelmUsernamePasswordDTO.class, 19659);
    kryo.register(BitbucketApiAccessDTO.class, 19660);
    kryo.register(BitbucketUsernameTokenApiAccessDTO.class, 19661);
    kryo.register(BitbucketApiAccessType.class, 19662);
    kryo.register(BitbucketApiAccessSpecDTO.class, 19663);
    kryo.register(NewRelicConnectorDTO.class, 19664);
    kryo.register(AppDynamicsAuthType.class, 19665);
    kryo.register(GcpCloudCostConnectorDTO.class, 19666);
    kryo.register(BitbucketSshCredentialsDTO.class, 19667);
    kryo.register(PrometheusConnectorDTO.class, 19668);
    kryo.register(DatadogConnectorDTO.class, 19669);
    kryo.register(AzureKeyVaultConnectorDTO.class, 19670);
    kryo.register(CEFeatures.class, 19671);
    kryo.register(SumoLogicConnectorDTO.class, 19672);
    kryo.register(DynatraceConnectorDTO.class, 19673);
    kryo.register(CustomHealthKeyAndValue.class, 19674);
    kryo.register(PagerDutyConnectorDTO.class, 19675);
    kryo.register(CustomHealthConnectorDTO.class, 19676);
    kryo.register(CustomHealthMethod.class, 19677);
    kryo.register(ServiceNowConnectorDTO.class, 19678);
    kryo.register(ErrorTrackingConnectorDTO.class, 19679);

    kryo.register(AzureConnectorDTO.class, 19680);
    kryo.register(AzureConstants.class, 19681);
    kryo.register(AzureCredentialSpecDTO.class, 19682);
    kryo.register(AzureManualDetailsDTO.class, 19683);
    kryo.register(AzureCredentialType.class, 19684);
    kryo.register(AzureCredentialDTO.class, 19685);
    kryo.register(AzureSecretType.class, 19686);
    kryo.register(AzureAuthCredentialDTO.class, 19687);
    kryo.register(AzureAuthDTO.class, 19688);
    kryo.register(AzureClientSecretKeyDTO.class, 19689);
    kryo.register(AzureClientKeyCertDTO.class, 19690);

    kryo.register(PhysicalDataCenterConnectorDTO.class, 19691);
    kryo.register(HostDTO.class, 19692);
    kryo.register(AzureRepoConnectorDTO.class, 19693);
    kryo.register(AzureRepoHttpCredentialsDTO.class, 19694);
    kryo.register(AzureRepoHttpAuthenticationType.class, 19695);
    kryo.register(AzureRepoApiAccessDTO.class, 19696);
    kryo.register(AzureRepoSshCredentialsDTO.class, 19697);
    kryo.register(AzureRepoApiAccessSpecDTO.class, 19698);
    kryo.register(AzureRepoTokenSpecDTO.class, 19699);
    kryo.register(AzureRepoApiAccessType.class, 19800);
    kryo.register(AzureRepoAuthenticationDTO.class, 19801);
    kryo.register(AzureRepoCredentialsDTO.class, 19802);
    kryo.register(AzureRepoUsernameTokenDTO.class, 19803);
    kryo.register(AzureRepoHttpCredentialsSpecDTO.class, 19804);
    kryo.register(AzureInheritFromDelegateDetailsDTO.class, 19805);
    kryo.register(AzureManagedIdentityType.class, 19806);
    kryo.register(AzureUserAssignedMSIAuthDTO.class, 19807);
    kryo.register(AzureMSIAuthDTO.class, 19808);
    kryo.register(AzureSystemAssignedMSIAuthDTO.class, 19809);
    kryo.register(AzureMSIAuthUADTO.class, 19810);
    kryo.register(AzureMSIAuthSADTO.class, 19811);
    kryo.register(SpotConnectorDTO.class, 21001);
    kryo.register(SpotCredentialDTO.class, 21002);
    kryo.register(SpotCredentialSpecDTO.class, 21003);
    kryo.register(SpotCredentialType.class, 21004);
    kryo.register(SpotPermanentTokenConfigSpecDTO.class, 21005);

    kryo.register(JenkinsAuthCredentialsDTO.class, 29112);
    kryo.register(JenkinsAuthenticationDTO.class, 29113);
    kryo.register(JenkinsAuthType.class, 29114);
    kryo.register(JenkinsConnectorDTO.class, 29115);
    kryo.register(JenkinsUserNamePasswordDTO.class, 29116);
    kryo.register(JenkinsBearerTokenDTO.class, 29130);
    kryo.register(OciHelmAuthCredentialsDTO.class, 29131);
    kryo.register(OciHelmAuthenticationDTO.class, 29132);
    kryo.register(OciHelmAuthType.class, 29133);
    kryo.register(OciHelmConnectorDTO.class, 29134);
    kryo.register(OciHelmUsernamePasswordDTO.class, 29135);
    kryo.register(AzureRepoConnectionTypeDTO.class, 19854);
    kryo.register(CustomSecretManagerConnectorDTO.class, 19875);
    kryo.register(TemplateLinkConfigForCustomSecretManager.class, 19877);
    kryo.register(GcpSecretManagerConnectorDTO.class, 19878);
    kryo.register(ELKConnectorDTO.class, 10000001);
    kryo.register(ELKAuthType.class, 10000002);

    kryo.register(AzureArtifactsConnectorDTO.class, 10000101);
    kryo.register(AzureArtifactsCredentialsDTO.class, 10000102);
    kryo.register(AzureArtifactsAuthenticationType.class, 10000103);
    kryo.register(AzureArtifactsAuthenticationDTO.class, 10000109);
    kryo.register(AzureArtifactsTokenDTO.class, 10000111);
    kryo.register(ServiceNowAuthenticationDTO.class, 10000112);
    kryo.register(ServiceNowAuthType.class, 10000113);
    kryo.register(ServiceNowAuthCredentialsDTO.class, 10000114);
    kryo.register(ServiceNowUserNamePasswordDTO.class, 10000115);
  }
}
