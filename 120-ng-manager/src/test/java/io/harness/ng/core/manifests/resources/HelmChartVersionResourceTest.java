/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.manifests.resources;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.resources.HelmChartService;
import io.harness.cdng.manifest.resources.dtos.HelmManifestInternalDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class HelmChartVersionResourceTest extends CategoryTest {
  @Mock private HelmChartService helmChartService;
  @Mock private HelmChartVersionResourceUtils helmChartVersionResourceUtils;
  @InjectMocks private HelmChartVersionResource helmChartVersionResource;
  private AutoCloseable mocks;
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String SERVICE_REF = "serviceRef";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String REGION = "us-east-1";
  private static final String CHART_NAME = "chartName";
  private static final String BUCKET = "bucket";
  private static final String FOLDER_PATH = "/folderPath";
  private static final String FQN_PATH = "fqnPath";
  private static final String REGISTRY_ID = "registryId";
  private static final GitEntityFindInfoDTO GIT_ENTITY_FIND_INFO_DTO = GitEntityFindInfoDTO.builder().build();

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testGetHelmChartVersionDetailsWithGitStoreYaml() throws IOException {
    String gitStoreYaml = "identifier: manifest\n"
        + "type: HelmChart\n"
        + "spec:\n"
        + " store:\n"
        + "   type: Git\n"
        + "   spec:\n"
        + "     connectorRef: ConnectorDisconnectHandler\n"
        + "     gitFetchType: Branch\n"
        + "     folderPath: folderPath\n"
        + "     branch: main\n"
        + "   skipResourceVersioning: false\n"
        + "   helmVersion: V3";
    HelmManifestInternalDTO helmManifestInternalDTO = YamlUtils.read(gitStoreYaml, HelmManifestInternalDTO.class);
    doReturn(helmManifestInternalDTO)
        .when(helmChartService)
        .locateManifestInService(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_REF), eq(FQN_PATH));
    helmChartVersionResource.getHelmChartVersionDetailsWithYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
        SERVICE_REF, FQN_PATH, CONNECTOR_REF, CHART_NAME, REGION, BUCKET, FOLDER_PATH, null, null,
        GIT_ENTITY_FIND_INFO_DTO, "");
    verify(helmChartVersionResourceUtils, times(2))
        .resolveExpression(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), any(), any(), any(),
            eq(FQN_PATH), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testGetHelmChartVersionDetailsWithS3StoreYaml() throws IOException {
    String s3StoreYaml = "identifier: manifest\n"
        + "type: HelmChart\n"
        + "spec:\n"
        + "  store:\n"
        + "    type: S3\n"
        + "    spec:\n"
        + "      connectorRef: connectorRef\n"
        + "      bucketName: bucket\n"
        + "      folderPath: folderPath\n"
        + "      region: region\n"
        + "    chartName: todolist\n"
        + "    chartVersion: \"\"\n"
        + "    helmVersion: V3\n"
        + "    skipResourceVersioning: false\n"
        + "    enableDeclarativeRollback: false";
    HelmManifestInternalDTO helmManifestInternalDTO = YamlUtils.read(s3StoreYaml, HelmManifestInternalDTO.class);
    doReturn(helmManifestInternalDTO)
        .when(helmChartService)
        .locateManifestInService(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_REF), eq(FQN_PATH));
    helmChartVersionResource.getHelmChartVersionDetailsWithYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
        SERVICE_REF, FQN_PATH, CONNECTOR_REF, CHART_NAME, REGION, BUCKET, FOLDER_PATH, null, null,
        GIT_ENTITY_FIND_INFO_DTO, "");
    verify(helmChartVersionResourceUtils, times(5))
        .resolveExpression(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), any(), any(), any(),
            eq(FQN_PATH), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testGetHelmChartVersionDetailsWithGcsStoreYaml() throws IOException {
    String gcsStoreYaml = "identifier: manifest\n"
        + "type: HelmChart\n"
        + "spec:\n"
        + "  store:\n"
        + "    type: Gcs\n"
        + "    spec:\n"
        + "      connectorRef: connectorRef\n"
        + "      bucketName: bucket\n"
        + "      folderPath: folderPath\n"
        + "    chartName: todolist\n"
        + "    chartVersion: \"\"\n"
        + "    helmVersion: V3\n"
        + "    skipResourceVersioning: false\n"
        + "    enableDeclarativeRollback: false";
    HelmManifestInternalDTO helmManifestInternalDTO = YamlUtils.read(gcsStoreYaml, HelmManifestInternalDTO.class);
    doReturn(helmManifestInternalDTO)
        .when(helmChartService)
        .locateManifestInService(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_REF), eq(FQN_PATH));
    helmChartVersionResource.getHelmChartVersionDetailsWithYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
        SERVICE_REF, FQN_PATH, CONNECTOR_REF, CHART_NAME, REGION, BUCKET, FOLDER_PATH, null, null,
        GIT_ENTITY_FIND_INFO_DTO, "");
    verify(helmChartVersionResourceUtils, times(4))
        .resolveExpression(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), any(), any(), any(),
            eq(FQN_PATH), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testGetHelmChartVersionDetailsWithOciHelmStoreYaml() throws IOException {
    String ociHelmStoreYaml = "identifier: manifest\n"
        + "type: HelmChart\n"
        + "spec:\n"
        + "  store:\n"
        + "    type: OciHelmChart\n"
        + "    spec:\n"
        + "      config:\n"
        + "        type: Generic\n"
        + "        spec:\n"
        + "          connectorRef: connectorRef\n"
        + "        basePath: folderPath\n"
        + "  chartName: chartName\n"
        + "  chartVersion: \"\"\n"
        + "  helmVersion: V380\n"
        + "  skipResourceVersioning: false\n"
        + "  enableDeclarativeRollback: false";
    HelmManifestInternalDTO helmManifestInternalDTO = YamlUtils.read(ociHelmStoreYaml, HelmManifestInternalDTO.class);
    doReturn(helmManifestInternalDTO)
        .when(helmChartService)
        .locateManifestInService(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_REF), eq(FQN_PATH));
    helmChartVersionResource.getHelmChartVersionDetailsWithYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
        SERVICE_REF, FQN_PATH, CONNECTOR_REF, CHART_NAME, REGION, BUCKET, FOLDER_PATH, null, null,
        GIT_ENTITY_FIND_INFO_DTO, "");
    verify(helmChartVersionResourceUtils, times(3))
        .resolveExpression(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), any(), any(), any(),
            eq(FQN_PATH), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testGetHelmChartVersionDetailsWithEcrStoreConfigYaml() throws IOException {
    String ecrHelmYaml = "identifier: manifest\n"
        + "type: HelmChart\n"
        + "spec:\n"
        + "  store:\n"
        + "    type: OciHelmChart\n"
        + "    spec:\n"
        + "      config:\n"
        + "        type: ECR\n"
        + "        spec:\n"
        + "          connectorRef: connectorRef\n"
        + "          region: region\n"
        + "          registryId: registryId\n"
        + "        basePath: folderPath\n"
        + "  chartName: chartName\n"
        + "  chartVersion: \"\"\n"
        + "  helmVersion: V380\n"
        + "  skipResourceVersioning: false\n"
        + "  enableDeclarativeRollback: false";
    HelmManifestInternalDTO helmManifestInternalDTO = YamlUtils.read(ecrHelmYaml, HelmManifestInternalDTO.class);
    doReturn(helmManifestInternalDTO)
        .when(helmChartService)
        .locateManifestInService(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(SERVICE_REF), eq(FQN_PATH));
    helmChartVersionResource.getHelmChartVersionDetailsWithYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID,
        SERVICE_REF, FQN_PATH, CONNECTOR_REF, CHART_NAME, REGION, BUCKET, FOLDER_PATH, null, REGISTRY_ID,
        GIT_ENTITY_FIND_INFO_DTO, "");
    verify(helmChartVersionResourceUtils, times(5))
        .resolveExpression(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PIPELINE_ID), any(), any(), any(),
            eq(FQN_PATH), eq(GIT_ENTITY_FIND_INFO_DTO), eq(SERVICE_REF));
  }
}
