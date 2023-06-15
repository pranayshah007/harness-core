/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.heartbeat.ArtifactoryValidationParamsProvider;
import io.harness.connector.heartbeat.AwsKmsConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.AwsSecretManagerValidationParamsProvider;
import io.harness.connector.heartbeat.AwsValidationParamsProvider;
import io.harness.connector.heartbeat.AzureArtifactsConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.AzureKeyVaultConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.AzureValidationParamsProvider;
import io.harness.connector.heartbeat.CEK8sConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.CVConnectorParamsProvider;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.CustomSecretManagerValidationParamProvider;
import io.harness.connector.heartbeat.DockerConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.GcpKmsConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.GcpSecretManagerValidationParamProvider;
import io.harness.connector.heartbeat.GcpValidationParamsProvider;
import io.harness.connector.heartbeat.HttpHelmConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.JiraValidationParamsProvider;
import io.harness.connector.heartbeat.K8sConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.NexusValidationParamsProvider;
import io.harness.connector.heartbeat.NoOpConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.OciHelmConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.PhysicalDataCenterConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.RancherConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.ScmConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.ServiceNowValidationParamsProvider;
import io.harness.connector.heartbeat.SpotValidationParamsProvider;
import io.harness.connector.heartbeat.TasConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.TerraformCloudValidationParamsProvider;
import io.harness.connector.heartbeat.VaultConnectorValidationParamsProvider;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.connector.mappers.appdynamicsmapper.AppDynamicsDTOToEntity;
import io.harness.connector.mappers.appdynamicsmapper.AppDynamicsEntityToDTO;
import io.harness.connector.mappers.artifactorymapper.ArtifactoryDTOToEntity;
import io.harness.connector.mappers.artifactorymapper.ArtifactoryEntityToDTO;
import io.harness.connector.mappers.awscodecommit.AwsCodeCommitDTOToEntity;
import io.harness.connector.mappers.awscodecommit.AwsCodeCommitEntityToDTO;
import io.harness.connector.mappers.awsmapper.AwsDTOToEntity;
import io.harness.connector.mappers.awsmapper.AwsEntityToDTO;
import io.harness.connector.mappers.azureartifacts.AzureArtifactsDTOToEntity;
import io.harness.connector.mappers.azureartifacts.AzureArtifactsEntityToDTO;
import io.harness.connector.mappers.azuremapper.AzureDTOToEntity;
import io.harness.connector.mappers.azuremapper.AzureEntityToDTO;
import io.harness.connector.mappers.azurerepomapper.AzureRepoDTOToEntity;
import io.harness.connector.mappers.azurerepomapper.AzureRepoEntityToDTO;
import io.harness.connector.mappers.bamboo.BambooDTOToEntity;
import io.harness.connector.mappers.bamboo.BambooEntityToDTO;
import io.harness.connector.mappers.bitbucketconnectormapper.BitbucketDTOToEntity;
import io.harness.connector.mappers.bitbucketconnectormapper.BitbucketEntityToDTO;
import io.harness.connector.mappers.ceawsmapper.CEAwsDTOToEntity;
import io.harness.connector.mappers.ceawsmapper.CEAwsEntityToDTO;
import io.harness.connector.mappers.ceazure.CEAzureDTOToEntity;
import io.harness.connector.mappers.ceazure.CEAzureEntityToDTO;
import io.harness.connector.mappers.cek8s.CEKubernetesDTOToEntity;
import io.harness.connector.mappers.cek8s.CEKubernetesEntityToDTO;
import io.harness.connector.mappers.customhealthconnectormapper.CustomHealthDTOToEntity;
import io.harness.connector.mappers.customhealthconnectormapper.CustomHealthEntityToDTO;
import io.harness.connector.mappers.datadogmapper.DatadogDTOToEntity;
import io.harness.connector.mappers.datadogmapper.DatadogEntityToDTO;
import io.harness.connector.mappers.docker.DockerDTOToEntity;
import io.harness.connector.mappers.docker.DockerEntityToDTO;
import io.harness.connector.mappers.dynatracemapper.DynatraceDTOToEntity;
import io.harness.connector.mappers.dynatracemapper.DynatraceEntityToDTO;
import io.harness.connector.mappers.elkmapper.ELKDTOToEntity;
import io.harness.connector.mappers.elkmapper.ELKEntityToDTO;
import io.harness.connector.mappers.errortrackingmapper.ErrorTrackingDTOToEntity;
import io.harness.connector.mappers.errortrackingmapper.ErrorTrackingEntityToDTO;
import io.harness.connector.mappers.gcpcloudcost.GcpCloudCostDTOToEntity;
import io.harness.connector.mappers.gcpcloudcost.GcpCloudCostEntityToDTO;
import io.harness.connector.mappers.gcpmappers.GcpDTOToEntity;
import io.harness.connector.mappers.gcpmappers.GcpEntityToDTO;
import io.harness.connector.mappers.gitconnectormapper.GitDTOToEntity;
import io.harness.connector.mappers.gitconnectormapper.GitEntityToDTO;
import io.harness.connector.mappers.githubconnector.GithubDTOToEntity;
import io.harness.connector.mappers.githubconnector.GithubEntityToDTO;
import io.harness.connector.mappers.gitlabconnector.GitlabDTOToEntity;
import io.harness.connector.mappers.gitlabconnector.GitlabEntityToDTO;
import io.harness.connector.mappers.helm.HttpHelmDTOToEntity;
import io.harness.connector.mappers.helm.HttpHelmEntityToDTO;
import io.harness.connector.mappers.helm.OciHelmDTOToEntity;
import io.harness.connector.mappers.helm.OciHelmEntityToDTO;
import io.harness.connector.mappers.jenkins.JenkinsDTOToEntity;
import io.harness.connector.mappers.jenkins.JenkinsEntityToDTO;
import io.harness.connector.mappers.jira.JiraDTOToEntity;
import io.harness.connector.mappers.jira.JiraEntityToDTO;
import io.harness.connector.mappers.kubernetesMapper.KubernetesDTOToEntity;
import io.harness.connector.mappers.kubernetesMapper.KubernetesEntityToDTO;
import io.harness.connector.mappers.newerlicmapper.NewRelicDTOToEntity;
import io.harness.connector.mappers.newerlicmapper.NewRelicEntityToDTO;
import io.harness.connector.mappers.nexusmapper.NexusDTOToEntity;
import io.harness.connector.mappers.nexusmapper.NexusEntityToDTO;
import io.harness.connector.mappers.pagerduty.PagerDutyDTOToEntity;
import io.harness.connector.mappers.pagerduty.PagerDutyEntityToDTO;
import io.harness.connector.mappers.pdcconnector.PhysicalDataCenterDTOToEntity;
import io.harness.connector.mappers.pdcconnector.PhysicalDataCenterEntityToDTO;
import io.harness.connector.mappers.prometheusmapper.PrometheusDTOToEntity;
import io.harness.connector.mappers.prometheusmapper.PrometheusEntityToDTO;
import io.harness.connector.mappers.rancherMapper.RancherDTOToEntity;
import io.harness.connector.mappers.rancherMapper.RancherEntityToDTO;
import io.harness.connector.mappers.secretmanagermapper.AwsKmsDTOToEntity;
import io.harness.connector.mappers.secretmanagermapper.AwsKmsEntityToDTO;
import io.harness.connector.mappers.secretmanagermapper.AwsSecretManagerDTOToEntity;
import io.harness.connector.mappers.secretmanagermapper.AwsSecretManagerEntityToDTO;
import io.harness.connector.mappers.secretmanagermapper.AzureKeyVaultDTOToEntity;
import io.harness.connector.mappers.secretmanagermapper.AzureKeyVaultEntityToDTO;
import io.harness.connector.mappers.secretmanagermapper.CustomSecretManagerDTOToEntity;
import io.harness.connector.mappers.secretmanagermapper.CustomSecretManagerEntityToDTO;
import io.harness.connector.mappers.secretmanagermapper.GcpKmsDTOToEntity;
import io.harness.connector.mappers.secretmanagermapper.GcpKmsEntityToDTO;
import io.harness.connector.mappers.secretmanagermapper.GcpSecretManagerDTOToEntity;
import io.harness.connector.mappers.secretmanagermapper.GcpSecretManagerEntityToDTO;
import io.harness.connector.mappers.secretmanagermapper.LocalDTOToEntity;
import io.harness.connector.mappers.secretmanagermapper.LocalEntityToDTO;
import io.harness.connector.mappers.secretmanagermapper.VaultDTOToEntity;
import io.harness.connector.mappers.secretmanagermapper.VaultEntityToDTO;
import io.harness.connector.mappers.servicenow.ServiceNowDTOtoEntity;
import io.harness.connector.mappers.servicenow.ServiceNowEntityToDTO;
import io.harness.connector.mappers.signalfxmapper.SignalFXDTOToEntity;
import io.harness.connector.mappers.signalfxmapper.SignalFXEntityToDTO;
import io.harness.connector.mappers.splunkconnectormapper.SplunkDTOToEntity;
import io.harness.connector.mappers.splunkconnectormapper.SplunkEntityToDTO;
import io.harness.connector.mappers.spotmapper.SpotDTOToEntity;
import io.harness.connector.mappers.spotmapper.SpotEntityToDTO;
import io.harness.connector.mappers.sumologicmapper.SumoLogicDTOToEntity;
import io.harness.connector.mappers.sumologicmapper.SumoLogicEntityToDTO;
import io.harness.connector.mappers.tasmapper.TasDTOToEntity;
import io.harness.connector.mappers.tasmapper.TasEntityToDTO;
import io.harness.connector.mappers.terraformcloudmapper.TerraformCloudDTOToEntity;
import io.harness.connector.mappers.terraformcloudmapper.TerraformCloudEntityToDTO;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.NotSupportedValidationHandler;
import io.harness.connector.task.artifactory.ArtifactoryValidationHandler;
import io.harness.connector.task.aws.AwsValidationHandler;
import io.harness.connector.task.azure.AzureValidationHandler;
import io.harness.connector.task.docker.DockerValidationHandler;
import io.harness.connector.task.gcp.GcpValidationTaskHandler;
import io.harness.connector.task.git.GitValidationHandler;
import io.harness.connector.task.rancher.RancherValidationHandler;
import io.harness.connector.task.spot.SpotValidationHandler;
import io.harness.connector.task.tas.TasValidationHandler;
import io.harness.connector.task.terraformcloud.TerraformCloudValidationHandler;
import io.harness.connector.validator.ArtifactoryConnectionValidator;
import io.harness.connector.validator.AwsConnectorValidator;
import io.harness.connector.validator.AzureArtifactsConnectorValidator;
import io.harness.connector.validator.AzureConnectorValidator;
import io.harness.connector.validator.BambooConnectionValidator;
import io.harness.connector.validator.BambooConnectorValidationsParamsProvider;
import io.harness.connector.validator.CCMConnectorValidator;
import io.harness.connector.validator.CEKubernetesConnectionValidator;
import io.harness.connector.validator.CVConnectorValidator;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.connector.validator.DockerConnectionValidator;
import io.harness.connector.validator.ErrorTrackingConnectorValidator;
import io.harness.connector.validator.GcpConnectorValidator;
import io.harness.connector.validator.HttpHelmRepoConnectionValidator;
import io.harness.connector.validator.JenkinsConnectionValidator;
import io.harness.connector.validator.JiraConnectorValidator;
import io.harness.connector.validator.KubernetesConnectionValidator;
import io.harness.connector.validator.NexusConnectorValidator;
import io.harness.connector.validator.OciHelmRepoConnectionValidator;
import io.harness.connector.validator.PhysicalDataCenterConnectorValidator;
import io.harness.connector.validator.RancherConnectionValidator;
import io.harness.connector.validator.SecretManagerConnectorValidator;
import io.harness.connector.validator.ServiceNowConnectorValidator;
import io.harness.connector.validator.SpotConnectorValidator;
import io.harness.connector.validator.TerraformCloudConnectorValidator;
import io.harness.connector.validator.scmValidators.AwsCodeCommitValidator;
import io.harness.connector.validator.scmValidators.AzureRepoConnectorValidator;
import io.harness.connector.validator.scmValidators.BitbucketConnectorValidator;
import io.harness.connector.validator.scmValidators.GitConnectorValidator;
import io.harness.connector.validator.scmValidators.GithubConnectorValidator;
import io.harness.connector.validator.scmValidators.GitlabConnectorValidator;
import io.harness.connector.validator.scmValidators.JenkinsConnectorValidationsParamsProvider;
import io.harness.connector.validator.scmValidators.TasConnectorValidator;
import io.harness.delegate.beans.connector.ConnectorType;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(DX)
public class ConnectorRegistryFactory {
  private static Map<ConnectorType, ConnectorRegistrar> registrar = new HashMap<>();

  static {
    registrar.put(ConnectorType.KUBERNETES_CLUSTER,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_PROVIDER, KubernetesConnectionValidator.class,
            K8sConnectorValidationParamsProvider.class, KubernetesDTOToEntity.class, KubernetesEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.CE_KUBERNETES_CLUSTER,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_COST, CEKubernetesConnectionValidator.class,
            CEK8sConnectorValidationParamsProvider.class, CEKubernetesDTOToEntity.class, CEKubernetesEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.GIT,
        new ConnectorRegistrar(ConnectorCategory.CODE_REPO, GitConnectorValidator.class,
            ScmConnectorValidationParamsProvider.class, GitDTOToEntity.class, GitEntityToDTO.class,
            GitValidationHandler.class));
    registrar.put(ConnectorType.APP_DYNAMICS,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, CVConnectorValidator.class,
            CVConnectorParamsProvider.class, AppDynamicsDTOToEntity.class, AppDynamicsEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.NEW_RELIC,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, CVConnectorValidator.class,
            CVConnectorParamsProvider.class, NewRelicDTOToEntity.class, NewRelicEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.DATADOG,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, CVConnectorValidator.class,
            CVConnectorParamsProvider.class, DatadogDTOToEntity.class, DatadogEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.SPLUNK,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, CVConnectorValidator.class,
            CVConnectorParamsProvider.class, SplunkDTOToEntity.class, SplunkEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.PROMETHEUS,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, CVConnectorValidator.class,
            CVConnectorParamsProvider.class, PrometheusDTOToEntity.class, PrometheusEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.SUMOLOGIC,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, CVConnectorValidator.class,
            CVConnectorParamsProvider.class, SumoLogicDTOToEntity.class, SumoLogicEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.DYNATRACE,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, CVConnectorValidator.class,
            CVConnectorParamsProvider.class, DynatraceDTOToEntity.class, DynatraceEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.ELASTICSEARCH,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, CVConnectorValidator.class,
            CVConnectorParamsProvider.class, ELKDTOToEntity.class, ELKEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.VAULT,
        new ConnectorRegistrar(ConnectorCategory.SECRET_MANAGER, SecretManagerConnectorValidator.class,
            VaultConnectorValidationParamsProvider.class, VaultDTOToEntity.class, VaultEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.AZURE_KEY_VAULT,
        new ConnectorRegistrar(ConnectorCategory.SECRET_MANAGER, SecretManagerConnectorValidator.class,
            AzureKeyVaultConnectorValidationParamsProvider.class, AzureKeyVaultDTOToEntity.class,
            AzureKeyVaultEntityToDTO.class, NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.GCP_KMS,
        new ConnectorRegistrar(ConnectorCategory.SECRET_MANAGER, SecretManagerConnectorValidator.class,
            GcpKmsConnectorValidationParamsProvider.class, GcpKmsDTOToEntity.class, GcpKmsEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.AWS_KMS,
        new ConnectorRegistrar(ConnectorCategory.SECRET_MANAGER, SecretManagerConnectorValidator.class,
            AwsKmsConnectorValidationParamsProvider.class, AwsKmsDTOToEntity.class, AwsKmsEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.AWS_SECRET_MANAGER,
        new ConnectorRegistrar(ConnectorCategory.SECRET_MANAGER, SecretManagerConnectorValidator.class,
            AwsSecretManagerValidationParamsProvider.class, AwsSecretManagerDTOToEntity.class,
            AwsSecretManagerEntityToDTO.class, NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.LOCAL,
        new ConnectorRegistrar(ConnectorCategory.SECRET_MANAGER, SecretManagerConnectorValidator.class,
            NoOpConnectorValidationParamsProvider.class, LocalDTOToEntity.class, LocalEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.DOCKER,
        new ConnectorRegistrar(ConnectorCategory.ARTIFACTORY, DockerConnectionValidator.class,
            DockerConnectorValidationParamsProvider.class, DockerDTOToEntity.class, DockerEntityToDTO.class,
            DockerValidationHandler.class));
    registrar.put(ConnectorType.GCP,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_PROVIDER, GcpConnectorValidator.class,
            GcpValidationParamsProvider.class, GcpDTOToEntity.class, GcpEntityToDTO.class,
            GcpValidationTaskHandler.class));
    registrar.put(ConnectorType.AWS,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_PROVIDER, AwsConnectorValidator.class,
            AwsValidationParamsProvider.class, AwsDTOToEntity.class, AwsEntityToDTO.class, AwsValidationHandler.class));
    registrar.put(ConnectorType.SPOT,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_PROVIDER, SpotConnectorValidator.class,
            SpotValidationParamsProvider.class, SpotDTOToEntity.class, SpotEntityToDTO.class,
            SpotValidationHandler.class));
    registrar.put(ConnectorType.AZURE,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_PROVIDER, AzureConnectorValidator.class,
            AzureValidationParamsProvider.class, AzureDTOToEntity.class, AzureEntityToDTO.class,
            AzureValidationHandler.class));
    registrar.put(ConnectorType.CE_AWS,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_COST, CCMConnectorValidator.class,
            NoOpConnectorValidationParamsProvider.class, CEAwsDTOToEntity.class, CEAwsEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.ARTIFACTORY,
        new ConnectorRegistrar(ConnectorCategory.ARTIFACTORY, ArtifactoryConnectionValidator.class,
            ArtifactoryValidationParamsProvider.class, ArtifactoryDTOToEntity.class, ArtifactoryEntityToDTO.class,
            ArtifactoryValidationHandler.class));
    registrar.put(ConnectorType.JIRA,
        new ConnectorRegistrar(ConnectorCategory.TICKETING, JiraConnectorValidator.class,
            JiraValidationParamsProvider.class, JiraDTOToEntity.class, JiraEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.NEXUS,
        new ConnectorRegistrar(ConnectorCategory.ARTIFACTORY, NexusConnectorValidator.class,
            NexusValidationParamsProvider.class, NexusDTOToEntity.class, NexusEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.GITHUB,
        new ConnectorRegistrar(ConnectorCategory.CODE_REPO, GithubConnectorValidator.class,
            ScmConnectorValidationParamsProvider.class, GithubDTOToEntity.class, GithubEntityToDTO.class,
            GitValidationHandler.class));
    registrar.put(ConnectorType.HARNESS,
        new ConnectorRegistrar(ConnectorCategory.CODE_REPO, GithubConnectorValidator.class,
            ScmConnectorValidationParamsProvider.class, GithubDTOToEntity.class, GithubEntityToDTO.class,
            GitValidationHandler.class));
    registrar.put(ConnectorType.GITLAB,
        new ConnectorRegistrar(ConnectorCategory.CODE_REPO, GitlabConnectorValidator.class,
            ScmConnectorValidationParamsProvider.class, GitlabDTOToEntity.class, GitlabEntityToDTO.class,
            GitValidationHandler.class));
    registrar.put(ConnectorType.BITBUCKET,
        new ConnectorRegistrar(ConnectorCategory.CODE_REPO, BitbucketConnectorValidator.class,
            ScmConnectorValidationParamsProvider.class, BitbucketDTOToEntity.class, BitbucketEntityToDTO.class,
            GitValidationHandler.class));
    registrar.put(ConnectorType.CODECOMMIT,
        new ConnectorRegistrar(ConnectorCategory.CODE_REPO, AwsCodeCommitValidator.class,
            NoOpConnectorValidationParamsProvider.class, AwsCodeCommitDTOToEntity.class, AwsCodeCommitEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.CE_AZURE,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_COST, CCMConnectorValidator.class,
            NoOpConnectorValidationParamsProvider.class, CEAzureDTOToEntity.class, CEAzureEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.GCP_CLOUD_COST,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_COST, CCMConnectorValidator.class,
            NoOpConnectorValidationParamsProvider.class, GcpCloudCostDTOToEntity.class, GcpCloudCostEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.HTTP_HELM_REPO,
        new ConnectorRegistrar(ConnectorCategory.ARTIFACTORY, HttpHelmRepoConnectionValidator.class,
            HttpHelmConnectorValidationParamsProvider.class, HttpHelmDTOToEntity.class, HttpHelmEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.OCI_HELM_REPO,
        new ConnectorRegistrar(ConnectorCategory.ARTIFACTORY, OciHelmRepoConnectionValidator.class,
            OciHelmConnectorValidationParamsProvider.class, OciHelmDTOToEntity.class, OciHelmEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.PAGER_DUTY,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, CVConnectorValidator.class,
            CVConnectorParamsProvider.class, PagerDutyDTOToEntity.class, PagerDutyEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.CUSTOM_HEALTH,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, CVConnectorValidator.class,
            CVConnectorParamsProvider.class, CustomHealthDTOToEntity.class, CustomHealthEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.SERVICENOW,
        new ConnectorRegistrar(ConnectorCategory.TICKETING, ServiceNowConnectorValidator.class,
            ServiceNowValidationParamsProvider.class, ServiceNowDTOtoEntity.class, ServiceNowEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.ERROR_TRACKING,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, ErrorTrackingConnectorValidator.class,
            CVConnectorParamsProvider.class, ErrorTrackingDTOToEntity.class, ErrorTrackingEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.AZURE_REPO,
        new ConnectorRegistrar(ConnectorCategory.CODE_REPO, AzureRepoConnectorValidator.class,
            ScmConnectorValidationParamsProvider.class, AzureRepoDTOToEntity.class, AzureRepoEntityToDTO.class,
            GitValidationHandler.class));
    registrar.put(ConnectorType.PDC,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_PROVIDER, PhysicalDataCenterConnectorValidator.class,
            PhysicalDataCenterConnectorValidationParamsProvider.class, PhysicalDataCenterDTOToEntity.class,
            PhysicalDataCenterEntityToDTO.class, NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.JENKINS,
        new ConnectorRegistrar(ConnectorCategory.ARTIFACTORY, JenkinsConnectionValidator.class,
            JenkinsConnectorValidationsParamsProvider.class, JenkinsDTOToEntity.class, JenkinsEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.BAMBOO,
        new ConnectorRegistrar(ConnectorCategory.ARTIFACTORY, BambooConnectionValidator.class,
            BambooConnectorValidationsParamsProvider.class, BambooDTOToEntity.class, BambooEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.CUSTOM_SECRET_MANAGER,
        new ConnectorRegistrar(ConnectorCategory.SECRET_MANAGER, SecretManagerConnectorValidator.class,
            CustomSecretManagerValidationParamProvider.class, CustomSecretManagerDTOToEntity.class,
            CustomSecretManagerEntityToDTO.class, NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.AZURE_ARTIFACTS,
        new ConnectorRegistrar(ConnectorCategory.ARTIFACTORY, AzureArtifactsConnectorValidator.class,
            AzureArtifactsConnectorValidationParamsProvider.class, AzureArtifactsDTOToEntity.class,
            AzureArtifactsEntityToDTO.class, NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.TAS,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_PROVIDER, TasConnectorValidator.class,
            TasConnectorValidationParamsProvider.class, TasDTOToEntity.class, TasEntityToDTO.class,
            TasValidationHandler.class));
    registrar.put(ConnectorType.GCP_SECRET_MANAGER,
        new ConnectorRegistrar(ConnectorCategory.SECRET_MANAGER, SecretManagerConnectorValidator.class,
            GcpSecretManagerValidationParamProvider.class, GcpSecretManagerDTOToEntity.class,
            GcpSecretManagerEntityToDTO.class, NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.TERRAFORM_CLOUD,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_PROVIDER, TerraformCloudConnectorValidator.class,
            TerraformCloudValidationParamsProvider.class, TerraformCloudDTOToEntity.class,
            TerraformCloudEntityToDTO.class, TerraformCloudValidationHandler.class));
    registrar.put(ConnectorType.SIGNALFX,
        new ConnectorRegistrar(ConnectorCategory.MONITORING, CVConnectorValidator.class,
            CVConnectorParamsProvider.class, SignalFXDTOToEntity.class, SignalFXEntityToDTO.class,
            NotSupportedValidationHandler.class));
    registrar.put(ConnectorType.RANCHER,
        new ConnectorRegistrar(ConnectorCategory.CLOUD_PROVIDER, RancherConnectionValidator.class,
            RancherConnectorValidationParamsProvider.class, RancherDTOToEntity.class, RancherEntityToDTO.class,
            RancherValidationHandler.class));
  }

  public static Class<? extends ConnectionValidator> getConnectorValidator(ConnectorType connectorType) {
    return registrar.get(connectorType).getConnectorValidator();
  }

  public static Class<? extends ConnectorValidationParamsProvider> getConnectorValidationParamsProvider(
      ConnectorType connectorType) {
    return registrar.get(connectorType).getConnectorValidationParams();
  }

  public static ConnectorCategory getConnectorCategory(ConnectorType connectorType) {
    return registrar.get(connectorType).getConnectorCategory();
  }

  public static Class<? extends ConnectorDTOToEntityMapper<?, ?>> getConnectorDTOToEntityMapper(
      ConnectorType connectorType) {
    return registrar.get(connectorType).getConnectorDTOToEntityMapper();
  }

  public static Class<? extends ConnectorEntityToDTOMapper<?, ?>> getConnectorEntityToDTOMapper(
      ConnectorType connectorType) {
    return registrar.get(connectorType).getConnectorEntityToDTOMapper();
  }

  public static Class<? extends ConnectorValidationHandler> getConnectorValidationHandler(ConnectorType connectorType) {
    return registrar.get(connectorType).getConnectorValidationHandler();
  }
}
