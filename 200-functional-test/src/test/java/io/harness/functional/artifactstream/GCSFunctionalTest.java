/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.artifactstream;

import static io.harness.rule.OwnerRule.ANSHUL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.ServiceGenerator.Services;
import io.harness.generator.SettingGenerator;
import io.harness.generator.SettingGenerator.Settings;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.testframework.framework.Setup;
import io.harness.testframework.restutils.ArtifactRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.GcsArtifactStream;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.Status;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import java.util.ArrayList;
import java.util.HashMap;
import javax.ws.rs.core.GenericType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GCSFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private SettingGenerator settingGenerator;

  private static final String GCS_PROJECT = "playground-243019";
  private static final String GCS_ARTIFACT = "todolist-v1.0.zip";
  private static final String GCS_BUCKET = "functional-test";

  private Application application;
  private final Seed seed = new Seed(0);
  private Owners owners;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(FunctionalTests.class)
  @Ignore("This test is Flaky. Need to debug more by the test owner")
  public void shouldCollectGCSArtifact() {
    Service service = serviceGenerator.ensurePredefined(seed, owners, Services.GENERIC_TEST);
    SettingAttribute gcpCloudProvider = settingGenerator.ensurePredefined(seed, owners, Settings.GCP_PLAYGROUND);

    // List buckets and paths for project
    listBuckets(gcpCloudProvider);
    listArtifactPaths(gcpCloudProvider, GCS_BUCKET);

    // Create an artifact stream with retrieved bucket and artifact
    ArtifactStream savedArtifactSteam = saveAndGetGcsArtifactStream(service, gcpCloudProvider, GCS_ARTIFACT);
    assertThat(savedArtifactSteam).isNotNull();

    final Artifact collectedArtifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, application.getUuid(), savedArtifactSteam.getUuid(), 0);
    assertThat(collectedArtifact).isNotNull();

    assertThat(collectedArtifact.getArtifactStreamId()).isEqualTo(savedArtifactSteam.getUuid());
    assertThat(collectedArtifact.getStatus()).isEqualTo(Status.APPROVED);
    assertThat(collectedArtifact.getMetadata().get("bucketName")).isEqualTo(GCS_BUCKET);
    assertThat(collectedArtifact.getMetadata().get("artifactFileName")).isEqualTo(GCS_ARTIFACT);

    // Clean up all resources
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam("appId", application.getUuid())
        .pathParam("id", savedArtifactSteam.getUuid())
        .delete("/artifactstreams/{id}")
        .then()
        .statusCode(200);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(FunctionalTests.class)
  @Ignore("This test is Flaky. Need to debug more by the test owner")
  public void shouldCollectGCSArtifactWithRegex() {
    Service service = serviceGenerator.ensurePredefined(seed, owners, Services.GENERIC_TEST);
    SettingAttribute gcpCloudProvider = settingGenerator.ensurePredefined(seed, owners, Settings.GCP_PLAYGROUND);

    // List buckets and paths for project
    listBuckets(gcpCloudProvider);
    listArtifactPaths(gcpCloudProvider, GCS_BUCKET);

    // Create an artifact stream with retrieved bucket and artifact
    String artifactPath = "todolist-v1*";
    ArtifactStream savedArtifactSteam = saveAndGetGcsArtifactStream(service, gcpCloudProvider, artifactPath);
    assertThat(savedArtifactSteam).isNotNull();

    final Artifact collectedArtifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, application.getUuid(), savedArtifactSteam.getUuid(), 0);

    assertThat(collectedArtifact.getArtifactStreamId()).isEqualTo(savedArtifactSteam.getUuid());
    assertThat(collectedArtifact.getStatus()).isEqualTo(Status.APPROVED);
    assertThat(collectedArtifact.getMetadata().get("bucketName")).isEqualTo(GCS_BUCKET);
    assertThat(collectedArtifact.getMetadata().get("artifactFileName")).isEqualTo(GCS_ARTIFACT);

    // Clean up all resources
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam("appId", application.getUuid())
        .pathParam("id", savedArtifactSteam.getUuid())
        .delete("/artifactstreams/{id}")
        .then()
        .statusCode(200);
  }

  private void listBuckets(SettingAttribute gcpCloudProvider) {
    // List buckets for project
    GenericType<RestResponse<HashMap<String, String>>> bucketType =
        new GenericType<RestResponse<HashMap<String, String>>>() {};
    RestResponse<HashMap<String, String>> bucketResponse = Setup.portal()
                                                               .auth()
                                                               .oauth2(bearerToken)
                                                               .queryParam("appId", application.getUuid())
                                                               .queryParam("settingId", gcpCloudProvider.getUuid())
                                                               .queryParam("projectId", GCS_PROJECT)
                                                               .get("/build-sources/buckets")
                                                               .as(bucketType.getType());
    assertThat(bucketResponse).isNotNull();
    assertThat(bucketResponse.getResource()).isNotEmpty();
    assertThat(bucketResponse.getResource()).containsKey(GCS_BUCKET);
  }

  private void listArtifactPaths(SettingAttribute gcpCloudProvider, String bucket) {
    // List artifact paths for bucket
    GenericType<RestResponse<ArrayList<String>>> artifactPathType =
        new GenericType<RestResponse<ArrayList<String>>>() {};
    RestResponse<ArrayList<String>> artifactPathsResponse = Setup.portal()
                                                                .auth()
                                                                .oauth2(bearerToken)
                                                                .queryParam("appId", application.getUuid())
                                                                .queryParam("settingId", gcpCloudProvider.getUuid())
                                                                .queryParam("streamType", ArtifactStreamType.GCS.name())
                                                                .pathParam("bucket", bucket)
                                                                .get("/build-sources/jobs/{bucket}/paths")
                                                                .as(artifactPathType.getType());
    assertThat(artifactPathsResponse).isNotNull();
    assertThat(artifactPathsResponse.getResource()).isNotEmpty();
  }

  private ArtifactStream saveAndGetGcsArtifactStream(
      Service service, SettingAttribute gcpCloudProvider, String artifactPath) {
    GenericType<RestResponse<GcsArtifactStream>> artifactStreamType =
        new GenericType<RestResponse<GcsArtifactStream>>() {};
    GcsArtifactStream gcsArtifactStream = GcsArtifactStream.builder()
                                              .appId(application.getUuid())
                                              .serviceId(service.getUuid())
                                              .artifactPaths(Lists.newArrayList(artifactPath))
                                              .jobname(GCS_BUCKET)
                                              .name("GCS Artifact Stream" + System.currentTimeMillis())
                                              .sourceName(GCS_BUCKET + "/" + artifactPath)
                                              .settingId(gcpCloudProvider.getUuid())
                                              .projectId(GCS_PROJECT)
                                              .autoPopulate(true)
                                              .build();

    RestResponse<GcsArtifactStream> restResponse = Setup.portal()
                                                       .auth()
                                                       .oauth2(bearerToken)
                                                       .queryParam("accountId", application.getAccountId())
                                                       .queryParam("appId", application.getUuid())
                                                       .body(gcsArtifactStream, ObjectMapperType.GSON)
                                                       .contentType(ContentType.JSON)
                                                       .post("/artifactstreams")
                                                       .as(artifactStreamType.getType());

    ArtifactStream savedArtifactSteam = restResponse.getResource();
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo(gcsArtifactStream.getSourceName());
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ArtifactStreamType.GCS.name());
    return savedArtifactSteam;
  }
}
