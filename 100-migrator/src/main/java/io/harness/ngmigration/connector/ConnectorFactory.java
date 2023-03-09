/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import static software.wings.settings.SettingVariableTypes.AMAZON_S3_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.APP_DYNAMICS;
import static software.wings.settings.SettingVariableTypes.ARTIFACTORY;
import static software.wings.settings.SettingVariableTypes.AWS;
import static software.wings.settings.SettingVariableTypes.AZURE;
import static software.wings.settings.SettingVariableTypes.AZURE_ARTIFACTS_PAT;
import static software.wings.settings.SettingVariableTypes.DATA_DOG;
import static software.wings.settings.SettingVariableTypes.DOCKER;
import static software.wings.settings.SettingVariableTypes.ELK;
import static software.wings.settings.SettingVariableTypes.GCP;
import static software.wings.settings.SettingVariableTypes.GCS_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.GIT;
import static software.wings.settings.SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES;
import static software.wings.settings.SettingVariableTypes.HTTP_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.JENKINS;
import static software.wings.settings.SettingVariableTypes.JIRA;
import static software.wings.settings.SettingVariableTypes.KUBERNETES_CLUSTER;
import static software.wings.settings.SettingVariableTypes.NEW_RELIC;
import static software.wings.settings.SettingVariableTypes.NEXUS;
import static software.wings.settings.SettingVariableTypes.OCI_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.PCF;
import static software.wings.settings.SettingVariableTypes.PROMETHEUS;
import static software.wings.settings.SettingVariableTypes.SERVICENOW;
import static software.wings.settings.SettingVariableTypes.SPLUNK;
import static software.wings.settings.SettingVariableTypes.SPOT_INST;
import static software.wings.settings.SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.SettingAttribute;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class ConnectorFactory {
  private static final BaseConnector artifactoryConnector = new ArtifactoryConnectorImpl();
  private static final BaseConnector nexusConnector = new NexusConnectorImpl();
  private static final BaseConnector dockerConnector = new DockerConnectorImpl();
  private static final BaseConnector kubernetesConnector = new KubernetesConnectorImpl();
  private static final BaseConnector gitConnector = new GitConnectorImpl();
  private static final BaseConnector gcpConnector = new GcpConnectorImpl();
  private static final BaseConnector azureConnector = new AzureConnectorImpl();
  private static final BaseConnector httpHelmConnector = new HttpHelmConnectorImpl();
  private static final BaseConnector awsConnector = new AWSConnectorImpl();
  private static final BaseConnector pcfConnector = new PcfConnectorImpl();
  private static final BaseConnector ociHelmConnector = new OCIHelmConnectorImpl();
  private static final BaseConnector unsupportedConnector = new UnsupportedConnectorImpl();

  private static final BaseConnector elkConnector = new ElkConnectorImpl();
  private static final BaseConnector jiraConnector = new JiraConnectorImpl();
  private static final BaseConnector serviceNowConnector = new ServiceNowConnectorImpl();

  private static final BaseConnector jenkinsConnector = new JenkinsConnectorImpl();
  private static final BaseConnector sshConnector = new SshConnectorImpl();
  private static final BaseConnector winRmConnector = new WinrmConnectorImpl();
  private static final BaseConnector gcsHelmRepoConnector = new GcsHelmConnectorImpl();
  private static final BaseConnector awsS3HelmConnector = new AwsS3HelmConnectorImpl();
  private static final BaseConnector datadogConnector = new DatadogConnectorImpl();
  private static final BaseConnector newrelicConnector = new NewrelicConnectorImpl();
  private static final BaseConnector prometheusConnector = new PrometheusConnectorImpl();
  private static final BaseConnector appDynamicsConnector = new AppDynamicsConnectorImpl();
  private static final BaseConnector splunkConnector = new SplunkConnectorImpl();
  private static final BaseConnector spotConnector = new SpotConnectorImpl();
  private static final BaseConnector azureArtifactPat = new AzureArtifactPatConnectorImpl();

  public static final Map<SettingVariableTypes, BaseConnector> CONNECTOR_FACTORY_MAP =
      ImmutableMap.<SettingVariableTypes, BaseConnector>builder()
          .put(NEXUS, nexusConnector)
          .put(ARTIFACTORY, artifactoryConnector)
          .put(DOCKER, dockerConnector)
          .put(KUBERNETES_CLUSTER, kubernetesConnector)
          .put(GIT, gitConnector)
          .put(GCP, gcpConnector)
          .put(AZURE, azureConnector)
          .put(HTTP_HELM_REPO, httpHelmConnector)
          .put(AWS, awsConnector)
          .put(PCF, pcfConnector)
          .put(OCI_HELM_REPO, ociHelmConnector)
          .put(JIRA, jiraConnector)
          .put(SERVICENOW, serviceNowConnector)
          .put(JENKINS, jenkinsConnector)
          .put(HOST_CONNECTION_ATTRIBUTES, sshConnector)
          .put(WINRM_CONNECTION_ATTRIBUTES, winRmConnector)
          .put(GCS_HELM_REPO, gcsHelmRepoConnector)
          .put(AMAZON_S3_HELM_REPO, awsS3HelmConnector)
          .put(DATA_DOG, datadogConnector)
          .put(NEW_RELIC, newrelicConnector)
          .put(PROMETHEUS, prometheusConnector)
          .put(APP_DYNAMICS, appDynamicsConnector)
          .put(SPLUNK, splunkConnector)
          .put(SPOT_INST, spotConnector)
          .put(AZURE_ARTIFACTS_PAT, azureArtifactPat)
          .put(ELK, elkConnector)
          .build();

  public static BaseConnector getConnector(SettingAttribute settingAttribute) {
    SettingVariableTypes settingVariableTypes = settingAttribute.getValue().getSettingType();
    if (CONNECTOR_FACTORY_MAP.containsKey(settingVariableTypes)) {
      return CONNECTOR_FACTORY_MAP.get(settingAttribute.getValue().getSettingType());
    }
    return unsupportedConnector;
  }
}
