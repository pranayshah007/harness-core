/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.ngpipeline.inputset.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.GitDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetResponseBody;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class InputSetsApiUtilsTest extends CategoryTest {
  private InputSetsApiUtils inputSetsApiUtils;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private NGSettingsClient ngSettingsClient;
  String identifier = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  String account = "accountId";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    inputSetsApiUtils = new InputSetsApiUtils(pmsFeatureFlagHelper, ngSettingsClient);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read file " + filename, e);
    }
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetGitDetails() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .objectIdOfYaml("objectId")
                                        .branch("branch")
                                        .repo("repoName")
                                        .repoURL("repoURL")
                                        .filePath("filePath")
                                        .build();
    GitDetails gitDetails = inputSetsApiUtils.getGitDetails(inputSetEntity);
    assertEquals("objectId", gitDetails.getObjectId());
    assertEquals("branch", gitDetails.getBranchName());
    assertEquals("filePath", gitDetails.getFilePath());
    assertEquals("repoURL", gitDetails.getRepoUrl());
    assertEquals("repoName", gitDetails.getRepoName());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetResponseBody() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .yaml("yaml")
                                        .identifier(identifier)
                                        .name(name)
                                        .createdAt(123456L)
                                        .lastUpdatedAt(987654L)
                                        .build();
    InputSetResponseBody responseBody = inputSetsApiUtils.getInputSetResponse(inputSetEntity);
    assertEquals("yaml", responseBody.getInputSetYaml());
    assertEquals(identifier, responseBody.getIdentifier());
    assertEquals(name, responseBody.getName());
    assertEquals(123456L, responseBody.getCreated().longValue());
    assertEquals(987654L, responseBody.getUpdated().longValue());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetVersion() {
    String yaml = readFile("inputSetV1.yaml");
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CI_YAML_VERSIONING)).thenReturn(true);
    String inputSetVersion = inputSetsApiUtils.inputSetVersion(account, yaml);
    assertThat(inputSetVersion).isEqualTo(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetVersionFeatureDisabled() {
    String yaml = readFile("inputSetV1.yaml");
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CI_YAML_VERSIONING)).thenReturn(false);
    String inputSetVersion = inputSetsApiUtils.inputSetVersion(account, yaml);
    assertThat(inputSetVersion).isEqualTo(HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputSetVersionOnV0Yaml() {
    String yaml = readFile("inputSet1.yml");
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CI_YAML_VERSIONING)).thenReturn(true);
    String inputSetVersion = inputSetsApiUtils.inputSetVersion(account, yaml);
    assertThat(inputSetVersion).isEqualTo(HarnessYamlVersion.V0);
  }
}
