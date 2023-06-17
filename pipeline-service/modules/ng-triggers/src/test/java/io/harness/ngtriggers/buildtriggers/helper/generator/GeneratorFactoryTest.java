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
import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.AMIPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.AcrPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.ArtifactoryRegistryPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.AzureArtifactsPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.BambooPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.CustomPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.DockerRegistryPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.EcrPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GARPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GCSHelmPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GcrPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GeneratorFactory;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GitPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GithubPackagesPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GoogleCloudStoragePollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.HttpHelmPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.JenkinsPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.Nexus2RegistryPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.Nexus3PollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.PollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.S3HelmPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.S3PollingItemGenerator;
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
  private S3PollingItemGenerator s3PollingItemGenerator;
  private GCSHelmPollingItemGenerator gcsHelmPollingItemGenerator;
  private GcrPollingItemGenerator gcrPollingItemGenerator;
  private EcrPollingItemGenerator ecrPollingItemGenerator;
  private DockerRegistryPollingItemGenerator dockerRegistryPollingItemGenerator;
  private ArtifactoryRegistryPollingItemGenerator artifactoryRegistryPollingItemGenerator;
  private JenkinsPollingItemGenerator jenkinsPollingItemGenerator;
  private BambooPollingItemGenerator bambooPollingItemGenerator;
  private AMIPollingItemGenerator amiPollingItemGenerator;
  private GitPollingItemGenerator gitPollingItemGenerator;
  private GoogleCloudStoragePollingItemGenerator googleCloudStoragePollingItemGenerator;

  private GeneratorFactory generatorFactory;
  @InjectMocks private NGTriggerElementMapper ngTriggerElementMapper;
  private ClassLoader classLoader;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    buildTriggerHelper = new BuildTriggerHelper(null);
    httpHelmPollingItemGenerator = new HttpHelmPollingItemGenerator(buildTriggerHelper);
    s3HelmPollingItemGenerator = new S3HelmPollingItemGenerator(buildTriggerHelper);
    S3PollingItemGenerator s3PollingItemGenerator = new S3PollingItemGenerator(buildTriggerHelper);
    GCSHelmPollingItemGenerator gcsHelmPollingItemGenerator = new GCSHelmPollingItemGenerator(buildTriggerHelper);
    GcrPollingItemGenerator gcrPollingItemGenerator = new GcrPollingItemGenerator(buildTriggerHelper);
    EcrPollingItemGenerator ecrPollingItemGenerator = new EcrPollingItemGenerator(buildTriggerHelper);
    DockerRegistryPollingItemGenerator dockerRegistryPollingItemGenerator =
        new DockerRegistryPollingItemGenerator(buildTriggerHelper);
    GithubPackagesPollingItemGenerator githubPackagesPollingItemGenerator =
        new GithubPackagesPollingItemGenerator(buildTriggerHelper);
    JenkinsPollingItemGenerator jenkinsPollingItemGenerator = new JenkinsPollingItemGenerator(buildTriggerHelper);
    BambooPollingItemGenerator bambooPollingItemGenerator = new BambooPollingItemGenerator(buildTriggerHelper);
    AcrPollingItemGenerator acrPollingItemGenerator = new AcrPollingItemGenerator(buildTriggerHelper);
    CustomPollingItemGenerator customPollingItemGenerator = new CustomPollingItemGenerator(buildTriggerHelper);
    ArtifactoryRegistryPollingItemGenerator artifactoryRegistryPollingItemGenerator =
        new ArtifactoryRegistryPollingItemGenerator(buildTriggerHelper);
    GARPollingItemGenerator garPollingItemGenerator = new GARPollingItemGenerator(buildTriggerHelper);
    Nexus2RegistryPollingItemGenerator nexus2RegistryPollingItemGenerator =
        new Nexus2RegistryPollingItemGenerator(buildTriggerHelper);
    Nexus3PollingItemGenerator nexus3PollingItemGenerator = new Nexus3PollingItemGenerator(buildTriggerHelper);
    AzureArtifactsPollingItemGenerator azureArtifactsPollingItemGenerator =
        new AzureArtifactsPollingItemGenerator(buildTriggerHelper);
    AMIPollingItemGenerator amiPollingItemGenerator = new AMIPollingItemGenerator(buildTriggerHelper);
    generatorFactory = new GeneratorFactory(buildTriggerHelper, httpHelmPollingItemGenerator,
        s3HelmPollingItemGenerator, s3PollingItemGenerator, gcsHelmPollingItemGenerator, gcrPollingItemGenerator,
        ecrPollingItemGenerator, dockerRegistryPollingItemGenerator, artifactoryRegistryPollingItemGenerator,
        acrPollingItemGenerator, jenkinsPollingItemGenerator, gitPollingItemGenerator, customPollingItemGenerator,
        garPollingItemGenerator, githubPackagesPollingItemGenerator, nexus2RegistryPollingItemGenerator,
        nexus3PollingItemGenerator, azureArtifactsPollingItemGenerator, amiPollingItemGenerator,
        googleCloudStoragePollingItemGenerator, bambooPollingItemGenerator);

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

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGCRPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    assertType("gcr_pipeline_artifact_snippet_runtime_all.yaml", "ng-trigger-artifact-gcr.yaml",
        GcrPollingItemGenerator.class);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testS3PollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    assertType(
        "s3_pipeline_artifact_snippet_runtime_all.yaml", "ng-trigger-artifact-s3.yaml", S3PollingItemGenerator.class);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testAMIPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    assertType("ami_pipeline_artifact_snippet_runtime_all.yaml", "ng-trigger-artifact-ami.yaml",
        AMIPollingItemGenerator.class);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testAzureIPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    assertType("azure_pipeline_artifact_snippet_runtime_all.yaml", "ng-trigger-artifact-azure.yaml",
        AzureArtifactsPollingItemGenerator.class);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGithubIPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    assertType("github_pipeline_artifact_snippet_runtime_all.yaml", "ng-trigger-artifact-github.yaml",
        GithubPackagesPollingItemGenerator.class);
  }

  private void assertType(String pipelinePath, String triggerYmlPath, Class expectedGeneratprClass) throws Exception {
    String ecr_pipeline_artifact_snippet_runtime_all =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(pipelinePath)), StandardCharsets.UTF_8);
    String ngTriggerYaml_artifact_ecr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(triggerYmlPath)), StandardCharsets.UTF_8);
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ngTriggerYaml_artifact_ecr, false);
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
        triggerDetails, ecr_pipeline_artifact_snippet_runtime_all);
    PollingItemGenerator pollingItemGenerator = generatorFactory.retrievePollingItemGenerator(buildTriggerOpsData);
    assertThat(pollingItemGenerator.getClass().isAssignableFrom(expectedGeneratprClass)).isTrue();
  }
}