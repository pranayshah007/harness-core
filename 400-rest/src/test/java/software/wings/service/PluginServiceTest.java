/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.FeatureName.ARTIFACT_STREAM_REFACTOR;
import static io.harness.beans.FeatureName.HELM_OCI_SUPPORT;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.AccountPlugin.Builder.anAccountPlugin;
import static software.wings.beans.PluginCategory.Artifact;
import static software.wings.beans.PluginCategory.AzureArtifacts;
import static software.wings.beans.PluginCategory.CloudProvider;
import static software.wings.beans.PluginCategory.Collaboration;
import static software.wings.beans.PluginCategory.ConnectionAttributes;
import static software.wings.beans.PluginCategory.HelmRepo;
import static software.wings.beans.PluginCategory.LoadBalancer;
import static software.wings.beans.PluginCategory.SourceRepo;
import static software.wings.beans.PluginCategory.Verification;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.APMVerificationConfig;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.AwsConfig;
import software.wings.beans.AzureConfig;
import software.wings.beans.BambooConfig;
import software.wings.beans.BugsnagConfig;
import software.wings.beans.CustomArtifactServerConfig;
import software.wings.beans.DatadogConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.DynaTraceConfig;
import software.wings.beans.ElasticLoadBalancerConfig;
import software.wings.beans.ElkConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.InstanaConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.JiraConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.PcfConfig;
import software.wings.beans.PhysicalDataCenterConfig;
import software.wings.beans.PrometheusConfig;
import software.wings.beans.ScalyrConfig;
import software.wings.beans.ServiceNowConfig;
import software.wings.beans.SftpConfig;
import software.wings.beans.SmbConfig;
import software.wings.beans.SplunkConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.SumoConfig;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.LogzConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.beans.settings.helm.OciHelmRepoConfig;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.service.impl.PluginServiceImpl;
import software.wings.service.intfc.PluginService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.StateType;

import java.io.IOException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

/**
 * Created by peeyushaggarwal on 10/21/16.
 */
public class PluginServiceTest extends CategoryTest {
  private PluginService pluginService = new PluginServiceImpl();

  private String accountId = "ACCOUNT_ID";
  private String multiArtifactEnabledAccountId = "MULTI_ARTIFACT_ENABLED_ACCOUNT_ID";

  //  @Inject private FeatureFlagService featureFlagService;
  @Mock private FeatureFlagService mockFeatureFlagService;

  @Before
  public void setup() throws IOException, IllegalAccessException {
    initMocks(this);
    FieldUtils.writeField(pluginService, "featureFlagService", mockFeatureFlagService, true);
    when(mockFeatureFlagService.isEnabled(ARTIFACT_STREAM_REFACTOR, accountId)).thenReturn(false);
    doReturn(true).when(mockFeatureFlagService).isEnabled(eq(HELM_OCI_SUPPORT), any());
    when(mockFeatureFlagService.isEnabled(ARTIFACT_STREAM_REFACTOR, multiArtifactEnabledAccountId)).thenReturn(true);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetInstalledPlugins() throws Exception {
    assertThat(pluginService.getInstalledPlugins(accountId))
        .hasSize(38)
        .containsExactly(anAccountPlugin()
                             .withSettingClass(JenkinsConfig.class)
                             .withAccountId(accountId)
                             .withIsEnabled(true)
                             .withDisplayName("Jenkins")
                             .withType("JENKINS")
                             .withPluginCategories(asList(Verification, Artifact))
                             .build(),
            anAccountPlugin()
                .withSettingClass(BambooConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Bamboo")
                .withType("BAMBOO")
                .withPluginCategories(asList(Artifact))
                .build(),
            anAccountPlugin()
                .withSettingClass(DockerConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Docker Registry")
                .withType("DOCKER")
                .withPluginCategories(asList(Artifact))
                .build(),
            anAccountPlugin()
                .withSettingClass(NexusConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Nexus")
                .withType("NEXUS")
                .withPluginCategories(asList(Artifact))
                .build(),
            anAccountPlugin()
                .withSettingClass(ArtifactoryConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Artifactory")
                .withType("ARTIFACTORY")
                .withPluginCategories(asList(Artifact))
                .build(),
            anAccountPlugin()
                .withSettingClass(AppDynamicsConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("AppDynamics")
                .withType("APP_DYNAMICS")
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(NewRelicConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("New Relic")
                .withType(StateType.NEW_RELIC.name())
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(DynaTraceConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Dynatrace")
                .withType(StateType.DYNA_TRACE.name())
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(PrometheusConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Prometheus")
                .withType(StateType.PROMETHEUS.name())
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(APMVerificationConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(false)
                .withDisplayName("APM Verification")
                .withType(SettingVariableTypes.APM_VERIFICATION.name())
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(DatadogConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Datadog")
                .withType(StateType.DATA_DOG.name())
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(BugsnagConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Bugsnag")
                .withType("BUG_SNAG")
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(SplunkConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Splunk")
                .withType("SPLUNK")
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(ElkConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("ELK")
                .withType("ELK")
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(LogzConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("LOGZ")
                .withType("LOGZ")
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(SumoConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Sumo Logic")
                .withType("SUMO")
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(InstanaConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName(SettingVariableTypes.INSTANA.getDisplayName())
                .withType(SettingVariableTypes.INSTANA.name())
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(ScalyrConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Scalyr")
                .withType(StateType.SCALYR.name())
                .withPluginCategories(asList(Verification))
                .build(),
            anAccountPlugin()
                .withSettingClass(SmtpConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("SMTP")
                .withType("SMTP")
                .withPluginCategories(asList(Collaboration))
                .build(),
            anAccountPlugin()
                .withSettingClass(AwsConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Amazon Web Services")
                .withType("AWS")
                .withPluginCategories(asList(CloudProvider))
                .build(),
            anAccountPlugin()
                .withSettingClass(GcpConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Google Cloud Platform")
                .withType("GCP")
                .withPluginCategories(asList(CloudProvider))
                .build(),
            anAccountPlugin()
                .withSettingClass(PhysicalDataCenterConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Physical Data Center")
                .withType("PHYSICAL_DATA_CENTER")
                .withPluginCategories(asList(CloudProvider))
                .build(),
            anAccountPlugin()
                .withSettingClass(KubernetesClusterConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Kubernetes Cluster")
                .withType("KUBERNETES_CLUSTER")
                .withPluginCategories(asList(CloudProvider))
                .build(),
            anAccountPlugin()
                .withSettingClass(AzureConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Microsoft Azure")
                .withType("AZURE")
                .withPluginCategories(asList(CloudProvider))
                .build(),
            anAccountPlugin()
                .withSettingClass(HostConnectionAttributes.class)
                .withAccountId(accountId)
                .withIsEnabled(false)
                .withDisplayName("Host Connection Attributes")
                .withType("HOST_CONNECTION_ATTRIBUTES")
                .withPluginCategories(asList(ConnectionAttributes))
                .build(),
            anAccountPlugin()
                .withSettingClass(ElasticLoadBalancerConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Elastic Classic Load Balancer")
                .withType("ELB")
                .withPluginCategories(asList(LoadBalancer))
                .build(),
            anAccountPlugin()
                .withSettingClass(PcfConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Tanzu Application Services")
                .withType("PCF")
                .withPluginCategories(asList(CloudProvider))
                .build(),
            anAccountPlugin()
                .withSettingClass(GitConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Git Repository")
                .withType("GIT")
                .withPluginCategories(asList(SourceRepo))
                .build(),
            anAccountPlugin()
                .withSettingClass(SmbConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("SMB")
                .withType("SMB")
                .withPluginCategories(asList(Artifact))
                .build(),
            anAccountPlugin()
                .withSettingClass(SftpConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("SFTP")
                .withType("SFTP")
                .withPluginCategories(asList(Artifact))
                .build(),
            anAccountPlugin()
                .withSettingClass(JiraConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("Jira")
                .withType("JIRA")
                .withPluginCategories(asList(Collaboration))
                .build(),
            anAccountPlugin()
                .withSettingClass(HttpHelmRepoConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName(SettingVariableTypes.HTTP_HELM_REPO.getDisplayName())
                .withType(SettingVariableTypes.HTTP_HELM_REPO.name())
                .withPluginCategories(asList(HelmRepo))
                .build(),
            anAccountPlugin()
                .withSettingClass(OciHelmRepoConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName(SettingVariableTypes.OCI_HELM_REPO.getDisplayName())
                .withType(SettingVariableTypes.OCI_HELM_REPO.name())
                .withPluginCategories(asList(HelmRepo))
                .build(),
            anAccountPlugin()
                .withSettingClass(AmazonS3HelmRepoConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName(SettingVariableTypes.AMAZON_S3_HELM_REPO.getDisplayName())
                .withType(SettingVariableTypes.AMAZON_S3_HELM_REPO.name())
                .withPluginCategories(asList(HelmRepo))
                .build(),
            anAccountPlugin()
                .withSettingClass(GCSHelmRepoConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName(SettingVariableTypes.GCS_HELM_REPO.getDisplayName())
                .withType(SettingVariableTypes.GCS_HELM_REPO.name())
                .withPluginCategories(asList(HelmRepo))
                .build(),
            anAccountPlugin()
                .withSettingClass(ServiceNowConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName("ServiceNow")
                .withType("SERVICENOW")
                .withPluginCategories(asList(Collaboration))
                .build(),
            anAccountPlugin()
                .withSettingClass(SpotInstConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName(SettingVariableTypes.SPOT_INST.getDisplayName())
                .withType(SettingVariableTypes.SPOT_INST.toString())
                .withPluginCategories(singletonList(CloudProvider))
                .build(),
            anAccountPlugin()
                .withSettingClass(AzureArtifactsPATConfig.class)
                .withAccountId(accountId)
                .withIsEnabled(true)
                .withDisplayName(SettingVariableTypes.AZURE_ARTIFACTS_PAT.getDisplayName())
                .withType(SettingVariableTypes.AZURE_ARTIFACTS_PAT.name())
                .withPluginCategories(asList(AzureArtifacts))
                .build());

    assertThat(pluginService.getInstalledPlugins(multiArtifactEnabledAccountId))
        .hasSize(39)
        .contains(anAccountPlugin()
                      .withSettingClass(CustomArtifactServerConfig.class)
                      .withAccountId(multiArtifactEnabledAccountId)
                      .withIsEnabled(true)
                      .withDisplayName(SettingVariableTypes.CUSTOM.getDisplayName())
                      .withType(SettingVariableTypes.CUSTOM.name())
                      .withPluginCategories(asList(Artifact))
                      .build());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldGetPluginSettingSchema() throws Exception {
    assertThat(pluginService.getPluginSettingSchema(accountId))
        .hasSize(38)
        .containsOnlyKeys("APP_DYNAMICS", "NEW_RELIC", "DYNA_TRACE", "PROMETHEUS", "APM_VERIFICATION", "DATA_DOG",
            "INSTANA", "SCALYR", "JENKINS", "BAMBOO", "SMTP", "BUG_SNAG", "SPLUNK", "ELK", "LOGZ", "SUMO", "AWS", "GCP",
            "AZURE", "PHYSICAL_DATA_CENTER", "KUBERNETES_CLUSTER", "DOCKER", "HOST_CONNECTION_ATTRIBUTES", "ELB",
            "NEXUS", "ARTIFACTORY", "PCF", "GIT", "JIRA", "SMB", "SFTP", "HTTP_HELM_REPO", "OCI_HELM_REPO",
            "AMAZON_S3_HELM_REPO", "GCS_HELM_REPO", "SERVICENOW", "SPOT_INST", "AZURE_ARTIFACTS_PAT");
    assertThat(pluginService.getPluginSettingSchema(multiArtifactEnabledAccountId))
        .hasSize(39)
        .containsOnlyKeys("APP_DYNAMICS", "NEW_RELIC", "DYNA_TRACE", "PROMETHEUS", "APM_VERIFICATION", "DATA_DOG",
            "JENKINS", "BAMBOO", "SMTP", "BUG_SNAG", "SPLUNK", "ELK", "LOGZ", "SUMO", "INSTANA", "SCALYR", "AWS", "GCP",
            "AZURE", "PHYSICAL_DATA_CENTER", "KUBERNETES_CLUSTER", "DOCKER", "HOST_CONNECTION_ATTRIBUTES", "ELB",
            "NEXUS", "ARTIFACTORY", "PCF", "GIT", "JIRA", "SMB", "SFTP", "HTTP_HELM_REPO", "OCI_HELM_REPO",
            "AMAZON_S3_HELM_REPO", "GCS_HELM_REPO", "SERVICENOW", "CUSTOM", "SPOT_INST", "AZURE_ARTIFACTS_PAT");
  }
}
