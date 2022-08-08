/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

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
import static java.util.stream.Collectors.toMap;

import io.harness.beans.FeatureName;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ff.FeatureFlagService;
import io.harness.serializer.JsonUtils;

import software.wings.beans.APMVerificationConfig;
import software.wings.beans.AccountPlugin;
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
import software.wings.beans.RancherConfig;
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
import software.wings.service.intfc.PluginService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by peeyushaggarwal on 10/20/16.
 */
@Singleton
public class PluginServiceImpl implements PluginService {
  private static final String stencilsPath = "/templates/plugins/";
  private static final String uiSchemaSuffix = "-SettingUISchema.json";

  @Inject @Transient private transient FeatureFlagService featureFlagService;

  @Override
  public List<AccountPlugin> getInstalledPlugins(String accountId) {
    List<AccountPlugin> pluginList = Lists.newArrayList(anAccountPlugin()
                                                            .withSettingClass(JenkinsConfig.class)
                                                            .withAccountId(accountId)
                                                            .withIsEnabled(true)
                                                            .withDisplayName("Jenkins")
                                                            .withType("JENKINS")
                                                            .withPluginCategories(asList(Verification, Artifact))
                                                            .withUiSchema(readUiSchema("JENKINS"))
                                                            .build(),
        anAccountPlugin()
            .withSettingClass(BambooConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Bamboo")
            .withType("BAMBOO")
            .withPluginCategories(asList(Artifact))
            .withUiSchema(readUiSchema("BAMBOO"))
            .build(),
        anAccountPlugin()
            .withSettingClass(DockerConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Docker Registry")
            .withType("DOCKER")
            .withPluginCategories(asList(Artifact))
            .withUiSchema(readUiSchema("DOCKER"))
            .build(),
        anAccountPlugin()
            .withSettingClass(NexusConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Nexus")
            .withType("NEXUS")
            .withPluginCategories(asList(Artifact))
            .withUiSchema(readUiSchema("NEXUS"))
            .build(),
        anAccountPlugin()
            .withSettingClass(ArtifactoryConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Artifactory")
            .withType("ARTIFACTORY")
            .withPluginCategories(asList(Artifact))
            .withUiSchema(readUiSchema("ARTIFACTORY"))
            .build(),
        anAccountPlugin()
            .withSettingClass(AppDynamicsConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("AppDynamics")
            .withType("APP_DYNAMICS")
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema("APP_DYNAMICS"))
            .build(),
        anAccountPlugin()
            .withSettingClass(NewRelicConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("New Relic")
            .withType(SettingVariableTypes.NEW_RELIC.name())
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema(SettingVariableTypes.NEW_RELIC.name()))
            .build(),
        anAccountPlugin()
            .withSettingClass(DynaTraceConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName(SettingVariableTypes.DYNA_TRACE.getDisplayName())
            .withType(SettingVariableTypes.DYNA_TRACE.name())
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema(SettingVariableTypes.DYNA_TRACE.name()))
            .build(),
        anAccountPlugin()
            .withSettingClass(PrometheusConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName(SettingVariableTypes.PROMETHEUS.getDisplayName())
            .withType(SettingVariableTypes.PROMETHEUS.name())
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema(SettingVariableTypes.PROMETHEUS.name()))
            .build(),
        anAccountPlugin()
            .withSettingClass(APMVerificationConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(false)
            .withDisplayName(SettingVariableTypes.APM_VERIFICATION.getDisplayName())
            .withType(SettingVariableTypes.APM_VERIFICATION.name())
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema(SettingVariableTypes.APM_VERIFICATION.name()))
            .build(),
        anAccountPlugin()
            .withSettingClass(DatadogConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName(SettingVariableTypes.DATA_DOG.getDisplayName())
            .withType(SettingVariableTypes.DATA_DOG.name())
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema(SettingVariableTypes.DATA_DOG.name()))
            .build(),
        anAccountPlugin()
            .withSettingClass(BugsnagConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName(SettingVariableTypes.BUG_SNAG.getDisplayName())
            .withType(SettingVariableTypes.BUG_SNAG.name())
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema(SettingVariableTypes.BUG_SNAG.name()))
            .build(),
        //        anAccountPlugin()
        //            .withSettingClass(AwsConfig.class)
        //            .accountId(accountId)
        //            .withIsEnabled(true)
        //            .displayName(SettingVariableTypes.CLOUD_WATCH.getDisplayName())
        //            .type(SettingVariableTypes.CLOUD_WATCH.name())
        //            .withPluginCategories(asList(Verification))
        //            .withUiSchema(readUiSchema(SettingVariableTypes.CLOUD_WATCH.name()))
        //            .build(),
        anAccountPlugin()
            .withSettingClass(SplunkConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Splunk")
            .withType("SPLUNK")
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema("SPLUNK"))
            .build(),
        anAccountPlugin()
            .withSettingClass(ElkConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName(SettingVariableTypes.ELK.name())
            .withType(SettingVariableTypes.ELK.name())
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema(SettingVariableTypes.ELK.name()))
            .build(),
        anAccountPlugin()
            .withSettingClass(LogzConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName(SettingVariableTypes.LOGZ.name())
            .withType(SettingVariableTypes.LOGZ.name())
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema(SettingVariableTypes.LOGZ.name()))
            .build(),
        anAccountPlugin()
            .withSettingClass(SumoConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName(SettingVariableTypes.SUMO.getDisplayName())
            .withType(SettingVariableTypes.SUMO.name())
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema(SettingVariableTypes.SUMO.name()))
            .build(),
        anAccountPlugin()
            .withSettingClass(InstanaConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName(SettingVariableTypes.INSTANA.getDisplayName())
            .withType(SettingVariableTypes.INSTANA.name())
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema(SettingVariableTypes.INSTANA.name()))
            .build(),
        anAccountPlugin()
            .withSettingClass(ScalyrConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName(SettingVariableTypes.SCALYR.getDisplayName())
            .withType(SettingVariableTypes.SCALYR.name())
            .withPluginCategories(asList(Verification))
            .withUiSchema(readUiSchema(SettingVariableTypes.SCALYR.name()))
            .build(),
        anAccountPlugin()
            .withSettingClass(SmtpConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("SMTP")
            .withType("SMTP")
            .withPluginCategories(asList(Collaboration))
            .withUiSchema(readUiSchema("SMTP"))
            .build(),
        anAccountPlugin()
            .withSettingClass(AwsConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Amazon Web Services")
            .withType("AWS")
            .withPluginCategories(asList(CloudProvider))
            .withUiSchema(readUiSchema("AWS"))
            .build(),
        anAccountPlugin()
            .withSettingClass(GcpConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Google Cloud Platform")
            .withType("GCP")
            .withPluginCategories(asList(CloudProvider))
            .withUiSchema(readUiSchema("GCP"))
            .build(),
        anAccountPlugin()
            .withSettingClass(PhysicalDataCenterConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Physical Data Center")
            .withType("PHYSICAL_DATA_CENTER")
            .withPluginCategories(asList(CloudProvider))
            .withUiSchema(readUiSchema("PHYSICAL_DATA_CENTER"))
            .build(),
        anAccountPlugin()
            .withSettingClass(KubernetesClusterConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Kubernetes Cluster")
            .withType("KUBERNETES_CLUSTER")
            .withPluginCategories(asList(CloudProvider))
            .withUiSchema(readUiSchema("KUBERNETES_CLUSTER"))
            .build(),
        anAccountPlugin()
            .withSettingClass(AzureConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Microsoft Azure")
            .withType("AZURE")
            .withPluginCategories(asList(CloudProvider))
            .withUiSchema(readUiSchema("AZURE"))
            .build(),
        anAccountPlugin()
            .withSettingClass(HostConnectionAttributes.class)
            .withAccountId(accountId)
            .withIsEnabled(false)
            .withDisplayName("Host Connection Attributes")
            .withType("HOST_CONNECTION_ATTRIBUTES")
            .withPluginCategories(asList(ConnectionAttributes))
            .withUiSchema(readUiSchema("HOST_CONNECTION_ATTRIBUTES"))
            .build(),
        anAccountPlugin()
            .withSettingClass(ElasticLoadBalancerConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Elastic Classic Load Balancer")
            .withType("ELB")
            .withPluginCategories(asList(LoadBalancer))
            .withUiSchema(readUiSchema("ELB"))
            .build(),
        anAccountPlugin()
            .withSettingClass(PcfConfig.class)
            .withAccountId(accountId)
            .withIsEnabled(true)
            .withDisplayName("Tanzu Application Services")
            .withType("PCF")
            .withPluginCategories(asList(CloudProvider))
            .withUiSchema(readUiSchema("PCF"))
            .build());

    pluginList.add(anAccountPlugin()
                       .withSettingClass(GitConfig.class)
                       .withAccountId(accountId)
                       .withIsEnabled(true)
                       .withDisplayName("Git Repository")
                       .withType("GIT")
                       .withPluginCategories(asList(SourceRepo))
                       .withUiSchema(readUiSchema("GIT"))
                       .build());
    pluginList.add(anAccountPlugin()
                       .withSettingClass(SmbConfig.class)
                       .withAccountId(accountId)
                       .withIsEnabled(true)
                       .withDisplayName("SMB")
                       .withType("SMB")
                       .withPluginCategories(asList(Artifact))
                       .withUiSchema(readUiSchema("SMB"))
                       .build());
    pluginList.add(anAccountPlugin()
                       .withSettingClass(SftpConfig.class)
                       .withAccountId(accountId)
                       .withIsEnabled(true)
                       .withDisplayName("SFTP")
                       .withType("SFTP")
                       .withPluginCategories(asList(Artifact))
                       .withUiSchema(readUiSchema("SFTP"))
                       .build());
    pluginList.add(anAccountPlugin()
                       .withSettingClass(JiraConfig.class)
                       .withAccountId(accountId)
                       .withIsEnabled(true)
                       .withDisplayName("Jira")
                       .withType("JIRA")
                       .withPluginCategories(asList(Collaboration))
                       .withUiSchema(readUiSchema("JIRA"))
                       .build());
    pluginList.add(anAccountPlugin()
                       .withSettingClass(HttpHelmRepoConfig.class)
                       .withAccountId(accountId)
                       .withIsEnabled(true)
                       .withDisplayName(SettingVariableTypes.HTTP_HELM_REPO.getDisplayName())
                       .withType(SettingVariableTypes.HTTP_HELM_REPO.name())
                       .withPluginCategories(asList(HelmRepo))
                       .withUiSchema(readUiSchema(SettingVariableTypes.HTTP_HELM_REPO.name()))
                       .build());
    if (featureFlagService.isEnabled(FeatureName.HELM_OCI_SUPPORT, accountId)) {
      pluginList.add(anAccountPlugin()
                         .withSettingClass(OciHelmRepoConfig.class)
                         .withAccountId(accountId)
                         .withIsEnabled(true)
                         .withDisplayName(SettingVariableTypes.OCI_HELM_REPO.getDisplayName())
                         .withType(SettingVariableTypes.OCI_HELM_REPO.name())
                         .withPluginCategories(asList(HelmRepo))
                         .withUiSchema(readUiSchema(SettingVariableTypes.OCI_HELM_REPO.name()))
                         .build());
    }
    pluginList.add(anAccountPlugin()
                       .withSettingClass(AmazonS3HelmRepoConfig.class)
                       .withAccountId(accountId)
                       .withIsEnabled(true)
                       .withDisplayName(SettingVariableTypes.AMAZON_S3_HELM_REPO.getDisplayName())
                       .withType(SettingVariableTypes.AMAZON_S3_HELM_REPO.name())
                       .withPluginCategories(asList(HelmRepo))
                       .withUiSchema(readUiSchema(SettingVariableTypes.AMAZON_S3_HELM_REPO.name()))
                       .build());
    pluginList.add(anAccountPlugin()
                       .withSettingClass(GCSHelmRepoConfig.class)
                       .withAccountId(accountId)
                       .withIsEnabled(true)
                       .withDisplayName(SettingVariableTypes.GCS_HELM_REPO.getDisplayName())
                       .withType(SettingVariableTypes.GCS_HELM_REPO.name())
                       .withPluginCategories(asList(HelmRepo))
                       .withUiSchema(readUiSchema(SettingVariableTypes.GCS_HELM_REPO.name()))
                       .build());

    pluginList.add(anAccountPlugin()
                       .withSettingClass(ServiceNowConfig.class)
                       .withAccountId(accountId)
                       .withIsEnabled(true)
                       .withDisplayName(SettingVariableTypes.SERVICENOW.getDisplayName())
                       .withType(SettingVariableTypes.SERVICENOW.toString())
                       .withPluginCategories(asList(Collaboration))
                       .withUiSchema(readUiSchema("SERVICENOW"))
                       .build());

    pluginList.add(anAccountPlugin()
                       .withSettingClass(SpotInstConfig.class)
                       .withAccountId(accountId)
                       .withIsEnabled(true)
                       .withDisplayName(SettingVariableTypes.SPOT_INST.getDisplayName())
                       .withType(SettingVariableTypes.SPOT_INST.toString())
                       .withPluginCategories(singletonList(CloudProvider))
                       .build());

    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
      pluginList.add(anAccountPlugin()
                         .withSettingClass(CustomArtifactServerConfig.class)
                         .withAccountId(accountId)
                         .withIsEnabled(true)
                         .withDisplayName(SettingVariableTypes.CUSTOM.getDisplayName())
                         .withType(SettingVariableTypes.CUSTOM.name())
                         .withPluginCategories(asList(Artifact))
                         .withUiSchema(readUiSchema(SettingVariableTypes.CUSTOM.name()))
                         .build());
    }

    pluginList.add(anAccountPlugin()
                       .withSettingClass(AzureArtifactsPATConfig.class)
                       .withAccountId(accountId)
                       .withIsEnabled(true)
                       .withDisplayName(SettingVariableTypes.AZURE_ARTIFACTS_PAT.getDisplayName())
                       .withType(SettingVariableTypes.AZURE_ARTIFACTS_PAT.name())
                       .withPluginCategories(asList(AzureArtifacts))
                       .withUiSchema(readUiSchema(SettingVariableTypes.AZURE_ARTIFACTS_PAT.name()))
                       .build());
    if (featureFlagService.isEnabled(FeatureName.RANCHER_SUPPORT, accountId)) {
      pluginList.add(anAccountPlugin()
                         .withSettingClass(RancherConfig.class)
                         .withAccountId(accountId)
                         .withIsEnabled(true)
                         .withDisplayName("Rancher")
                         .withType("RANCHER")
                         .withPluginCategories(asList(CloudProvider))
                         .withUiSchema(readUiSchema("RANCHER"))
                         .build());
    }
    return pluginList;
  }

  @Override
  public Map<String, Map<String, Object>> getPluginSettingSchema(String accountId) {
    return getInstalledPlugins(accountId)
        .stream()
        .filter(accountPlugin -> accountPlugin.getSettingClass() != null)
        .collect(toMap(AccountPlugin::getType,
            accountPlugin
            -> ImmutableMap.of("jsonSchema", JsonUtils.jsonSchema(accountPlugin.getSettingClass()), "uiSchema",
                (accountPlugin.getUiSchema() != null) ? accountPlugin.getUiSchema() : new HashMap<String, Object>())));
  }

  private Object readUiSchema(String type) {
    try {
      return readResource(stencilsPath + type + uiSchemaSuffix);
    } catch (Exception e) {
      return new HashMap<String, Object>();
    }
  }

  private Object readResource(String file) {
    try {
      URL url = this.getClass().getResource(file);
      String json = Resources.toString(url, Charsets.UTF_8);
      return JsonUtils.asObject(json, HashMap.class);
    } catch (Exception e) {
      throw new InvalidArgumentsException(ImmutablePair.of("file", "Error reading ui schema - " + file), e);
    }
  }
}
