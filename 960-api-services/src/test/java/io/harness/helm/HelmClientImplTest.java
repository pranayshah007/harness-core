/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helm;

import static io.harness.annotations.dev.HarnessModule._960_API_SERVICES;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.YOGESH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.helm.HelmClientImpl.HelmCliResponse;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@TargetModule(_960_API_SERVICES)
public class HelmClientImplTest extends CategoryTest {
  @Mock private K8sGlobalConfigService k8sGlobalConfigService;
  @InjectMocks private io.harness.helm.HelmClientImpl helmClient = Mockito.spy(HelmClientImpl.class);
  private ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
  private HelmCommandData helmInstallCommandData = HelmCommandData.builder().build();
  private HelmCommandData helmRollbackCommandData = HelmCommandData.builder().build();
  private LogCallback executionLogCallback = Mockito.mock(LogCallback.class);

  @Before
  public void setup() throws InterruptedException, TimeoutException, IOException {
    MockitoAnnotations.initMocks(this);
    doReturn(HelmCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
        .when(helmClient)
        .executeHelmCLICommandNoDefaultTimeout(anyString(), any(OutputStream.class), any());
    buildHelmInstallCommandData();
    buildHelmRollbackCommandData();
    when(k8sGlobalConfigService.getHelmPath(any(HelmVersion.class))).thenReturn("/client-tools/v3.1/helm");
  }

  @After
  public void after() {
    verify(k8sGlobalConfigService, never()).getHelmPath(HelmVersion.V2);
  }

  private void buildHelmRollbackCommandData() {
    helmRollbackCommandData.setNewReleaseVersion(3);
    helmRollbackCommandData.setRollBackVersion(1);
    helmRollbackCommandData.setPrevReleaseVersion(2);
    helmRollbackCommandData.setTimeOutInMillis(10000);
    helmRollbackCommandData.setKubeConfigLocation("~/.kube/dummy-config");
    helmRollbackCommandData.setLogCallback(executionLogCallback);
    helmRollbackCommandData.setReleaseName("best-release-ever");
    helmRollbackCommandData.setChartName("redis");
    helmRollbackCommandData.setChartVersion("1.1.1");
    helmRollbackCommandData.setChartUrl("https://abc.com");
    helmRollbackCommandData.setCommandFlags(null);
    helmRollbackCommandData.setHelmCmdFlagsNull(true);
    helmRollbackCommandData.setRepoConfigNull(true);
  }

  private void buildHelmInstallCommandData() {
    helmInstallCommandData.setLogCallback(executionLogCallback);
    helmInstallCommandData.setReleaseName("crazy-helm");
    helmInstallCommandData.setChartName("harness");
    helmInstallCommandData.setChartVersion("0.0.1");
    helmInstallCommandData.setChartUrl("https://oci-registry");
    helmInstallCommandData.setNamespace("helm-namespace");
    helmInstallCommandData.setKubeConfigLocation("~/.kube/dummy-config");
    helmInstallCommandData.setYamlFiles(asList("value-0.yaml", "value-1.yaml"));
    helmInstallCommandData.setRepoName("stable");
    helmInstallCommandData.setCommandFlags(null);
    helmInstallCommandData.setHelmCmdFlagsNull(true);
    helmInstallCommandData.setRepoConfigNull(true);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void upgrade() throws Exception {
    ConsumerWrapper<HelmCommandData> command = helmCommandData -> helmClient.upgrade(helmCommandData, false);
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo(
            "helm upgrade  crazy-helm harness --repo https://oci-registry --version 0.0.1  -f ./repository/helm/overrides/3d6bbbe972d7519aa70587fc065139e1.yaml -f ./repository/helm/overrides/e8073c3baf625e6ea83327282c26f1a6.yaml");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config helm upgrade  crazy-helm harness --repo https://oci-registry --version 0.0.1");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config helm upgrade --debug --tls crazy-helm harness --repo https://oci-registry --version 0.0.1  -f ./repository/helm/overrides/3d6bbbe972d7519aa70587fc065139e1.yaml -f ./repository/helm/overrides/e8073c3baf625e6ea83327282c26f1a6.yaml");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo(
            "/client-tools/v3.1/helm upgrade  crazy-helm harness --repo https://oci-registry --version 0.0.1  -f ./repository/helm/overrides/3d6bbbe972d7519aa70587fc065139e1.yaml -f ./repository/helm/overrides/e8073c3baf625e6ea83327282c26f1a6.yaml");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm upgrade  crazy-helm harness --repo https://oci-registry --version 0.0.1");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm upgrade --debug --tls crazy-helm harness --repo https://oci-registry --version 0.0.1  -f ./repository/helm/overrides/3d6bbbe972d7519aa70587fc065139e1.yaml -f ./repository/helm/overrides/e8073c3baf625e6ea83327282c26f1a6.yaml");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void install() throws Exception {
    ConsumerWrapper<HelmCommandData> command = r -> helmClient.install(r, false);
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo(
            "helm install  harness --repo https://oci-registry --version 0.0.1  -f ./repository/helm/overrides/3d6bbbe972d7519aa70587fc065139e1.yaml -f ./repository/helm/overrides/e8073c3baf625e6ea83327282c26f1a6.yaml --name crazy-helm --namespace helm-namespace");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config helm install  harness --repo https://oci-registry --version 0.0.1  --name crazy-helm --namespace helm-namespace");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config helm install --debug --tls harness --repo https://oci-registry --version 0.0.1  -f ./repository/helm/overrides/3d6bbbe972d7519aa70587fc065139e1.yaml -f ./repository/helm/overrides/e8073c3baf625e6ea83327282c26f1a6.yaml --name crazy-helm --namespace helm-namespace");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo(
            "/client-tools/v3.1/helm install  crazy-helm harness --repo https://oci-registry --version 0.0.1    -f ./repository/helm/overrides/3d6bbbe972d7519aa70587fc065139e1.yaml -f ./repository/helm/overrides/e8073c3baf625e6ea83327282c26f1a6.yaml --namespace helm-namespace");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm install  crazy-helm harness --repo https://oci-registry --version 0.0.1    --namespace helm-namespace");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm install  crazy-helm harness --repo https://oci-registry --version 0.0.1  --debug --tls  -f ./repository/helm/overrides/3d6bbbe972d7519aa70587fc065139e1.yaml -f ./repository/helm/overrides/e8073c3baf625e6ea83327282c26f1a6.yaml --namespace helm-namespace");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void rollback() throws Exception {
    ConsumerWrapper<HelmCommandData> command = r -> helmClient.rollback(r, false);
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmRollbackCommandData))
        .isEqualTo("helm rollback  best-release-ever 2");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmRollbackCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm rollback --debug --tls best-release-ever 2");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmRollbackCommandData))
        .isEqualTo("/client-tools/v3.1/helm rollback  best-release-ever 2");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmRollbackCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm rollback  best-release-ever 2 --debug --tls");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void releaseHistory() throws Exception {
    ConsumerWrapper<HelmCommandData> command = r -> helmClient.releaseHistory(r, false);
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("helm hist  crazy-helm --max 5");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm hist  crazy-helm --max 5");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm hist  --tls crazy-helm --max 5");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm hist crazy-helm   --max 5");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm hist crazy-helm   --max 5");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm hist crazy-helm --debug --tls  --max 5");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void listReleases() throws Exception {
    ConsumerWrapper<HelmCommandData> command = r -> helmClient.listReleases(r, false);
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("helm list  ^crazy-helm$");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm list  ^crazy-helm$");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm list  --tls ^crazy-helm$");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm list  --filter ^crazy-helm$");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm list  --filter ^crazy-helm$");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm list --debug --tls --filter ^crazy-helm$");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getClientAndServerVersion() throws Exception {
    ConsumerWrapper<HelmCommandData> command = r -> helmClient.getClientAndServerVersion(r, false);
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("helm version --short");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm version --short");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm version --short --debug --tls");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("helm version --short");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm version --short");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm version --short --debug --tls");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void addPublicRepo() throws Exception {
    ConsumerWrapper<HelmCommandData> command = r -> helmClient.addPublicRepo(r, false);
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("helm repo add stable https://oci-registry");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm repo add stable https://oci-registry");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm repo add stable https://oci-registry");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm repo add stable https://oci-registry");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm repo add stable https://oci-registry");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm repo add stable https://oci-registry");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void addPublicRepoWithForceUpdate() throws Exception {
    doReturn(HelmCliResponse.builder()
                 .output("already exists")
                 .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                 .build())
        .when(helmClient)
        .executeHelmCLICommand(eq("/client-tools/v3.1/helm repo add stable https://oci-registry"));

    doReturn(HelmCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
        .when(helmClient)
        .executeHelmCLICommand(eq("/client-tools/v3.1/helm repo add stable https://oci-registry --force-update"));

    helmInstallCommandData.setHelmVersion(HelmVersion.V380);
    assertThatCode(() -> helmClient.addPublicRepo(helmInstallCommandData, false));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void repoUpdate() throws Exception {
    ConsumerWrapper<HelmCommandData> command = r -> helmClient.repoUpdate(r);
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("helm repo update ${HELM_HOME_PATH_FLAG}");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm repo update ${HELM_HOME_PATH_FLAG}");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm repo update ${HELM_HOME_PATH_FLAG}");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm repo update");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm repo update");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm repo update --debug --tls");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getHelmRepoList() throws Exception {
    ConsumerWrapper<HelmCommandData> command = r -> helmClient.getHelmRepoList(r);
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmInstallCommandData)).isEqualTo("helm repo list");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm repo list");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm repo list");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm repo list");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm repo list");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm repo list");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void deleteHelmRelease() throws Exception {
    ConsumerWrapper<HelmCommandData> command = r -> helmClient.deleteHelmRelease(r, false);
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("helm delete  --purge crazy-helm");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm delete  --purge crazy-helm");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm delete --debug --tls --purge crazy-helm");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm uninstall crazy-helm");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm uninstall crazy-helm");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm uninstall crazy-helm --debug --tls");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void searchChart() throws Exception {
    ConsumerWrapper<HelmCommandData> command = r -> helmClient.searchChart(r, "harness-helm");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("helm search harness-helm");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm search harness-helm");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("KUBECONFIG=~/.kube/dummy-config helm search harness-helm");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm search repo harness-helm");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm search repo harness-helm");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm search repo harness-helm");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void renderChart() throws Exception {
    String chartLocation = "chartLocation";
    String namespace = "namespace";
    List<String> valuesOverrides = Collections.emptyList();
    ConsumerWrapper<HelmCommandData> command =
        r -> helmClient.renderChart(r, chartLocation, namespace, valuesOverrides, false);
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo("helm template chartLocation  --name crazy-helm --namespace namespace");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config helm template chartLocation  --name crazy-helm --namespace namespace");
    assertThat(getCommandWithCommandFlags(HelmVersion.V2, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config helm template chartLocation --debug --tls --name crazy-helm --namespace namespace ");
    assertThat(getCommandWithNoKubeConfig(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo("/client-tools/v3.1/helm template crazy-helm chartLocation  --namespace namespace");
    assertThat(getCommandWithNoValueOverride(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm template crazy-helm chartLocation  --namespace namespace");
    assertThat(getCommandWithCommandFlags(HelmVersion.V3, command, helmInstallCommandData))
        .isEqualTo(
            "KUBECONFIG=~/.kube/dummy-config /client-tools/v3.1/helm template crazy-helm chartLocation --debug --tls --namespace namespace ");
  }

  private String getCommandWithCommandFlags(
      HelmVersion helmVersion, ConsumerWrapper<HelmCommandData> consumer, HelmCommandData request) throws Exception {
    request.setHelmVersion(helmVersion);
    request.setCommandFlags("--debug --tls");
    return getHelmCommandPassedToExecutor(consumer, request);
  }

  private String getCommandWithNoKubeConfig(
      HelmVersion helmVersion, ConsumerWrapper<HelmCommandData> consumer, HelmCommandData request) throws Exception {
    request.setHelmVersion(helmVersion);
    request.setKubeConfigLocation(null);
    return getHelmCommandPassedToExecutor(consumer, request);
  }

  private String getCommandWithNoValueOverride(
      HelmVersion helmVersion, ConsumerWrapper<HelmCommandData> consumer, HelmCommandData request) throws Exception {
    request.setHelmVersion(helmVersion);
    request.setYamlFiles(null);
    return getHelmCommandPassedToExecutor(consumer, request).trim();
  }

  private String getHelmCommandPassedToExecutor(ConsumerWrapper<HelmCommandData> consumer, HelmCommandData request)
      throws Exception {
    consumer.accept(request);
    verify(helmClient, Mockito.atLeastOnce())
        .executeHelmCLICommandNoDefaultTimeout(stringCaptor.capture(), nullable(OutputStream.class), any());
    buildHelmInstallCommandData();
    buildHelmRollbackCommandData();
    return stringCaptor.getValue();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteHelmCLICommand() throws Exception {
    String successCommand = "exit 0";
    String failCommand = "exit 1";
    // Override @Before setup
    doCallRealMethod()
        .when(helmClient)
        .executeHelmCLICommandNoDefaultTimeout(anyString(), any(OutputStream.class), any());

    assertThat(helmClient.executeHelmCLICommand(successCommand).getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(helmClient.executeHelmCLICommand(failCommand).getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @FunctionalInterface
  public interface ConsumerWrapper<T> {
    void accept(T t) throws Exception;
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testSegregationOfOutputAndError() throws Exception {
    String errorMsg = "Unexpected Error";
    HelmClientImpl.LogErrorStream errorStream1 = new HelmClientImpl.LogErrorStream();
    errorStream1.processLine(errorMsg);
    HelmClientImpl.ErrorActivityOutputStream errorStream2 =
        new HelmClientImpl.ErrorActivityOutputStream(getLogCallback());
    errorStream2.processLine(errorMsg);
    String expectedYamlMessage = "Some Yaml File\n";
    String command = "echo 'Some Yaml File'";
    String combinedOutput = errorMsg + " " + expectedYamlMessage;
    doCallRealMethod()
        .when(helmClient)
        .executeHelmCLICommandNoDefaultTimeout(command, errorStream1, Collections.emptyMap());
    doCallRealMethod()
        .when(helmClient)
        .executeHelmCLICommandNoDefaultTimeout(command, errorStream2, Collections.emptyMap());
    HelmCliResponse helmCliResponse1 =
        helmClient.executeHelmCLICommandNoDefaultTimeout(command, errorStream1, Collections.emptyMap());
    HelmCliResponse helmCliResponse2 =
        helmClient.executeHelmCLICommandNoDefaultTimeout(command, errorStream2, Collections.emptyMap());
    assertThat(helmCliResponse1.getOutput().equals(expectedYamlMessage));
    assertThat(helmCliResponse1.getErrorStreamOutput().equals(errorMsg));
    assertThat(helmCliResponse1.getOutputWithErrorStream().equals(combinedOutput));
    assertThat(helmCliResponse2.getOutput().equals(expectedYamlMessage));
    assertThat(helmCliResponse2.getErrorStreamOutput().equals(errorMsg));
    assertThat(helmCliResponse2.getOutputWithErrorStream().equals(combinedOutput));
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestV3() {
    final String helmPath = "helm3/helm";
    final String expectedCommand = "helm3/helm get manifest release-1 --namespace=default";
    testGetManifest(expectedCommand, helmPath, HelmVersion.V3);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestV2() {
    // The helm v2 will be always default to helm
    final String helmPath = "helm";
    final String expectedCommand = "helm get manifest release-1";
    testGetManifest(expectedCommand, helmPath, HelmVersion.V2);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestWithRetriesFailed() {
    final HelmCommandData commandData = HelmCommandData.builder()
                                            .releaseName("release-retry")
                                            .kubeConfigLocation(".kube2/config")
                                            .helmVersion(HelmVersion.V3)
                                            .build();

    final HelmClientException helmClientException =
        new HelmClientException("Something went wrong", HelmCliCommandType.GET_MANIFEST);

    doThrow(new HelmClientRuntimeException(helmClientException))
        .when(helmClient)
        .executeWithExceptionHandling(
            eq("KUBECONFIG=.kube2/config helm get manifest release-retry --namespace=default"),
            eq(HelmCliCommandType.GET_MANIFEST), eq("Failed to retrieve helm manifest"), any(OutputStream.class),
            anyMap());
    doReturn("helm").when(k8sGlobalConfigService).getHelmPath(HelmVersion.V3);

    assertThatThrownBy(() -> helmClient.getManifest(commandData, "default"))
        .isInstanceOf(HelmClientRuntimeException.class);
    verify(helmClient, times(3))
        .executeWithExceptionHandling(
            eq("KUBECONFIG=.kube2/config helm get manifest release-retry --namespace=default"),
            eq(HelmCliCommandType.GET_MANIFEST), eq("Failed to retrieve helm manifest"), any(OutputStream.class),
            anyMap());
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetManifestWithRetriesSuccessful() {
    final HelmCommandData commandData = HelmCommandData.builder()
                                            .releaseName("release-retry-successful")
                                            .kubeConfigLocation(".kube2/config")
                                            .helmVersion(HelmVersion.V3)
                                            .build();

    final HelmClientException helmClientException =
        new HelmClientException("Something went wrong", HelmCliCommandType.GET_MANIFEST);
    final AtomicInteger calledCount = new AtomicInteger();

    final HelmCliResponse successfulResponse = HelmCliResponse.builder()
                                                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                   .output("manifest.yaml content")
                                                   .build();

    doAnswer(invocation -> {
      if (calledCount.incrementAndGet() < 2) {
        throw new HelmClientRuntimeException(helmClientException);
      }

      return successfulResponse;
    })
        .when(helmClient)
        .executeWithExceptionHandling(
            eq("KUBECONFIG=.kube2/config helm get manifest release-retry-successful --namespace=default"),
            eq(HelmCliCommandType.GET_MANIFEST), eq("Failed to retrieve helm manifest"), any(OutputStream.class),
            anyMap());
    doReturn("helm").when(k8sGlobalConfigService).getHelmPath(HelmVersion.V3);

    HelmCliResponse resultResponse = helmClient.getManifest(commandData, "default");

    assertThat(resultResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(resultResponse.getOutput()).isEqualTo("manifest.yaml content");
    verify(helmClient, times(2))
        .executeWithExceptionHandling(
            eq("KUBECONFIG=.kube2/config helm get manifest release-retry-successful --namespace=default"),
            eq(HelmCliCommandType.GET_MANIFEST), eq("Failed to retrieve helm manifest"), any(OutputStream.class),
            anyMap());
  }

  @SneakyThrows
  private void testGetManifest(String expectedCommand, String helmPath, HelmVersion helmVersion) {
    final HelmCommandData commandData = HelmCommandData.builder()
                                            .releaseName("release-1")
                                            .kubeConfigLocation(".kube/config")
                                            .helmVersion(helmVersion)
                                            .build();
    final HelmCliResponse getManifestResponse = HelmCliResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .output("manifest.yaml content")
                                                    .build();

    final ArgumentCaptor<String> executedCommand = ArgumentCaptor.forClass(String.class);

    doReturn(getManifestResponse)
        .when(helmClient)
        .executeWithExceptionHandling(executedCommand.capture(), eq(HelmCliCommandType.GET_MANIFEST),
            eq("Failed to retrieve helm manifest"), any(OutputStream.class), anyMap());
    doReturn(helmPath).when(k8sGlobalConfigService).getHelmPath(helmVersion);

    HelmCliResponse resultResponse = helmClient.getManifest(commandData, "default");

    assertThat(executedCommand.getValue()).isEqualTo("KUBECONFIG=.kube/config " + expectedCommand);
    assertThat(resultResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(resultResponse.getOutput()).isEqualTo("manifest.yaml content");
  }

  private LogCallback getLogCallback() {
    return new LogCallback() {
      @Override
      public void saveExecutionLog(String line) {}

      @Override
      public void saveExecutionLog(String line, LogLevel logLevel) {}

      @Override
      public void saveExecutionLog(String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus) {}
    };
  }
}
