/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.artifactstream;

import static io.harness.rule.OwnerRule.GARVIT;

import static java.lang.String.format;
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
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.Status;

import com.google.inject.Inject;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.GenericType;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class AzureArtifactsFunctionalTest extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private SettingGenerator settingGenerator;

  private static final String PROTOCOL_TYPE_MAVEN = "maven";
  private static final String PROJECT_ID_MAVEN = "";
  private static final String FEED_ID_MAVEN = "garvit-test";
  private static final String PACKAGE_ID_MAVEN = "610b1a30-9b03-4380-b869-a098b3794396";
  private static final String PACKAGE_NAME_MAVEN = "com.mycompany.app:my-app";
  private static final String VERSION_MAVEN = "1.1-SNAPSHOT";
  private static final String VERSION_ID_MAVEN = "d71998b1-c1e5-4c07-a162-cdc3149134d7";

  private static final String PROTOCOL_TYPE_NUGET = "nuget";
  private static final String PROJECT_ID_NUGET = "sample-project";
  private static final String FEED_ID_NUGET = "other-feed";
  private static final String PACKAGE_ID_NUGET = "caf0bd59-1296-4aa8-bad7-da47f73ce237";
  private static final String PACKAGE_NAME_NUGET = "AppLogger";
  private static final String VERSION_NUGET = "0.3.0";
  private static final String VERSION_ID_NUGET = "3bf06344-04aa-4e3a-a174-82d6bf25880f";

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
  @Owner(developers = GARVIT)
  @Category(FunctionalTests.class)
  @Ignore("TODO: Fix azure artifacts tests - repo config seems to have changed")
  public void shouldCollectMavenArtifactsWithoutProject() {
    Service service = serviceGenerator.ensurePredefined(seed, owners, Services.GENERIC_TEST);
    SettingAttribute azureArtifactsSetting =
        settingGenerator.ensurePredefined(seed, owners, Settings.AZURE_ARTIFACTS_CONNECTOR);

    listFeeds(azureArtifactsSetting, PROJECT_ID_MAVEN, FEED_ID_MAVEN);
    listPackages(azureArtifactsSetting, PROJECT_ID_MAVEN, FEED_ID_MAVEN, PROTOCOL_TYPE_MAVEN, PACKAGE_ID_MAVEN,
        PACKAGE_NAME_MAVEN);

    ArtifactStream savedArtifactSteam = saveAndGetAzureArtifactsArtifactStream(service, azureArtifactsSetting,
        PROTOCOL_TYPE_MAVEN, PROJECT_ID_MAVEN, FEED_ID_MAVEN, PACKAGE_ID_MAVEN, PACKAGE_NAME_MAVEN);
    assertThat(savedArtifactSteam).isNotNull();

    final Artifact collectedArtifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, application.getUuid(), savedArtifactSteam.getUuid(), 0);
    assertThat(collectedArtifact).isNotNull();

    assertThat(collectedArtifact.getArtifactStreamId()).isEqualTo(savedArtifactSteam.getUuid());
    assertThat(collectedArtifact.getStatus()).isEqualTo(Status.APPROVED);
    assertThat(collectedArtifact.getMetadata().get("version")).isEqualTo(VERSION_MAVEN);
    assertThat(collectedArtifact.getMetadata().get("versionId")).isEqualTo(VERSION_ID_MAVEN);
    assertThat(collectedArtifact.getMetadata().get("buildNo")).isEqualTo(VERSION_MAVEN);

    // Clean up resources.
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
  @Owner(developers = GARVIT)
  @Category(FunctionalTests.class)
  @Ignore("TODO: Fix azure artifacts tests - repo config seems to have changed")
  public void shouldCollectNuGetArtifactsWithProject() {
    Service service = serviceGenerator.ensurePredefined(seed, owners, Services.GENERIC_TEST);
    SettingAttribute azureArtifactsSetting =
        settingGenerator.ensurePredefined(seed, owners, Settings.AZURE_ARTIFACTS_CONNECTOR);

    listProjects(azureArtifactsSetting, PROJECT_ID_NUGET);
    listFeeds(azureArtifactsSetting, PROJECT_ID_NUGET, FEED_ID_NUGET);
    listPackages(azureArtifactsSetting, PROJECT_ID_NUGET, FEED_ID_NUGET, PROTOCOL_TYPE_NUGET, PACKAGE_ID_NUGET,
        PACKAGE_NAME_NUGET);

    ArtifactStream savedArtifactSteam = saveAndGetAzureArtifactsArtifactStream(service, azureArtifactsSetting,
        PROTOCOL_TYPE_NUGET, PROJECT_ID_NUGET, FEED_ID_NUGET, PACKAGE_ID_NUGET, PACKAGE_NAME_NUGET);
    assertThat(savedArtifactSteam).isNotNull();

    final Artifact collectedArtifact = ArtifactRestUtils.waitAndFetchArtifactByArtfactStream(
        bearerToken, application.getUuid(), savedArtifactSteam.getUuid(), 0);
    assertThat(collectedArtifact).isNotNull();

    assertThat(collectedArtifact.getArtifactStreamId()).isEqualTo(savedArtifactSteam.getUuid());
    assertThat(collectedArtifact.getStatus()).isEqualTo(Status.APPROVED);
    assertThat(collectedArtifact.getMetadata().get("version")).isEqualTo(VERSION_NUGET);
    assertThat(collectedArtifact.getMetadata().get("versionId")).isEqualTo(VERSION_ID_NUGET);
    assertThat(collectedArtifact.getMetadata().get("buildNo")).isEqualTo(VERSION_NUGET);

    // Clean up resources.
    Setup.portal()
        .auth()
        .oauth2(bearerToken)
        .queryParam("appId", application.getUuid())
        .pathParam("id", savedArtifactSteam.getUuid())
        .delete("/artifactstreams/{id}")
        .then()
        .statusCode(200);
  }

  private void listProjects(SettingAttribute azureArtifactsSetting, String project) {
    Response response = null;
    for (int i = 0; i < 3; i++) {
      response = Setup.portal()
                     .auth()
                     .oauth2(bearerToken)
                     .queryParam("accountId", application.getAccountId())
                     .queryParam("settingId", azureArtifactsSetting.getUuid())
                     .get("/build-sources/projects");
      log.info("List projects response: {}, ", response.prettyPrint());
      if (response.getStatusCode() < 400) {
        break;
      }
    }

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(200);

    GenericType<RestResponse<List<AzureDevopsProject>>> projectType =
        new GenericType<RestResponse<List<AzureDevopsProject>>>() {};
    RestResponse<List<AzureDevopsProject>> projectsResponse = response.as(projectType.getType());
    assertThat(projectsResponse).isNotNull();
    assertThat(projectsResponse.getResource()).isNotEmpty();
    assertThat(projectsResponse.getResource()
                   .stream()
                   .filter(projectObj -> projectObj.getName().equals(project))
                   .collect(Collectors.toList()))
        .isNotEmpty();
  }

  private void listFeeds(SettingAttribute azureArtifactsSetting, String projectId, String feed) {
    Response response = null;
    for (int i = 0; i < 3; i++) {
      response = Setup.portal()
                     .auth()
                     .oauth2(bearerToken)
                     .queryParam("accountId", application.getAccountId())
                     .queryParam("settingId", azureArtifactsSetting.getUuid())
                     .queryParam("project", projectId)
                     .get("/build-sources/feeds");
      log.info("List feeds response: {}, ", response.prettyPrint());
      if (response.getStatusCode() < 400) {
        break;
      }
    }

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(200);

    GenericType<RestResponse<List<AzureArtifactsFeed>>> feedType =
        new GenericType<RestResponse<List<AzureArtifactsFeed>>>() {};
    RestResponse<List<AzureArtifactsFeed>> feedsResponse = response.as(feedType.getType());
    assertThat(feedsResponse).isNotNull();
    assertThat(feedsResponse.getResource()).isNotEmpty();
    assertThat(feedsResponse.getResource()
                   .stream()
                   .filter(feedObj -> feedObj.getName().equals(feed))
                   .collect(Collectors.toList()))
        .isNotEmpty();
  }

  private void listPackages(SettingAttribute azureArtifactsSetting, String projectId, String feed, String protocolType,
      String packageId, String packageName) {
    Response response = null;
    for (int i = 0; i < 3; i++) {
      response = Setup.portal()
                     .auth()
                     .oauth2(bearerToken)
                     .queryParam("accountId", application.getAccountId())
                     .queryParam("settingId", azureArtifactsSetting.getUuid())
                     .queryParam("project", projectId)
                     .queryParam("protocolType", protocolType)
                     .get(format("/build-sources/feeds/%s/packages", feed));
      log.info("List packages response: {}, ", response.prettyPrint());
      if (response.getStatusCode() < 400) {
        break;
      }
    }

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(200);

    GenericType<RestResponse<List<AzureArtifactsPackage>>> packageType =
        new GenericType<RestResponse<List<AzureArtifactsPackage>>>() {};
    RestResponse<List<AzureArtifactsPackage>> packagesResponse = response.as(packageType.getType());
    assertThat(packagesResponse).isNotNull();
    assertThat(packagesResponse.getResource()).isNotEmpty();
    assertThat(
        packagesResponse.getResource()
            .stream()
            .filter(packageObj -> packageObj.getId().equals(packageId) && packageObj.getName().equals(packageName))
            .collect(Collectors.toList()))
        .isNotEmpty();
  }

  private ArtifactStream saveAndGetAzureArtifactsArtifactStream(Service service, SettingAttribute azureArtifactsSetting,
      String protocolType, String projectId, String feed, String packageId, String packageName) {
    AzureArtifactsArtifactStream azureArtifactsArtifactStream =
        AzureArtifactsArtifactStream.builder()
            .appId(application.getUuid())
            .serviceId(service.getUuid())
            .settingId(azureArtifactsSetting.getUuid())
            .name(format("Azure Artifact Artifact Stream %s %d", protocolType, System.currentTimeMillis()))
            .protocolType(protocolType)
            .project(projectId)
            .feed(feed)
            .packageId(packageId)
            .packageName(packageName)
            .sourceName(packageName)
            .autoPopulate(false)
            .build();

    Response response = null;
    for (int i = 0; i < 3; i++) {
      response = Setup.portal()
                     .auth()
                     .oauth2(bearerToken)
                     .queryParam("accountId", application.getAccountId())
                     .queryParam("appId", application.getUuid())
                     .body(azureArtifactsArtifactStream, ObjectMapperType.GSON)
                     .contentType(ContentType.JSON)
                     .post("/artifactstreams");
      log.info("Save artifact stream response: {}, ", response.prettyPrint());
      if (response.getStatusCode() < 400) {
        break;
      }
    }

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(200);

    GenericType<RestResponse<AzureArtifactsArtifactStream>> artifactStreamType =
        new GenericType<RestResponse<AzureArtifactsArtifactStream>>() {};
    RestResponse<AzureArtifactsArtifactStream> restResponse = response.as(artifactStreamType.getType());
    ArtifactStream savedArtifactSteam = restResponse.getResource();
    assertThat(savedArtifactSteam.getUuid()).isNotEmpty();
    assertThat(savedArtifactSteam.getSourceName()).isEqualTo(azureArtifactsArtifactStream.getSourceName());
    assertThat(savedArtifactSteam.getArtifactStreamType()).isEqualTo(ArtifactStreamType.AZURE_ARTIFACTS.name());
    return savedArtifactSteam;
  }
}
