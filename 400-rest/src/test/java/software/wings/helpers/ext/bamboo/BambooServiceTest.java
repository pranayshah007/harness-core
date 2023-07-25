/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.bamboo;

import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static software.wings.utils.WingsTestConstants.ARTIFACT_PATH;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.MapEntry.entry;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.BambooConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.collect.artifacts.ArtifactCollectionCommonTaskHelper;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.utils.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.inject.Inject;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by anubhaw on 12/8/16.
 */
@OwnedBy(HarnessTeam.CDC)
public class BambooServiceTest extends WingsBaseTest {
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig()
                                                          .usingFilesUnderClasspath("400-rest/src/test/resources")
                                                          .disableRequestJournal()
                                                          .port(0));

  @Inject @InjectMocks DelegateFileManager delegateFileManager;
  ;
  @Mock private ArtifactCollectionCommonTaskHelper artifactCollectionCommonTaskHelper;

  @Mock private EncryptionService encryptionService;
  @InjectMocks private BambooService bambooService = new BambooServiceImpl();

  private BambooConfig bambooConfig;
  @Before
  public void setupMocks() {
    bambooConfig = BambooConfig.builder()
                       .bambooUrl("http://localhost:" + wireMockRule.port())
                       .username("admin")
                       .password("admin".toCharArray())
                       .build();
    on(bambooService).set("timeLimiter", new FakeTimeLimiter());
    on(bambooService).set("encryptionService", encryptionService);
    on(bambooService).set("artifactCollectionCommonTaskHelper", artifactCollectionCommonTaskHelper);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetPlanKeys() {
    assertThat(bambooService.getPlanKeys(bambooConfig, null))
        .contains(entry("TES-PLAN", "plan-1"), entry("TES-PLN10", "plan-10"), entry("TES-PLN100", "plan-100"),
            entry("TES-PLN1000", "plan-1000"), entry("TES-PLN10000", "plan-10000"), entry("TES-PLN1001", "plan-1001"));
  }

  @Test(expected = ArtifactServerException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetPlanKeysExceptionWithInvalidCreds() {
    wireMockRule.stubFor(get(urlEqualTo("/rest/api/latest/plan.json?authType=basic&max-results=1000"))
                             .willReturn(aResponse().withStatus(401).withHeader("Content-Type", "application/json")));
    bambooService.getPlanKeys(bambooConfig, null);
  }

  @Test(expected = ArtifactServerException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetPlanKeysException() {
    wireMockRule.stubFor(get(urlEqualTo("/rest/api/latest/plan.json?authType=basic&max-results=1000"))
                             .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json")));
    bambooService.getPlanKeys(bambooConfig, null);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetLastSuccessfulBuild() {
    BuildDetails buildDetails = bambooService.getLastSuccessfulBuild(bambooConfig, null, "TOD-TODIR");
    assertThat(buildDetails).isNotNull();
    assertThat(buildDetails.getNumber()).isEqualTo("104");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetLastSuccessfulBuildWithArtifactFileMetadata() {
    BuildDetails buildDetails =
        bambooService.getLastSuccessfulBuild(bambooConfig, null, "TP-PLAN", asList("myartifacts/todolist.war"));
    assertThat(buildDetails).isNotNull();
    assertThat(buildDetails.getNumber()).isEqualTo("4");
    assertThat(buildDetails.getArtifactFileMetadataList()).isNotEmpty();
    assertThat(buildDetails.getArtifactFileMetadataList().get(0).getFileName()).isEqualTo("todolist.war");
    assertThat(buildDetails.getArtifactFileMetadataList().get(0).getUrl())
        .isEqualTo(
            "http://ec2-18-208-86-222.compute-1.amazonaws.com:8085/browse/TP-PLAN-4/artifact/JOB1/myartifacts/todolist.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotGetLastSuccessfulBuild() {
    BuildDetails buildDetails = bambooService.getLastSuccessfulBuild(bambooConfig, null, "TOD-TOD");
    assertThat(buildDetails).isNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetBuildsForJobWithEmptyArtifactPaths() {
    // backward compatibility - with artifact paths as ""
    List<BuildDetails> bamboo_plan_key = bambooService.getBuilds(bambooConfig, null, "TP-PLAN2", asList(""), 50);
    assertThat(bamboo_plan_key).isNotEmpty();
    assertThat(bamboo_plan_key.get(0)).extracting(BuildDetails::getNumber).isEqualTo("4");
    assertThat(bamboo_plan_key.get(0).getArtifactFileMetadataList()).isEmpty();
    assertThat(bamboo_plan_key.get(0).getMetadata().get("planName")).isEqualTo("plan-1");
    assertThat(bamboo_plan_key.get(0).getMetadata().get("id")).isEqualTo("5013835");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetBuildsForJobWithCustomArtifactPaths() {
    List<BuildDetails> bamboo_plan_key = bambooService.getBuilds(
        bambooConfig, null, "TP-PLAN3", asList("myartifacts/todolist.war", "myartifacts/todolist.zip"), 50);
    assertThat(bamboo_plan_key).isNotEmpty();
    assertThat(bamboo_plan_key.get(0)).extracting(BuildDetails::getNumber).isEqualTo("4");
    assertThat(bamboo_plan_key.get(0).getArtifactFileMetadataList().get(0).getUrl())
        .isEqualTo(
            "http://localhost:" + wireMockRule.port() + "/browse/TP-PLAN3-4/artifact/JOB1/myartifacts/todolist.war");
    assertThat(bamboo_plan_key.get(0).getArtifactFileMetadataList().get(0).getFileName()).isEqualTo("todolist.war");
    assertThat(bamboo_plan_key.get(0).getArtifactFileMetadataList().get(1).getUrl())
        .isEqualTo(
            "http://localhost:" + wireMockRule.port() + "/browse/TP-PLAN3-4/artifact/JOB1/myartifacts/todolist.zip");
    assertThat(bamboo_plan_key.get(0).getArtifactFileMetadataList().get(1).getFileName()).isEqualTo("todolist.zip");
  }

  @Test(expected = ArtifactServerException.class)
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetBuildsForJobError() {
    wireMockRule.stubFor(get(
        urlEqualTo(
            "/rest/api/latest/result/BAMBOO_PLAN_KEY.json?authType=basic&buildState=Successful&expand=results.result&max-result=50"))
                             .willReturn(aResponse().withStatus(200).withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
    bambooService.getBuilds(bambooConfig, null, "BAMBOO_PLAN_KEY", asList(ARTIFACT_PATH), 50);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetArtifactPath() {
    List<String> artifactPaths = bambooService.getArtifactPath(bambooConfig, null, "TP-PLAN");
    assertThat(artifactPaths).isNotEmpty();
    assertThat(artifactPaths.size()).isEqualTo(1);
    assertThat(artifactPaths).contains("myartifacts/todolist.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadArtifact() throws FileNotFoundException {
    wireMockRule.stubFor(get(urlEqualTo("/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.tar"))
                             .willReturn(aResponse().withBody(new byte[] {1, 2, 3, 4})));
    ArtifactStreamAttributes artifactStreamAttributes =
        ArtifactStreamAttributes.builder()
            .jobName("TOD-TOD")
            .artifactPaths(asList("artifacts/todolist.tar"))
            .artifactFileMetadata(asList(ArtifactFileMetadata.builder()
                                             .fileName("todolist.tar")
                                             .url("http://localhost:" + wireMockRule.port()
                                                 + "/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.tar")
                                             .build()))
            .build();
    ListNotifyResponseData listNotifyResponseData = new ListNotifyResponseData();
    DelegateFile delegateFile = DelegateFile.Builder.aDelegateFile().withFileId("FILE_ID").build();
    when(delegateFileManager.upload(any(), any())).thenReturn(delegateFile);
    bambooService.downloadArtifacts(
        bambooConfig, null, artifactStreamAttributes, "11", null, null, null, listNotifyResponseData);
    verify(artifactCollectionCommonTaskHelper, times(1))
        .addDataToResponse(any(),
            eq("http://localhost:" + wireMockRule.port() + "/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.tar"),
            any(), any(), any(), any());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetArtifactFileSize() {
    wireMockRule.stubFor(get(urlEqualTo("/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.tar"))
                             .willReturn(aResponse().withBody(new byte[] {1, 2, 3, 4})));
    long size = bambooService.getFileSize(bambooConfig, null, "todolist.tar",
        "http://localhost:" + wireMockRule.port() + "/artifact/TOD-TOD/JOB1/build-11/artifacts/todolist.tar");
    assertThat(size).isEqualTo(4);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetJobKeys() {
    List<String> actual = bambooService.getJobKeys(bambooConfig, null, "planKey");
    assertThat(actual).hasSize(1).isEqualTo(Lists.newArrayList("TP-PLAN2"));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetJobKeysFails() {
    wireMockRule.stubFor(
        get(urlEqualTo(
                "/rest/api/latest/plan/randomKey.json?authType=basic&expand=stages.stage.plans.plan&max-results=10000"))
            .willReturn(aResponse().withStatus(400)));
    assertThatThrownBy(() -> bambooService.getJobKeys(bambooConfig, null, "randomKey"))
        .isInstanceOf(ArtifactServerException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  @Ignore("TODO: fix with the new version of com.github.tomakehurst.wiremock")
  public void shouldGetTriggerPlan() {
    wireMockRule.stubFor(
        post(urlEqualTo("/rest/api/latest/queue/planKey?authtype=basic&stage&executeAllStages"))
            .withRequestBody(matching(".*"))
            .willReturn(aResponse().withStatus(200).withBody("{\"buildResultKey\" : \"someBuildResultKey\"}")));
    assertThat(bambooService.triggerPlan(bambooConfig, null, "planKey", null)).isEqualTo("someBuildResultKey");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldFailGetTriggerPlan() {
    wireMockRule.stubFor(post(urlEqualTo("/rest/api/latest/queue/planKey?authtype=basic&stage&executeAllStages"))
                             .withRequestBody(matching(".*"))
                             .willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(() -> bambooService.triggerPlan(bambooConfig, null, "planKey", null))
        .isInstanceOf(InvalidArtifactServerException.class);

    wireMockRule.stubFor(post(urlEqualTo("/rest/api/latest/queue/planKey?authtype=basic&stage&executeAllStages"))
                             .withRequestBody(matching(".*"))
                             .willReturn(aResponse().withStatus(400)));

    assertThatThrownBy(() -> bambooService.triggerPlan(bambooConfig, null, "planKey", null))
        .isInstanceOf(InvalidArtifactServerException.class);

    wireMockRule.stubFor(post(urlEqualTo("/rest/api/latest/queue/planKey?authtype=basic&stage&executeAllStages"))
                             .withRequestBody(matching(".*"))
                             .willReturn(aResponse().withStatus(200)));

    assertThatThrownBy(() -> bambooService.triggerPlan(bambooConfig, null, "planKey", null))
        .isInstanceOf(InvalidArtifactServerException.class);

    wireMockRule.stubFor(post(urlEqualTo("/rest/api/latest/queue/planKey?authtype=basic&stage&executeAllStages"))
                             .withRequestBody(matching(".*"))
                             .willReturn(aResponse().withStatus(200).withBody("{}")));

    assertThatThrownBy(() -> bambooService.triggerPlan(bambooConfig, null, "planKey", null))
        .isInstanceOf(InvalidArtifactServerException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetBuildResult() {
    Result actual = bambooService.getBuildResult(bambooConfig, null, "TOD-TODIR");
    Result expected = JsonUtils.convertStringToObj(
        JsonUtils.readResourceFile("__files/bamboo/expected-body-get-build-result-details.json", JsonNode.class)
            .toString()
            .replace("9095", String.valueOf(wireMockRule.port())),
        Result.class);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldFailGetBuildResult() {
    wireMockRule.stubFor(get(urlEqualTo("/rest/api/latest/result/TOD-TODIR.json?authType=basic"))
                             .willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(() -> bambooService.getBuildResult(bambooConfig, null, "TOD-TODIR"))
        .isInstanceOf(ArtifactServerException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetBuildResultStatus() {
    wireMockRule.stubFor(get(urlEqualTo("/rest/api/latest/result/status/TOD-TODIR.json?authType=basic"))
                             .willReturn(aResponse().withStatus(200).withBody(
                                 "{\"finished\" : true, \"prettyQueuedTime\" : \"Thu, 1 Aug, 05:41 PM\"}")));
    Status actual = bambooService.getBuildResultStatus(bambooConfig, null, "TOD-TODIR");
    assertThat(actual.isFinished()).isTrue();
    assertThat(actual.getPrettyQueuedTime()).isEqualTo("Thu, 1 Aug, 05:41 PM");

    actual = bambooService.getBuildResultStatus(bambooConfig, null, "doesNotExist");
    assertThat(actual.isFinished()).isTrue();
    assertThat(actual.getPrettyQueuedTime()).isNull();

    // Please note: This is the existing behaviour where for any response code we are not throwing any error
    // We simply return status as NULL. Fix if this assumption is not true
    wireMockRule.stubFor(get(urlEqualTo("/rest/api/latest/result/status/failingBuild.json?authType=basic"))
                             .willReturn(aResponse().withStatus(400).withBody("{\"message\" : \"Bad Request\"}")));
    actual = bambooService.getBuildResultStatus(bambooConfig, null, "failingBuild");
    assertThat(actual).isNull();
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionIfPasswordIsNotDecrypted() {
    BambooConfig bambooConfigPasswordEncrypted =
        BambooConfig.builder().password(null).encryptedPassword("encrypted").build();
    assertThatThrownBy(()
                           -> ((BambooServiceImpl) bambooService)
                                  .getBasicAuthCredentials(bambooConfigPasswordEncrypted, Collections.emptyList()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to decrypt password for Bamboo connector");
  }

  @Test(expected = ArtifactServerException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void shouldDownloadArtifacts() throws FileNotFoundException {
    bambooService.downloadArtifacts(bambooConfig, null, asList("myartifacts/todolist.war"), "TP-PLAN", "4");
  }
}
