/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.jenkins;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.SHIVAM;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.artifacts.jenkins.client.JenkinsClient;
import io.harness.artifacts.jenkins.client.JenkinsCustomServer;
import io.harness.artifacts.jenkins.service.JenkinsRegistryUtils;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.HintException;
import io.harness.logging.LoggingInitializer;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;

import software.wings.WingsBaseTest;
import software.wings.beans.JenkinsConfig;
import software.wings.helpers.ext.jenkins.model.JobProperty;
import software.wings.helpers.ext.jenkins.model.JobWithExtendedDetails;
import software.wings.helpers.ext.jenkins.model.ParametersDefinitionProperty;
import software.wings.utils.JsonUtils;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import com.offbytwo.jenkins.model.QueueReference;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.http.client.HttpResponseException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.powermock.api.mockito.PowerMockito;

/**
 * The Class JenkinsTest.
 */
@OwnedBy(CDC)
public class JenkinsRegistryUtilsTest extends WingsBaseTest {
  private static final String JENKINS_URL = "http://localhost:%s/";
  private static final String USERNAME = "wingsbuild";
  private static String PASSWORD = "password";

  @Inject ScmSecret scmSecret;
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig()
                                                          .usingFilesUnderClasspath("400-rest/src/test/resources")
                                                          .disableRequestJournal()
                                                          .port(8089));

  private String rootUrl;
  private JenkinsInternalConfig jenkinsInternalConfig;
  @Inject private JenkinsRegistryUtils jenkinsRegistryUtils;
  private Jenkins jenkins;

  @Before
  public void setup() throws URISyntaxException {
    rootUrl = String.format(JENKINS_URL, wireMockRule.port());
    PASSWORD = scmSecret.decryptToString(new SecretName("jenkins_password"));
    LoggingInitializer.initializeLogging();
    jenkinsInternalConfig = JenkinsInternalConfig.builder()
                                .jenkinsUrl(rootUrl)
                                .username(USERNAME)
                                .password(PASSWORD.toCharArray())
                                .useConnectorUrlForJobExecution(true)
                                .build();
  }

  /**
   * Should get job from jenkins.
   *
   * @throws URISyntaxException the URI syntax exception
   * @throws IOException        Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldGetJobFromJenkins() throws IOException {
    assertThat(jenkinsRegistryUtils.getJobWithDetails(jenkinsInternalConfig, "scheduler")).isNotNull();
  }

  /**
   * Should get child jobs from jenkins.
   *
   * @throws URISyntaxException the URI syntax exception
   * @throws IOException        Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldGetJobsFromJenkins() throws IOException {
    wireMockRule.stubFor(
        get(urlEqualTo("/job/parentJob/api/json"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"_class\":\"com.cloudbees.hudson.plugins.folder.Folder\",\"property\":[{},{\"_class\":\"hudson.plugins.jobConfigHistory.JobConfigHistoryProjectAction\"},{},{\"_class\":\"com.cloudbees.plugins.credentials.ViewCredentialsAction\"}],\"description\":null,\"displayName\":\"parentJob\",\"displayNameOrNull\":null,\"fullDisplayName\":\"parentJob\",\"fullName\":\"parentJob\",\"name\":\"parentJob\",\"url\":\"https://jenkins.wings.software/job/parentJob/\",\"healthReport\":[],\"jobs\":[{\"_class\":\"hudson.maven.MavenModuleSet\",\"name\":\"abcd\",\"url\":\"https://jenkins.wings.software/job/parentJob/job/abcd/\"},{\"_class\":\"hudson.maven.MavenModuleSet\",\"name\":\"parentJob_war_copy\",\"url\":\"https://jenkins.wings.software/job/parentJob/job/parentJob_war_copy/\",\"color\":\"notbuilt\"}],\"primaryView\":{\"_class\":\"hudson.model.AllView\",\"name\":\"All\",\"url\":\"https://jenkins.wings.software/job/parentJob/\"},\"views\":[{\"_class\":\"hudson.model.AllView\",\"name\":\"All\",\"url\":\"https://jenkins.wings.software/job/parentJob/\"}]}")
                    .withHeader("Content-Type", "application/json")));

    List<JobDetails> jobs = jenkinsRegistryUtils.getJobs(jenkinsInternalConfig, "parentJob");
    assertThat(jobs.size() == 2).isTrue();
  }

  /**
   * Should get child jobs from jenkins.
   *
   * @throws URISyntaxException the URI syntax exception
   * @throws IOException        Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldGetJobsFromJenkinsForDifferentHost() throws IOException {
    wireMockRule.stubFor(
        get(urlEqualTo("/job/parentJob/api/json"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"_class\":\"com.cloudbees.hudson.plugins.folder.Folder\",\"actions\":[{},{\"_class\":\"hudson.plugins.jobConfigHistory.JobConfigHistoryProjectAction\"},{},{\"_class\":\"com.cloudbees.plugins.credentials.ViewCredentialsAction\"}],\"description\":null,\"displayName\":\"parentJob\",\"displayNameOrNull\":null,\"fullDisplayName\":\"parentJob\",\"fullName\":\"parentJob\",\"name\":\"parentJob\",\"url\":\"https://jenkins.wings.software/job/parentJob/\",\"healthReport\":[],\"jobs\":[{\"_class\":\"hudson.maven.MavenModuleSet\",\"name\":\"abcd\",\"url\":\"https://jenkins.wings.software/job/parentJob/job/abcd/\"},{\"_class\":\"hudson.maven.MavenModuleSet\",\"name\":\"parentJob_war_copy\",\"url\":\"https://jenkins.wings.software/job/parentJob/job/parentJob_war_copy/\",\"color\":\"notbuilt\"}],\"primaryView\":{\"_class\":\"hudson.model.AllView\",\"name\":\"All\",\"url\":\"https://jenkins.wings.software/job/parentJob/\"},\"views\":[{\"_class\":\"hudson.model.AllView\",\"name\":\"All\",\"url\":\"https://jenkins.wings.software/job/parentJob/\"}]}")
                    .withHeader("Content-Type", "application/json")));

    List<JobDetails> jobs = jenkinsRegistryUtils.getJobs(jenkinsInternalConfig, "parentJob");
    assertThat(jobs.size() == 2).isTrue();
    assertThat(jobs.get(0).getJobName().equals("parentJob/parentJob_war_copy")).isTrue();
    assertThat(jobs.get(1).getJobName().equals("parentJob/abcd")).isTrue();
  }

  /**
   * Should return null when job does not exist.
   *
   * @throws URISyntaxException the URI syntax exception
   * @throws IOException        Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldReturnNullWhenJobDoesNotExist() throws URISyntaxException, IOException {
    assertThat(jenkinsRegistryUtils.getJobWithDetails(jenkinsInternalConfig, "scheduler1")).isNull();
  }

  /**
   * Should get last n build details for git jobs.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldGetLastNBuildDetailsForGitJobs() throws IOException {
    List<BuildDetails> buildDetails = jenkinsRegistryUtils.getBuildsForJob(jenkinsInternalConfig, "scheduler",
        asList("build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar", "todolist.war"), 5);
    assertThat(buildDetails)
        .hasSize(4)
        .extracting(BuildDetails::getNumber, BuildDetails::getRevision)
        .containsExactly(tuple("67", "1bfdd1174d41e1f32cbfc287f18c3cc040ca90e3"),
            tuple("65", "1bfdd1174d41e1f32cbfc287f18c3cc040ca90e3"),
            tuple("64", "1bfdd1174d41e1f32cbfc287f18c3cc040ca90e3"),
            tuple("63", "1bfdd1174d41e1f32cbfc287f18c3cc040ca90e3"));

    buildDetails.forEach(buildDetails1 -> {
      String url = rootUrl + "job/scheduler/" + buildDetails1.getNumber()
          + "/artifact/build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar";
      assertThat(buildDetails1.getArtifactFileMetadataList()).isNotEmpty();
      assertThat(buildDetails1.getArtifactFileMetadataList())
          .extracting(ArtifactFileMetadata::getFileName, ArtifactFileMetadata::getUrl)
          .containsExactly(tuple("docker-scheduler-1.0-SNAPSHOT-all.jar", url));
    });
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldGetLastSuccessfulBuildForGitJob() throws IOException {
    BuildDetails buildDetails = jenkinsRegistryUtils.getLastSuccessfulBuildForJob(
        jenkinsInternalConfig, "scheduler", asList("build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar"));
    assertThat(buildDetails).isNotNull();
    assertThat(buildDetails.getNumber()).isEqualTo("67");
    assertThat(buildDetails.getRevision()).isEqualTo("1bfdd1174d41e1f32cbfc287f18c3cc040ca90e3");
    assertThat(buildDetails.getArtifactFileMetadataList().size()).isEqualTo(1);
    assertThat(buildDetails.getArtifactFileMetadataList().get(0).getFileName())
        .isEqualTo("docker-scheduler-1.0-SNAPSHOT-all.jar");
    assertThat(buildDetails.getArtifactFileMetadataList().get(0).getUrl())
        .isEqualTo(rootUrl + "job/scheduler/67/artifact/build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldGetNullLastSuccessfulBuildForNonExistingGitJob() throws IOException {
    BuildDetails buildDetails = jenkinsRegistryUtils.getLastSuccessfulBuildForJob(
        jenkinsInternalConfig, "scheduler1", asList("build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar"));
    assertThat(buildDetails).isNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldGetLastNBuildDetailsForSvnJobs() throws IOException {
    List<BuildDetails> buildDetails = jenkinsRegistryUtils.getBuildsForJob(
        jenkinsInternalConfig, "scheduler-svn", asList("build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar"), 5);
    assertThat(buildDetails)
        .hasSize(4)
        .extracting(BuildDetails::getNumber, BuildDetails::getRevision)
        .containsExactly(tuple("65", "39"), tuple("64", "39"), tuple("63", "39"), tuple("62", "39"));
    buildDetails.forEach(buildDetails1 -> {
      String url = rootUrl + "job/scheduler-svn/" + buildDetails1.getNumber()
          + "/artifact/build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar";
      assertThat(buildDetails1.getArtifactFileMetadataList()).isNotEmpty();
      assertThat(buildDetails1.getArtifactFileMetadataList())
          .extracting(ArtifactFileMetadata::getFileName, ArtifactFileMetadata::getUrl)
          .containsExactly(tuple("docker-scheduler-1.0-SNAPSHOT-all.jar", url));
    });

    buildDetails = jenkinsRegistryUtils.getBuildsForJob(
        jenkinsInternalConfig, "scheduler-svn", asList("build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar"), 5, true);
    assertThat(buildDetails)
        .hasSize(5)
        .extracting(BuildDetails::getNumber, BuildDetails::getRevision)
        .containsExactly(tuple("65", "39"), tuple("64", "39"), tuple("63", "39"), tuple("62", "39"), tuple("61", "39"));
    buildDetails.forEach(buildDetails1 -> {
      String url = rootUrl + "job/scheduler-svn/" + buildDetails1.getNumber()
          + "/artifact/build/libs/docker-scheduler-1.0-SNAPSHOT-all.jar";
      assertThat(buildDetails1.getArtifactFileMetadataList()).isNotEmpty();
      assertThat(buildDetails1.getArtifactFileMetadataList())
          .extracting(ArtifactFileMetadata::getFileName, ArtifactFileMetadata::getUrl)
          .containsExactly(tuple("docker-scheduler-1.0-SNAPSHOT-all.jar", url));
    });
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldTriggerJobWithParameters() throws IOException {
    QueueReference queueItem =
        jenkinsRegistryUtils.trigger("todolist_war", jenkinsInternalConfig, ImmutableMap.of("Test", "Test"));
    assertThat(queueItem.getQueueItemUrlPart()).isNotNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldFetchBuildFromQueueItem() throws IOException, URISyntaxException {
    JenkinsInternalConfig jenkinsInternalConfigTest =
        JenkinsInternalConfig.builder().jenkinsUrl(rootUrl).username(USERNAME).password(PASSWORD.toCharArray()).build();
    Build build =
        jenkinsRegistryUtils.getBuild(new QueueReference(rootUrl + "queue/item/27287"), jenkinsInternalConfigTest);
    assertThat(build.getQueueId()).isEqualTo(27287);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldTriggerJobWithoutParameters() throws IOException {
    QueueReference queueItem =
        jenkinsRegistryUtils.trigger("todolist_war", jenkinsInternalConfig, Collections.emptyMap());
    assertThat(queueItem.getQueueItemUrlPart()).isNotNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldTestGetJobParameters() {
    JobWithDetails jobWithDetails = jenkinsRegistryUtils.getJobWithDetails(jenkinsInternalConfig, "todolist_promot");
    assertThat(jobWithDetails).isNotNull();
    assertThat(jobWithDetails).isInstanceOf(JobWithExtendedDetails.class);
    JobWithExtendedDetails jobWithExtendedDetails = (JobWithExtendedDetails) jobWithDetails;
    assertThat(jobWithExtendedDetails).extracting(JobWithExtendedDetails::getProperties).isNotNull();

    List<ParametersDefinitionProperty> properties = jobWithExtendedDetails.getProperties()
                                                        .stream()
                                                        .map(JobProperty::getParameterDefinitions)
                                                        .filter(Objects::nonNull)
                                                        .flatMap(Collection::stream)
                                                        .collect(toList());
    assertThat(properties)
        .isNotNull()
        .extracting(ParametersDefinitionProperty::getName)
        .containsSequence("revision", "branch", "Choices", "boolean", "Credentials");
    assertThat(properties)
        .extracting(ParametersDefinitionProperty::getDefaultParameterValue)
        .extracting("value")
        .contains("release");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void triggerJobWithParametersWithConnectorUrl() throws IOException {
    QueueReference queueItem =
        jenkinsRegistryUtils.trigger("todolist_war", jenkinsInternalConfig, ImmutableMap.of("Test", "Test"));
    assertThat(queueItem.getQueueItemUrlPart()).isNotNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void triggerJobWithoutParametersWithConnectorUrl() throws IOException {
    QueueReference queueItem =
        jenkinsRegistryUtils.trigger("todolist_war", jenkinsInternalConfig, Collections.emptyMap());
    assertThat(queueItem.getQueueItemUrlPart()).isNotNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void fetchBuildFromQueueItemWithConnectorURL() throws IOException, URISyntaxException {
    Build build =
        jenkinsRegistryUtils.getBuild(new QueueReference(rootUrl + "queue/item/27287"), jenkinsInternalConfig);
    assertThat(build.getNumber()).isEqualTo(21);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldRetryOnFailures() throws IOException, URISyntaxException {
    JenkinsInternalConfig jenkinsInternalConfigTest =
        JenkinsInternalConfig.builder().jenkinsUrl(rootUrl).username(USERNAME).password(PASSWORD.toCharArray()).build();
    JenkinsCustomServer jenkinsServer = mock(JenkinsCustomServer.class);
    mockStatic(JenkinsClient.class);
    PowerMockito.when(JenkinsClient.getJenkinsServer(any())).thenReturn(jenkinsServer);

    // Tests for GetJobWithDetails
    JobWithDetails jobWithDetails = new JobWithDetails();
    when(jenkinsServer.getJob(any(), any()))
        .thenThrow(new HttpResponseException(500, "Something went wrong"))
        .thenThrow(new HttpResponseException(400, "Server Error"))
        .thenReturn(jobWithDetails);
    JobWithDetails actual = jenkinsRegistryUtils.getJobWithDetails(any(), "randomJob");
    assertThat(actual).isEqualTo(jobWithDetails);
    verify(jenkinsServer, times(3)).getJob(any(), eq("randomJob"));

    // Tests for GetJob
    Job job = new Job();
    when(jenkinsServer.createJob(any(), eq("randomJob"), any(JenkinsInternalConfig.class)))
        .thenThrow(new HttpResponseException(500, "Something went wrong"))
        .thenThrow(new HttpResponseException(400, "Server Error"))
        .thenReturn(job);
    Job actualJob = jenkinsRegistryUtils.getJob("randomJob", jenkinsInternalConfigTest);
    assertThat(actualJob).isEqualTo(job);
    verify(jenkinsServer, times(3)).createJob(any(), eq("randomJob"), any(JenkinsInternalConfig.class));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldGetAllJobsFromJenkins() throws IOException {
    wireMockRule.stubFor(
        get(urlEqualTo("/api/json"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"_class\":\"com.cloudbees.hudson.plugins.folder.Folder\",\"property\":[{},{\"_class\":\"hudson.plugins.jobConfigHistory.JobConfigHistoryProjectAction\"},{},{\"_class\":\"com.cloudbees.plugins.credentials.ViewCredentialsAction\"}],\"description\":null,\"displayName\":\"parentJob\",\"displayNameOrNull\":null,\"fullDisplayName\":\"parentJob\",\"fullName\":\"parentJob\",\"name\":\"parentJob\",\"url\":\"https://jenkins.wings.software/job/\",\"healthReport\":[],\"jobs\":[{\"_class\":\"hudson.maven.MavenModuleSet\",\"name\":\"abcd\",\"url\":\"https://jenkins.wings.software/job/parentJob/job/abcd/\"},{\"_class\":\"hudson.maven.MavenModuleSet\",\"name\":\"parentJob_war_copy\",\"url\":\"https://jenkins.wings.software/job/parentJob/job/parentJob_war_copy/\",\"color\":\"notbuilt\"}],\"primaryView\":{\"_class\":\"hudson.model.AllView\",\"name\":\"All\",\"url\":\"https://jenkins.wings.software/job/parentJob/\"},\"views\":[{\"_class\":\"hudson.model.AllView\",\"name\":\"All\",\"url\":\"https://jenkins.wings.software/job/parentJob/\"}]}")
                    .withHeader("Content-Type", "application/json")));

    List<JobDetails> jobs = jenkinsRegistryUtils.getJobs(jenkinsInternalConfig, "");
    assertThat(jobs.size() == 2).isTrue();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void triggerThrowErrorJobNotFound() throws IOException {
    CustomJenkinsServer jenkinsServer = mock(CustomJenkinsServer.class);
    when(jenkinsServer.createJob(any(FolderJob.class), eq("randomJob"), any(JenkinsConfig.class))).thenReturn(null);
    assertThatThrownBy(
        () -> jenkinsRegistryUtils.trigger("randomJob", jenkinsInternalConfig, ImmutableMap.of("Test", "Test")))
        .isInstanceOf(ArtifactServerException.class);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetJobsReturnsEmptyArrayWhenException() throws IOException {
    JenkinsCustomServer jenkinsServer = mock(JenkinsCustomServer.class);
    when(jenkinsServer.getJobs()).thenThrow(new RuntimeException());
    assertThat(jenkinsRegistryUtils.getJobs(jenkinsInternalConfig, "randomJob")).isEmpty();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testIsRunning() throws IOException, URISyntaxException {
    CustomJenkinsHttpClient jenkinsHttpClient = mock(CustomJenkinsHttpClient.class);
    mockStatic(JenkinsClient.class);
    when(JenkinsClient.getJenkinsHttpClient(any())).thenReturn(jenkinsHttpClient);
    assertThat(jenkinsRegistryUtils.isRunning(jenkinsInternalConfig)).isTrue();

    when(jenkinsHttpClient.get(eq("/"))).thenThrow(new HttpResponseException(401, "Unauthorized"));
    assertThatThrownBy(() -> jenkinsRegistryUtils.isRunning(jenkinsInternalConfig))
        .isInstanceOf(HintException.class)
        .extracting("message")
        .isEqualTo("Invalid Jenkins credentials");

    when(jenkinsHttpClient.get(eq("/"))).thenThrow(new HttpResponseException(403, "Forbidden"));
    assertThatThrownBy(() -> jenkinsRegistryUtils.isRunning(jenkinsInternalConfig))
        .isInstanceOf(HintException.class)
        .extracting("message")
        .isEqualTo("User not authorized to access jenkins");

    when(jenkinsHttpClient.get(eq("/"))).thenThrow(new SocketTimeoutException());
    assertThatThrownBy(() -> jenkinsRegistryUtils.isRunning(jenkinsInternalConfig))
        .isInstanceOf(ArtifactServerException.class)
        .extracting("message")
        .isEqualTo("SocketTimeoutException");

    // 4 this is the sum of all the tests.
    verify(jenkinsHttpClient, times(4)).get(eq("/"));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void checkGetEnvVarsReturnsEnvMapCorrectly() throws IOException, URISyntaxException {
    // In case of blank we get empty response
    assertThat(jenkinsRegistryUtils.getEnvVars("     ", jenkinsInternalConfig)).isEmpty();

    CustomJenkinsHttpClient jenkinsHttpClient = mock(CustomJenkinsHttpClient.class);
    mockStatic(JenkinsClient.class);
    when(JenkinsClient.getJenkinsHttpClient(any())).thenReturn(jenkinsHttpClient);

    Map<String, String> apiResponseEnvMap = ImmutableMap.<String, String>builder()
                                                .put("JOB_NAME", "test")
                                                .put("JENKINS_VERSION", "2.150.1")
                                                .put("key1.xyz", "shouldIgnore")
                                                .put("key2.abc", "ignored")
                                                .put("dot.com", "notConsider")
                                                .build();
    when(jenkinsHttpClient.get("job/test/2/injectedEnvVars/api/json"))
        .thenThrow(new HttpResponseException(500, "Something went wrong"))
        .thenThrow(new HttpResponseException(400, "Some Server Error"))
        .thenReturn(JsonUtils
                        .toJsonNode(ImmutableMap.<String, Object>builder()
                                        .put("_class", "org.jenkinsci.plugins.envinject.EnvInjectVarList")
                                        .put("envMap", apiResponseEnvMap)
                                        .build())
                        .toString());

    Map<String, String> envMap = jenkinsRegistryUtils.getEnvVars("job/test/2", jenkinsInternalConfig);

    assertThat(envMap).hasSize(2).isEqualTo(
        ImmutableMap.<String, String>builder().put("JOB_NAME", "test").put("JENKINS_VERSION", "2.150.1").build());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void checkGetEnvVarsReturnsEnvMapThrowsError() throws IOException, URISyntaxException {
    CustomJenkinsHttpClient jenkinsHttpClient = mock(CustomJenkinsHttpClient.class);
    mockStatic(JenkinsClient.class);
    when(JenkinsClient.getJenkinsHttpClient(any())).thenReturn(jenkinsHttpClient);

    when(jenkinsHttpClient.get("job/test/2/injectedEnvVars/api/json"))
        .thenThrow(new HttpResponseException(401, "Unauthorized"));

    assertThatThrownBy(() -> jenkinsRegistryUtils.getEnvVars("job/test/2", jenkinsInternalConfig))
        .isInstanceOf(HintException.class)
        .extracting("message")
        .isEqualTo("Failure in fetching environment variables for job ");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void constructJobPathDetails() throws URISyntaxException {
    JenkinsImpl jenkinsImpl = new JenkinsImpl("");

    // default case when called by delegate
    JenkinsImpl.JobPathDetails jobPathDetails = jenkinsImpl.constructJobPathDetails("project/release/new%2Ftest");
    assertThat(jobPathDetails.getParentJobUrl()).isEqualTo("/job/project/job/release/");
    assertThat(jobPathDetails.getParentJobName()).isEqualTo("release");
    assertThat(jobPathDetails.getChildJobName()).isEqualTo("new%2Ftest");

    // more than three paths when called by delegate
    jobPathDetails = jenkinsImpl.constructJobPathDetails("project/release/master");
    assertThat(jobPathDetails.getParentJobUrl()).isEqualTo("/job/project/job/release/");
    assertThat(jobPathDetails.getParentJobName()).isEqualTo("release");
    assertThat(jobPathDetails.getChildJobName()).isEqualTo("master");

    // default case when called by ui
    jobPathDetails = jenkinsImpl.constructJobPathDetails("project%2Frelease%2Fnew%252Ftest");
    assertThat(jobPathDetails.getParentJobUrl()).isEqualTo("/job/project/job/release/");
    assertThat(jobPathDetails.getParentJobName()).isEqualTo("release");
    assertThat(jobPathDetails.getChildJobName()).isEqualTo("new%2Ftest");

    // more than three paths when called by ui
    jobPathDetails = jenkinsImpl.constructJobPathDetails("project%2Frelease%2Fmaster");
    assertThat(jobPathDetails.getParentJobUrl()).isEqualTo("/job/project/job/release/");
    assertThat(jobPathDetails.getParentJobName()).isEqualTo("release");
    assertThat(jobPathDetails.getChildJobName()).isEqualTo("master");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void verifyGetConsoleLogs() throws IOException, URISyntaxException {
    JenkinsInternalConfig jenkinsInternalConfigTest =
        JenkinsInternalConfig.builder().jenkinsUrl(rootUrl).username(USERNAME).password(PASSWORD.toCharArray()).build();
    JenkinsCustomServer jenkinsServer = mock(JenkinsCustomServer.class);
    mockStatic(JenkinsClient.class);
    PowerMockito.when(JenkinsClient.getJenkinsServer(any())).thenReturn(jenkinsServer);
    when(jenkinsServer.getJenkinsConsoleLogs(any(), any(), any())).thenReturn("test logs");
    String result = jenkinsRegistryUtils.getJenkinsConsoleLogs(jenkinsInternalConfigTest, "test", "1234");
    assertThat(result).isEqualTo("test logs");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void verifyGetConsoleLogsIOException() throws IOException, URISyntaxException {
    JenkinsInternalConfig jenkinsInternalConfigTest =
        JenkinsInternalConfig.builder().jenkinsUrl(rootUrl).username(USERNAME).password(PASSWORD.toCharArray()).build();
    JenkinsCustomServer jenkinsServer = mock(JenkinsCustomServer.class);
    mockStatic(JenkinsClient.class);
    PowerMockito.when(JenkinsClient.getJenkinsServer(any())).thenReturn(jenkinsServer);
    when(jenkinsServer.getJenkinsConsoleLogs(any(), any(), any())).thenThrow(IOException.class);
    String consoleLogs = jenkinsRegistryUtils.getJenkinsConsoleLogs(jenkinsInternalConfigTest, "test", "1234");
    assertThat(consoleLogs).isNullOrEmpty();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void verifyGetConsoleLogsURIException() throws IOException, URISyntaxException {
    JenkinsInternalConfig jenkinsInternalConfigTest =
        JenkinsInternalConfig.builder().jenkinsUrl(rootUrl).username(USERNAME).password(PASSWORD.toCharArray()).build();
    JenkinsCustomServer jenkinsServer = mock(JenkinsCustomServer.class);
    mockStatic(JenkinsClient.class);
    PowerMockito.when(JenkinsClient.getJenkinsServer(any())).thenThrow(URISyntaxException.class);
    when(jenkinsServer.getJenkinsConsoleLogs(any(), any(), any())).thenReturn("test log");
    String consoleLogs = jenkinsRegistryUtils.getJenkinsConsoleLogs(jenkinsInternalConfigTest, "test", "1234");
    assertThat(consoleLogs).isNullOrEmpty();
  }
}
