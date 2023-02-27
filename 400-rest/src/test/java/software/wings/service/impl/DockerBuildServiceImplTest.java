/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;

import software.wings.WingsBaseTest;
import software.wings.beans.DockerConfig;
import software.wings.rules.Integration;
import software.wings.service.intfc.DockerBuildService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

/**
 * Created by anubhaw on 1/6/17.
 */
@Integration
@Slf4j
public class DockerBuildServiceImplTest extends WingsBaseTest {
  private static final String DOCKER_REGISTRY_URL = "https://registry.hub.docker.com/v2/";

  @Inject private DockerRegistryService dockerRegistryService;
  @Inject private ScmSecret scmSecret;

  @Inject @InjectMocks private DockerBuildService dockerBuildService;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldGetBuildsWithoutCredentials() {
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl(DOCKER_REGISTRY_URL).build();
    List<BuildDetailsInternal> builds = dockerRegistryService.getBuilds(dockerInternalConfig, "library/mysql", 5, null);
    log.info(builds.toString());
    assertThat(builds.size()).isEqualTo(5);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldGetBuildsWithCredentials() {
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder()
            .dockerRegistryUrl(DOCKER_REGISTRY_URL)
            .username("anubhaw")
            .password(scmSecret.decryptToString(new SecretName("docker_config_anubhaw_password")))
            .build();
    List<BuildDetailsInternal> builds = dockerRegistryService.getBuilds(dockerInternalConfig, "library/mysql", 5, null);
    log.info(builds.toString());
    assertThat(builds.size()).isGreaterThanOrEqualTo(5);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldGetLastSuccessfulBuild() {
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder()
            .dockerRegistryUrl(DOCKER_REGISTRY_URL)
            .username("anubhaw")
            .password(scmSecret.decryptToString(new SecretName("docker_config_anubhaw_password")))
            .build();
    BuildDetailsInternal build = dockerRegistryService.getLastSuccessfulBuild(dockerInternalConfig, "library/mysql");
    log.info(build.toString());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldValidateInvalidUrl() {
    DockerConfig dockerConfig =
        DockerConfig.builder()
            .dockerRegistryUrl("invalid_url")
            .username("anubhaw")
            .password(scmSecret.decryptToCharArray(new SecretName("docker_config_anubhaw_password")))
            .build();
    try {
      dockerBuildService.validateArtifactServer(dockerConfig, Collections.emptyList());
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_ARTIFACT_SERVER.toString());
      assertThat(e.getParams()).isNotEmpty();
      assertThat(e.getParams().get("message")).isEqualTo("Docker Registry URL must be a valid URL");
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldValidateCredentials() {
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder()
            .dockerRegistryUrl(DOCKER_REGISTRY_URL)
            .username("invalid")
            .password(scmSecret.decryptToString(new SecretName("docker_config_anubhaw_password")))
            .build();
    try {
      dockerRegistryService.validateCredentials(dockerInternalConfig);
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_ARTIFACT_SERVER.toString());
      assertThat(e.getParams()).isNotEmpty();
      assertThat(e.getParams().get("message")).isEqualTo("Invalid Docker Registry credentials");
    }
  }
}
