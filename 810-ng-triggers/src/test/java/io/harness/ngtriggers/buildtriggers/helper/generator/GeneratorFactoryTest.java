/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helper.generator;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.AcrPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.ArtifactoryRegistryPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.DockerRegistryPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.EcrPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GCSHelmPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GcrPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GeneratorFactory;
import io.harness.ngtriggers.buildtriggers.helpers.generator.HttpHelmPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.PollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.S3HelmPollingItemGenerator;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class GeneratorFactoryTest extends CategoryTest {
  private BuildTriggerHelper buildTriggerHelper;
  private HttpHelmPollingItemGenerator httpHelmPollingItemGenerator;
  private S3HelmPollingItemGenerator s3HelmPollingItemGenerator;
  private GCSHelmPollingItemGenerator gcsHelmPollingItemGenerator;
  private GcrPollingItemGenerator gcrPollingItemGenerator;
  private EcrPollingItemGenerator ecrPollingItemGenerator;
  private DockerRegistryPollingItemGenerator dockerRegistryPollingItemGenerator;
  private ArtifactoryRegistryPollingItemGenerator artifactoryRegistryPollingItemGenerator;
  private GeneratorFactory generatorFactory;
  @InjectMocks private NGTriggerElementMapper ngTriggerElementMapper;
  private ClassLoader classLoader;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    buildTriggerHelper = new BuildTriggerHelper(null);
    httpHelmPollingItemGenerator = new HttpHelmPollingItemGenerator(buildTriggerHelper);
    s3HelmPollingItemGenerator = new S3HelmPollingItemGenerator(buildTriggerHelper);
    GCSHelmPollingItemGenerator gcsHelmPollingItemGenerator = new GCSHelmPollingItemGenerator(buildTriggerHelper);
    GcrPollingItemGenerator gcrPollingItemGenerator = new GcrPollingItemGenerator(buildTriggerHelper);
    EcrPollingItemGenerator ecrPollingItemGenerator = new EcrPollingItemGenerator(buildTriggerHelper);
    DockerRegistryPollingItemGenerator dockerRegistryPollingItemGenerator =
        new DockerRegistryPollingItemGenerator(buildTriggerHelper);
    AcrPollingItemGenerator acrPollingItemGenerator = new AcrPollingItemGenerator(buildTriggerHelper);
    ArtifactoryRegistryPollingItemGenerator artifactoryRegistryPollingItemGenerator =
        new ArtifactoryRegistryPollingItemGenerator(buildTriggerHelper);
    generatorFactory = new GeneratorFactory(buildTriggerHelper, httpHelmPollingItemGenerator,
        s3HelmPollingItemGenerator, gcsHelmPollingItemGenerator, gcrPollingItemGenerator, ecrPollingItemGenerator,
        dockerRegistryPollingItemGenerator, artifactoryRegistryPollingItemGenerator, acrPollingItemGenerator);
    classLoader = getClass().getClassLoader();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEcrPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    assertType("ecr_pipeline_artifact_snippet_runtime_all.yaml", "ng-trigger-artifact-ecr.yaml",
        EcrPollingItemGenerator.class);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGcrPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    assertType("gcr_pipeline_artifact_snippet_runtime_all.yaml", "ng-trigger-artifact-gcr.yaml",
        GcrPollingItemGenerator.class);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testDockerRegistryPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    assertType("dockerregistry_pipeline_artifact_snippet_runtime_all.yaml", "ng-trigger-artifact-dockerregistry.yaml",
        DockerRegistryPollingItemGenerator.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testArtifactoryPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    assertType("artifactory_pipeline_artifact_snippet_runtime_all.yaml", "ng-trigger-artifact-artifactory.yaml",
        ArtifactoryRegistryPollingItemGenerator.class);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testAcrPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    assertType("acr_pipeline_artifact_snippet_runtime_all.yaml", "ng-trigger-artifact-acr.yaml",
        AcrPollingItemGenerator.class);
  }

  private void assertType(String pipelinePath, String triggerYmlPath, Class expectedGeneratprClass) throws Exception {
    String ecr_pipeline_artifact_snippet_runtime_all =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(pipelinePath)), StandardCharsets.UTF_8);
    String ngTriggerYaml_artifact_ecr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(triggerYmlPath)), StandardCharsets.UTF_8);
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ngTriggerYaml_artifact_ecr);
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
        triggerDetails, ecr_pipeline_artifact_snippet_runtime_all);
    PollingItemGenerator pollingItemGenerator = generatorFactory.retrievePollingItemGenerator(buildTriggerOpsData);
    assertThat(pollingItemGenerator.getClass().isAssignableFrom(expectedGeneratprClass)).isTrue();
  }
}
