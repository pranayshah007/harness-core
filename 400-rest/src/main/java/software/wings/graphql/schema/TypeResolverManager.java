/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.schema;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.QLApprovalStageExecution;
import software.wings.graphql.schema.type.QLDeploymentOutcome;
import software.wings.graphql.schema.type.QLExecutedAlongPipeline;
import software.wings.graphql.schema.type.QLExecutedByAPIKey;
import software.wings.graphql.schema.type.QLExecutedByTrigger;
import software.wings.graphql.schema.type.QLExecutedByUser;
import software.wings.graphql.schema.type.QLPipelineExecution;
import software.wings.graphql.schema.type.QLWorkflowExecution;
import software.wings.graphql.schema.type.QLWorkflowStageExecution;
import software.wings.graphql.schema.type.aggregation.QLAggregatedData;
import software.wings.graphql.schema.type.aggregation.QLSinglePointData;
import software.wings.graphql.schema.type.aggregation.QLStackedData;
import software.wings.graphql.schema.type.aggregation.QLStackedTimeSeriesData;
import software.wings.graphql.schema.type.aggregation.QLTimeSeriesData;
import software.wings.graphql.schema.type.approval.QLJIRAApprovalDetails;
import software.wings.graphql.schema.type.approval.QLSNOWApprovalDetails;
import software.wings.graphql.schema.type.approval.QLShellScriptDetails;
import software.wings.graphql.schema.type.approval.QLUGApprovalDetails;
import software.wings.graphql.schema.type.artifactSource.QLACRArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLAMIArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLAmazonS3ArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLArtifactoryArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLArtifactoryDockerProps;
import software.wings.graphql.schema.type.artifactSource.QLArtifactoryFileProps;
import software.wings.graphql.schema.type.artifactSource.QLAzureArtifactsArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLAzureMachineImageArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLBambooArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLCustomArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLDockerArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLECRArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLGCRArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLGCSArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLJenkinsArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLNexusArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLNexusDockerProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusMavenProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusNpmProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusNugetProps;
import software.wings.graphql.schema.type.artifactSource.QLSFTPArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLSMBArtifactSource;
import software.wings.graphql.schema.type.audit.QLApiKeyChangeSet;
import software.wings.graphql.schema.type.audit.QLGenericChangeSet;
import software.wings.graphql.schema.type.audit.QLGitChangeSet;
import software.wings.graphql.schema.type.audit.QLUserChangeSet;
import software.wings.graphql.schema.type.cloudProvider.QLAwsCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLAzureCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLGcpCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLKubernetesClusterCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLPcfCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLPhysicalDataCenterCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLSpotInstCloudProvider;
import software.wings.graphql.schema.type.connector.QLAmazonS3Connector;
import software.wings.graphql.schema.type.connector.QLAmazonS3HelmRepoConnector;
import software.wings.graphql.schema.type.connector.QLApmVerificationConnector;
import software.wings.graphql.schema.type.connector.QLAppDynamicsConnector;
import software.wings.graphql.schema.type.connector.QLArtifactoryConnector;
import software.wings.graphql.schema.type.connector.QLBambooConnector;
import software.wings.graphql.schema.type.connector.QLBugSnagConnector;
import software.wings.graphql.schema.type.connector.QLDataDogConnector;
import software.wings.graphql.schema.type.connector.QLDockerConnector;
import software.wings.graphql.schema.type.connector.QLDynaTraceConnector;
import software.wings.graphql.schema.type.connector.QLECRConnector;
import software.wings.graphql.schema.type.connector.QLElbConnector;
import software.wings.graphql.schema.type.connector.QLElkConnector;
import software.wings.graphql.schema.type.connector.QLGCRConnector;
import software.wings.graphql.schema.type.connector.QLGCSConnector;
import software.wings.graphql.schema.type.connector.QLGCSHelmRepoConnector;
import software.wings.graphql.schema.type.connector.QLGitConnector;
import software.wings.graphql.schema.type.connector.QLHttpHelmRepoConnector;
import software.wings.graphql.schema.type.connector.QLInstanaConnector;
import software.wings.graphql.schema.type.connector.QLJenkinsConnector;
import software.wings.graphql.schema.type.connector.QLJiraConnector;
import software.wings.graphql.schema.type.connector.QLLogzConnector;
import software.wings.graphql.schema.type.connector.QLNewRelicConnector;
import software.wings.graphql.schema.type.connector.QLNexusConnector;
import software.wings.graphql.schema.type.connector.QLOciHelmRepoConnector;
import software.wings.graphql.schema.type.connector.QLPrometheusConnector;
import software.wings.graphql.schema.type.connector.QLSMBConnector;
import software.wings.graphql.schema.type.connector.QLServiceNowConnector;
import software.wings.graphql.schema.type.connector.QLSftpConnector;
import software.wings.graphql.schema.type.connector.QLSlackConnector;
import software.wings.graphql.schema.type.connector.QLSmtpConnector;
import software.wings.graphql.schema.type.connector.QLSplunkConnector;
import software.wings.graphql.schema.type.connector.QLSumoConnector;
import software.wings.graphql.schema.type.instance.QLAutoScalingGroupInstance;
import software.wings.graphql.schema.type.instance.QLCodeDeployInstance;
import software.wings.graphql.schema.type.instance.QLEc2Instance;
import software.wings.graphql.schema.type.instance.QLEcsContainerInstance;
import software.wings.graphql.schema.type.instance.QLK8SPodInstance;
import software.wings.graphql.schema.type.instance.QLPcfInstance;
import software.wings.graphql.schema.type.instance.QLPhysicalHostInstance;
import software.wings.graphql.schema.type.secrets.QLEncryptedFile;
import software.wings.graphql.schema.type.secrets.QLEncryptedText;
import software.wings.graphql.schema.type.secrets.QLKerberosAuthentication;
import software.wings.graphql.schema.type.secrets.QLSSHAuthentication;
import software.wings.graphql.schema.type.secrets.QLSSHCredential;
import software.wings.graphql.schema.type.secrets.QLWinRMCredential;
import software.wings.graphql.schema.type.trigger.QLFromTriggeringAppManifest;
import software.wings.graphql.schema.type.trigger.QLFromTriggeringArtifactSource;
import software.wings.graphql.schema.type.trigger.QLFromTriggeringPipeline;
import software.wings.graphql.schema.type.trigger.QLFromWebhookPayload;
import software.wings.graphql.schema.type.trigger.QLLastCollected;
import software.wings.graphql.schema.type.trigger.QLLastCollectedManifest;
import software.wings.graphql.schema.type.trigger.QLLastDeployedFromPipeline;
import software.wings.graphql.schema.type.trigger.QLLastDeployedFromWorkflow;
import software.wings.graphql.schema.type.trigger.QLLastDeployedManifestFromPipeline;
import software.wings.graphql.schema.type.trigger.QLLastDeployedManifestFromWorkflow;
import software.wings.graphql.schema.type.trigger.QLManifestFromTriggeringPipeline;
import software.wings.graphql.schema.type.trigger.QLManifestFromWebhookPayload;
import software.wings.graphql.schema.type.trigger.QLOnNewArtifact;
import software.wings.graphql.schema.type.trigger.QLOnNewManifest;
import software.wings.graphql.schema.type.trigger.QLOnPipelineCompletion;
import software.wings.graphql.schema.type.trigger.QLOnSchedule;
import software.wings.graphql.schema.type.trigger.QLOnWebhook;
import software.wings.graphql.schema.type.trigger.QLPipelineAction;
import software.wings.graphql.schema.type.trigger.QLWorkflowAction;
import software.wings.graphql.schema.type.usergroup.QLLDAPSettings;
import software.wings.graphql.schema.type.usergroup.QLSAMLSettings;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;
import graphql.schema.TypeResolver;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.experimental.UtilityClass;

@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
@OwnedBy(HarnessTeam.DX)
public class TypeResolverManager {
  // Uniface is a short for union or interface
  @UtilityClass
  public static final class TypeResolverManagerUnifaces {
    public static final String Cause = "Cause";
    public static final String CloudProvider = "CloudProvider";
    public static final String Connector = "Connector";
    public static final String Data = "Data";
    public static final String Execution = "Execution";
    public static final String ArtifactSource = "ArtifactSource";
    public static final String ArtifactoryProps = "ArtifactoryProps";
    public static final String NexusProps = "NexusProps";
    public static final String Instance = "Instance";
    public static final String Outcome = "Outcome";
    public static final String PhysicalInstance = "PhysicalInstance";
    public static final String ChangeSet = "ChangeSet";
    public static final String LinkedSSOSetting = "LinkedSSOSetting";
    public static final String Secret = "Secret";
    public static final String SSHAuthenticationType = "SSHAuthenticationType";
    public static final String TRIGGER_CONDITION = "TriggerCondition";
    public static final String TRIGGER_ACTION = "TriggerAction";
    public static final String ARTIFACT_SELECTION = "ArtifactSelection";
    public static final String PipelineStageExecution = "PipelineStageExecution";
    public static final String MANIFEST_SELECTION = "ManifestSelection";
    public static final String APPROVAL_DETAILS = "ApprovalDetails";
  }

  @UtilityClass
  public static final class TypeResolverManagerTypes {
    public static final String AggregatedData = "AggregatedData";
    public static final String ApprovalStageExecution = "ApprovalStageExecution";
    public static final String AutoScalingGroupInstance = "AutoScalingGroupInstance";
    public static final String AwsCloudProvider = "AwsCloudProvider";
    public static final String AzureCloudProvider = "AzureCloudProvider";
    public static final String CodeDeployInstance = "CodeDeployInstance";
    public static final String DeploymentOutcome = "DeploymentOutcome";
    public static final String Ec2Instance = "Ec2Instance";
    public static final String EcsContainerInstance = "EcsContainerInstance";
    public static final String ExecutedAlongPipeline = "ExecutedAlongPipeline";
    public static final String ExecutedByTrigger = "ExecutedByTrigger";
    public static final String ExecutedByUser = "ExecutedByUser";
    public static final String ExecutedByAPIKey = "ExecutedByAPIKey";
    public static final String GcpCloudProvider = "GcpCloudProvider";
    public static final String K8sPodInstance = "K8sPodInstance";
    public static final String KubernetesCloudProvider = "KubernetesCloudProvider";
    public static final String PcfCloudProvider = "PcfCloudProvider";
    public static final String SpotInstCloudProvider = "SpotInstCloudProvider";
    public static final String PcfInstance = "PcfInstance";
    public static final String PhysicalDataCenterCloudProvider = "PhysicalDataCenterCloudProvider";
    public static final String PhysicalHostInstance = "PhysicalHostInstance";
    public static final String PipelineExecution = "PipelineExecution";
    public static final String PipelineStageExecution = "PipelineStageExecution";
    public static final String SinglePointData = "SinglePointData";
    public static final String StackedData = "StackedData";
    public static final String StackedTimeSeriesData = "StackedTimeSeriesData";
    public static final String TimeSeriesData = "TimeSeriesData";
    public static final String WorkflowExecution = "WorkflowExecution";
    public static final String WorkflowStageExecution = "WorkflowStageExecution";

    public static final String ACRArtifactSource = "ACRArtifactSource";
    public static final String AmazonS3ArtifactSource = "AmazonS3ArtifactSource";
    public static final String AMIArtifactSource = "AMIArtifactSource";
    public static final String ArtifactoryArtifactSource = "ArtifactoryArtifactSource";
    public static final String AzureArtifactsArtifactSource = "AzureArtifactsArtifactSource";
    public static final String AzureMachineImageArtifactSource = "AzureMachineImageArtifactSource";
    public static final String BambooArtifactSource = "BambooArtifactSource";
    public static final String CustomArtifactSource = "CustomArtifactSource";
    public static final String DockerArtifactSource = "DockerArtifactSource";
    public static final String ECRArtifactSource = "ECRArtifactSource";
    public static final String GCRArtifactSource = "GCRArtifactSource";
    public static final String GCSArtifactSource = "GCSArtifactSource";
    public static final String JenkinsArtifactSource = "JenkinsArtifactSource";
    public static final String NexusArtifactSource = "NexusArtifactSource";
    public static final String SFTPArtifactSource = "SFTPArtifactSource";
    public static final String SMBArtifactSource = "SMBArtifactSource";
    public static final String ArtifactoryDockerProps = "ArtifactoryDockerProps";
    public static final String ArtifactoryFileProps = "ArtifactoryFileProps";
    public static final String NexusDockerProps = "NexusDockerProps";
    public static final String NexusMavenProps = "NexusMavenProps";
    public static final String NexusNpmProps = "NexusNpmProps";
    public static final String NexusNugetProps = "NexusNugetProps";

    public static final String AmazonS3Connector = "AmazonS3Connector";
    public static final String ApmVerificationConnector = "ApmVerificationConnector";
    public static final String AppDynamicsConnector = "AppDynamicsConnector";
    public static final String InstanaCollector = "InstanaConnector";
    public static final String ArtifactoryConnector = "ArtifactoryConnector";
    public static final String BambooConnector = "BambooConnector";
    public static final String BugSnagConnector = "BugSnagConnector";
    public static final String DataDogConnector = "DataDogConnector";
    public static final String DockerConnector = "DockerConnector";
    public static final String DynaTraceConnector = "DynaTraceConnector";
    public static final String ECRConnector = "ECRConnector";
    public static final String ElbConnector = "ElbConnector";
    public static final String ElkConnector = "ElkConnector";
    public static final String GCRConnector = "GCRConnector";
    public static final String GCSConnector = "GCSConnector";
    public static final String GitConnector = "GitConnector";
    public static final String JenkinsConnector = "JenkinsConnector";
    public static final String JiraConnector = "JiraConnector";
    public static final String LogzConnector = "LogzConnector";
    public static final String NewRelicConnector = "NewRelicConnector";
    public static final String NexusConnector = "NexusConnector";
    public static final String PrometheusConnector = "PrometheusConnector";
    public static final String ServiceNowConnector = "ServiceNowConnector";
    public static final String SftpConnector = "SftpConnector";
    public static final String SlackConnector = "SlackConnector";
    public static final String SMBConnector = "SMBConnector";
    public static final String SmtpConnector = "SmtpConnector";
    public static final String SplunkConnector = "SplunkConnector";
    public static final String SumoConnector = "SumoConnector";
    public static final String GCSHelmRepoConnector = "GCSHelmRepoConnector";
    public static final String HttpHelmRepoConnector = "HttpHelmRepoConnector";
    public static final String ociHelmRepoConnector = "OciHelmRepoConnector";
    public static final String AmazonS3HelmRepoConnector = "AmazonS3HelmRepoConnector";
    public static final String UserChangeSet = "UserChangeSet";
    public static final String GitChangeSet = "GitChangeSet";
    public static final String ApiKeyChangeSet = "ApiKeyChangeSet";
    public static final String GenericChangeSet = "GenericChangeSet";
    public static final String ldapSettings = "LDAPSettings";
    public static final String samlSettings = "SAMLSettings";
    public static final String encryptedText = "EncryptedText";
    public static final String encryptedFile = "EncryptedFile";
    public static final String winRMCredential = "WinRMCredential";
    public static final String sshCredential = "SSHCredential";
    public static final String sshAuthentication = "SSHAuthentication";
    public static final String kerberosAuthentication = "KerberosAuthentication";

    public static final String ON_NEW_ARTIFACT = "OnNewArtifact";
    public static final String ON_PIPELINE_COMPLETION = "OnPipelineCompletion";
    public static final String ON_WEB_HOOK = "OnWebhook";
    public static final String ON_SCHEDULE = "OnSchedule";
    public static final String WORKFLOW_ACTION = "WorkflowAction";
    public static final String PIPELINE_ACTION = "PipelineAction";
    public static final String ON_NEW_MANIFEST = "OnNewManifest";
    public static final String FROM_TRIGGERING_ARTIFACT_SOURCE = "FromTriggeringArtifactSource";
    public static final String LAST_COLLECTED = "LastCollected";
    public static final String LAST_DEPLOYED_FROM_WORKFLOW = "LastDeployedFromWorkflow";
    public static final String LAST_DEPLOYED_FROM_PIPELINE = "LastDeployedFromPipeline";
    public static final String FROM_TRIGGERING_PIPELINE = "FromTriggeringPipeline";
    public static final String FROM_WEBHOOK_PAYLOAD = "FromWebhookPayload";
    public static final String EXECUTION_INPUTS_TO_RESUME_PIPELINE = "ExecutionInputsToResumePipeline";

    public static final String FROM_TRIGGERING_APP_MANIFEST = "FromTriggeringAppManifest";
    public static final String LAST_COLLECTED_MANIFEST = "LastCollectedManifest";
    public static final String LAST_DEPLOYED_MANIFEST_FROM_WORKFLOW = "LastDeployedManifestFromWorkflow";
    public static final String LAST_DEPLOYED_MANIFEST_FROM_PIPELINE = "LastDeployedManifestFromPipeline";
    public static final String MANIFEST_FROM_TRIGGERING_PIPELINE = "ManifestFromTriggeringPipeline";
    public static final String MANIFEST_FROM_WEBHOOK_PAYLOAD = "ManifestFromWebhookPayload";

    public static final String UG_APPROVAL_DETAILS = "UserGroupApprovalDetails";
    public static final String JIRA_APPROVAL_DETAILS = "JiraApprovalDetails";
    public static final String SNOW_APPROVAL_DETAILS = "SNOWApprovalDetails";
    public static final String SHELL_SCRIPT_DETAILS = "ShellScriptDetails";
  }

  /**
   * Later, we should have TEST to make sure a fieldName is only used once
   * otherwise it may be overridden.
   * @return
   */
  public Map<String, TypeResolver> getTypeResolverMap() {
    return ImmutableMap.<String, TypeResolver>builder()
        .put(TypeResolverManagerUnifaces.Cause,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLExecutedByAPIKey.class, TypeResolverManagerTypes.ExecutedByAPIKey)
                    .put(QLExecutedByUser.class, TypeResolverManagerTypes.ExecutedByUser)
                    .put(QLExecutedAlongPipeline.class, TypeResolverManagerTypes.ExecutedAlongPipeline)
                    .put(QLExecutedByTrigger.class, TypeResolverManagerTypes.ExecutedByTrigger)
                    .build()))
        .put(TypeResolverManagerUnifaces.CloudProvider,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLAwsCloudProvider.class, TypeResolverManagerTypes.AwsCloudProvider)
                    .put(QLPhysicalDataCenterCloudProvider.class,
                        TypeResolverManagerTypes.PhysicalDataCenterCloudProvider)
                    .put(QLAzureCloudProvider.class, TypeResolverManagerTypes.AzureCloudProvider)
                    .put(QLGcpCloudProvider.class, TypeResolverManagerTypes.GcpCloudProvider)
                    .put(QLKubernetesClusterCloudProvider.class, TypeResolverManagerTypes.KubernetesCloudProvider)
                    .put(QLPcfCloudProvider.class, TypeResolverManagerTypes.PcfCloudProvider)
                    .put(QLSpotInstCloudProvider.class, TypeResolverManagerTypes.SpotInstCloudProvider)
                    .build()))
        .put(TypeResolverManagerUnifaces.Connector,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLAmazonS3Connector.class, TypeResolverManagerTypes.AmazonS3Connector)
                    .put(QLApmVerificationConnector.class, TypeResolverManagerTypes.ApmVerificationConnector)
                    .put(QLAppDynamicsConnector.class, TypeResolverManagerTypes.AppDynamicsConnector)
                    .put(QLArtifactoryConnector.class, TypeResolverManagerTypes.ArtifactoryConnector)
                    .put(QLBambooConnector.class, TypeResolverManagerTypes.BambooConnector)
                    .put(QLBugSnagConnector.class, TypeResolverManagerTypes.BugSnagConnector)
                    .put(QLDataDogConnector.class, TypeResolverManagerTypes.DataDogConnector)
                    .put(QLDockerConnector.class, TypeResolverManagerTypes.DockerConnector)
                    .put(QLDynaTraceConnector.class, TypeResolverManagerTypes.DynaTraceConnector)
                    .put(QLECRConnector.class, TypeResolverManagerTypes.ECRConnector)
                    .put(QLElbConnector.class, TypeResolverManagerTypes.ElbConnector)
                    .put(QLElkConnector.class, TypeResolverManagerTypes.ElkConnector)
                    .put(QLGCRConnector.class, TypeResolverManagerTypes.GCRConnector)
                    .put(QLGCSConnector.class, TypeResolverManagerTypes.GCSConnector)
                    .put(QLGitConnector.class, TypeResolverManagerTypes.GitConnector)
                    .put(QLJenkinsConnector.class, TypeResolverManagerTypes.JenkinsConnector)
                    .put(QLJiraConnector.class, TypeResolverManagerTypes.JiraConnector)
                    .put(QLLogzConnector.class, TypeResolverManagerTypes.LogzConnector)
                    .put(QLNewRelicConnector.class, TypeResolverManagerTypes.NewRelicConnector)
                    .put(QLNexusConnector.class, TypeResolverManagerTypes.NexusConnector)
                    .put(QLPrometheusConnector.class, TypeResolverManagerTypes.PrometheusConnector)
                    .put(QLServiceNowConnector.class, TypeResolverManagerTypes.ServiceNowConnector)
                    .put(QLSftpConnector.class, TypeResolverManagerTypes.SftpConnector)
                    .put(QLSlackConnector.class, TypeResolverManagerTypes.SlackConnector)
                    .put(QLSMBConnector.class, TypeResolverManagerTypes.SMBConnector)
                    .put(QLSmtpConnector.class, TypeResolverManagerTypes.SmtpConnector)
                    .put(QLSplunkConnector.class, TypeResolverManagerTypes.SplunkConnector)
                    .put(QLSumoConnector.class, TypeResolverManagerTypes.SumoConnector)
                    .put(QLInstanaConnector.class, TypeResolverManagerTypes.InstanaCollector)
                    .put(QLGCSHelmRepoConnector.class, TypeResolverManagerTypes.GCSHelmRepoConnector)
                    .put(QLHttpHelmRepoConnector.class, TypeResolverManagerTypes.HttpHelmRepoConnector)
                    .put(QLOciHelmRepoConnector.class, TypeResolverManagerTypes.ociHelmRepoConnector)
                    .put(QLAmazonS3HelmRepoConnector.class, TypeResolverManagerTypes.AmazonS3HelmRepoConnector)
                    .build()))
        .put(TypeResolverManagerUnifaces.Execution,
            getResultTypeResolver(ImmutableMap.<Class, String>builder()
                                      .put(QLPipelineExecution.class, TypeResolverManagerTypes.PipelineExecution)
                                      .put(QLWorkflowExecution.class, TypeResolverManagerTypes.WorkflowExecution)
                                      .build()))
        .put(TypeResolverManagerUnifaces.PipelineStageExecution,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLApprovalStageExecution.class, TypeResolverManagerTypes.ApprovalStageExecution)
                    .put(QLWorkflowStageExecution.class, TypeResolverManagerTypes.WorkflowStageExecution)
                    .build()))
        .put(TypeResolverManagerUnifaces.ArtifactSource,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLACRArtifactSource.class, TypeResolverManagerTypes.ACRArtifactSource)
                    .put(QLAmazonS3ArtifactSource.class, TypeResolverManagerTypes.AmazonS3ArtifactSource)
                    .put(QLAMIArtifactSource.class, TypeResolverManagerTypes.AMIArtifactSource)
                    .put(QLArtifactoryArtifactSource.class, TypeResolverManagerTypes.ArtifactoryArtifactSource)
                    .put(QLAzureArtifactsArtifactSource.class, TypeResolverManagerTypes.AzureArtifactsArtifactSource)
                    .put(QLAzureMachineImageArtifactSource.class,
                        TypeResolverManagerTypes.AzureMachineImageArtifactSource)
                    .put(QLBambooArtifactSource.class, TypeResolverManagerTypes.BambooArtifactSource)
                    .put(QLCustomArtifactSource.class, TypeResolverManagerTypes.CustomArtifactSource)
                    .put(QLDockerArtifactSource.class, TypeResolverManagerTypes.DockerArtifactSource)
                    .put(QLECRArtifactSource.class, TypeResolverManagerTypes.ECRArtifactSource)
                    .put(QLGCRArtifactSource.class, TypeResolverManagerTypes.GCRArtifactSource)
                    .put(QLGCSArtifactSource.class, TypeResolverManagerTypes.GCSArtifactSource)
                    .put(QLJenkinsArtifactSource.class, TypeResolverManagerTypes.JenkinsArtifactSource)
                    .put(QLNexusArtifactSource.class, TypeResolverManagerTypes.NexusArtifactSource)
                    .put(QLSFTPArtifactSource.class, TypeResolverManagerTypes.SFTPArtifactSource)
                    .put(QLSMBArtifactSource.class, TypeResolverManagerTypes.SMBArtifactSource)
                    .build()))
        .put(TypeResolverManagerUnifaces.ArtifactoryProps,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLArtifactoryDockerProps.class, TypeResolverManagerTypes.ArtifactoryDockerProps)
                    .put(QLArtifactoryFileProps.class, TypeResolverManagerTypes.ArtifactoryFileProps)
                    .build()))
        .put(TypeResolverManagerUnifaces.NexusProps,
            getResultTypeResolver(ImmutableMap.<Class, String>builder()
                                      .put(QLNexusDockerProps.class, TypeResolverManagerTypes.NexusDockerProps)
                                      .put(QLNexusMavenProps.class, TypeResolverManagerTypes.NexusMavenProps)
                                      .put(QLNexusNpmProps.class, TypeResolverManagerTypes.NexusNpmProps)
                                      .put(QLNexusNugetProps.class, TypeResolverManagerTypes.NexusNugetProps)
                                      .build()))
        .put(TypeResolverManagerUnifaces.Instance,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLPhysicalHostInstance.class, TypeResolverManagerTypes.PhysicalHostInstance)
                    .put(QLK8SPodInstance.class, TypeResolverManagerTypes.K8sPodInstance)
                    .put(QLEc2Instance.class, TypeResolverManagerTypes.Ec2Instance)
                    .put(QLPcfInstance.class, TypeResolverManagerTypes.PcfInstance)
                    .put(QLCodeDeployInstance.class, TypeResolverManagerTypes.CodeDeployInstance)
                    .put(QLEcsContainerInstance.class, TypeResolverManagerTypes.EcsContainerInstance)
                    .put(QLAutoScalingGroupInstance.class, TypeResolverManagerTypes.AutoScalingGroupInstance)
                    .build()))
        .put(TypeResolverManagerUnifaces.Outcome,
            getResultTypeResolver(ImmutableMap.<Class, String>builder()
                                      .put(QLDeploymentOutcome.class, TypeResolverManagerTypes.DeploymentOutcome)
                                      .build()))
        .put(TypeResolverManagerUnifaces.PhysicalInstance,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLPhysicalHostInstance.class, TypeResolverManagerTypes.PhysicalHostInstance)
                    .put(QLEc2Instance.class, TypeResolverManagerTypes.Ec2Instance)
                    .put(QLCodeDeployInstance.class, TypeResolverManagerTypes.CodeDeployInstance)
                    .put(QLAutoScalingGroupInstance.class, TypeResolverManagerTypes.AutoScalingGroupInstance)
                    .build()))
        .put(TypeResolverManagerUnifaces.Data,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLAggregatedData.class, TypeResolverManagerTypes.AggregatedData)
                    .put(QLSinglePointData.class, TypeResolverManagerTypes.SinglePointData)
                    .put(QLStackedData.class, TypeResolverManagerTypes.StackedData)
                    .put(QLTimeSeriesData.class, TypeResolverManagerTypes.TimeSeriesData)
                    .put(QLStackedTimeSeriesData.class, TypeResolverManagerTypes.StackedTimeSeriesData)
                    .build()))
        .put(TypeResolverManagerUnifaces.ChangeSet,
            getResultTypeResolver(ImmutableMap.<Class, String>builder()
                                      .put(QLUserChangeSet.class, TypeResolverManagerTypes.UserChangeSet)
                                      .put(QLGitChangeSet.class, TypeResolverManagerTypes.GitChangeSet)
                                      .put(QLApiKeyChangeSet.class, TypeResolverManagerTypes.ApiKeyChangeSet)
                                      .put(QLGenericChangeSet.class, TypeResolverManagerTypes.GenericChangeSet)
                                      .build()))
        .put(TypeResolverManagerUnifaces.LinkedSSOSetting,
            getResultTypeResolver(ImmutableMap.<Class, String>builder()
                                      .put(QLLDAPSettings.class, TypeResolverManagerTypes.ldapSettings)
                                      .put(QLSAMLSettings.class, TypeResolverManagerTypes.samlSettings)
                                      .build()))
        .put(TypeResolverManagerUnifaces.Secret,
            getResultTypeResolver(ImmutableMap.<Class, String>builder()
                                      .put(QLEncryptedText.class, TypeResolverManagerTypes.encryptedText)
                                      .put(QLEncryptedFile.class, TypeResolverManagerTypes.encryptedFile)
                                      .put(QLWinRMCredential.class, TypeResolverManagerTypes.winRMCredential)
                                      .put(QLSSHCredential.class, TypeResolverManagerTypes.sshCredential)
                                      .build()))
        .put(TypeResolverManagerUnifaces.SSHAuthenticationType,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLSSHAuthentication.class, TypeResolverManagerTypes.sshAuthentication)
                    .put(QLKerberosAuthentication.class, TypeResolverManagerTypes.kerberosAuthentication)
                    .build()))
        .put(TypeResolverManagerUnifaces.TRIGGER_CONDITION,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLOnNewArtifact.class, TypeResolverManagerTypes.ON_NEW_ARTIFACT)
                    .put(QLOnPipelineCompletion.class, TypeResolverManagerTypes.ON_PIPELINE_COMPLETION)
                    .put(QLOnWebhook.class, TypeResolverManagerTypes.ON_WEB_HOOK)
                    .put(QLOnSchedule.class, TypeResolverManagerTypes.ON_SCHEDULE)
                    .put(QLOnNewManifest.class, TypeResolverManagerTypes.ON_NEW_MANIFEST)
                    .build()))
        .put(TypeResolverManagerUnifaces.TRIGGER_ACTION,
            getResultTypeResolver(ImmutableMap.<Class, String>builder()
                                      .put(QLWorkflowAction.class, TypeResolverManagerTypes.WORKFLOW_ACTION)
                                      .put(QLPipelineAction.class, TypeResolverManagerTypes.PIPELINE_ACTION)
                                      .build()))
        .put(TypeResolverManagerUnifaces.ARTIFACT_SELECTION,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLFromTriggeringArtifactSource.class, TypeResolverManagerTypes.FROM_TRIGGERING_ARTIFACT_SOURCE)
                    .put(QLLastCollected.class, TypeResolverManagerTypes.LAST_COLLECTED)
                    .put(QLLastDeployedFromWorkflow.class, TypeResolverManagerTypes.LAST_DEPLOYED_FROM_WORKFLOW)
                    .put(QLLastDeployedFromPipeline.class, TypeResolverManagerTypes.LAST_DEPLOYED_FROM_PIPELINE)
                    .put(QLFromTriggeringPipeline.class, TypeResolverManagerTypes.FROM_TRIGGERING_PIPELINE)
                    .put(QLFromWebhookPayload.class, TypeResolverManagerTypes.FROM_WEBHOOK_PAYLOAD)
                    .build()))
        .put(TypeResolverManagerUnifaces.MANIFEST_SELECTION,
            getResultTypeResolver(
                ImmutableMap.<Class, String>builder()
                    .put(QLFromTriggeringAppManifest.class, TypeResolverManagerTypes.FROM_TRIGGERING_APP_MANIFEST)
                    .put(QLLastCollectedManifest.class, TypeResolverManagerTypes.LAST_COLLECTED_MANIFEST)
                    .put(QLLastDeployedManifestFromWorkflow.class,
                        TypeResolverManagerTypes.LAST_DEPLOYED_MANIFEST_FROM_WORKFLOW)
                    .put(QLLastDeployedManifestFromPipeline.class,
                        TypeResolverManagerTypes.LAST_DEPLOYED_MANIFEST_FROM_PIPELINE)
                    .put(QLManifestFromTriggeringPipeline.class,
                        TypeResolverManagerTypes.MANIFEST_FROM_TRIGGERING_PIPELINE)
                    .put(QLManifestFromWebhookPayload.class, TypeResolverManagerTypes.MANIFEST_FROM_WEBHOOK_PAYLOAD)
                    .build()))
        .put(TypeResolverManagerUnifaces.APPROVAL_DETAILS,
            getResultTypeResolver(ImmutableMap.<Class, String>builder()
                                      .put(QLUGApprovalDetails.class, TypeResolverManagerTypes.UG_APPROVAL_DETAILS)
                                      .put(QLJIRAApprovalDetails.class, TypeResolverManagerTypes.JIRA_APPROVAL_DETAILS)
                                      .put(QLSNOWApprovalDetails.class, TypeResolverManagerTypes.SNOW_APPROVAL_DETAILS)
                                      .put(QLShellScriptDetails.class, TypeResolverManagerTypes.SHELL_SCRIPT_DETAILS)
                                      .build()))
        .build();
  }

  private TypeResolver getResultTypeResolver(Map<Class, String> types) {
    return env -> {
      Object javaObject = env.getObject();
      final Set<Entry<Class, String>> entries = types.entrySet();

      for (Entry<Class, String> entry : types.entrySet()) {
        if (entry.getKey().isAssignableFrom(javaObject.getClass())) {
          return env.getSchema().getObjectType(entry.getValue());
        }
      }

      return null;
    };
  }
}
