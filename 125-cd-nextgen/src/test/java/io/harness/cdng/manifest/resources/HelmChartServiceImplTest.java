/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.resources;

import static io.harness.rule.OwnerRule.ACHYUTH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.manifest.resources.dtos.HelmChartResponseDTO;
import io.harness.cdng.manifest.resources.dtos.HelmManifestInternalDTO;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.helm.HelmFetchChartVersionResponse;
import io.harness.k8s.model.HelmVersion;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class HelmChartServiceImplTest extends CategoryTest {
  public static final String accountId = "acc";
  public static final String orgId = "org";
  public static final String projId = "proj";
  public static final String serviceRef = "svc";
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private ConnectorService connectorService;
  @InjectMocks @Spy HelmChartServiceImpl helmChartServiceImpl;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testLocateManifestInService() throws IOException {
    String manifest = "manifest:\n"
        + "                      identifier: m1\n"
        + "                      type: HelmChart\n"
        + "                      spec:\n"
        + "                        store:\n"
        + "                          type: Http\n"
        + "                          spec:\n"
        + "                            connectorRef: account.http_helm_2\n"
        + "                        chartName: nginx\n"
        + "                        chartVersion: <+input>\n"
        + "                        helmVersion: V3\n"
        + "                        skipResourceVersioning: false\n";

    String manifestPath = "manifest";

    YamlNode yamlNode = YamlNode.fromYamlPath(manifest, manifestPath);

    doReturn(yamlNode)
        .when(serviceEntityService)
        .getYamlNodeForFqn(eq(accountId), eq(orgId), eq(projId), eq(serviceRef), anyString());

    HelmManifestInternalDTO helmManifestInternalDTO =
        helmChartServiceImpl.locateManifestInService(accountId, orgId, projId, serviceRef, manifestPath);

    HelmChartManifest helmChartManifest = (HelmChartManifest) helmManifestInternalDTO.getSpec();

    assertThat(helmChartManifest.getChartName().getValue()).isEqualTo("nginx");
    assertThat(helmChartManifest.getHelmVersion()).isEqualTo(HelmVersion.V3);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testGetHelmChartVersionDetails() {
    String manifestPath = "stages.HelmDeploy.spec.serviceConfig.serviceDefinition.spec.manifests.m1";

    HelmChartManifestOutcome helmChartManifestOutcome =
        HelmChartManifestOutcome.builder().chartName(ParameterField.createValueField("my-chart")).build();

    StoreDelegateConfig storeDelegateConfig = HttpHelmStoreDelegateConfig.builder()
                                                  .repoName("repo")
                                                  .repoDisplayName("repo")
                                                  .httpHelmConnector(HttpHelmConnectorDTO.builder().build())
                                                  .build();

    HelmFetchChartVersionResponse helmFetchChartVersionResponse =
        HelmFetchChartVersionResponse.builder().chartVersionsList(Arrays.asList("0.1.0", "0.1.1")).build();

    doReturn(helmChartManifestOutcome).when(helmChartServiceImpl).getHelmChartManifestOutcome(any());
    doReturn(new HelmManifestInternalDTO())
        .when(helmChartServiceImpl)
        .locateManifestInService(eq(accountId), eq(orgId), eq(projId), eq(serviceRef), eq(manifestPath));
    doReturn(HelmVersion.V3).when(helmChartServiceImpl).getHelmVersionBasedOnFF(any(), eq(accountId));
    doReturn(storeDelegateConfig)
        .when(helmChartServiceImpl)
        .getStoreDelegateConfig(any(), eq(accountId), eq(orgId), eq(projId), eq(""), eq(""), eq(""), eq(""));
    doReturn(new HashSet<>()).when(helmChartServiceImpl).getDelegateSelectors(any());
    doReturn(helmFetchChartVersionResponse).when(delegateGrpcClientWrapper).executeSyncTask(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(eq(accountId), any());

    HelmChartResponseDTO helmChartResponseDTO = helmChartServiceImpl.getHelmChartVersionDetails(
        accountId, orgId, projId, serviceRef, manifestPath, "", "", "", "", "");
    assertThat(helmChartResponseDTO.getHelmChartVersions()).contains("0.1.0", "0.1.1");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testGetConnector() {
    doReturn(
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder()
                                       .connectorConfig(
                                           HttpHelmConnectorDTO.builder().helmRepoUrl("https://bitnami.com").build())
                                       .connectorType(ConnectorType.HTTP_HELM_REPO)
                                       .build())
                        .build()))
        .when(connectorService)
        .get(accountId, orgId, projId, "connectorId");

    ConnectorInfoDTO connectorInfoDTO = helmChartServiceImpl.getConnector(accountId, orgId, projId, "connectorId");

    assertThat(((HttpHelmConnectorDTO) connectorInfoDTO.getConnectorConfig()).getHelmRepoUrl())
        .isEqualTo("https://bitnami.com");
  }
}
