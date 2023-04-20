/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.helpers.ext.jenkins.model.ParametersDefinitionProperty.builder;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_PATH;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.BUILD_JOB_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

import io.harness.artifacts.jenkins.service.JenkinsRegistryUtils;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.artifacts.mappers.JenkinsRequestResponseMapper;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.jenkins.JenkinsFactory;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.jenkins.model.JobProperty;
import software.wings.helpers.ext.jenkins.model.JobWithExtendedDetails;
import software.wings.helpers.ext.jenkins.model.ParametersDefinitionProperty.DefaultParameterValue;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.service.intfc.JenkinsBuildService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.JobWithDetails;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Created by peeyushaggarwal on 5/13/16.
 */
public class JenkinsBuildServiceTest extends WingsBaseTest {
  private static final JenkinsConfig jenkinsConfig = JenkinsConfig.builder()
                                                         .jenkinsUrl("http://jenkins")
                                                         .username("username")
                                                         .password("password".toCharArray())
                                                         .accountId(ACCOUNT_ID)
                                                         .build();

  @Mock private JenkinsFactory jenkinsFactory;

  @Mock private Jenkins jenkins;

  @Mock private JenkinsRegistryUtils jenkinsRegistryUtils;

  @Inject @InjectMocks JenkinsUtils jenkinsUtil;

  @InjectMocks @Inject private JenkinsBuildService jenkinsBuildService;

  private static final JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                                         .uuid(ARTIFACT_STREAM_ID)
                                                                         .appId(APP_ID)
                                                                         .settingId("")
                                                                         .sourceName(ARTIFACT_STREAM_NAME)
                                                                         .jobname("job1")
                                                                         .artifactPaths(asList(ARTIFACT_PATH))
                                                                         .build();

  /**
   * setups all mocks for test.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Before
  public void setupMocks() throws IOException {
    when(jenkinsFactory.create(anyString(), anyString(), any(char[].class))).thenReturn(jenkins);
    when(jenkins.getBuildsForJob(eq("job1"), eq(asList(ARTIFACT_PATH)), anyInt()))
        .thenReturn(Lists.newArrayList(aBuildDetails().withNumber("67").withRevision("1bfdd117").build(),
            aBuildDetails().withNumber("65").withRevision("1bfdd117").build(),
            aBuildDetails().withNumber("64").withRevision("1bfdd117").build(),
            aBuildDetails().withNumber("63").withRevision("1bfdd117").build()));
  }

  /**
   * Should fail validation when job does not exists.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldFailValidationWhenJobDoesNotExists() throws IOException {
    jenkinsArtifactStream.setJobname("job2");
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(()
                        -> jenkinsBuildService.getBuilds(
                            APP_ID, jenkinsArtifactStream.fetchArtifactStreamAttributes(null), jenkinsConfig, null));
  }

  /**
   * Should return list of builds.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldReturnListOfBuilds() throws IOException {
    assertThat(jenkinsBuildService.getBuilds(
                   APP_ID, jenkinsArtifactStream.fetchArtifactStreamAttributes(null), jenkinsConfig, null))
        .hasSize(4)
        .extracting(BuildDetails::getNumber, BuildDetails::getRevision)
        .containsExactly(
            tuple("67", "1bfdd117"), tuple("65", "1bfdd117"), tuple("64", "1bfdd117"), tuple("63", "1bfdd117"));
  }

  /**
   * Should fetch job names.
   *
   * @throws IOException the io exception
   */
  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldFetchJobNames() throws IOException {
    when(jenkins.getJobs(any())).thenReturn(ImmutableList.of(new JobDetails("jobName", false)));
    List<JobDetails> jobs = jenkinsBuildService.getJobs(jenkinsConfig, null, Optional.empty());
    List<String> jobNames = jenkinsBuildService.extractJobNameFromJobDetails(jobs);
    assertThat(jobNames).containsExactly("jobName");
  }

  /**
   * Should fetch artifact paths.
   *
   * @throws IOException the io exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldFetchArtifactPaths() throws IOException {
    JobWithDetails jobWithDetails = Mockito.mock(JobWithDetails.class, RETURNS_DEEP_STUBS);
    Artifact artifact = new Artifact();
    artifact.setRelativePath("relativePath");
    when(jenkins.getJobWithDetails(BUILD_JOB_NAME)).thenReturn(jobWithDetails);
    when(jobWithDetails.getLastSuccessfulBuild().details().getArtifacts()).thenReturn(ImmutableList.of(artifact));
    assertThat(jenkinsBuildService.getArtifactPaths(BUILD_JOB_NAME, null, jenkinsConfig, null))
        .containsExactly("relativePath");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateInvalidUrl() {
    final String badUrl = "BAD_URL";
    JenkinsConfig badJenkinsConfig = JenkinsConfig.builder()
                                         .jenkinsUrl(badUrl)
                                         .username("username")
                                         .password("password".toCharArray())
                                         .accountId(ACCOUNT_ID)
                                         .build();
    try {
      jenkinsBuildService.validateArtifactServer(badJenkinsConfig, Collections.emptyList());
      fail("jenkinsBuildService.validateArtifactServer did not throw!!!");
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo("Could not reach Jenkins Server at :" + badUrl);
      assertThat(e.getCode()).isEqualTo(ErrorCode.HINT);
      assertThat(e.getParams()).isNotEmpty();
      assertThat(e.getParams().get("message")).isEqualTo("Could not reach Jenkins Server at :" + badUrl);
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestGetJobParameters() {
    JobWithExtendedDetails jobWithDetails = Mockito.mock(JobWithExtendedDetails.class, RETURNS_DEEP_STUBS);
    when(jenkinsRegistryUtils.getJobWithDetails(
             JenkinsRequestResponseMapper.toJenkinsInternalConfig(jenkinsConfig), BUILD_JOB_NAME))
        .thenReturn(jobWithDetails);
    JobProperty jobProperty =
        JobProperty.builder()
            .parameterDefinitions(asList(
                builder()
                    .name("branch")
                    .defaultParameterValue(DefaultParameterValue.builder().name("branch").value("release").build())
                    .build(),
                builder()
                    .name("revision")
                    .defaultParameterValue(DefaultParameterValue.builder().name("revision").build())
                    .build(),
                builder()
                    .name("Choices")
                    .defaultParameterValue(DefaultParameterValue.builder().name("B").value("B").build())
                    .choices(asList("A", "B", "C"))
                    .build(),
                builder()
                    .name("BooleanParams")
                    .type("BooleanParameterDefinition")
                    .defaultParameterValue(DefaultParameterValue.builder().name("B").value("B").build())
                    .choices(asList("A", "B", "C"))
                    .build()))
            .build();
    when(jobWithDetails.getProperties()).thenReturn(asList(jobProperty));
    JobDetails jobDetails = jenkinsBuildService.getJob(BUILD_JOB_NAME, jenkinsConfig, null);
    assertThat(jobDetails).isNotNull();
    assertThat(jobDetails.getParameters())
        .isNotNull()
        .extracting(JobDetails.JobParameter::getName)
        .contains("branch", "revision", "Choices", "BooleanParams");
    assertThat(jobDetails.getParameters())
        .extracting(JobDetails.JobParameter::getOptions)
        .contains(asList("A", "B", "C"), asList("true", "false"));

    assertThat(jobDetails.getParameters())
        .extracting(JobDetails.JobParameter::getDefaultValue)
        .contains("release", "B");
  }
}
