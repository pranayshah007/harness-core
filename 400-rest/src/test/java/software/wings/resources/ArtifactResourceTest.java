/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.category.element.UnitTests;
import io.harness.exception.violation.ConstraintViolationExceptionMapper;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.artifact.ArtifactView;
import software.wings.exception.WingsExceptionMapper;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.PermitService;
import software.wings.utils.ResourceTestRule;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.beanutils.BeanUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Verifier;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactResourceTest extends CategoryTest {
  /**
   * The constant ARTIFACT_SERVICE.
   */
  public static final ArtifactService ARTIFACT_SERVICE = mock(ArtifactService.class);
  public static final ArtifactStreamService ARTIFACT_STREAM_SERVICE =
      mock(ArtifactStreamService.class, RETURNS_DEEP_STUBS);
  public static final PermitService PERMIT_SERVICE = mock(PermitService.class, RETURNS_DEEP_STUBS);
  public static final AppService APP_SERVICE = mock(AppService.class);
  public static final AlertService ALERT_SERVICE = mock(AlertService.class);

  /**
   * The constant RESOURCES.
   */
  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new ArtifactResource(
              ARTIFACT_SERVICE, ARTIFACT_STREAM_SERVICE, PERMIT_SERVICE, APP_SERVICE, ALERT_SERVICE))
          .type(ConstraintViolationExceptionMapper.class)
          .type(WingsExceptionMapper.class)
          .build();

  /**
   * The constant ACTUAL.
   */
  public static final Artifact ACTUAL = anArtifact().withAccountId(ACCOUNT_ID).withAppId(APP_ID).build();

  /**
   * The Verifier.
   */
  @Rule
  public Verifier verifier = new Verifier() {
    @Override
    protected void verify() throws Throwable {
      verifyNoMoreInteractions(ARTIFACT_SERVICE);
    }
  };

  /**
   * The Temp folder.
   */
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder(new File("/tmp"));

  private File tempFile;

  /**
   * Sets the up.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Before
  public void setUp() throws IOException, InvocationTargetException, IllegalAccessException {
    reset(ARTIFACT_SERVICE);
    when(ARTIFACT_SERVICE.create(any(Artifact.class))).thenReturn(ACTUAL);
    when(ARTIFACT_SERVICE.update(any(Artifact.class))).thenReturn(ACTUAL);
    ArtifactView artifactView = new ArtifactView();
    BeanUtils.copyProperties(artifactView, ACTUAL);
    when(ARTIFACT_SERVICE.getWithServices(ARTIFACT_ID, APP_ID)).thenReturn(artifactView);
    when(ARTIFACT_SERVICE.delete(ACCOUNT_ID, ARTIFACT_ID)).thenReturn(true);

    tempFile = tempFolder.newFile();
    Files.write("Dummy".getBytes(), tempFile);
    when(APP_SERVICE.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(ARTIFACT_SERVICE.download(ACCOUNT_ID, ARTIFACT_ID)).thenReturn(tempFile);
    PageResponse<Artifact> pageResponse = new PageResponse<>();
    pageResponse.setResponse(Lists.newArrayList(ACTUAL));
    pageResponse.setTotal(1L);
  }

  /**
   * Should create new artifact.
   */
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldCreateNewArtifact() {
    Artifact artifact = anArtifact()
                            .withAccountId(ACCOUNT_ID)
                            .withAppId(APP_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withMetadata(new ArtifactMetadata(ImmutableMap.of("BUILD_NO", "5")))
                            .build();
    when(ARTIFACT_STREAM_SERVICE.get(ARTIFACT_STREAM_ID).fetchArtifactDisplayName("5")).thenReturn("DISPLAY_NAME");

    when(ARTIFACT_STREAM_SERVICE.get(ARTIFACT_STREAM_ID).getFailedCronAttempts()).thenReturn(10);

    when(ARTIFACT_STREAM_SERVICE.get(ARTIFACT_STREAM_ID).getUuid()).thenReturn(ARTIFACT_STREAM_ID);

    when(ARTIFACT_STREAM_SERVICE.get(ARTIFACT_STREAM_ID).getAccountId()).thenReturn(ACCOUNT_ID);

    RestResponse<Artifact> restResponse =
        RESOURCES.client()
            .target("/artifacts?appId=" + APP_ID)
            .request()
            .post(entity(artifact, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Artifact>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(Artifact.class);
    verify(ARTIFACT_SERVICE).create(artifact);
    verify(ARTIFACT_STREAM_SERVICE).updateFailedCronAttemptsAndLastIteration(ACCOUNT_ID, ARTIFACT_STREAM_ID, 0, false);
    verify(PERMIT_SERVICE).releasePermitByKey(ARTIFACT_STREAM_ID);
  }

  /**
   * Should update artifact.
   */
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldUpdateArtifact() {
    Artifact artifact = anArtifact().withAccountId(ACCOUNT_ID).withAppId(APP_ID).withUuid(ARTIFACT_ID).build();

    RestResponse<Artifact> restResponse =
        RESOURCES.client()
            .target("/artifacts/" + ARTIFACT_ID + "?appId=" + APP_ID)
            .request()
            .put(entity(artifact, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Artifact>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(Artifact.class);
    verify(ARTIFACT_SERVICE).update(artifact);
  }

  /**
   * Should get artifact.
   */
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetArtifact() {
    RestResponse<ArtifactView> restResponse = RESOURCES.client()
                                                  .target("/artifacts/" + ARTIFACT_ID + "?appId=" + APP_ID)
                                                  .request()
                                                  .get(new GenericType<RestResponse<ArtifactView>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(ArtifactView.class);
    verify(ARTIFACT_SERVICE).getWithServices(ARTIFACT_ID, APP_ID);
  }

  /**
   * Should download artifact.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDownloadArtifact() throws IOException {
    Response restResponse = RESOURCES.client()
                                .target("/artifacts/" + ARTIFACT_ID + "/artifactFile"
                                    + "?appId=" + APP_ID)
                                .request()
                                .get();
    assertThat(restResponse.getMediaType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    assertThat(restResponse.getHeaderString("Content-Disposition"))
        .isEqualTo("attachment; filename=" + tempFile.getName());
    assertThat(restResponse.getEntity()).isInstanceOf(ByteArrayInputStream.class);
    assertThat(tempFile).hasContent(new String(ByteStreams.toByteArray((InputStream) restResponse.getEntity())));
    verify(ARTIFACT_SERVICE).download(ACCOUNT_ID, ARTIFACT_ID);
  }

  /**
   * Should list artifact.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListArtifact() {
    RESOURCES.client()
        .target("/artifacts/?appId=" + APP_ID)
        .request()
        .get(new GenericType<RestResponse<PageResponse<Artifact>>>() {});
    PageRequest<Artifact> expectedPageRequest = new PageRequest<>();
    expectedPageRequest.addFilter("appId", Operator.EQ, APP_ID);
    expectedPageRequest.setOffset("0");
    verify(ARTIFACT_SERVICE).listArtifactsForService(APP_ID, null, expectedPageRequest);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListArtifactWithServiceId() {
    RESOURCES.client()
        .target("/artifacts/?appId=" + APP_ID + "&serviceId=" + SERVICE_ID)
        .request()
        .get(new GenericType<RestResponse<PageResponse<Artifact>>>() {});
    PageRequest<Artifact> expectedPageRequest = new PageRequest<>();
    expectedPageRequest.addFilter("appId", Operator.EQ, APP_ID);
    expectedPageRequest.setOffset("0");
    verify(ARTIFACT_SERVICE).listArtifactsForService(APP_ID, SERVICE_ID, expectedPageRequest);
  }
  /**
   * Should delete artifact.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDeleteArtifact() throws IOException {
    Response response = RESOURCES.client().target("/artifacts/" + ARTIFACT_ID + "?appId=" + APP_ID).request().delete();
    verify(ARTIFACT_SERVICE).delete(ACCOUNT_ID, ARTIFACT_ID);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldListArtifactsWithCollectionEnabled() throws IOException {
    RESOURCES.client()
        .target("/artifacts/collection-enabled-artifacts?appId=" + APP_ID + "&serviceId=" + SERVICE_ID)
        .request()
        .get(new GenericType<RestResponse<PageResponse<Artifact>>>() {});
    PageRequest<Artifact> expectedPageRequest = new PageRequest<>();
    expectedPageRequest.addFilter("appId", Operator.EQ, APP_ID);
    expectedPageRequest.setOffset("0");
    verify(ARTIFACT_SERVICE).listArtifactsForServiceWithCollectionEnabled(APP_ID, SERVICE_ID, expectedPageRequest);
  }
}
