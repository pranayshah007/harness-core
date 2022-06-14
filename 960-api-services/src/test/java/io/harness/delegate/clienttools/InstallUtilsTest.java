/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.clienttools;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.delegate.clienttools.ClientTool.CHARTMUSEUM;
import static io.harness.delegate.clienttools.ClientTool.GO_TEMPLATE;
import static io.harness.delegate.clienttools.ClientTool.HARNESS_PYWINRM;
import static io.harness.delegate.clienttools.ClientTool.HELM;
import static io.harness.delegate.clienttools.ClientTool.KUBECTL;
import static io.harness.delegate.clienttools.ClientTool.KUSTOMIZE;
import static io.harness.delegate.clienttools.ClientTool.OC;
import static io.harness.delegate.clienttools.ClientTool.SCM;
import static io.harness.delegate.clienttools.ClientTool.TERRAFORM_CONFIG_INSPECT;
import static io.harness.delegate.clienttools.InstallUtils.getLatestVersionPath;
import static io.harness.delegate.clienttools.InstallUtils.getPath;
import static io.harness.delegate.clienttools.InstallUtils.setupClientTools;
import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.FunctionalTests;
import io.harness.category.element.UnitTests;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.rule.Owner;

import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@OwnedBy(DEL)
@RunWith(PowerMockRunner.class)
@PrepareForTest({InstallUtils.class})
public class InstallUtilsTest extends CategoryTest {
  private static final String PWD = Paths.get(".").toAbsolutePath().normalize().toString();

  private static final String DEFAULT_KUSTOMIZE_3_PATH = PWD + "/client-tools/kustomize/v3.5.4/kustomize";
  private static final String DEFAULT_KUSTOMIZE_4_PATH = PWD + "/client-tools/kustomize/v4.0.0/kustomize";
  private static final String DEFAULT_SCM_PATH = PWD + "/client-tools/scm/298576b8/scm";
  private static final String DEFAULT_OC_PATH = PWD + "/client-tools/oc/v4.2.16/oc";
  private static final String DEFAULT_TFCONFIG_INSPECT_1_0_PATH =
      PWD + "/client-tools/tf-config-inspect/v1.0/terraform-config-inspect";
  private static final String DEFAULT_TFCONFIG_INSPECT_1_1_PATH =
      PWD + "/client-tools/tf-config-inspect/v1.1/terraform-config-inspect";
  private static final String DEFAULT_CHARTMUSEUM_0_8_PATH = PWD + "/client-tools/chartmuseum/v0.8.2/chartmuseum";
  private static final String DEFAULT_CHARTMUSEUM_0_12_PATH = PWD + "/client-tools/chartmuseum/v0.12.0/chartmuseum";
  private static final String DEFAULT_HELM_38_PATH = PWD + "/client-tools/helm/v3.8.0/helm";
  private static final String DEFAULT_HELM_3_PATH = PWD + "/client-tools/helm/v3.1.2/helm";
  private static final String DEFAULT_HELM_2_PATH = PWD + "/client-tools/helm/v2.13.1/helm";
  private static final String DEFAULT_PYWINRM_PATH = PWD + "/client-tools/harness-pywinrm/v0.4-dev/harness-pywinrm";
  private static final String DEFAULT_GOTEMPLATE_PATH = PWD + "/client-tools/go-template/v0.4/go-template";
  private static final String DEFAULT_KUBECTL_1_19_PATH = PWD + "/client-tools/kubectl/v1.19.2/kubectl";
  private static final String DEFAULT_KUBECTL_1_13_PATH = PWD + "/client-tools/kubectl/v1.13.2/kubectl";

  @Before
  public void setUp() throws Exception {
    mockStatic(InstallUtils.class, CALLS_REAL_METHODS);
    doReturn(true).when(InstallUtils.class, "validateToolExists", any(), any());
    doNothing().when(InstallUtils.class, "initTool", any(), any());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(FunctionalTests.class)
  public void whenCustomPathsThenTheyAreUsed() {
    final String kustomizePath = "/usr/local/bin/kustomize";
    final String kubectlPath = "/usr/local/bin/kubectl";
    final String helm3Path = "/usr/local/bin/helm3/helm";
    final String helmPath = "/usr/local/bin/helm2/helm";
    final String ocPath = "/usr/local/bin/oc";
    final DelegateConfiguration customConfig = DelegateConfiguration.builder()
                                                   .managerUrl("localhost")
                                                   .clientToolsDownloadDisabled(true)
                                                   .kustomizePath(kustomizePath)
                                                   .kubectlPath(kubectlPath)
                                                   .helm3Path(helm3Path)
                                                   .helmPath(helmPath)
                                                   .ocPath(ocPath)
                                                   .build();

    setupClientTools(customConfig);

    assertThat(getPath(KUBECTL, KubectlVersion.V1_13)).isEqualTo(kubectlPath);
    assertThat(getPath(KUBECTL, KubectlVersion.V1_19)).isEqualTo(kubectlPath);
    assertThat(getPath(OC, OcVersion.V4_2)).isEqualTo(ocPath);
    assertThat(getPath(HELM, HelmVersion.V2)).isEqualTo(helmPath);
    assertThat(getPath(HELM, HelmVersion.V3)).isEqualTo(helm3Path);
    assertThat(getLatestVersionPath(KUSTOMIZE)).isEqualTo(kustomizePath);
    assertThat(getPath(KUSTOMIZE, KustomizeVersion.V3)).isEqualTo(kustomizePath);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(FunctionalTests.class)
  public void whenCustomBinaryNameThenTheyAreUsed() {
    final String helm3Path = "/usr/local/bin/helm3";
    final String helmPath = "/usr/local/bin/helm2";
    final DelegateConfiguration customConfig = DelegateConfiguration.builder()
                                                   .managerUrl("localhost")
                                                   .clientToolsDownloadDisabled(true)
                                                   .helm3Path(helm3Path)
                                                   .helmPath(helmPath)
                                                   .build();

    setupClientTools(customConfig);

    assertThat(getPath(HELM, HelmVersion.V2)).isEqualTo(helmPath);
    assertThat(getPath(HELM, HelmVersion.V3)).isEqualTo(helm3Path);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(FunctionalTests.class)
  public void whenNoAbsoluthPathThenTheyAreUsed() {
    final String kubectl = "kubectl";
    final DelegateConfiguration customConfig = DelegateConfiguration.builder()
                                                   .managerUrl("localhost")
                                                   .clientToolsDownloadDisabled(true)
                                                   .kubectlPath(kubectl)
                                                   .build();

    setupClientTools(customConfig);

    assertThat(getPath(KUBECTL, KubectlVersion.V1_13)).isEqualTo(PWD + "/kubectl");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenCustomHelm2ThenHelm3Default() {
    final String customHelm2Path = "/custom/helm2Path/helm";
    final DelegateConfiguration customConfig = DelegateConfiguration.builder()
                                                   .managerUrl("localhost")
                                                   .clientToolsDownloadDisabled(true)
                                                   .helmPath(customHelm2Path)
                                                   .build();

    setupClientTools(customConfig);

    assertThat(getPath(HELM, HelmVersion.V2)).isEqualTo(customHelm2Path);
    assertThat(getPath(HELM, HelmVersion.V3)).isEqualTo(DEFAULT_HELM_3_PATH);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenCustomHelm3ThenHelm2Default() {
    final String customHelm3Path = "/custom/helm3Path/helm";
    final DelegateConfiguration customConfig = DelegateConfiguration.builder()
                                                   .managerUrl("localhost")
                                                   .clientToolsDownloadDisabled(true)
                                                   .helm3Path(customHelm3Path)
                                                   .build();

    setupClientTools(customConfig);

    assertThat(getPath(HELM, HelmVersion.V3)).isEqualTo(customHelm3Path);
    assertThat(getPath(HELM, HelmVersion.V2)).isEqualTo(DEFAULT_HELM_2_PATH);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void whenSetupClientToolsThenDefaultPathsSet() {
    final DelegateConfiguration delegateConfiguration = DelegateConfiguration.builder()
                                                            .managerUrl("localhost")
                                                            .clientToolsDownloadDisabled(true)
                                                            .maxCachedArtifacts(10)
                                                            .build();

    setupClientTools(delegateConfiguration);

    assertThat(getPath(KUBECTL, KubectlVersion.V1_13)).isEqualTo(DEFAULT_KUBECTL_1_13_PATH);
    assertThat(getPath(KUBECTL, KubectlVersion.V1_19)).isEqualTo(DEFAULT_KUBECTL_1_19_PATH);
    assertThat(getPath(GO_TEMPLATE, GoTemplateVersion.V0_4)).isEqualTo(DEFAULT_GOTEMPLATE_PATH);
    assertThat(getPath(HARNESS_PYWINRM, HarnessPywinrmVersion.V0_4)).isEqualTo(DEFAULT_PYWINRM_PATH);
    assertThat(getPath(HELM, HelmVersion.V2)).isEqualTo(DEFAULT_HELM_2_PATH);
    assertThat(getPath(HELM, HelmVersion.V3)).isEqualTo(DEFAULT_HELM_3_PATH);
    assertThat(getPath(HELM, HelmVersion.V3_8)).isEqualTo(DEFAULT_HELM_38_PATH);
    assertThat(getLatestVersionPath(CHARTMUSEUM)).isEqualTo(DEFAULT_CHARTMUSEUM_0_12_PATH);
    assertThat(getPath(CHARTMUSEUM, ChartmuseumVersion.V0_8)).isEqualTo(DEFAULT_CHARTMUSEUM_0_8_PATH);
    assertThat(getPath(CHARTMUSEUM, ChartmuseumVersion.V0_12)).isEqualTo(DEFAULT_CHARTMUSEUM_0_12_PATH);
    assertThat(getLatestVersionPath(TERRAFORM_CONFIG_INSPECT)).isEqualTo(DEFAULT_TFCONFIG_INSPECT_1_1_PATH);
    assertThat(getPath(TERRAFORM_CONFIG_INSPECT, TerraformConfigInspectVersion.V1_0))
        .isEqualTo(DEFAULT_TFCONFIG_INSPECT_1_0_PATH);
    assertThat(getPath(TERRAFORM_CONFIG_INSPECT, TerraformConfigInspectVersion.V1_1))
        .isEqualTo(DEFAULT_TFCONFIG_INSPECT_1_1_PATH);
    assertThat(getPath(OC, OcVersion.V4_2)).isEqualTo(DEFAULT_OC_PATH);
    assertThat(getLatestVersionPath(KUSTOMIZE)).isEqualTo(DEFAULT_KUSTOMIZE_4_PATH);
    assertThat(getPath(KUSTOMIZE, KustomizeVersion.V3)).isEqualTo(DEFAULT_KUSTOMIZE_3_PATH);
    assertThat(getPath(KUSTOMIZE, KustomizeVersion.V4)).isEqualTo(DEFAULT_KUSTOMIZE_4_PATH);
    assertThat(getPath(SCM, ScmVersion.DEFAULT)).isEqualTo(DEFAULT_SCM_PATH);
  }
}
