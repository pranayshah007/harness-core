/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.mapper;

import static io.harness.polling.contracts.Category.ARTIFACT;
import static io.harness.polling.contracts.Category.MANIFEST;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.polling.bean.HelmChartManifestInfo;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.artifact.AcrArtifactInfo;
import io.harness.polling.bean.artifact.DockerHubArtifactInfo;
import io.harness.polling.bean.artifact.EcrArtifactInfo;
import io.harness.polling.bean.artifact.GcrArtifactInfo;
import io.harness.polling.contracts.AcrPayload;
import io.harness.polling.contracts.DockerHubPayload;
import io.harness.polling.contracts.EcrPayload;
import io.harness.polling.contracts.GcrPayload;
import io.harness.polling.contracts.GcsHelmPayload;
import io.harness.polling.contracts.HelmVersion;
import io.harness.polling.contracts.HttpHelmPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Qualifier;
import io.harness.polling.contracts.S3HelmPayload;
import io.harness.polling.contracts.Type;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class PollingDocumentMapperTest extends CDNGTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String CONNECTOR_REF = "CONNECTOR_REF";
  private static final String CHART_NAME = "CHART_NAME";
  private static final String SIGNATURE = "SIGNATURE";
  private static final String POLLING_DOC_ID = "POLLING_DOC_ID";
  Qualifier qualifier =
      Qualifier.newBuilder().setAccountId(ACCOUNT_ID).setProjectId(PROJECT_ID).setOrganizationId(ORG_ID).build();

  @Inject PollingDocumentMapper pollingDocumentMapper;

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testHttpHelmManifestInfoMapper() {
    HttpHelmPayload httpHelmPayload =
        HttpHelmPayload.newBuilder().setChartName(CHART_NAME).setHelmVersion(HelmVersion.V2).build();
    PollingPayloadData pollingPayloadData = PollingPayloadData.newBuilder()
                                                .setConnectorRef(CONNECTOR_REF)
                                                .setHttpHelmPayload(httpHelmPayload)
                                                .setType(Type.HTTP_HELM)
                                                .build();
    PollingItem pollingItem = getPollingItem(MANIFEST, pollingPayloadData);

    PollingDocument pollingDocument = pollingDocumentMapper.toPollingDocument(pollingItem);
    assertPollingDocument(pollingDocument);
    assertThat(pollingDocument.getPollingType()).isEqualTo(io.harness.polling.bean.PollingType.MANIFEST);
    assertThat(pollingDocument.getPollingInfo()).isInstanceOf(HelmChartManifestInfo.class);
    HelmChartManifestInfo helmChartManifestInfo = (HelmChartManifestInfo) pollingDocument.getPollingInfo();
    assertThat(helmChartManifestInfo.getChartName()).isEqualTo(CHART_NAME);
    assertThat(helmChartManifestInfo.getHelmVersion().toString()).isEqualTo("V2");
    assertThat(helmChartManifestInfo.getStore().getKind()).isEqualTo(ManifestStoreType.HTTP);
    assertThat(helmChartManifestInfo.getStore().getConnectorReference().getValue()).isEqualTo(CONNECTOR_REF);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testS3HelmManifestInfoMapper() {
    S3HelmPayload s3HelmPayload = S3HelmPayload.newBuilder()
                                      .setChartName(CHART_NAME)
                                      .setHelmVersion(HelmVersion.V2)
                                      .setBucketName("my-bucket")
                                      .setRegion("my-region")
                                      .setFolderPath("my-folder")
                                      .build();
    PollingPayloadData pollingPayloadData = PollingPayloadData.newBuilder()
                                                .setConnectorRef(CONNECTOR_REF)
                                                .setS3HelmPayload(s3HelmPayload)
                                                .setType(Type.S3_HELM)
                                                .build();
    PollingItem pollingItem = getPollingItem(MANIFEST, pollingPayloadData);

    PollingDocument pollingDocument = pollingDocumentMapper.toPollingDocument(pollingItem);
    assertPollingDocument(pollingDocument);
    assertThat(pollingDocument.getPollingType()).isEqualTo(io.harness.polling.bean.PollingType.MANIFEST);
    assertThat(pollingDocument.getPollingInfo()).isInstanceOf(HelmChartManifestInfo.class);
    HelmChartManifestInfo helmChartManifestInfo = (HelmChartManifestInfo) pollingDocument.getPollingInfo();
    assertThat(helmChartManifestInfo.getChartName()).isEqualTo(CHART_NAME);
    assertThat(helmChartManifestInfo.getHelmVersion().toString()).isEqualTo("V2");
    assertThat(helmChartManifestInfo.getStore().getKind()).isEqualTo(ManifestStoreType.S3);
    assertThat(helmChartManifestInfo.getStore().getConnectorReference().getValue()).isEqualTo(CONNECTOR_REF);
    S3StoreConfig storeConfig = (S3StoreConfig) helmChartManifestInfo.getStore();
    assertThat(storeConfig.getBucketName().getValue()).isEqualTo("my-bucket");
    assertThat(storeConfig.getRegion().getValue()).isEqualTo("my-region");
    assertThat(storeConfig.getFolderPath().getValue()).isEqualTo("my-folder");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGcsHelmManifestInfoMapper() {
    GcsHelmPayload gcsHelmPayload = GcsHelmPayload.newBuilder()
                                        .setChartName(CHART_NAME)
                                        .setHelmVersion(HelmVersion.V2)
                                        .setBucketName("my-bucket")
                                        .setFolderPath("my-folder")
                                        .build();
    PollingPayloadData pollingPayloadData = PollingPayloadData.newBuilder()
                                                .setConnectorRef(CONNECTOR_REF)
                                                .setGcsHelmPayload(gcsHelmPayload)
                                                .setType(Type.GCS_HELM)
                                                .build();
    PollingItem pollingItem = getPollingItem(MANIFEST, pollingPayloadData);

    PollingDocument pollingDocument = pollingDocumentMapper.toPollingDocument(pollingItem);
    assertPollingDocument(pollingDocument);
    assertThat(pollingDocument.getPollingType()).isEqualTo(io.harness.polling.bean.PollingType.MANIFEST);
    assertThat(pollingDocument.getPollingInfo()).isInstanceOf(HelmChartManifestInfo.class);
    HelmChartManifestInfo helmChartManifestInfo = (HelmChartManifestInfo) pollingDocument.getPollingInfo();
    assertThat(helmChartManifestInfo.getChartName()).isEqualTo(CHART_NAME);
    assertThat(helmChartManifestInfo.getHelmVersion().toString()).isEqualTo("V2");
    assertThat(helmChartManifestInfo.getStore().getKind()).isEqualTo(ManifestStoreType.GCS);
    assertThat(helmChartManifestInfo.getStore().getConnectorReference().getValue()).isEqualTo(CONNECTOR_REF);
    GcsStoreConfig storeConfig = (GcsStoreConfig) helmChartManifestInfo.getStore();
    assertThat(storeConfig.getBucketName().getValue()).isEqualTo("my-bucket");
    assertThat(storeConfig.getFolderPath().getValue()).isEqualTo("my-folder");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testEcrArtifactInfoMapper() {
    EcrPayload ecrPayload = EcrPayload.newBuilder().setRegion("region").setImagePath("my-image").build();
    PollingPayloadData pollingPayloadData = PollingPayloadData.newBuilder()
                                                .setConnectorRef(CONNECTOR_REF)
                                                .setEcrPayload(ecrPayload)
                                                .setType(Type.ECR)
                                                .build();
    PollingItem pollingItem = getPollingItem(ARTIFACT, pollingPayloadData);

    PollingDocument pollingDocument = pollingDocumentMapper.toPollingDocument(pollingItem);
    assertPollingDocument(pollingDocument);
    assertThat(pollingDocument.getPollingType()).isEqualTo(io.harness.polling.bean.PollingType.ARTIFACT);
    assertThat(pollingDocument.getPollingInfo()).isInstanceOf(EcrArtifactInfo.class);
    EcrArtifactInfo ecrArtifactInfo = (EcrArtifactInfo) pollingDocument.getPollingInfo();
    assertThat(ecrArtifactInfo.getImagePath()).isEqualTo("my-image");
    assertThat(ecrArtifactInfo.getRegion()).isEqualTo("region");
    assertThat(ecrArtifactInfo.getConnectorRef()).isEqualTo(CONNECTOR_REF);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGcrArtifactInfoMapper() {
    GcrPayload gcrPayload = GcrPayload.newBuilder().setImagePath("my-image").setRegistryHostname("host").build();
    PollingPayloadData pollingPayloadData = PollingPayloadData.newBuilder()
                                                .setConnectorRef(CONNECTOR_REF)
                                                .setGcrPayload(gcrPayload)
                                                .setType(Type.GCR)
                                                .build();
    PollingItem pollingItem = getPollingItem(ARTIFACT, pollingPayloadData);

    PollingDocument pollingDocument = pollingDocumentMapper.toPollingDocument(pollingItem);
    assertPollingDocument(pollingDocument);
    assertThat(pollingDocument.getPollingType()).isEqualTo(io.harness.polling.bean.PollingType.ARTIFACT);
    assertThat(pollingDocument.getPollingInfo()).isInstanceOf(GcrArtifactInfo.class);
    GcrArtifactInfo gcrArtifactInfo = (GcrArtifactInfo) pollingDocument.getPollingInfo();
    assertThat(gcrArtifactInfo.getImagePath()).isEqualTo("my-image");
    assertThat(gcrArtifactInfo.getRegistryHostname()).isEqualTo("host");
    assertThat(gcrArtifactInfo.getConnectorRef()).isEqualTo(CONNECTOR_REF);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDockerHubArtifactInfoMapper() {
    DockerHubPayload dockerHubArtifactPayload = DockerHubPayload.newBuilder().setImagePath("my-image").build();
    PollingPayloadData pollingPayloadData = PollingPayloadData.newBuilder()
                                                .setConnectorRef(CONNECTOR_REF)
                                                .setDockerHubPayload(dockerHubArtifactPayload)
                                                .setType(Type.DOCKER_HUB)
                                                .build();
    PollingItem pollingItem = getPollingItem(ARTIFACT, pollingPayloadData);

    PollingDocument pollingDocument = pollingDocumentMapper.toPollingDocument(pollingItem);
    assertPollingDocument(pollingDocument);
    assertThat(pollingDocument.getPollingType()).isEqualTo(io.harness.polling.bean.PollingType.ARTIFACT);
    assertThat(pollingDocument.getPollingInfo()).isInstanceOf(DockerHubArtifactInfo.class);
    DockerHubArtifactInfo dockerHubArtifactInfo = (DockerHubArtifactInfo) pollingDocument.getPollingInfo();
    assertThat(dockerHubArtifactInfo.getImagePath()).isEqualTo("my-image");
    assertThat(dockerHubArtifactInfo.getConnectorRef()).isEqualTo(CONNECTOR_REF);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testAcrArtifactInfoMapper() {
    AcrPayload acrArtifactPayload = AcrPayload.newBuilder()
                                        .setSubscriptionId("my-subscription")
                                        .setRegistry("my-registry")
                                        .setRepository("my-repository")
                                        .build();
    PollingPayloadData pollingPayloadData = PollingPayloadData.newBuilder()
                                                .setConnectorRef(CONNECTOR_REF)
                                                .setAcrPayload(acrArtifactPayload)
                                                .setType(Type.ACR)
                                                .build();
    PollingItem pollingItem = getPollingItem(ARTIFACT, pollingPayloadData);

    PollingDocument pollingDocument = pollingDocumentMapper.toPollingDocument(pollingItem);
    assertPollingDocument(pollingDocument);
    assertThat(pollingDocument.getPollingType()).isEqualTo(io.harness.polling.bean.PollingType.ARTIFACT);
    assertThat(pollingDocument.getPollingInfo()).isInstanceOf(AcrArtifactInfo.class);
    AcrArtifactInfo acrArtifactInfo = (AcrArtifactInfo) pollingDocument.getPollingInfo();
    assertThat(acrArtifactInfo.getSubscriptionId()).isEqualTo("my-subscription");
    assertThat(acrArtifactInfo.getRegistry()).isEqualTo("my-registry");
    assertThat(acrArtifactInfo.getRepository()).isEqualTo("my-repository");
    assertThat(acrArtifactInfo.getConnectorRef()).isEqualTo(CONNECTOR_REF);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testToPollingDocumentWithoutPollingInfo() {
    DockerHubPayload dockerHubArtifactPayload = DockerHubPayload.newBuilder().setImagePath("my-image").build();
    PollingPayloadData pollingPayloadData = PollingPayloadData.newBuilder()
                                                .setConnectorRef(CONNECTOR_REF)
                                                .setDockerHubPayload(dockerHubArtifactPayload)
                                                .setType(Type.DOCKER_HUB)
                                                .build();
    PollingItem pollingItem = getPollingItem(ARTIFACT, pollingPayloadData);

    PollingDocument pollingDocument = pollingDocumentMapper.toPollingDocumentWithoutPollingInfo(pollingItem);
    assertThat(pollingDocument).isNotNull();
    assertThat(pollingDocument.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(pollingDocument.getProjectIdentifier()).isEqualTo(PROJECT_ID);
    assertThat(pollingDocument.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(pollingDocument.getFailedAttempts()).isEqualTo(0);
    assertThat(pollingDocument.getSignatures()).hasSize(1);
    assertThat(pollingDocument.getSignatures().get(0)).isEqualTo(SIGNATURE);
    assertThat(pollingDocument.getUuid()).isEqualTo(POLLING_DOC_ID);
    assertThat(pollingDocument.getPollingInfo()).isNull();
    assertThat(pollingDocument.getPollingType()).isEqualTo(io.harness.polling.bean.PollingType.ARTIFACT);
  }

  private void assertPollingDocument(PollingDocument pollingDocument) {
    assertThat(pollingDocument).isNotNull();
    assertThat(pollingDocument.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(pollingDocument.getProjectIdentifier()).isEqualTo(PROJECT_ID);
    assertThat(pollingDocument.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(pollingDocument.getFailedAttempts()).isEqualTo(0);
    assertThat(pollingDocument.getSignatures()).hasSize(1);
    assertThat(pollingDocument.getSignatures().get(0)).isEqualTo(SIGNATURE);
    assertThat(pollingDocument.getUuid()).isEqualTo(POLLING_DOC_ID);
    assertThat(pollingDocument.getPollingInfo()).isNotNull();
  }

  private PollingItem getPollingItem(
      io.harness.polling.contracts.Category category, PollingPayloadData pollingPayloadData) {
    return PollingItem.newBuilder()
        .setCategory(category)
        .setQualifier(qualifier)
        .setPollingPayloadData(pollingPayloadData)
        .setSignature(SIGNATURE)
        .setPollingDocId(POLLING_DOC_ID)
        .build();
  }
}
