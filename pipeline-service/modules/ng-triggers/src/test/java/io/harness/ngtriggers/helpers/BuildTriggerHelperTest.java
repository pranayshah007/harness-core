/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.ManifestType.HELM_MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.polling.contracts.HelmVersion.V2;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.EcrSpec;
import io.harness.ngtriggers.beans.source.artifact.HelmManifestSpec;
import io.harness.ngtriggers.beans.source.artifact.ManifestTriggerConfig;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.pms.inputset.InputSetErrorDTOPMS;
import io.harness.pms.inputset.InputSetErrorResponseDTOPMS;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.polling.contracts.AcrPayload;
import io.harness.polling.contracts.ArtifactoryRegistryPayload;
import io.harness.polling.contracts.DockerHubPayload;
import io.harness.polling.contracts.EcrPayload;
import io.harness.polling.contracts.GcrPayload;
import io.harness.polling.contracts.GcsHelmPayload;
import io.harness.polling.contracts.HttpHelmPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Qualifier;
import io.harness.polling.contracts.S3HelmPayload;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.util.Strings;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class BuildTriggerHelperTest extends CategoryTest {
  @InjectMocks BuildTriggerHelper buildTriggerHelper;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidatePollingItemForArtifact_DockerRegistry() {
    PollingItem pollingItem = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setDockerHubPayload(DockerHubPayload.newBuilder().setImagePath("test").build())
            .build());

    validatePollingItemForArtifact(pollingItem);

    final PollingItem pollingItemNoConn = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setDockerHubPayload(DockerHubPayload.newBuilder().setImagePath("test").build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoConn))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ConnectorRef can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoImage = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setDockerHubPayload(DockerHubPayload.newBuilder().build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoImage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("imagePath can not be blank. Needs to have concrete value");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testValidatePollingItemForArtifact_ArtifactoryGenericRegistry() {
    PollingItem pollingItem = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setArtifactoryRegistryPayload(ArtifactoryRegistryPayload.newBuilder()
                                               .setRepository("repo")
                                               .setArtifactDirectory("dir")
                                               .setRepositoryFormat("generic")
                                               .setArtifactPath(Strings.EMPTY)
                                               .setRepositoryUrl(Strings.EMPTY)
                                               .build())
            .build());

    validatePollingItemForArtifact(pollingItem);

    final PollingItem pollingItemNoConn = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef(Strings.EMPTY)
            .setArtifactoryRegistryPayload(ArtifactoryRegistryPayload.newBuilder()
                                               .setRepository("repo")
                                               .setArtifactDirectory("dir")
                                               .setRepositoryFormat("generic")
                                               .setArtifactPath(Strings.EMPTY)
                                               .setRepositoryUrl(Strings.EMPTY)
                                               .build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoConn))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ConnectorRef can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoRepository = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setArtifactoryRegistryPayload(ArtifactoryRegistryPayload.newBuilder()
                                               .setRepository(Strings.EMPTY)
                                               .setArtifactDirectory("dir")
                                               .setRepositoryFormat("generic")
                                               .setArtifactPath(Strings.EMPTY)
                                               .setRepositoryUrl(Strings.EMPTY)
                                               .build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoRepository))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("repository can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoArtifactDirectory =
        generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
            PollingPayloadData.newBuilder()
                .setConnectorRef("conn")
                .setArtifactoryRegistryPayload(ArtifactoryRegistryPayload.newBuilder()
                                                   .setRepository("repo")
                                                   .setArtifactDirectory(Strings.EMPTY)
                                                   .setRepositoryFormat("generic")
                                                   .setArtifactPath(Strings.EMPTY)
                                                   .setRepositoryUrl(Strings.EMPTY)
                                                   .build())
                .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoArtifactDirectory))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("artifactDirectory can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoRepositoryFormat =
        generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
            PollingPayloadData.newBuilder()
                .setConnectorRef("conn")
                .setArtifactoryRegistryPayload(ArtifactoryRegistryPayload.newBuilder()
                                                   .setRepository("repo")
                                                   .setArtifactDirectory("dir")
                                                   .setRepositoryFormat(Strings.EMPTY)
                                                   .setArtifactPath(Strings.EMPTY)
                                                   .setRepositoryUrl(Strings.EMPTY)
                                                   .build())
                .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoRepositoryFormat))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("repositoryFormat can not be blank. Needs to have concrete value");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testValidatePollingItemForArtifact_ArtifactoryDockerRegistry() {
    PollingItem pollingItem = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setArtifactoryRegistryPayload(ArtifactoryRegistryPayload.newBuilder()
                                               .setRepository("repo")
                                               .setArtifactDirectory(Strings.EMPTY)
                                               .setRepositoryFormat("docker")
                                               .setArtifactPath("path")
                                               .setRepositoryUrl(Strings.EMPTY)
                                               .build())
            .build());

    validatePollingItemForArtifact(pollingItem);

    final PollingItem pollingItemNoConn = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef(Strings.EMPTY)
            .setArtifactoryRegistryPayload(ArtifactoryRegistryPayload.newBuilder()
                                               .setRepository("repo")
                                               .setArtifactDirectory(Strings.EMPTY)
                                               .setRepositoryFormat("docker")
                                               .setArtifactPath("path")
                                               .setRepositoryUrl(Strings.EMPTY)
                                               .build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoConn))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ConnectorRef can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoRepository = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setArtifactoryRegistryPayload(ArtifactoryRegistryPayload.newBuilder()
                                               .setRepository(Strings.EMPTY)
                                               .setArtifactDirectory(Strings.EMPTY)
                                               .setRepositoryFormat("docker")
                                               .setArtifactPath("path")
                                               .setRepositoryUrl(Strings.EMPTY)
                                               .build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoRepository))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("repository can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoArtifactPath = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setArtifactoryRegistryPayload(ArtifactoryRegistryPayload.newBuilder()
                                               .setRepository("repo")
                                               .setArtifactDirectory(Strings.EMPTY)
                                               .setRepositoryFormat("docker")
                                               .setArtifactPath(Strings.EMPTY)
                                               .setRepositoryUrl(Strings.EMPTY)
                                               .build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoArtifactPath))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("artifactPath can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoRepositoryFormat =
        generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
            PollingPayloadData.newBuilder()
                .setConnectorRef("conn")
                .setArtifactoryRegistryPayload(ArtifactoryRegistryPayload.newBuilder()
                                                   .setRepository("repo")
                                                   .setArtifactDirectory(Strings.EMPTY)
                                                   .setRepositoryFormat(Strings.EMPTY)
                                                   .setArtifactPath("path")
                                                   .setRepositoryUrl(Strings.EMPTY)
                                                   .build())
                .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoRepositoryFormat))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("repositoryFormat can not be blank. Needs to have concrete value");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidatePollingItemForArtifact_Gcr() {
    PollingItem pollingItem = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setGcrPayload(GcrPayload.newBuilder().setRegistryHostname("gcr.io").setImagePath("test").build())
            .build());

    validatePollingItemForArtifact(pollingItem);

    final PollingItem pollingItemNoConn = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setGcrPayload(GcrPayload.newBuilder().setRegistryHostname("gcr.io").setImagePath("test").build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoConn))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ConnectorRef can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoImage = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setGcrPayload(GcrPayload.newBuilder().setRegistryHostname("gcr.io").build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoImage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("imagePath can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoRegistryHostName =
        generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
            PollingPayloadData.newBuilder()
                .setConnectorRef("conn")
                .setGcrPayload(GcrPayload.newBuilder().setImagePath("test").build())
                .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoRegistryHostName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("registryHostname can not be blank. Needs to have concrete value");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidatePollingItemForArtifact_ecr() {
    PollingItem pollingItem = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setEcrPayload(EcrPayload.newBuilder().setRegion("us-east-1").setImagePath("test").build())
            .build());

    validatePollingItemForArtifact(pollingItem);

    final PollingItem pollingItemNoConn = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setEcrPayload(EcrPayload.newBuilder().setRegion("us-east-1").setImagePath("test").build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoConn))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ConnectorRef can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoImage = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setEcrPayload(EcrPayload.newBuilder().setRegion("us-east-1").build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoImage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("imagePath can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoRegion = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setEcrPayload(EcrPayload.newBuilder().setImagePath("test").build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoRegion))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("region can not be blank. Needs to have concrete value");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidatePollingItemForHelmHttp() {
    PollingItem pollingItem = generatePollingItem(io.harness.polling.contracts.Category.MANIFEST,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setHttpHelmPayload(HttpHelmPayload.newBuilder().setHelmVersion(V2).setChartName("chart").build())
            .build());

    validatePollingItemForManifest(pollingItem);

    final PollingItem pollingItemNoConn = generatePollingItem(io.harness.polling.contracts.Category.MANIFEST,
        PollingPayloadData.newBuilder()
            .setHttpHelmPayload(HttpHelmPayload.newBuilder().setHelmVersion(V2).setChartName("chart").build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForHelmChart(pollingItemNoConn))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ConnectorRef can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoChart = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setHttpHelmPayload(HttpHelmPayload.newBuilder().setHelmVersion(V2).build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForHelmChart(pollingItemNoChart))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ChartName can not be blank. Needs to have concrete value");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidatePollingItemForHelmS3() {
    PollingItem pollingItem = generatePollingItem(io.harness.polling.contracts.Category.MANIFEST,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setS3HelmPayload(S3HelmPayload.newBuilder()
                                  .setHelmVersion(V2)
                                  .setChartName("chart")
                                  .setBucketName("bucket")
                                  .setFolderPath("path")
                                  .setRegion("us-east-1")
                                  .build())
            .build());

    validatePollingItemForManifest(pollingItem);

    final PollingItem pollingItemNoConn = generatePollingItem(io.harness.polling.contracts.Category.MANIFEST,
        PollingPayloadData.newBuilder()
            .setS3HelmPayload(S3HelmPayload.newBuilder()
                                  .setHelmVersion(V2)
                                  .setChartName("chart")
                                  .setBucketName("bucket")
                                  .setFolderPath("path")
                                  .setRegion("us-east-1")
                                  .build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForHelmChart(pollingItemNoConn))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ConnectorRef can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoChart = generatePollingItem(io.harness.polling.contracts.Category.MANIFEST,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setS3HelmPayload(S3HelmPayload.newBuilder()
                                  .setHelmVersion(V2)
                                  .setBucketName("bucket")
                                  .setFolderPath("path")
                                  .setRegion("us-east-1")
                                  .build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForHelmChart(pollingItemNoChart))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ChartName can not be blank. Needs to have concrete value");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidatePollingItemForHelmGcs() {
    PollingItem pollingItem = generatePollingItem(io.harness.polling.contracts.Category.MANIFEST,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setGcsHelmPayload(GcsHelmPayload.newBuilder()
                                   .setHelmVersion(V2)
                                   .setChartName("chart")
                                   .setBucketName("bucket")
                                   .setFolderPath("path")
                                   .build())
            .build());

    validatePollingItemForManifest(pollingItem);

    final PollingItem pollingItemNoConn = generatePollingItem(io.harness.polling.contracts.Category.MANIFEST,
        PollingPayloadData.newBuilder()
            .setGcsHelmPayload(GcsHelmPayload.newBuilder()
                                   .setHelmVersion(V2)
                                   .setChartName("chart")
                                   .setBucketName("bucket")
                                   .setFolderPath("path")
                                   .build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForHelmChart(pollingItemNoConn))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ConnectorRef can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoChart = generatePollingItem(io.harness.polling.contracts.Category.MANIFEST,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setGcsHelmPayload(
                GcsHelmPayload.newBuilder().setHelmVersion(V2).setBucketName("bucket").setFolderPath("path").build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForHelmChart(pollingItemNoChart))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ChartName can not be blank. Needs to have concrete value");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testVerifyStageAndBuildRefForArtifact() {
    ArtifactTriggerConfig ecr =
        ArtifactTriggerConfig.builder().type(ArtifactType.ECR).spec(EcrSpec.builder().build()).build();
    ecr.setStageIdentifier("dev");
    ecr.setArtifactRef("primary");
    NGTriggerConfigV2 ngTriggerConfigV2 =
        NGTriggerConfigV2.builder().source(NGTriggerSourceV2.builder().type(ARTIFACT).spec(ecr).build()).build();

    try {
      buildTriggerHelper.verifyStageAndBuildRef(
          TriggerDetails.builder().ngTriggerConfigV2(ngTriggerConfigV2).build(), "artifactRef");
    } catch (Exception e) {
      fail("Exception was n ot expected");
    }

    ecr.setStageIdentifier(null);
    assertThatThrownBy(()
                           -> buildTriggerHelper.verifyStageAndBuildRef(
                               TriggerDetails.builder().ngTriggerConfigV2(ngTriggerConfigV2).build(), "artifactRef"))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("stageIdentifier can not be blank/missing. ");

    ecr.setArtifactRef(null);
    assertThatThrownBy(()
                           -> buildTriggerHelper.verifyStageAndBuildRef(
                               TriggerDetails.builder().ngTriggerConfigV2(ngTriggerConfigV2).build(), "artifactRef"))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("stageIdentifier can not be blank/missing. artifactRef can not be blank/missing. ");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testErrorMapGeneration() {
    Map<String, InputSetErrorResponseDTOPMS> map = new HashMap<>();
    map.put("path.abc",
        InputSetErrorResponseDTOPMS.builder()
            .errors(Arrays.asList(InputSetErrorDTOPMS.builder()
                                      .identifierOfErrorSource("identifierOfErrorSource")
                                      .message("message1")
                                      .fieldName("fieldName1")
                                      .build()))
            .build());

    Map<String, Map<String, String>> expectedErrorMap = new HashMap<>();
    Map<String, String> fields = new HashMap<>();
    fields.put("fieldName", "fieldName1");
    fields.put("message", "message1");
    expectedErrorMap.put("path.abc", fields);
    assertThat(
        buildTriggerHelper.generateErrorMap(InputSetErrorWrapperDTOPMS.builder().uuidToErrorResponseMap(map).build()))
        .isEqualTo(expectedErrorMap);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testVerifyStageAndBuildRefForManifest() {
    ManifestTriggerConfig manifestTriggerConfig = ManifestTriggerConfig.builder()
                                                      .type(HELM_MANIFEST)
                                                      .spec(HelmManifestSpec.builder().chartName("chart").build())
                                                      .build();

    manifestTriggerConfig.setStageIdentifier("dev");
    manifestTriggerConfig.setManifestRef("man1");
    NGTriggerConfigV2 ngTriggerConfigV2 =
        NGTriggerConfigV2.builder()
            .source(NGTriggerSourceV2.builder().type(MANIFEST).spec(manifestTriggerConfig).build())
            .build();

    try {
      buildTriggerHelper.verifyStageAndBuildRef(
          TriggerDetails.builder().ngTriggerConfigV2(ngTriggerConfigV2).build(), "manifestRef");
    } catch (Exception e) {
      fail("Exception was n ot expected");
    }

    manifestTriggerConfig.setStageIdentifier(null);
    assertThatThrownBy(()
                           -> buildTriggerHelper.verifyStageAndBuildRef(
                               TriggerDetails.builder().ngTriggerConfigV2(ngTriggerConfigV2).build(), "manifestRef"))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("stageIdentifier can not be blank/missing. ");

    manifestTriggerConfig.setManifestRef(null);
    assertThatThrownBy(()
                           -> buildTriggerHelper.verifyStageAndBuildRef(
                               TriggerDetails.builder().ngTriggerConfigV2(ngTriggerConfigV2).build(), "artifactRef"))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("stageIdentifier can not be blank/missing. artifactRef can not be blank/missing. ");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidatePollingItemForArtifact_Acr() {
    PollingItem pollingItem = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setAcrPayload(AcrPayload.newBuilder()
                               .setSubscriptionId("test-subscriptionId")
                               .setRegistry("test-registry")
                               .setRepository("test-repository")
                               .build())
            .build());

    validatePollingItemForArtifact(pollingItem);

    final PollingItem pollingItemNoConn = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setAcrPayload(AcrPayload.newBuilder()
                               .setSubscriptionId("test-subscriptionId")
                               .setRegistry("test-registry")
                               .setRepository("test-repository")
                               .build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoConn))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ConnectorRef can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoSubscription = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setAcrPayload(
                AcrPayload.newBuilder().setRegistry("test-registry").setRepository("test-repository").build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoSubscription))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("subscriptionId can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoRegistry = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setAcrPayload(AcrPayload.newBuilder()
                               .setSubscriptionId("test-subscriptionId")
                               .setRepository("test-repository")
                               .build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoRegistry))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("registry can not be blank. Needs to have concrete value");

    final PollingItem pollingItemNoRepository = generatePollingItem(io.harness.polling.contracts.Category.ARTIFACT,
        PollingPayloadData.newBuilder()
            .setConnectorRef("conn")
            .setAcrPayload(
                AcrPayload.newBuilder().setSubscriptionId("test-subscriptionId").setRegistry("test-registry").build())
            .build());

    assertThatThrownBy(() -> buildTriggerHelper.validatePollingItemForArtifact(pollingItemNoRepository))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("repository can not be blank. Needs to have concrete value");
  }

  private void validatePollingItemForArtifact(PollingItem pollingItem) {
    try {
      buildTriggerHelper.validatePollingItemForArtifact(pollingItem);
    } catch (Exception e) {
      fail("Exception wasnt expected");
    }
  }

  private void validatePollingItemForManifest(PollingItem pollingItem) {
    try {
      buildTriggerHelper.validatePollingItemForHelmChart(pollingItem);
    } catch (Exception e) {
      fail("Exception wasnt expected");
    }
  }

  PollingItem generatePollingItem(
      io.harness.polling.contracts.Category category, PollingPayloadData pollingPayloadData) {
    return PollingItem.newBuilder()
        .setCategory(category)
        .setQualifier(Qualifier.newBuilder().setProjectId("proj").setOrganizationId("org").setAccountId("acc").build())
        .setSignature("sig1")
        .setPollingPayloadData(pollingPayloadData)
        .build();
  }
}
