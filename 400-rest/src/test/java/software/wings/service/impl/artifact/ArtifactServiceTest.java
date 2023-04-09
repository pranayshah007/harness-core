/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.persistence.artifact.Artifact.ContentStatus.DELETED;
import static software.wings.persistence.artifact.Artifact.ContentStatus.DOWNLOADED;
import static software.wings.persistence.artifact.Artifact.ContentStatus.DOWNLOADING;
import static software.wings.persistence.artifact.Artifact.ContentStatus.METADATA_ONLY;
import static software.wings.persistence.artifact.Artifact.ContentStatus.NOT_DOWNLOADED;
import static software.wings.persistence.artifact.Artifact.Status;
import static software.wings.persistence.artifact.Artifact.Status.APPROVED;
import static software.wings.persistence.artifact.Artifact.Status.QUEUED;
import static software.wings.persistence.artifact.Artifact.Status.READY;
import static software.wings.persistence.artifact.Artifact.Status.RUNNING;
import static software.wings.persistence.artifact.ArtifactFile.Builder.anArtifactFile;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.persistence.HQuery;
import io.harness.queue.QueuePublisher;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactView;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.EcrArtifactStream;
import software.wings.beans.artifact.GcrArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.collect.CollectEvent;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;
import software.wings.persistence.artifact.Artifact.Builder;
import software.wings.persistence.artifact.Artifact.ContentStatus;
import software.wings.persistence.artifact.ArtifactFile;
import software.wings.rules.SetupScheduler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryType;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.ConstraintViolationException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

@OwnedBy(CDC)
@SetupScheduler
public class ArtifactServiceTest extends WingsBaseTest {
  private static final String GLOBAL_APP_ID = "__GLOBAL_APP_ID__";
  private static final String SETTING_ID = "SETTING_ID";
  @Inject @Spy private WingsPersistence persistence;

  @Mock private FileService fileService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private AppService appService;
  @Mock private HQuery<Application> appQuery;
  @Mock private QueuePublisher<CollectEvent> collectQueue;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private SettingsService settingsService;
  @Mock private ArtifactCollectionUtils artifactCollectionUtils;

  @InjectMocks @Inject private ArtifactService artifactService;
  private String BUILD_NO = "buildNo";
  private Builder artifactBuilder = anArtifact()
                                        .withAccountId(ACCOUNT_ID)
                                        .withAppId(APP_ID)
                                        .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "200")))
                                        .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                        .withRevision("1.0")
                                        .withDisplayName("DISPLAY_NAME")
                                        .withCreatedAt(System.currentTimeMillis())
                                        .withCreatedBy(EmbeddedUser.builder().uuid("USER_ID").build());
  private Builder artifactLatestBuilder = anArtifact()
                                              .withAccountId(ACCOUNT_ID)
                                              .withAppId(APP_ID)
                                              .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "220")))
                                              .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                              .withRevision("1.1")
                                              .withDisplayName("LATEST_DISPLAY_NAME")
                                              .withCreatedAt(System.currentTimeMillis() - 10)
                                              .withCreatedBy(EmbeddedUser.builder().uuid("USER_ID").build());

  private Artifact artifact = artifactBuilder.build();

  private JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                            .accountId(ACCOUNT_ID)
                                                            .uuid(ARTIFACT_STREAM_ID)
                                                            .appId(APP_ID)
                                                            .sourceName("ARTIFACT_SOURCE")
                                                            .serviceId(SERVICE_ID)
                                                            .build();

  @Before
  public void setUp() {
    persistence.save(Service.builder().appId(APP_ID).artifactType(ArtifactType.WAR).uuid(SERVICE_ID).build());
    persistence.save(CustomArtifactStream.builder().uuid(ARTIFACT_STREAM_ID).name(ARTIFACT_STREAM_NAME).build());
    when(appQuery.filter(any(), any())).thenReturn(appQuery);

    when(appService.exist(APP_ID)).thenReturn(true);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(jenkinsArtifactStream);
    when(persistence.get(Artifact.class, ARTIFACT_ID)).thenReturn(artifact);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCreateArtifactWhenValid() {
    assertThat(artifactService.create(artifactBuilder.but().build(), true))
        .isNotNull()
        .hasFieldOrPropertyWithValue("artifactSourceName", "ARTIFACT_SOURCE");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldNotCreateDuplicateArtifact() {
    Artifact artifact = artifactService.create(artifactBuilder.but().build(), false);
    Artifact dupArtifact = artifactService.create(artifactBuilder.but().build(), false);
    assertThat(artifact.getUuid()).isEqualTo(dupArtifact.getUuid());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotCreateDuplicateJenkinsArtifact() {
    when(artifactCollectionUtils.getArtifactStreamAttributes(eq(jenkinsArtifactStream), anyBoolean()))
        .thenReturn(ArtifactStreamAttributes.builder().build());
    Artifact oldArtifact = artifactService.create(artifactBuilder.but().build());
    Artifact newArtifact = artifactService.create(artifactBuilder.but().build());
    assertThat(newArtifact.getUuid()).isEqualTo(oldArtifact.getUuid());
    assertThat(oldArtifact.getBuildIdentity()).contains("200");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotCreateDuplicateAMIArtifact() {
    AmiArtifactStream artifactStream = AmiArtifactStream.builder()
                                           .uuid(ARTIFACT_STREAM_ID)
                                           .appId(APP_ID)
                                           .sourceName("ARTIFACT_SOURCE")
                                           .serviceId(SERVICE_ID)
                                           .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactCollectionUtils.getArtifactStreamAttributes(eq(artifactStream), anyBoolean()))
        .thenReturn(ArtifactStreamAttributes.builder().build());
    Artifact oldArtifact = artifactService.create(artifactBuilder.but().build());
    Artifact newArtifact = artifactService.create(artifactBuilder.but().build());
    assertThat(newArtifact.getUuid()).isEqualTo(oldArtifact.getUuid());
    assertThat(oldArtifact.getBuildIdentity()).contains("1.0");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotCreateDuplicateS3Artifact() {
    AmazonS3ArtifactStream artifactStream = AmazonS3ArtifactStream.builder()
                                                .uuid(ARTIFACT_STREAM_ID)
                                                .appId(APP_ID)
                                                .sourceName("ARTIFACT_SOURCE")
                                                .serviceId(SERVICE_ID)
                                                .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);
    when(artifactCollectionUtils.getArtifactStreamAttributes(eq(artifactStream), anyBoolean()))
        .thenReturn(ArtifactStreamAttributes.builder().build());
    Map<String, String> metadata = ImmutableMap.of("artifactPath", "path");
    Artifact oldArtifact =
        artifactService.create(artifactBuilder.but().withMetadata(new ArtifactMetadata(metadata)).build());
    Artifact newArtifact =
        artifactService.create(artifactBuilder.but().withMetadata(new ArtifactMetadata(metadata)).build());
    assertThat(newArtifact.getUuid()).isEqualTo(oldArtifact.getUuid());
    assertThat(oldArtifact.getBuildIdentity()).contains("path");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldCreateNonDuplicateArtifact() {
    when(artifactCollectionUtils.getArtifactStreamAttributes(eq(jenkinsArtifactStream), anyBoolean()))
        .thenReturn(ArtifactStreamAttributes.builder().build());
    Artifact oldArtifact = artifactService.create(artifactBuilder.but().build());
    Artifact newArtifact = artifactService.create(artifactLatestBuilder.but().build());
    assertThat(newArtifact.getUuid()).isNotEqualTo(oldArtifact.getUuid());
    assertThat(oldArtifact.getBuildIdentity()).contains("200");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAddArtifactFile() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    assertThat(savedArtifact).isNotNull().hasFieldOrPropertyWithValue("artifactSourceName", "ARTIFACT_SOURCE");
    ArtifactFile artifactFile = anArtifactFile()
                                    .withAppId(APP_ID)
                                    .withName("test-artifact.war")
                                    .withUuid("5942bffe1e204f7f3004f455")
                                    .withFileUuid("5942bffe1e204f7f3004f455")
                                    .build();
    artifactService.addArtifactFile(savedArtifact.getUuid(), savedArtifact.getAccountId(), asList(artifactFile));
    Artifact updatedArtifact = artifactService.get(savedArtifact.getUuid());
    assertThat(updatedArtifact).isNotNull().extracting(Artifact::getArtifactFiles).isNotNull();

    assertThat(updatedArtifact.getArtifactFiles().get(0).getFileUuid()).isEqualTo("5942bffe1e204f7f3004f455");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenAppIdDoesNotMatchForArtifacToBeCreated() {
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> artifactService.create(artifactBuilder.but().withAppId("BAD_APP_ID").build(), true));
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenReleaseIdDoesNotMatchForArtifacToBeCreated() {
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(
            () -> artifactService.create(artifactBuilder.but().withArtifactStreamId("NON_EXISTENT_ID").build(), true));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldThrowExceptionWhenArtifactToBeCreatedIsInvalid() {
    assertThatExceptionOfType(ConstraintViolationException.class)
        .isThrownBy(() -> artifactService.create(artifactBuilder.but().withRevision(null).build(), true));
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldUpdateArtifactWhenValid() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);

    savedArtifact.setDisplayName("ARTIFACT_DISPLAY_NAME");
    Artifact updatedArtifact = artifactService.update(savedArtifact);
    assertThat(updatedArtifact).isEqualTo(savedArtifact);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenArtifactToBeUpdatedIsInvalid() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);

    savedArtifact.setDisplayName(null);
    assertThatExceptionOfType(ConstraintViolationException.class)
        .isThrownBy(() -> artifactService.update(savedArtifact));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateStatusApproved() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    artifactService.updateStatus(savedArtifact.getUuid(), savedArtifact.getAccountId(), APPROVED);
    Artifact updatedArtifact = artifactService.get(savedArtifact.getUuid());
    assertThat(updatedArtifact).isNotNull();
    assertThat(updatedArtifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateStatusWithErrorMessage() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    artifactService.updateStatus(
        savedArtifact.getUuid(), savedArtifact.getAccountId(), Status.FAILED, "Failed to download artifact");
    Artifact updatedArtifact = artifactService.get(savedArtifact.getUuid());
    assertThat(updatedArtifact).isNotNull();
    assertThat(updatedArtifact.getStatus()).isEqualTo(Status.FAILED);
    assertThat(updatedArtifact.getErrorMessage()).isEqualTo("Failed to download artifact");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateContentStatus() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    artifactService.updateStatus(savedArtifact.getUuid(), savedArtifact.getAccountId(), APPROVED, DOWNLOADED);
    Artifact updatedArtifact = artifactService.get(savedArtifact.getUuid());
    assertThat(updatedArtifact).isNotNull();
    assertThat(updatedArtifact.getStatus()).isEqualTo(APPROVED);
    assertThat(updatedArtifact.getContentStatus()).isEqualTo(DOWNLOADED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateContentStatusWithErrorMessage() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    artifactService.updateStatus(savedArtifact.getUuid(), savedArtifact.getAccountId(), Status.FAILED,
        ContentStatus.FAILED, "Failed to download artifact");
    Artifact updatedArtifact = artifactService.get(savedArtifact.getUuid());
    assertThat(updatedArtifact).isNotNull();
    assertThat(updatedArtifact.getStatus()).isEqualTo(Status.FAILED);
    assertThat(updatedArtifact.getContentStatus()).isEqualTo(ContentStatus.FAILED);
    assertThat(updatedArtifact.getErrorMessage()).isEqualTo("Failed to download artifact");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldNotDownloadFileForArtifactWhenNotReady() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    assertThat(artifactService.download(ACCOUNT_ID, savedArtifact.getUuid())).isNull();
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDownloadFileForArtifactWhenReady() {
    File file = null;
    try {
      Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
      ArtifactFile artifactFile =
          anArtifactFile().withAppId(APP_ID).withName("test-artifact.war").withUuid("TEST_FILE_ID").build();
      persistence.save(artifactFile);
      savedArtifact.setArtifactFiles(Lists.newArrayList(artifactFile));
      savedArtifact.setStatus(READY);
      persistence.save(savedArtifact);
      when(fileService.download(any(), any(), any())).thenAnswer(invocation -> {
        File inputFile = invocation.getArgument(1, File.class);
        Files.write("Dummy".getBytes(), inputFile);
        return inputFile;
      });

      file = artifactService.download(ACCOUNT_ID, savedArtifact.getUuid());
      assertThat(file).isNotNull().hasContent("Dummy");
    } finally {
      if (file != null) {
        file.delete();
      }
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldListByAppId() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    assertThat(artifactService.listByAppId(APP_ID)).hasSize(1).containsExactly(savedArtifact);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldList() {
    constructArtifacts();

    persistence.save(CustomArtifactStream.builder().uuid(SERVICE_ID).name("test").build());

    when(artifactStreamServiceBindingService.listArtifactStreamIds(APP_ID, SERVICE_ID))
        .thenReturn(asList(ARTIFACT_STREAM_ID));
    List<Artifact> artifacts = artifactService.listArtifactsForService(
        APP_ID, SERVICE_ID, aPageRequest().addFilter(ArtifactKeys.accountId, EQ, ACCOUNT_ID).build());

    assertThat(artifacts)
        .hasSize(4)
        .extracting(Artifact::getBuildNo)
        .containsSequence("todolist-1.0-1.x86_64.rpm", "todolist-1.0-10.x86_64.rpm", "todolist-1.0-5.x86_64.rpm",
            "todolist-1.0-15.x86_64.rpm");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldListSortByBuildNoAtConnector() {
    when(artifactStreamServiceBindingService.listArtifactStreamIds(APP_ID, SERVICE_ID)).thenReturn(asList());
    List<Artifact> artifacts = artifactService.listArtifactsForService(
        GLOBAL_APP_ID, SERVICE_ID, aPageRequest().addFilter(ArtifactKeys.accountId, EQ, ACCOUNT_ID).build());
    assertThat(artifacts).hasSize(0);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListWithoutAppId() {
    constructArtifacts();

    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID)).thenReturn(asList(ARTIFACT_STREAM_ID));
    List<Artifact> artifacts = artifactService.listArtifactsForService(
        SERVICE_ID, aPageRequest().addFilter(ArtifactKeys.accountId, EQ, ACCOUNT_ID).build());

    assertThat(artifacts)
        .hasSize(4)
        .extracting(Artifact::getBuildNo)
        .containsSequence("todolist-1.0-1.x86_64.rpm", "todolist-1.0-10.x86_64.rpm", "todolist-1.0-5.x86_64.rpm",
            "todolist-1.0-15.x86_64.rpm");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldListSortByBuildNoAtConnectorLevel() {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(JenkinsArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(GLOBAL_APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .settingId(SETTING_ID)
                        .build());
    constructArtifactsAtConnectorLevel();

    List<Artifact> artifacts =
        artifactService.listArtifactsForService(aPageRequest()
                                                    .addFilter(Artifact.APP_ID_KEY2, EQ, GLOBAL_APP_ID)
                                                    .addFilter(Artifact.ACCOUNT_ID_KEY2, EQ, ACCOUNT_ID)
                                                    .addFilter(ArtifactKeys.artifactStreamId, EQ, ARTIFACT_STREAM_ID)
                                                    .addFilter(ArtifactKeys.settingId, EQ, SETTING_ID)
                                                    .build());

    assertThat(artifacts)
        .hasSize(4)
        .extracting(Artifact::getBuildNo)
        .containsSequence("todolist-1.0-1.x86_64.rpm", "todolist-1.0-10.x86_64.rpm", "todolist-1.0-5.x86_64.rpm",
            "todolist-1.0-15.x86_64.rpm");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListSortByBuildNoWithNoServiceId() {
    constructArtifacts();

    List<Artifact> artifacts = artifactService.listArtifactsForService(
        APP_ID, null, aPageRequest().addFilter(Artifact.APP_ID_KEY2, EQ, APP_ID).build());

    assertThat(artifacts)
        .hasSize(4)
        .extracting(Artifact::getBuildNo)
        .containsSequence("todolist-1.0-1.x86_64.rpm", "todolist-1.0-10.x86_64.rpm", "todolist-1.0-5.x86_64.rpm",
            "todolist-1.0-15.x86_64.rpm");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotListArtifactsOfDeletedArtifactStreams() {
    constructArtifacts();

    List<Artifact> artifacts = artifactService.listArtifactsForService(
        APP_ID, SERVICE_ID, aPageRequest().addFilter(Artifact.APP_ID_KEY2, EQ, APP_ID).build());

    assertThat(artifacts).isEmpty();
  }

  private void constructArtifacts() {
    artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0-1.x86_64.rpm")))
            .but()
            .build(),
        true);

    artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0-10.x86_64.rpm")))
            .but()
            .build(),
        true);
    artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0-5.x86_64.rpm")))
            .but()
            .build(),
        true);

    artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0-15.x86_64.rpm")))
            .but()
            .build(),
        true);
  }

  private void constructArtifactsAtConnectorLevel() {
    Builder artifactBuilder = anArtifact()
                                  .withAppId(GLOBAL_APP_ID)
                                  .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                  .withSettingId(SETTING_ID)
                                  .withAccountId(ACCOUNT_ID)
                                  .withRevision("1.0")
                                  .withDisplayName("DISPLAY_NAME")
                                  .withCreatedAt(System.currentTimeMillis())
                                  .withCreatedBy(EmbeddedUser.builder().uuid("USER_ID").build());

    artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0-1.x86_64.rpm")))
            .but()
            .build(),
        true);

    artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0-10.x86_64.rpm")))
            .but()
            .build(),
        true);
    artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0-5.x86_64.rpm")))
            .but()
            .build(),
        true);

    artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0-15.x86_64.rpm")))
            .but()
            .build(),
        true);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetArtifact() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    assertThat(artifactService.get(savedArtifact.getUuid())).isEqualTo(savedArtifact);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("TODO: this test seems wrong, exposed after upgrading assertJ")
  public void shouldGetArtifactWithServices() {
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID))
        .thenReturn(Service.builder().appId(APP_ID).artifactType(ArtifactType.WAR).uuid(SERVICE_ID).build());
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    Artifact updatedArtifact = artifactService.get(savedArtifact.getUuid());
    assertThat(updatedArtifact).isEqualTo(savedArtifact);
    //    assertThat(updatedArtifact).extracting(Artifact::getServices).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldFetchLatestArtifactForArtifactStream() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    Artifact latestArtifact = artifactService.fetchLatestArtifactForArtifactStream(jenkinsArtifactStream);
    assertThat(latestArtifact)
        .isNotNull()
        .extracting(Artifact::getArtifactSourceName)
        .isEqualTo(savedArtifact.getArtifactSourceName());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldFetchLastCollectedApprovedArtifactForArtifactStream() {
    Artifact artifact = artifactBuilder.build();
    Artifact savedArtifact = artifactService.create(artifact, true);
    artifactService.updateStatus(savedArtifact.getUuid(), savedArtifact.getAccountId(), APPROVED);
    Artifact latestArtifact =
        artifactService.fetchLastCollectedApprovedArtifactForArtifactStream(jenkinsArtifactStream);
    assertThat(latestArtifact)
        .isNotNull()
        .extracting(Artifact::getArtifactSourceName)
        .isEqualTo(savedArtifact.getArtifactSourceName());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchLastCollectedApprovedArtifactSortedForArtifactStream() {
    Artifact latestArtifact = artifactService.fetchLastCollectedApprovedArtifactSorted(jenkinsArtifactStream);
    assertThat(latestArtifact).isNull();

    Artifact savedArtifact = artifactService.create(artifactBuilder.build(), true);
    artifactService.updateStatus(savedArtifact.getUuid(), savedArtifact.getAccountId(), READY);
    Artifact savedArtifactLatest = artifactService.create(artifactLatestBuilder.build(), true);
    artifactService.updateStatus(savedArtifactLatest.getUuid(), savedArtifactLatest.getAccountId(), READY);
    latestArtifact = artifactService.fetchLastCollectedApprovedArtifactSorted(jenkinsArtifactStream);
    if (savedArtifact.getCreatedAt() > savedArtifactLatest.getCreatedAt()) {
      assertThat(latestArtifact).isNotNull().extracting(Artifact::getBuildNo).isEqualTo(savedArtifact.getBuildNo());
    }
    if (savedArtifactLatest.getCreatedAt() > savedArtifact.getCreatedAt()) {
      assertThat(latestArtifact)
          .isNotNull()
          .extracting(Artifact::getBuildNo)
          .isEqualTo(savedArtifactLatest.getBuildNo());
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldFetchLastCollectedArtifactForArtifactStream() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.build(), true);
    artifactService.updateStatus(savedArtifact.getUuid(), savedArtifact.getAccountId(), RUNNING);
    Artifact latestArtifact = artifactService.fetchLastCollectedArtifact(jenkinsArtifactStream);
    assertThat(latestArtifact)
        .isNotNull()
        .extracting(Artifact::getArtifactSourceName)
        .isEqualTo(savedArtifact.getArtifactSourceName());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactByBuildNumberSource() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.build(), true);
    artifactService.updateStatus(savedArtifact.getUuid(), savedArtifact.getAccountId(), APPROVED);
    Artifact latestArtifact =
        artifactService.getArtifactByBuildNumber(jenkinsArtifactStream, savedArtifact.getBuildNo(), false);
    assertThat(latestArtifact)
        .isNotNull()
        .extracting(Artifact::getArtifactSourceName)
        .isEqualTo(savedArtifact.getArtifactSourceName());
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldStartArtifactCollectionNoArtifact() {
    when(persistence.get(Artifact.class, ARTIFACT_ID)).thenReturn(null);
    artifactService.startArtifactCollection(ACCOUNT_ID, ARTIFACT_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotCollectArtifactAlreadyQueued() {
    when(persistence.get(Artifact.class, ARTIFACT_ID)).thenReturn(artifactBuilder.withStatus(QUEUED).build());
    Artifact artifact = artifactService.startArtifactCollection(ACCOUNT_ID, ARTIFACT_ID);
    assertThat(artifact.getStatus()).isEqualTo(QUEUED);
    Mockito.verify(collectQueue, times(0)).send(any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotCollectArtifactAlreadyRunning() {
    when(persistence.get(Artifact.class, ARTIFACT_ID)).thenReturn(artifactBuilder.withStatus(RUNNING).build());
    Artifact artifact = artifactService.startArtifactCollection(ACCOUNT_ID, ARTIFACT_ID);
    assertThat(artifact.getStatus()).isEqualTo(RUNNING);
    Mockito.verify(collectQueue, times(0)).send(any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotCollectArtifactWhenContentStatusMetadata() {
    when(persistence.get(Artifact.class, ARTIFACT_ID))
        .thenReturn(artifactBuilder.withStatus(APPROVED).withContentStatus(METADATA_ONLY).build());
    Artifact artifact = artifactService.startArtifactCollection(ACCOUNT_ID, ARTIFACT_ID);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
    Mockito.verify(collectQueue, times(0)).send(any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotCollectArtifactWhenContentStatusDOWNLOADING() {
    when(persistence.get(Artifact.class, ARTIFACT_ID))
        .thenReturn(artifactBuilder.withStatus(APPROVED).withContentStatus(DOWNLOADING).build());
    Artifact artifact = artifactService.startArtifactCollection(ACCOUNT_ID, ARTIFACT_ID);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
    Mockito.verify(collectQueue, times(0)).send(any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotCollectArtifactWhenContentStatusDOWNLOADED() {
    when(persistence.get(Artifact.class, ARTIFACT_ID))
        .thenReturn(artifactBuilder.withStatus(APPROVED).withContentStatus(DOWNLOADED).build());
    Artifact artifact = artifactService.startArtifactCollection(ACCOUNT_ID, ARTIFACT_ID);
    assertThat(artifact.getContentStatus()).isEqualTo(DOWNLOADED);
    Mockito.verify(collectQueue, times(0)).send(any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCollectArtifact() {
    when(persistence.get(Artifact.class, ARTIFACT_ID))
        .thenReturn(artifactBuilder.withStatus(APPROVED).withContentStatus(NOT_DOWNLOADED).build());
    Artifact artifact = artifactService.startArtifactCollection(ACCOUNT_ID, ARTIFACT_ID);
    assertThat(artifact.getContentStatus()).isEqualTo(NOT_DOWNLOADED);
    Mockito.verify(collectQueue).send(any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactContentStatus() {
    Artifact artifact = artifactBuilder.withStatus(APPROVED).withContentStatus(NOT_DOWNLOADED).build();
    when(persistence.get(Artifact.class, ARTIFACT_ID)).thenReturn(artifact);
    ContentStatus contentStatus = artifactService.getArtifactContentStatus(artifact);
    assertThat(contentStatus).isEqualTo(NOT_DOWNLOADED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactContentStatusDeleted() {
    Artifact artifact = artifactBuilder.withStatus(APPROVED).build();
    when(persistence.get(Artifact.class, ARTIFACT_ID)).thenReturn(artifact);
    when(artifactStreamService.get(artifact.getArtifactStreamId())).thenReturn(null);
    ContentStatus contentStatus = artifactService.getArtifactContentStatus(artifact);
    assertThat(contentStatus).isEqualTo(DELETED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactContentStatusStreamDeleted() {
    Artifact artifact = artifactBuilder.withUuid(ARTIFACT_ID).withStatus(APPROVED).build();
    when(persistence.get(Artifact.class, ARTIFACT_ID)).thenReturn(artifact);
    when(artifactStreamService.get(artifact.getArtifactStreamId())).thenReturn(null);
    ContentStatus contentStatus = artifactService.getArtifactContentStatus(artifact);
    assertThat(contentStatus).isEqualTo(METADATA_ONLY);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactContentStatusStreamDeletedWithFiles() {
    Artifact artifact = artifactBuilder.withUuid(ARTIFACT_ID)
                            .withStatus(APPROVED)
                            .withArtifactFiles(asList(anArtifactFile().build()))
                            .build();
    when(persistence.get(Artifact.class, ARTIFACT_ID)).thenReturn(artifact);
    when(artifactStreamService.get(artifact.getArtifactStreamId())).thenReturn(null);
    ContentStatus contentStatus = artifactService.getArtifactContentStatus(artifact);
    assertThat(contentStatus).isEqualTo(DOWNLOADED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForDockerStream() {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(DockerArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .serviceId(SERVICE_ID)
                        .build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(METADATA_ONLY);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForEcrStream() {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(EcrArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .serviceId(SERVICE_ID)
                        .build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(METADATA_ONLY);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForGcrStream() {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(GcrArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .serviceId(SERVICE_ID)
                        .build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(METADATA_ONLY);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForAcrStream() {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(GcrArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .serviceId(SERVICE_ID)
                        .build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(METADATA_ONLY);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForAmiStream() {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(AmiArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .serviceId(SERVICE_ID)
                        .build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(METADATA_ONLY);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForS3Stream() {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(AmazonS3ArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .serviceId(SERVICE_ID)
                        .build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(METADATA_ONLY);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForNexusDockerStream() {
    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID, true))
        .thenReturn(Service.builder().uuid(SERVICE_ID).artifactType(ArtifactType.DOCKER).build());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(NexusArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .serviceId(SERVICE_ID)
                        .build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(METADATA_ONLY);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForNexusStream() {
    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID, true))
        .thenReturn(Service.builder().uuid(SERVICE_ID).artifactType(ArtifactType.WAR).build());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(NexusArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .serviceId(SERVICE_ID)
                        .repositoryType(RepositoryType.maven.name())
                        .build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(NOT_DOWNLOADED);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForArtifactoryDockerStream() {
    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID, true))
        .thenReturn(Service.builder().uuid(SERVICE_ID).artifactType(ArtifactType.DOCKER).build());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(ArtifactoryArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .serviceId(SERVICE_ID)
                        .build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(METADATA_ONLY);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForArtifactoryStream() {
    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID, true))
        .thenReturn(Service.builder().uuid(SERVICE_ID).artifactType(ArtifactType.WAR).build());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(ArtifactoryArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .serviceId(SERVICE_ID)
                        .build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(null);
    assertThat(artifact.getStatus()).isEqualTo(QUEUED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForJenkinsStream() {
    when(persistence.get(Artifact.class, ARTIFACT_ID)).thenReturn(artifactBuilder.build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(null);
    assertThat(artifact.getStatus()).isEqualTo(QUEUED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactStatusForBambooStream() {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(BambooArtifactStream.builder()
                        .uuid(ARTIFACT_STREAM_ID)
                        .appId(APP_ID)
                        .sourceName("ARTIFACT_SOURCE")
                        .serviceId(SERVICE_ID)
                        .build());
    assertThat(artifactService.getArtifactContentStatus(artifact)).isEqualTo(null);
    assertThat(artifact.getStatus()).isEqualTo(QUEUED);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDeleteArtifact() {
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    assertThat(persistence.get(Artifact.class, savedArtifact.getUuid())).isNotNull();
    artifactService.delete(savedArtifact.getAccountId(), savedArtifact.getUuid());
    assertThat(persistence.get(Artifact.class, savedArtifact.getUuid())).isNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotDeleteMetadataOnlyArtifacts() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .appId(APP_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName("wingsplugins/todolist")
                                                    .autoPopulate(true)
                                                    .serviceId(SERVICE_ID)
                                                    .build();
    String dockerArtifactStreamId = persistence.save(dockerArtifactStream);

    JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                      .sourceName("todolistwar")
                                                      .settingId(SETTING_ID)
                                                      .appId(APP_ID)
                                                      .jobname("todolistwar")
                                                      .autoPopulate(true)
                                                      .metadataOnly(true)
                                                      .serviceId(SERVICE_ID)
                                                      .artifactPaths(asList("target/todolist.war"))
                                                      .build();

    String jenkinsArtifactStreamId = persistence.save(jenkinsArtifactStream);

    Artifact dockerArtifact = constructArtifact(dockerArtifactStreamId);

    Artifact jenkinsArtifact = constructArtifact(jenkinsArtifactStreamId);

    when(artifactStreamService.get(dockerArtifactStreamId)).thenReturn(dockerArtifactStream);
    artifactService.create(dockerArtifact, true);

    when(artifactStreamService.get(jenkinsArtifactStreamId)).thenReturn(jenkinsArtifactStream);
    artifactService.create(jenkinsArtifact, true);

    artifactService.deleteArtifacts(50);
    assertThat(artifactService.listByAppId(APP_ID)).hasSize(2);
  }

  private Artifact constructArtifact(String dockerArtifactStreamId) {
    return anArtifact()
        .withAppId(APP_ID)
        .withArtifactStreamId(dockerArtifactStreamId)
        .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "200")))
        .withRevision("1.0")
        .withDisplayName("DISPLAY_NAME")
        .build();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteArtifactsWithNoArtifactFiles() {
    JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                      .sourceName("todolistwar")
                                                      .settingId(SETTING_ID)
                                                      .appId(APP_ID)
                                                      .jobname("todolistwar")
                                                      .autoPopulate(true)
                                                      .serviceId(SERVICE_ID)
                                                      .artifactPaths(asList("target/todolist.war"))
                                                      .build();
    String jenkinsArtifactStreamId = persistence.save(jenkinsArtifactStream);
    Artifact jenkinsArtifact = anArtifact()
                                   .withAppId(APP_ID)
                                   .withArtifactStreamId(jenkinsArtifactStreamId)
                                   .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "200")))
                                   .withRevision("1.0")
                                   .withDisplayName("DISPLAY_NAME")
                                   .withStatus(APPROVED)
                                   .withContentStatus(DOWNLOADED)
                                   .build();

    when(artifactStreamService.get(jenkinsArtifactStreamId)).thenReturn(jenkinsArtifactStream);
    Artifact savedArtifact = artifactService.create(jenkinsArtifact, true);

    persistence.save(savedArtifact);
    artifactService.deleteArtifacts(0);
    assertThat(artifactService.listByAppId(APP_ID)).hasSize(1);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldDeleteArtifactsWithArtifactFiles() {
    createArtifactWithArtifactFile();
    artifactService.deleteArtifacts(0);
    assertThat(artifactService.listByAppId(APP_ID)).hasSize(0);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotDeleteFailedArtifacts() {
    createArtifactWithArtifactFile(ContentStatus.FAILED);
    artifactService.deleteArtifacts(0);
    assertThat(artifactService.listByAppId(APP_ID)).hasSize(1);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotDeleteArtifactsGreaterThanRetentionTime() {
    createArtifactWithArtifactFile();
    artifactService.deleteArtifacts(1);
    assertThat(artifactService.listByAppId(APP_ID)).hasSize(1);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldListByIds() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    assertThat(persistence.get(Artifact.class, savedArtifact.getUuid())).isNotNull();
    assertThat(artifactService.listByIds(ACCOUNT_ID, singletonList(savedArtifact.getUuid()))).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteWhenArtifactSourceNameChanged() {
    JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                      .sourceName("todolistwar")
                                                      .settingId(SETTING_ID)
                                                      .appId(APP_ID)
                                                      .jobname("todolistwar")
                                                      .autoPopulate(true)
                                                      .serviceId(SERVICE_ID)
                                                      .artifactPaths(asList("target/todolist.war"))
                                                      .build();
    String jenkinsArtifactStreamId = persistence.save(jenkinsArtifactStream);
    Artifact jenkinsArtifact = anArtifact()
                                   .withAppId(APP_ID)
                                   .withArtifactStreamId(jenkinsArtifactStreamId)
                                   .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "200")))
                                   .withRevision("1.0")
                                   .withDisplayName("DISPLAY_NAME")
                                   .withStatus(APPROVED)
                                   .build();

    when(artifactStreamService.get(jenkinsArtifactStreamId)).thenReturn(jenkinsArtifactStream);
    Artifact savedArtifact = artifactService.create(jenkinsArtifact, true);
    assertThat(savedArtifact).isNotNull();

    artifactService.deleteByArtifactStreamId(APP_ID, jenkinsArtifactStreamId);

    assertThat(artifactService.listByAppId(APP_ID)).hasSize(0);
  }

  private void createArtifactWithArtifactFile() {
    createArtifactWithArtifactFile(DOWNLOADED);
  }

  private void createArtifactWithArtifactFile(ContentStatus contentStatus) {
    JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                      .sourceName("todolistwar")
                                                      .settingId(SETTING_ID)
                                                      .appId(APP_ID)
                                                      .jobname("todolistwar")
                                                      .autoPopulate(true)
                                                      .serviceId(SERVICE_ID)
                                                      .artifactPaths(asList("target/todolist.war"))
                                                      .build();
    String jenkinsArtifactStreamId = persistence.save(jenkinsArtifactStream);
    Artifact jenkinsArtifact = anArtifact()
                                   .withAppId(APP_ID)
                                   .withArtifactStreamId(jenkinsArtifactStreamId)
                                   .withMetadata(new ArtifactMetadata(ImmutableMap.of("buildNo", "200")))
                                   .withRevision("1.0")
                                   .withDisplayName("DISPLAY_NAME")
                                   .withStatus(APPROVED)
                                   .build();

    when(artifactStreamService.get(jenkinsArtifactStreamId)).thenReturn(jenkinsArtifactStream);
    Artifact savedArtifact = artifactService.create(jenkinsArtifact, true);
    ArtifactFile artifactFile = anArtifactFile()
                                    .withAppId(APP_ID)
                                    .withName("test-artifact.war")
                                    .withUuid("5942bffe1e204f7f3004f455")
                                    .withFileUuid("5942bffe1e204f7f3004f455")
                                    .build();
    persistence.save(artifactFile);
    savedArtifact.setArtifactFiles(asList(artifactFile));
    savedArtifact.setContentStatus(contentStatus);

    persistence.save(savedArtifact);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testListByAppIdForGlobalAppId() {
    assertThat(artifactService.listByAppId(GLOBAL_APP_ID)).hasSize(0);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void testFetchArtifactFiles() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.but().build(), true);
    ArtifactFile artifactFile = anArtifactFile()
                                    .withAppId(APP_ID)
                                    .withName("test-artifact.war")
                                    .withUuid("5942bffe1e204f7f3004f455")
                                    .withFileUuid("5942bffe1e204f7f3004f455")
                                    .build();
    artifactService.addArtifactFile(savedArtifact.getUuid(), savedArtifact.getAccountId(), asList(artifactFile));
    List<ArtifactFile> artifactFiles = artifactService.fetchArtifactFiles(savedArtifact.getUuid());
    assertThat(artifactFiles.size()).isEqualTo(1);
    assertThat(artifactFiles).extracting(ArtifactFile::getName).contains("test-artifact.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testCreateArtifactAtConnectorWithoutAccountId() {
    when(settingsService.fetchAccountIdBySettingId(SETTING_ID)).thenReturn(ACCOUNT_ID);
    Builder artifactBuilder = anArtifact()
                                  .withAppId(GLOBAL_APP_ID)
                                  .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                  .withSettingId(SETTING_ID)
                                  .withRevision("1.0")
                                  .withDisplayName("DISPLAY_NAME")
                                  .withCreatedAt(System.currentTimeMillis())
                                  .withCreatedBy(EmbeddedUser.builder().uuid("USER_ID").build());

    Artifact artifact = artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0-1.x86_64.rpm")))
            .but()
            .build(),
        true);
    assertThat(artifact).isNotNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testValidateArtifactStatusForNexusAtConnector() {
    Builder artifactBuilder = anArtifact()
                                  .withAppId(GLOBAL_APP_ID)
                                  .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                  .withSettingId(SETTING_ID)
                                  .withRevision("1.0")
                                  .withDisplayName("DISPLAY_NAME")
                                  .withCreatedAt(System.currentTimeMillis())
                                  .withCreatedBy(EmbeddedUser.builder().uuid("USER_ID").build());
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .sourceName("nuget-hosted/NuGet.Sample.Package")
                                                  .settingId(SETTING_ID)
                                                  .appId(GLOBAL_APP_ID)
                                                  .jobname("nuget-hosted")
                                                  .packageName("NuGet.Sample.Package")
                                                  .autoPopulate(true)
                                                  .build();
    when(artifactStreamService.get(artifact.getArtifactStreamId())).thenReturn(nexusArtifactStream);
    Artifact artifact = artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "1.0.0.18279"))).but().build(),
        true);
    assertThat(artifact).isNotNull();
    assertThat(artifact.getBuildNo()).isEqualTo("1.0.0.18279");
    assertThat(artifact.getContentStatus()).isEqualTo(NOT_DOWNLOADED);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testValidateArtifactStatusForArtifactoryDockerAtConnector() {
    Builder artifactBuilder = anArtifact()
                                  .withAppId(GLOBAL_APP_ID)
                                  .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                  .withSettingId(SETTING_ID)
                                  .withDisplayName("DISPLAY_NAME")
                                  .withCreatedAt(System.currentTimeMillis())
                                  .withCreatedBy(EmbeddedUser.builder().uuid("USER_ID").build());
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .sourceName("docker/hello-world-harness")
                                                              .settingId(SETTING_ID)
                                                              .appId(GLOBAL_APP_ID)
                                                              .imageName("hello-world-harness")
                                                              .autoPopulate(true)
                                                              .build();
    when(artifactStreamService.get(artifact.getArtifactStreamId())).thenReturn(artifactoryArtifactStream);
    Artifact artifact = artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "latest"))).but().build(), true);
    assertThat(artifact).isNotNull();
    assertThat(artifact.getBuildNo()).isEqualTo("latest");
    assertThat(artifact.getContentStatus()).isEqualTo(METADATA_ONLY);
    assertThat(artifact.getStatus()).isEqualTo(APPROVED);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testValidateArtifactStatusForArtifactoryAnyAtConnector() {
    Builder artifactBuilder = anArtifact()
                                  .withAppId(GLOBAL_APP_ID)
                                  .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                  .withSettingId(SETTING_ID)
                                  .withDisplayName("DISPLAY_NAME")
                                  .withCreatedAt(System.currentTimeMillis())
                                  .withCreatedBy(EmbeddedUser.builder().uuid("USER_ID").build());
    ArtifactoryArtifactStream artifactoryArtifactStream =
        ArtifactoryArtifactStream.builder()
            .sourceName("harness-maven/com/mycompany/app/todolist/1.0/todolist-*.war")
            .settingId(SETTING_ID)
            .appId(GLOBAL_APP_ID)
            .jobname("harness-maven")
            .artifactPattern("com/mycompany/app/todolist/1.0/todolist-*.war")
            .autoPopulate(true)
            .build();
    when(artifactStreamService.get(artifact.getArtifactStreamId())).thenReturn(artifactoryArtifactStream);
    Artifact artifact = artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0.war"))).but().build(),
        true);
    assertThat(artifact).isNotNull();
    assertThat(artifact.getBuildNo()).isEqualTo("todolist-1.0.war");
    assertThat(artifact.getStatus()).isEqualTo(QUEUED);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testUpdateArtifactSourceName() {
    CustomArtifactStream customArtifactStream =
        CustomArtifactStream.builder().sourceName("test").uuid(ARTIFACT_STREAM_ID).build();
    Builder artifactBuilder = anArtifact()
                                  .withAppId(GLOBAL_APP_ID)
                                  .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                  .withSettingId(SETTING_ID)
                                  .withAccountId(ACCOUNT_ID)
                                  .withRevision("1.0")
                                  .withDisplayName("DISPLAY_NAME")
                                  .withCreatedAt(System.currentTimeMillis())
                                  .withCreatedBy(EmbeddedUser.builder().uuid("USER_ID").build());

    Artifact artifact1 = artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0.war")))
            .withArtifactStreamId(ARTIFACT_STREAM_ID)
            .withStatus(RUNNING)
            .but()
            .build(),
        true);
    Artifact artifact2 = artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.1.war")))
            .withArtifactStreamId(ARTIFACT_STREAM_ID)
            .withStatus(READY)
            .but()
            .build(),
        true);
    Artifact artifact3 = artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.2.war")))
            .withArtifactStreamId(ARTIFACT_STREAM_ID)
            .withStatus(Status.FAILED)
            .but()
            .build(),
        true);
    artifactService.updateArtifactSourceName(customArtifactStream);
    Artifact updatedArtifact1 = artifactService.get(artifact1.getUuid());
    assertThat(updatedArtifact1).isNotNull();
    assertThat(updatedArtifact1.getArtifactSourceName()).isEqualTo("test");
    Artifact updatedArtifact2 = artifactService.get(artifact2.getUuid());
    assertThat(updatedArtifact2).isNotNull();
    assertThat(updatedArtifact2.getArtifactSourceName()).isEqualTo("test");
    Artifact updatedArtifact3 = artifactService.get(artifact3.getUuid());
    assertThat(updatedArtifact3).isNotNull();
    assertThat(updatedArtifact1.getArtifactSourceName()).isEqualTo("test");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetArtifactWithServices() {
    Artifact artifact1 = artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0.war")))
            .withArtifactStreamId(ARTIFACT_STREAM_ID)
            .withAppId(APP_ID)
            .withStatus(RUNNING)
            .but()
            .build(),
        true);
    when(artifactStreamServiceBindingService.listServices(APP_ID, ARTIFACT_STREAM_ID))
        .thenReturn(asList(Service.builder().name("Service1").build()));
    ArtifactView artifact = artifactService.getWithServices(artifact1.getUuid(), APP_ID);
    assertThat(artifact.getServices()).isNotEmpty();
    assertThat(artifact.getServices()).extracting(Service::getName).containsExactly("Service1");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetArtifactWithSource() {
    Artifact artifact1 = artifactService.create(
        artifactBuilder.withMetadata(new ArtifactMetadata(ImmutableMap.of(BUILD_NO, "todolist-1.0.war")))
            .withArtifactStreamId(ARTIFACT_STREAM_ID)
            .withAccountId(ACCOUNT_ID)
            .withAppId(APP_ID)
            .withStatus(RUNNING)
            .but()
            .build(),
        true);
    Map<String, String> sourceProperties = new HashMap<>();
    sourceProperties.put("k1", "v1");
    sourceProperties.put("k2", "v2");
    when(artifactStreamService.fetchArtifactSourceProperties(ACCOUNT_ID, ARTIFACT_STREAM_ID))
        .thenReturn(sourceProperties);
    Artifact artifact = artifactService.getWithSource(artifact1.getUuid());
    assertThat(artifact.getSource()).isNotEmpty();
    assertThat(artifact.getSource().size()).isEqualTo(2);
    assertThat(artifact.getSource()).contains(entry("k1", "v1"), entry("k2", "v2"));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetCustomArtifactByBuildNumberSource() {
    CustomArtifactStream customArtifactStream =
        CustomArtifactStream.builder().sourceName("test").uuid(ARTIFACT_STREAM_ID).build();
    Artifact savedArtifact = artifactService.create(artifactBuilder.build(), true);
    artifactService.updateStatus(savedArtifact.getUuid(), savedArtifact.getAccountId(), APPROVED);
    Artifact latestArtifact =
        artifactService.getArtifactByBuildNumber(customArtifactStream, savedArtifact.getBuildNo(), false);
    assertThat(latestArtifact)
        .isNotNull()
        .extracting(Artifact::getArtifactSourceName)
        .isEqualTo(savedArtifact.getArtifactSourceName());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldCreateArtifactUsingResolvedArtifactStream() {
    NexusArtifactStream concreteNexusArtifactStream = NexusArtifactStream.builder()
                                                          .accountId(ACCOUNT_ID)
                                                          .appId(APP_ID)
                                                          .settingId(SETTING_ID)
                                                          .jobname("releases")
                                                          .groupId("mygroup")
                                                          .artifactPaths(asList("myartifact"))
                                                          .autoPopulate(false)
                                                          .serviceId(SERVICE_ID)
                                                          .name("testNexus")
                                                          .build();
    concreteNexusArtifactStream.setSourceName(concreteNexusArtifactStream.generateSourceName());
    when(artifactCollectionUtils.getArtifactStreamAttributes(eq(concreteNexusArtifactStream), anyBoolean()))
        .thenReturn(ArtifactStreamAttributes.builder().build());
    Artifact artifact = artifactService.create(artifactBuilder.but().build(), concreteNexusArtifactStream, false);
    assertThat(artifact.getUuid()).isNotNull();
    assertThat(artifact.getBuildIdentity()).contains("200");
    assertThat(artifact.getArtifactSourceName()).isEqualTo("releases/mygroup/myartifact");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetArtifactByBuildNumberAndSourceName() {
    Artifact savedArtifact = artifactService.create(artifactBuilder.build(), true);
    artifactService.updateStatus(savedArtifact.getUuid(), savedArtifact.getAccountId(), APPROVED);
    Artifact latestArtifact = artifactService.getArtifactByBuildNumberAndSourceName(
        jenkinsArtifactStream, savedArtifact.getBuildNo(), false, jenkinsArtifactStream.getSourceName());
    assertThat(latestArtifact).isNotNull().extracting(Artifact::getUuid).isEqualTo(savedArtifact.getUuid());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldListArtifactsForServiceWithCollectionEnabled() {
    constructArtifacts();
    CustomArtifactStream customArtifactStream =
        CustomArtifactStream.builder().uuid(ARTIFACT_STREAM_ID).name("test").build();
    persistence.save(customArtifactStream);

    CustomArtifactStream customArtifactStream2 =
        CustomArtifactStream.builder().uuid(ARTIFACT_STREAM_ID + "1").name("test1").build();
    customArtifactStream2.setCollectionEnabled(false);
    persistence.save(customArtifactStream2);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID + "1")).thenReturn(customArtifactStream2);
    artifactService.create(artifactBuilder.withArtifactStreamId(ARTIFACT_STREAM_ID + "1").build(), true);

    List<String> projections = new ArrayList<>();
    projections.add(ArtifactStreamKeys.uuid);
    projections.add(ArtifactStreamKeys.collectionEnabled);
    when(artifactStreamService.getArtifactStreamsForService(APP_ID, SERVICE_ID, projections))
        .thenReturn(asList(customArtifactStream, customArtifactStream2));
    List<Artifact> artifacts = artifactService.listArtifactsForServiceWithCollectionEnabled(
        APP_ID, SERVICE_ID, aPageRequest().addFilter(ArtifactKeys.accountId, EQ, ACCOUNT_ID).build());

    assertThat(artifacts)
        .hasSize(4)
        .extracting(Artifact::getBuildNo)
        .containsSequence("todolist-1.0-1.x86_64.rpm", "todolist-1.0-10.x86_64.rpm", "todolist-1.0-5.x86_64.rpm",
            "todolist-1.0-15.x86_64.rpm");
  }
}
