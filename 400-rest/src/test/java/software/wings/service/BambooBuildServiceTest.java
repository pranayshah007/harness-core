/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.service.intfc.BuildService.ARTIFACT_RETENTION_SIZE;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_PATH;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.BUILD_JOB_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.BambooConfig;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.service.intfc.BambooBuildService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by anubhaw on 12/1/16.
 */
public class BambooBuildServiceTest extends WingsBaseTest {
  @Mock private BambooService bambooService;
  @Inject @InjectMocks private BambooBuildService bambooBuildService;

  private static final BambooConfig bambooConfig =
      BambooConfig.builder().bambooUrl("http://bamboo").username("username").password("password".toCharArray()).build();
  private static final BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                                       .uuid(ARTIFACT_STREAM_ID)
                                                                       .appId(APP_ID)
                                                                       .settingId(SETTING_ID)
                                                                       .sourceName(ARTIFACT_STREAM_NAME)
                                                                       .jobname(BUILD_JOB_NAME)
                                                                       .artifactPaths(Arrays.asList(ARTIFACT_PATH))
                                                                       .build();

  @Before
  public void setUp() throws Exception {}

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetBuilds() {
    when(bambooService.getBuilds(
             bambooConfig, null, BUILD_JOB_NAME, Arrays.asList(ARTIFACT_PATH), ARTIFACT_RETENTION_SIZE))
        .thenReturn(
            Lists.newArrayList(aBuildDetails().withNumber("10").build(), aBuildDetails().withNumber("9").build()));
    List<BuildDetails> builds = bambooBuildService.getBuilds(
        APP_ID, bambooArtifactStream.fetchArtifactStreamAttributes(null), bambooConfig, null);
    assertThat(builds).hasSize(2).extracting(BuildDetails::getNumber).containsExactly("10", "9");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetPlans() {
    when(bambooService.getPlanKeys(bambooConfig, null))
        .thenReturn(ImmutableMap.of("PlanAKey", "PlanAName", "PlanBKey", "PlanBName"));
    List<JobDetails> jobs = bambooBuildService.getJobs(bambooConfig, null, Optional.empty());
    List<String> jobNames = bambooBuildService.extractJobNameFromJobDetails(jobs);
    assertThat(jobNames).hasSize(2).containsExactlyInAnyOrder("PlanAKey", "PlanBKey");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetArtifactPaths() {
    List<String> artifactPaths = bambooBuildService.getArtifactPaths(BUILD_JOB_NAME, null, bambooConfig, null);
    assertThat(artifactPaths.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldGetLastSuccessfulBuild() {
    when(bambooService.getLastSuccessfulBuild(bambooConfig, null, BUILD_JOB_NAME, Arrays.asList(ARTIFACT_PATH)))
        .thenReturn(aBuildDetails().withNumber("10").build());
    BuildDetails lastSuccessfulBuild = bambooBuildService.getLastSuccessfulBuild(
        APP_ID, bambooArtifactStream.fetchArtifactStreamAttributes(null), bambooConfig, null);
    assertThat(lastSuccessfulBuild.getNumber()).isEqualTo("10");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateInvalidUrl() {
    BambooConfig bambooConfig =
        BambooConfig.builder().bambooUrl("BAD_URL").username("username").password("password".toCharArray()).build();
    try {
      bambooBuildService.validateArtifactServer(bambooConfig, Collections.emptyList());
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_ARTIFACT_SERVER.toString());
      assertThat(e.getParams()).isNotEmpty();
      assertThat(e.getParams().get("message")).isEqualTo("Could not reach Bamboo Server at : BAD_URL");
    }
  }
}
