/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.artifactstream;

import static io.harness.generator.SettingGenerator.Settings.HARNESS_JENKINS_DEV_CONNECTOR;
import static io.harness.rule.OwnerRule.AADITI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.AccountGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.SettingGenerator;
import io.harness.generator.SettingGenerator.Settings;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.testframework.framework.Setup;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.SettingAttribute;
import software.wings.utils.RepositoryType;

import com.google.inject.Inject;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.GenericType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BuildSourceFunctionalTest extends AbstractFunctionalTest {
  @Inject private SettingGenerator settingGenerator;
  @Inject private OwnerManager ownerManager;
  @Inject private AccountGenerator accountGenerator;
  @Inject private ApplicationGenerator applicationGenerator;

  private final Randomizer.Seed seed = new Randomizer.Seed(0);
  private OwnerManager.Owners owners;
  private Application application;
  private Account account;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = owners.obtainApplication(
        () -> applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST));
    account = owners.obtainAccount();
    if (account == null) {
      account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    }
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  public void getJobsForJenkinsAtConnectorLevel() {
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, HARNESS_JENKINS_DEV_CONNECTOR);
    List<String> response = Setup.portal()
                                .auth()
                                .oauth2(bearerToken)
                                .queryParam("accountId", application.getAccountId())
                                .queryParam("settingId", settingAttribute.getUuid())
                                .contentType(ContentType.JSON)
                                .get("/settings/build-sources/jobs")
                                .jsonPath()
                                .getJsonObject("resource.jobName");
    assertThat(response).isNotNull();
    assertThat(response.size()).isGreaterThan(0);
    assertThat(response).contains("todolist-war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("This test is Flaky. Need to debug more by the test owner")
  public void getJobsForBambooAtConnectorLevel() {
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.HARNESS_BAMBOO_CONNECTOR);
    List<String> response = Setup.portal()
                                .auth()
                                .oauth2(bearerToken)
                                .queryParam("accountId", application.getAccountId())
                                .queryParam("settingId", settingAttribute.getUuid())
                                .contentType(ContentType.JSON)
                                .get("/settings/build-sources/jobs")
                                .jsonPath()
                                .getJsonObject("resource.jobName");
    assertThat(response).isNotNull();
    assertThat(response.size()).isGreaterThan(0);
    assertThat(response).contains("TOD-TOD");
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  public void getArtifactPathsForJenkinsAtConnectorLevel() {
    GenericType<RestResponse<Set<String>>> artifactStreamType = new GenericType<RestResponse<Set<String>>>() {

    };
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, HARNESS_JENKINS_DEV_CONNECTOR);
    RestResponse<Set<String>> restResponse = Setup.portal()
                                                 .auth()
                                                 .oauth2(bearerToken)
                                                 .queryParam("accountId", application.getAccountId())
                                                 .queryParam("settingId", settingAttribute.getUuid())
                                                 .pathParam("jobName", "todolist_war_copy2-do-not-delete")
                                                 .contentType(ContentType.JSON)
                                                 .get("/settings/build-sources/jobs/{jobName}/paths")
                                                 .as(artifactStreamType.getType());
    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource()).contains("target/todolist.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("This test is Flaky. Need to debug more by the test owner")
  public void getArtifactPathsForBambooAtConnectorLevel() {
    GenericType<RestResponse<Set<String>>> artifactStreamType = new GenericType<RestResponse<Set<String>>>() {

    };
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.HARNESS_BAMBOO_CONNECTOR);
    RestResponse<Set<String>> restResponse = Setup.portal()
                                                 .auth()
                                                 .oauth2(bearerToken)
                                                 .queryParam("accountId", application.getAccountId())
                                                 .queryParam("settingId", settingAttribute.getUuid())
                                                 .pathParam("jobName", "TOD-TOD")
                                                 .contentType(ContentType.JSON)
                                                 .get("/settings/build-sources/jobs/{jobName}/paths")
                                                 .as(artifactStreamType.getType());
    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().contains("artifacts/todolist.war")).isNotNull();
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  @Ignore("This test is Flaky. Need to debug more by the test owner")
  public void getDockerImagesNamesForArtifactoryAtConnectorLevel() {
    GenericType<RestResponse<Set<String>>> artifactStreamType = new GenericType<RestResponse<Set<String>>>() {

    };
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.HARNESS_ARTIFACTORY_CONNECTOR);
    RestResponse<Set<String>> restResponse = Setup.portal()
                                                 .auth()
                                                 .oauth2(bearerToken)
                                                 .queryParam("accountId", application.getAccountId())
                                                 .queryParam("settingId", settingAttribute.getUuid())
                                                 .pathParam("jobName", "docker")
                                                 .contentType(ContentType.JSON)
                                                 .get("/settings/build-sources/jobs/{jobName}/groupIds")
                                                 .as(artifactStreamType.getType());
    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().contains("hello-world-harness")).isNotNull();
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  @Ignore("Enable this when we are able to create Docker Repository type on jfrog.dev.harness.io")
  public void getRepositoriesForArtifactoryDockerAtConnectorLevel() {
    GenericType<RestResponse<Map<String, String>>> artifactStreamType =
        new GenericType<RestResponse<Map<String, String>>>() {

        };
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.HARNESS_ARTIFACTORY_CONNECTOR);
    RestResponse<Map<String, String>> restResponse = Setup.portal()
                                                         .auth()
                                                         .oauth2(bearerToken)
                                                         .queryParam("accountId", application.getAccountId())
                                                         .queryParam("settingId", settingAttribute.getUuid())
                                                         .queryParam("repositoryType", RepositoryType.docker.name())
                                                         .contentType(ContentType.JSON)
                                                         .get("/settings/build-sources/plans")
                                                         .as(artifactStreamType.getType());
    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().size()).isGreaterThan(0);
    assertThat(restResponse.getResource()).containsKey("docker");
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  @Ignore("This test is Flaky. Need to debug more by the test owner")
  public void getRepositoriesForArtifactoryAnyAtConnectorLevel() {
    GenericType<RestResponse<Map<String, String>>> artifactStreamType =
        new GenericType<RestResponse<Map<String, String>>>() {

        };
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.HARNESS_ARTIFACTORY_CONNECTOR);
    RestResponse<Map<String, String>> restResponse = Setup.portal()
                                                         .auth()
                                                         .oauth2(bearerToken)
                                                         .queryParam("accountId", application.getAccountId())
                                                         .queryParam("settingId", settingAttribute.getUuid())
                                                         .queryParam("repositoryType", RepositoryType.any.name())
                                                         .contentType(ContentType.JSON)
                                                         .get("/settings/build-sources/plans")
                                                         .as(artifactStreamType.getType());
    assertThat(restResponse.getResource()).withFailMessage(restResponse.toString()).isNotNull();
    assertThat(restResponse.getResource().size()).isGreaterThan(0);
    assertThat(restResponse.getResource()).containsKey("harness-maven");
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  public void getGroupIdsForNexus() {
    GenericType<RestResponse<Set<String>>> artifactStreamType = new GenericType<RestResponse<Set<String>>>() {

    };
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, Settings.HARNESS_NEXUS2_CONNECTOR);
    RestResponse<Set<String>> restResponse = Setup.portal()
                                                 .auth()
                                                 .oauth2(bearerToken)
                                                 .queryParam("accountId", application.getAccountId())
                                                 .queryParam("settingId", settingAttribute.getUuid())
                                                 .pathParam("jobName", "releases")
                                                 .contentType(ContentType.JSON)
                                                 .get("/settings/build-sources/jobs/{jobName}/groupIds")
                                                 .as(artifactStreamType.getType());
    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().size()).isGreaterThan(0);
    assertThat(restResponse.getResource().contains("releases")).isNotNull();
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  @Ignore("TODO - Will be enabled when ConnectorDisconnectHandler level apis are changed to account for nexus3 support")
  public void getGroupIdsForNexus3() {
    GenericType<RestResponse<Set<String>>> artifactStreamType = new GenericType<RestResponse<Set<String>>>() {

    };
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.HARNESS_NEXU3_CONNECTOR);
    RestResponse<Set<String>> restResponse = Setup.portal()
                                                 .auth()
                                                 .oauth2(bearerToken)
                                                 .queryParam("accountId", application.getAccountId())
                                                 .queryParam("settingId", settingAttribute.getUuid())
                                                 .pathParam("jobName", "docker-private")
                                                 .contentType(ContentType.JSON)
                                                 .get("/settings/build-sources/jobs/{jobName}/groupIds")
                                                 .as(artifactStreamType.getType());
    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().size()).isGreaterThan(0);
    assertThat(restResponse.getResource().contains("harness/todolist-sample")).isNotNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(FunctionalTests.class)
  @Ignore("This test is Flaky. Need to debug more by the test owner")
  public void getPlansForAmazonS3AtConnectorLevel() {
    GenericType<RestResponse<Map<String, String>>> artifactStreamType =
        new GenericType<RestResponse<Map<String, String>>>() {

        };
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.AWS_TEST_CLOUD_PROVIDER);
    RestResponse<Map<String, String>> restResponse = Setup.portal()
                                                         .auth()
                                                         .oauth2(bearerToken)
                                                         .queryParam("accountId", application.getAccountId())
                                                         .queryParam("settingId", settingAttribute.getUuid())
                                                         .queryParam("streamType", "AMAZON_S3")
                                                         .contentType(ContentType.JSON)
                                                         .get("/settings/build-sources/plans")
                                                         .as(artifactStreamType.getType());
    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().size()).isGreaterThan(0);
    assertThat(restResponse.getResource()).containsKey("harness-example");
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  public void getProjectForGCS() {
    GenericType<RestResponse<String>> artifactStreamType = new GenericType<RestResponse<String>>() {

    };
    final SettingAttribute settingAttribute = settingGenerator.ensurePredefined(seed, owners, Settings.GCP_PLAYGROUND);
    RestResponse<String> restResponse = Setup.portal()
                                            .auth()
                                            .oauth2(bearerToken)
                                            .queryParam("accountId", application.getAccountId())
                                            .queryParam("settingId", settingAttribute.getUuid())
                                            .contentType(ContentType.JSON)
                                            .get("/settings/build-sources/project")
                                            .as(artifactStreamType.getType());
    assertThat(restResponse.getResource()).isNotNull();
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  public void getSubscriptionsForACR() {
    GenericType<RestResponse<Map<String, String>>> artifactStreamType =
        new GenericType<RestResponse<Map<String, String>>>() {

        };
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.AZURE_TEST_CLOUD_PROVIDER);
    RestResponse<Map<String, String>> restResponse = Setup.portal()
                                                         .auth()
                                                         .oauth2(bearerToken)
                                                         .queryParam("accountId", application.getAccountId())
                                                         .queryParam("settingId", settingAttribute.getUuid())
                                                         .contentType(ContentType.JSON)
                                                         .get("/settings/subscriptions")
                                                         .as(artifactStreamType.getType());
    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().size()).isGreaterThan(0);
  }

  @Test
  @Owner(developers = AADITI, intermittent = true)
  @Category(FunctionalTests.class)
  public void getAWSRegionsForECR() {
    GenericType<RestResponse<List<Object>>> artifactStreamType = new GenericType<RestResponse<List<Object>>>() {

    };
    final SettingAttribute settingAttribute =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.AWS_TEST_CLOUD_PROVIDER);
    RestResponse<List<Object>> restResponse = Setup.portal()
                                                  .auth()
                                                  .oauth2(bearerToken)
                                                  .queryParam("accountId", application.getAccountId())
                                                  .contentType(ContentType.JSON)
                                                  .get("/settings/aws-regions")
                                                  .as(artifactStreamType.getType());
    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().size()).isGreaterThan(0);
  }
}
