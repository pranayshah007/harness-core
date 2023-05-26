/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.stateutils.buildstate;

import static io.harness.ci.buildstate.PluginSettingUtils.TAG_BUILD_EVENT;
import static io.harness.ci.buildstate.PluginSettingUtils.getRepoNameFromRepoUrl;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_SHA;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_MACHINE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REMOTE_URL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TAG;
import static io.harness.ci.commonconstants.CIExecutionConstants.DOCKER_REGISTRY_V1;
import static io.harness.ci.commonconstants.CIExecutionConstants.DOCKER_REGISTRY_V2;
import static io.harness.ci.commonconstants.CIExecutionConstants.DRONE_WORKSPACE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_SSL_NO_VERIFY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PATH_SEPARATOR;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.JAMES_RICKS;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;
import static io.harness.yaml.extended.ci.codebase.Build.builder;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.stepinfo.ACRStepInfo;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.beans.steps.stepinfo.GCRStepInfo;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.steps.stepinfo.RestoreCacheGCSStepInfo;
import io.harness.beans.steps.stepinfo.RestoreCacheS3StepInfo;
import io.harness.beans.steps.stepinfo.SaveCacheGCSStepInfo;
import io.harness.beans.steps.stepinfo.SaveCacheS3StepInfo;
import io.harness.beans.steps.stepinfo.UploadToArtifactoryStepInfo;
import io.harness.beans.steps.stepinfo.UploadToGCSStepInfo;
import io.harness.beans.steps.stepinfo.UploadToS3StepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.yaml.extended.ArchiveFormat;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.CodebaseUtils;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.attestation.verify.CosignVerifyAttestation;
import io.harness.ssca.beans.attestation.verify.VerifyAttestation;
import io.harness.ssca.beans.policy.EnforcementPolicy;
import io.harness.ssca.beans.source.ImageSbomSource;
import io.harness.ssca.beans.source.SbomSource;
import io.harness.ssca.beans.source.SbomSourceType;
import io.harness.ssca.beans.stepinfo.SscaEnforcementStepInfo;
import io.harness.ssca.beans.store.HarnessStore;
import io.harness.ssca.beans.store.PolicyStore;
import io.harness.ssca.beans.store.StoreType;
import io.harness.ssca.execution.SscaOrchestrationPluginUtils;
import io.harness.utils.CiCodebaseUtils;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildSpec;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class PluginSettingUtilsTest extends CIExecutionTestBase {
  @Inject public PluginSettingUtils pluginSettingUtils;

  @Mock private CodebaseUtils codebaseUtils;
  @Mock private CiCodebaseUtils ciCodebaseUtils;

  @Mock private ConnectorUtils connectorUtils;
  @Mock private SscaOrchestrationPluginUtils sscaOrchestrationPluginUtils;

  @Before
  public void setUp() {
    on(pluginSettingUtils).set("codebaseUtils", codebaseUtils);
    on(pluginSettingUtils).set("connectorUtils", connectorUtils);
    on(pluginSettingUtils).set("ciCodebaseUtils", ciCodebaseUtils);
    on(pluginSettingUtils).set("sscaOrchestrationPluginUtils", sscaOrchestrationPluginUtils);
    on(codebaseUtils).set("connectorUtils", connectorUtils);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetUploadToArtifactoryStepInfoStepEnvVariables() {
    UploadToArtifactoryStepInfo uploadToArtifactoryStepInfo =
        UploadToArtifactoryStepInfo.builder()
            .target(ParameterField.createValueField("repo/wings/software/module/1.0.0-SNAPSHOT"))
            .sourcePath(ParameterField.createValueField("target/libmodule.jar"))
            .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_TARGET", "repo/wings/software/module/1.0.0-SNAPSHOT");
    expected.put("PLUGIN_SOURCE", "target/libmodule.jar");
    expected.put("PLUGIN_ARTIFACT_FILE", "/addon/tmp/.plugin/artifact");
    expected.put("PLUGIN_FLAT", "true");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        uploadToArtifactoryStepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetGCRStepInfoEnvVariables() {
    GCRStepInfo gcrStepInfo =
        GCRStepInfo.builder()
            .host(ParameterField.createValueField("gcr.io/"))
            .projectID(ParameterField.createValueField("/ci"))
            .imageName(ParameterField.createValueField("harness"))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_REGISTRY", "gcr.io/ci");
    expected.put("PLUGIN_REPO", "harness");
    expected.put("PLUGIN_TAGS", "tag1,tag2");
    expected.put("PLUGIN_DOCKERFILE", "Dockerfile");
    expected.put("PLUGIN_CONTEXT", "context");
    expected.put("PLUGIN_TARGET", "target");
    expected.put("PLUGIN_BUILD_ARGS", "arg1=value1");
    expected.put("PLUGIN_CUSTOM_LABELS", "label=label1");
    expected.put("PLUGIN_SNAPSHOT_MODE", "redo");
    expected.put("PLUGIN_ARTIFACT_FILE", "/addon/tmp/.plugin/artifact");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        gcrStepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetACRStepInfoEnvVariables() {
    ACRStepInfo acrStepInfo =
        ACRStepInfo.builder()
            .repository(ParameterField.createValueField("repo.acr.com/test1"))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_REPO", "repo.acr.com/test1");
    expected.put("PLUGIN_REGISTRY", "repo.acr.com");
    expected.put("PLUGIN_TAGS", "tag1,tag2");
    expected.put("PLUGIN_DOCKERFILE", "Dockerfile");
    expected.put("PLUGIN_CONTEXT", "context");
    expected.put("PLUGIN_TARGET", "target");
    expected.put("PLUGIN_BUILD_ARGS", "arg1=value1");
    expected.put("PLUGIN_CUSTOM_LABELS", "label=label1");
    expected.put("PLUGIN_SNAPSHOT_MODE", "redo");
    expected.put("PLUGIN_ARTIFACT_FILE", "/addon/tmp/.plugin/artifact");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        acrStepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetECRStepInfoStepEnvVariables() {
    ECRStepInfo ecrStepInfo =
        ECRStepInfo.builder()
            .account(ParameterField.createValueField("6874654867"))
            .region(ParameterField.createValueField("eu-central-1"))
            .imageName(ParameterField.createValueField("harness"))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .baseImageConnectorRefs(ParameterField.createValueField(Collections.singletonList("docker")))
            .build();
    String dockerUrl = "dockerUrl";
    when(connectorUtils.getConnectorDetails(any(), eq("docker")))
        .thenReturn(ConnectorDetails.builder()
                        .connectorType(ConnectorType.DOCKER)
                        .connectorConfig(DockerConnectorDTO.builder().dockerRegistryUrl(dockerUrl).build())
                        .build());

    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_REGISTRY", "6874654867.dkr.ecr.eu-central-1.amazonaws.com");
    expected.put("PLUGIN_REGION", "eu-central-1");
    expected.put("PLUGIN_REPO", "harness");
    expected.put("PLUGIN_TAGS", "tag1,tag2");
    expected.put("PLUGIN_DOCKERFILE", "Dockerfile");
    expected.put("PLUGIN_CONTEXT", "context");
    expected.put("PLUGIN_TARGET", "target");
    expected.put("PLUGIN_BUILD_ARGS", "arg1=value1");
    expected.put("PLUGIN_CUSTOM_LABELS", "label=label1");
    expected.put("PLUGIN_SNAPSHOT_MODE", "redo");
    expected.put("PLUGIN_ARTIFACT_FILE", "/addon/tmp/.plugin/artifact");
    expected.put("PLUGIN_DOCKER_REGISTRY", dockerUrl);
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        ecrStepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);

    when(connectorUtils.getConnectorDetails(any(), eq("docker")))
        .thenReturn(ConnectorDetails.builder()
                        .connectorType(ConnectorType.DOCKER)
                        .connectorConfig(DockerConnectorDTO.builder().dockerRegistryUrl(DOCKER_REGISTRY_V2).build())
                        .build());
    actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        ecrStepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).containsKey("PLUGIN_DOCKER_REGISTRY");
    assertThat(actual.get("PLUGIN_DOCKER_REGISTRY")).isEqualTo(DOCKER_REGISTRY_V1);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetDockerStepInfoEnvVariables() {
    DockerStepInfo dockerStepInfo =
        DockerStepInfo.builder()
            .repo(ParameterField.createValueField("harness"))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_REPO", "harness");
    expected.put("PLUGIN_TAGS", "tag1,tag2");
    expected.put("PLUGIN_DOCKERFILE", "Dockerfile");
    expected.put("PLUGIN_CONTEXT", "context");
    expected.put("PLUGIN_TARGET", "target");
    expected.put("PLUGIN_BUILD_ARGS", "arg1=value1");
    expected.put("PLUGIN_CUSTOM_LABELS", "label=label1");
    expected.put("PLUGIN_SNAPSHOT_MODE", "redo");
    expected.put("PLUGIN_ARTIFACT_FILE", "/addon/tmp/.plugin/artifact");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        dockerStepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetRestoreCacheS3StepInfoEnvVariables() {
    RestoreCacheS3StepInfo restoreCacheS3StepInfo = RestoreCacheS3StepInfo.builder()
                                                        .key(ParameterField.createValueField("key"))
                                                        .bucket(ParameterField.createValueField("bucket"))
                                                        .endpoint(ParameterField.createValueField("endpoint"))
                                                        .region(ParameterField.createValueField("region"))
                                                        .build();
    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_BACKEND", "s3");
    expected.put("PLUGIN_BUCKET", "bucket");
    expected.put("PLUGIN_ENDPOINT", "endpoint");
    expected.put("PLUGIN_CACHE_KEY", "key");
    expected.put("PLUGIN_RESTORE", "true");
    expected.put("PLUGIN_EXIT_CODE", "true");
    expected.put("PLUGIN_REGION", "region");
    expected.put("PLUGIN_ARCHIVE_FORMAT", "tar");
    expected.put("PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT", "false");
    expected.put("PLUGIN_PATH_STYLE", "false");
    expected.put("PLUGIN_BACKEND_OPERATION_TIMEOUT", "100s");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        restoreCacheS3StepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetRestoreCacheS3StepInfoEnvVariablesArchiveSet() {
    RestoreCacheS3StepInfo restoreCacheS3StepInfo =
        RestoreCacheS3StepInfo.builder()
            .key(ParameterField.createValueField("key"))
            .bucket(ParameterField.createValueField("bucket"))
            .endpoint(ParameterField.createValueField("endpoint"))
            .region(ParameterField.createValueField("region"))
            .archiveFormat(ParameterField.createValueField(ArchiveFormat.GZIP))
            .failIfKeyNotFound(ParameterField.createValueField(true))
            .build();
    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_BACKEND", "s3");
    expected.put("PLUGIN_BUCKET", "bucket");
    expected.put("PLUGIN_ENDPOINT", "endpoint");
    expected.put("PLUGIN_CACHE_KEY", "key");
    expected.put("PLUGIN_RESTORE", "true");
    expected.put("PLUGIN_EXIT_CODE", "true");
    expected.put("PLUGIN_REGION", "region");
    expected.put("PLUGIN_ARCHIVE_FORMAT", "gzip");
    expected.put("PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT", "true");
    expected.put("PLUGIN_PATH_STYLE", "false");
    expected.put("PLUGIN_BACKEND_OPERATION_TIMEOUT", "100s");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        restoreCacheS3StepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetSaveCacheS3StepInfoEnvVariablesBasic() {
    SaveCacheS3StepInfo saveCacheS3StepInfo =
        SaveCacheS3StepInfo.builder()
            .key(ParameterField.createValueField("key"))
            .bucket(ParameterField.createValueField("bucket"))
            .region(ParameterField.createValueField("region"))
            .sourcePaths(ParameterField.createValueField(asList("path1", "path2")))
            .endpoint(ParameterField.createValueField("endpoint"))
            .build();
    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_BACKEND", "s3");
    expected.put("PLUGIN_MOUNT", "path1,path2");
    expected.put("PLUGIN_BUCKET", "bucket");
    expected.put("PLUGIN_ENDPOINT", "endpoint");
    expected.put("PLUGIN_CACHE_KEY", "key");
    expected.put("PLUGIN_REBUILD", "true");
    expected.put("PLUGIN_EXIT_CODE", "true");
    expected.put("PLUGIN_REGION", "region");
    expected.put("PLUGIN_PATH_STYLE", "false");
    expected.put("PLUGIN_ARCHIVE_FORMAT", "tar");
    expected.put("PLUGIN_BACKEND_OPERATION_TIMEOUT", "100s");
    expected.put("PLUGIN_OVERRIDE", "false");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        saveCacheS3StepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetSaveCacheS3StepInfoEnvVariablesArchiveSet() {
    SaveCacheS3StepInfo saveCacheS3StepInfo =
        SaveCacheS3StepInfo.builder()
            .key(ParameterField.createValueField("key"))
            .bucket(ParameterField.createValueField("bucket"))
            .region(ParameterField.createValueField("region"))
            .sourcePaths(ParameterField.createValueField(asList("path1", "path2")))
            .endpoint(ParameterField.createValueField("endpoint"))
            .archiveFormat(ParameterField.createValueField(ArchiveFormat.GZIP))
            .override(ParameterField.createValueField(true))
            .build();
    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_BACKEND", "s3");
    expected.put("PLUGIN_MOUNT", "path1,path2");
    expected.put("PLUGIN_BUCKET", "bucket");
    expected.put("PLUGIN_ENDPOINT", "endpoint");
    expected.put("PLUGIN_CACHE_KEY", "key");
    expected.put("PLUGIN_REBUILD", "true");
    expected.put("PLUGIN_EXIT_CODE", "true");
    expected.put("PLUGIN_REGION", "region");
    expected.put("PLUGIN_PATH_STYLE", "false");
    expected.put("PLUGIN_ARCHIVE_FORMAT", "gzip");
    expected.put("PLUGIN_BACKEND_OPERATION_TIMEOUT", "100s");
    expected.put("PLUGIN_OVERRIDE", "true");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        saveCacheS3StepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetRestoreCacheGCSStepInfoEnvVariables() {
    RestoreCacheGCSStepInfo restoreCacheGCSStepInfo = RestoreCacheGCSStepInfo.builder()
                                                          .key(ParameterField.createValueField("key"))
                                                          .bucket(ParameterField.createValueField("bucket"))
                                                          .build();
    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_BACKEND", "gcs");
    expected.put("PLUGIN_BUCKET", "bucket");
    expected.put("PLUGIN_CACHE_KEY", "key");
    expected.put("PLUGIN_RESTORE", "true");
    expected.put("PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT", "false");
    expected.put("PLUGIN_EXIT_CODE", "true");
    expected.put("PLUGIN_ARCHIVE_FORMAT", "tar");
    expected.put("PLUGIN_BACKEND_OPERATION_TIMEOUT", "100s");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        restoreCacheGCSStepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetRestoreCacheGCSStepInfoEnvVariablesArchiveSet() {
    RestoreCacheGCSStepInfo restoreCacheGCSStepInfo =
        RestoreCacheGCSStepInfo.builder()
            .key(ParameterField.createValueField("key"))
            .bucket(ParameterField.createValueField("bucket"))
            .archiveFormat(ParameterField.createValueField(ArchiveFormat.GZIP))
            .failIfKeyNotFound(ParameterField.createValueField(true))
            .build();
    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_BACKEND", "gcs");
    expected.put("PLUGIN_BUCKET", "bucket");
    expected.put("PLUGIN_CACHE_KEY", "key");
    expected.put("PLUGIN_RESTORE", "true");
    expected.put("PLUGIN_FAIL_RESTORE_IF_KEY_NOT_PRESENT", "true");
    expected.put("PLUGIN_EXIT_CODE", "true");
    expected.put("PLUGIN_ARCHIVE_FORMAT", "gzip");
    expected.put("PLUGIN_BACKEND_OPERATION_TIMEOUT", "100s");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        restoreCacheGCSStepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetSaveCacheGCSStepInfoEnvVariables() {
    SaveCacheGCSStepInfo saveCacheGCSStepInfo =
        SaveCacheGCSStepInfo.builder()
            .key(ParameterField.createValueField("key"))
            .bucket(ParameterField.createValueField("bucket"))
            .sourcePaths(ParameterField.createValueField(asList("path1", "path2")))
            .build();
    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_BACKEND", "gcs");
    expected.put("PLUGIN_MOUNT", "path1,path2");
    expected.put("PLUGIN_BUCKET", "bucket");
    expected.put("PLUGIN_CACHE_KEY", "key");
    expected.put("PLUGIN_REBUILD", "true");
    expected.put("PLUGIN_EXIT_CODE", "true");
    expected.put("PLUGIN_ARCHIVE_FORMAT", "tar");
    expected.put("PLUGIN_OVERRIDE", "false");
    expected.put("PLUGIN_BACKEND_OPERATION_TIMEOUT", "100s");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        saveCacheGCSStepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetSaveCacheGCSStepInfoEnvVariablesArchiveSet() {
    SaveCacheGCSStepInfo saveCacheGCSStepInfo =
        SaveCacheGCSStepInfo.builder()
            .key(ParameterField.createValueField("key"))
            .bucket(ParameterField.createValueField("bucket"))
            .sourcePaths(ParameterField.createValueField(asList("path1", "path2")))
            .archiveFormat(ParameterField.createValueField(ArchiveFormat.GZIP))
            .override(ParameterField.createValueField(false))
            .build();
    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_BACKEND", "gcs");
    expected.put("PLUGIN_MOUNT", "path1,path2");
    expected.put("PLUGIN_BUCKET", "bucket");
    expected.put("PLUGIN_CACHE_KEY", "key");
    expected.put("PLUGIN_REBUILD", "true");
    expected.put("PLUGIN_EXIT_CODE", "true");
    expected.put("PLUGIN_ARCHIVE_FORMAT", "gzip");
    expected.put("PLUGIN_OVERRIDE", "false");
    expected.put("PLUGIN_BACKEND_OPERATION_TIMEOUT", "100s");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        saveCacheGCSStepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetUploadToS3StepInfoEnvVariables() {
    UploadToS3StepInfo uploadToS3StepInfo = UploadToS3StepInfo.builder()
                                                .endpoint(ParameterField.createValueField("endpoint"))
                                                .region(ParameterField.createValueField("region"))
                                                .bucket(ParameterField.createValueField("bucket"))
                                                .sourcePath(ParameterField.createValueField("sources"))
                                                .target(ParameterField.createValueField("target"))
                                                .build();
    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_ENDPOINT", "endpoint");
    expected.put("PLUGIN_REGION", "region");
    expected.put("PLUGIN_BUCKET", "bucket");
    expected.put("PLUGIN_SOURCE", "sources");
    expected.put("PLUGIN_TARGET", "target");
    expected.put("PLUGIN_ARTIFACT_FILE", "/addon/tmp/.plugin/artifact");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        uploadToS3StepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldGetUploadToGCSStepInfoEnvVariables() {
    UploadToGCSStepInfo uploadToS3StepInfo = UploadToGCSStepInfo.builder()
                                                 .bucket(ParameterField.createValueField("bucket"))
                                                 .sourcePath(ParameterField.createValueField("pom.xml"))
                                                 .target(ParameterField.createValueField("dir/pom.xml"))
                                                 .build();
    Map<String, String> expected = new HashMap<>();
    expected.put("PLUGIN_SOURCE", "pom.xml");
    expected.put("PLUGIN_TARGET", "bucket/dir/pom.xml");
    expected.put("PLUGIN_ARTIFACT_FILE", "/addon/tmp/.plugin/artifact");

    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        uploadToS3StepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldGetPluginCompatibleStepInfoBaseImageConnectorRefs() {
    PluginCompatibleStep stepInfo = ECRStepInfo.builder()
                                        .account(ParameterField.createValueField("6874654867"))
                                        .region(ParameterField.createValueField("eu-central-1"))
                                        .imageName(ParameterField.createValueField("harness"))
                                        .tags(ParameterField.createValueField(asList("tag1", "tag2")))
                                        .baseImageConnectorRefs(ParameterField.createValueField(asList("docker")))
                                        .build();

    List<String> expected = new ArrayList<>();
    expected.add("docker");
    List<String> actual = pluginSettingUtils.getBaseImageConnectorRefs(stepInfo);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void shouldGetGitClonePluginCompatibleStepInfoBuildTypeTagEnvVariables() {
    BuildType buildType = BuildType.TAG;
    String buildValue = "myTag";
    String repoName = "myRepoName";

    final ParameterField<Build> buildParameter = createBuildParameter(buildType, buildValue);
    final GitCloneStepInfo stepInfo = GitCloneStepInfo.builder()
                                          .connectorRef(ParameterField.createValueField("myConnectorRef"))
                                          .build(buildParameter)
                                          .repoName(ParameterField.createValueField(repoName))
                                          .build();
    Map<String, String> expected = new HashMap<>();
    expected.put(DRONE_TAG, buildValue);
    expected.put(DRONE_BUILD_EVENT, TAG_BUILD_EVENT);
    expected.put(DRONE_WORKSPACE, STEP_MOUNT_PATH + PATH_SEPARATOR + repoName);
    expected.put("PLUGIN_DEPTH", GIT_CLONE_MANUAL_DEPTH.toString());
    expected.put(DRONE_NETRC_MACHINE, "");
    expected.put(DRONE_COMMIT_BRANCH, "");
    expected.put(DRONE_REMOTE_URL, "");
    expected.put(DRONE_COMMIT_SHA, "");

    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual =
        pluginSettingUtils.getPluginCompatibleEnvVariables(stepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void shouldGetGitClonePluginCompatibleStepInfoEnvVariables() {
    BuildType buildType = BuildType.BRANCH;
    String buildValue = "main";
    boolean sslVerify = true;
    String connectorRef = "myConnectorRef";
    String repoName = "myrepository";
    String scmProvider = "my.scmprovider.com";
    String scmUrl = "https://my.scmprovider.com/organization/myrepository.git";
    String cloneDir = "/harness/myCloneDir";
    Integer depth = 22;

    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    when(codebaseUtils.getGitConnector(any(), eq(connectorRef))).thenReturn(connectorDetails);

    when(ciCodebaseUtils.getGitConnector(any(), eq(connectorRef))).thenReturn(connectorDetails);
    Map<String, String> gitEnvVars = new HashMap<>();
    gitEnvVars.put(DRONE_REMOTE_URL, scmUrl);
    gitEnvVars.put(DRONE_NETRC_MACHINE, scmProvider);
    when(codebaseUtils.getGitEnvVariables(connectorDetails, repoName)).thenReturn(gitEnvVars);
    when(ciCodebaseUtils.getGitEnvVariables(connectorDetails, repoName)).thenReturn(gitEnvVars);

    final ParameterField<Build> buildParameter = createBuildParameter(buildType, buildValue);
    final GitCloneStepInfo stepInfo = GitCloneStepInfo.builder()
                                          .sslVerify(ParameterField.createValueField(sslVerify))
                                          .build(buildParameter)
                                          .connectorRef(ParameterField.createValueField(connectorRef))
                                          .repoName(ParameterField.createValueField(repoName))
                                          .cloneDirectory(ParameterField.createValueField(cloneDir))
                                          .depth(ParameterField.createValueField(depth))
                                          .build();

    Map<String, String> expected = new HashMap<>();
    expected.put(DRONE_TAG, "");
    expected.put(DRONE_BUILD_EVENT, "");
    expected.put(DRONE_COMMIT_SHA, "");
    expected.putAll(gitEnvVars);
    expected.put(DRONE_COMMIT_BRANCH, buildValue);
    expected.put(DRONE_WORKSPACE, cloneDir);
    expected.put("PLUGIN_DEPTH", depth.toString());

    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual =
        pluginSettingUtils.getPluginCompatibleEnvVariables(stepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void shouldGetGitClonePluginCompatibleStepInfoNoCloneDirAccountUrlEnvVariables() {
    BuildType buildType = BuildType.BRANCH;
    String buildValue = "main";
    boolean sslVerify = true;
    String connectorRef = "myConnectorRef";
    String repoName = "myrepository";
    String scmProvider = "my.scmprovider.com";
    String scmUrl = "https://"
        + "my.scmprovider.com"
        + "/organization/" + repoName + ".git";
    Integer depth = 22;

    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    when(codebaseUtils.getGitConnector(any(), eq(connectorRef))).thenReturn(connectorDetails);
    when(ciCodebaseUtils.getGitConnector(any(), eq(connectorRef))).thenReturn(connectorDetails);

    Map<String, String> gitEnvVars = new HashMap<>();
    gitEnvVars.put(DRONE_REMOTE_URL, scmUrl);
    gitEnvVars.put(DRONE_NETRC_MACHINE, scmProvider);
    when(codebaseUtils.getGitEnvVariables(connectorDetails, repoName)).thenReturn(gitEnvVars);
    when(ciCodebaseUtils.getGitEnvVariables(connectorDetails, repoName)).thenReturn(gitEnvVars);

    final ParameterField<Build> buildParameter = createBuildParameter(buildType, buildValue);
    final GitCloneStepInfo stepInfo = GitCloneStepInfo.builder()
                                          .sslVerify(ParameterField.createValueField(sslVerify))
                                          .build(buildParameter)
                                          .connectorRef(ParameterField.createValueField(connectorRef))
                                          .repoName(ParameterField.createValueField(repoName))
                                          .depth(ParameterField.createValueField(depth))
                                          .build();

    Map<String, String> expected = new HashMap<>();
    expected.put(DRONE_TAG, "");
    expected.put(DRONE_BUILD_EVENT, "");
    expected.put(DRONE_COMMIT_SHA, "");
    expected.putAll(gitEnvVars);
    expected.put(DRONE_COMMIT_BRANCH, buildValue);
    expected.put(DRONE_WORKSPACE, STEP_MOUNT_PATH + PATH_SEPARATOR + repoName);
    expected.put("PLUGIN_DEPTH", depth.toString());

    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual =
        pluginSettingUtils.getPluginCompatibleEnvVariables(stepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void shouldGetGitClonePluginCompatibleStepInfoNoCloneDirRepoUrlEnvVariables() {
    BuildType buildType = BuildType.BRANCH;
    String buildValue = "main";
    boolean sslVerify = true;
    String connectorRef = "myConnectorRef";
    String repoName = "myrepository";
    String scmProvider = "my.scmprovider.com";
    String scmUrl = "https://"
        + "my.scmprovider.com"
        + "/organization/" + repoName + ".git";
    Integer depth = 22;

    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    when(codebaseUtils.getGitConnector(any(), eq(connectorRef))).thenReturn(connectorDetails);

    when(ciCodebaseUtils.getGitConnector(any(), eq(connectorRef))).thenReturn(connectorDetails);
    Map<String, String> gitEnvVars = new HashMap<>();
    gitEnvVars.put(DRONE_REMOTE_URL, scmUrl);
    gitEnvVars.put(DRONE_NETRC_MACHINE, scmProvider);
    when(codebaseUtils.getGitEnvVariables(connectorDetails, null)).thenReturn(gitEnvVars);

    when(ciCodebaseUtils.getGitEnvVariables(connectorDetails, null)).thenReturn(gitEnvVars);

    final ParameterField<Build> buildParameter = createBuildParameter(buildType, buildValue);
    final GitCloneStepInfo stepInfo = GitCloneStepInfo.builder()
                                          .sslVerify(ParameterField.createValueField(sslVerify))
                                          .build(buildParameter)
                                          .connectorRef(ParameterField.createValueField(connectorRef))
                                          .repoName(ParameterField.createValueField(null))
                                          .depth(ParameterField.createValueField(depth))
                                          .build();

    Map<String, String> expected = new HashMap<>();
    expected.put(DRONE_TAG, "");
    expected.put(DRONE_BUILD_EVENT, "");
    expected.put(DRONE_COMMIT_SHA, "");
    expected.putAll(gitEnvVars);
    expected.put(DRONE_COMMIT_BRANCH, buildValue);
    expected.put(DRONE_WORKSPACE, STEP_MOUNT_PATH + PATH_SEPARATOR + repoName);
    expected.put("PLUGIN_DEPTH", depth.toString());

    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual =
        pluginSettingUtils.getPluginCompatibleEnvVariables(stepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test(expected = CIStageExecutionUserException.class)
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void shouldGetGitClonePluginCompatibleStepInfoInvalidCloneDirEnvVariables() {
    BuildType buildType = BuildType.TAG;
    String buildValue = "myTag";
    String cloneDir = "/harness";

    final ParameterField<Build> buildParameter = createBuildParameter(buildType, buildValue);
    final GitCloneStepInfo stepInfo = GitCloneStepInfo.builder()
                                          .connectorRef(ParameterField.createValueField("myConnectorRef"))
                                          .build(buildParameter)
                                          .repoName(ParameterField.createValueField(null))
                                          .cloneDirectory(ParameterField.createValueField(cloneDir))
                                          .build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    pluginSettingUtils.getPluginCompatibleEnvVariables(stepInfo, "identifier", 100, ambiance, Type.K8, false, true);
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void shouldGetGitClonePluginCompatibleStepInfoNoDepthNoBuildEnvVariables() {
    String repoName = "myRepoName";
    final GitCloneStepInfo stepInfo = GitCloneStepInfo.builder()
                                          .connectorRef(ParameterField.createValueField("myConnectorRef"))
                                          .repoName(ParameterField.createValueField(repoName))
                                          .build();

    Map<String, String> expected = new HashMap<>();
    expected.put(GIT_SSL_NO_VERIFY, String.valueOf(false));
    expected.put(DRONE_WORKSPACE, STEP_MOUNT_PATH + PATH_SEPARATOR + repoName);

    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual =
        pluginSettingUtils.getPluginCompatibleEnvVariables(stepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void shouldGetGitClonePluginCompatibleStepInfoZeroDepthEnvVariables() {
    boolean sslVerify = false;
    String repoName = "myRepoName";
    BuildType buildType = BuildType.BRANCH;
    String buildValue = "main";
    final ParameterField<Build> buildParameter = createBuildParameter(buildType, buildValue);
    final GitCloneStepInfo stepInfo = GitCloneStepInfo.builder()
                                          .connectorRef(ParameterField.createValueField("myConnectorRef"))
                                          .repoName(ParameterField.createValueField(repoName))
                                          .sslVerify(ParameterField.createValueField(sslVerify))
                                          .build(buildParameter)
                                          .depth(ParameterField.createValueField(0))
                                          .build();

    Map<String, String> expected = new HashMap<>();
    expected.put(DRONE_TAG, "");
    expected.put(DRONE_NETRC_MACHINE, "");
    expected.put(DRONE_BUILD_EVENT, "");
    expected.put(DRONE_REMOTE_URL, "");
    expected.put(DRONE_COMMIT_SHA, "");
    expected.put(GIT_SSL_NO_VERIFY, String.valueOf(!sslVerify));
    expected.put(DRONE_WORKSPACE, STEP_MOUNT_PATH + PATH_SEPARATOR + repoName);
    expected.put(DRONE_COMMIT_BRANCH, buildValue);

    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual =
        pluginSettingUtils.getPluginCompatibleEnvVariables(stepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  private static ParameterField<Build> createBuildParameter(BuildType buildType, String value) {
    final ParameterField<String> buildStringParameter = ParameterField.<String>builder().value(value).build();
    BuildSpec buildSpec = null;
    if (BuildType.BRANCH == buildType) {
      buildSpec = BranchBuildSpec.builder().branch(buildStringParameter).build();
    } else if (BuildType.TAG == buildType) {
      buildSpec = TagBuildSpec.builder().tag(buildStringParameter).build();
    } else if (BuildType.PR == buildType) {
      buildSpec = PRBuildSpec.builder().number(buildStringParameter).build();
    }
    final Build build = builder().spec(buildSpec).type(buildType).build();
    return ParameterField.<Build>builder().value(build).build();
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getRepoNameFromRepoUrlTest() {
    String githubSsh = "git@github.com:organization/repo.git";
    assertThat(getRepoNameFromRepoUrl(githubSsh)).isEqualTo("repo");

    String githubHttps = "https://github.com/organization/repo.git";
    assertThat(getRepoNameFromRepoUrl(githubHttps)).isEqualTo("repo");

    String gitlabSsh = "git@gitlab.com:organization/repo.git";
    assertThat(getRepoNameFromRepoUrl(gitlabSsh)).isEqualTo("repo");

    String gitlabHttps = "https://gitlab.com/organization/repo.git";
    assertThat(getRepoNameFromRepoUrl(gitlabHttps)).isEqualTo("repo");

    String bitbucketSsh = "git@bitbucket.org:organization/repo.git";
    assertThat(getRepoNameFromRepoUrl(bitbucketSsh)).isEqualTo("repo");

    String bitbucketHttps = "https://username@bitbucket.org/organization/repo.git";
    assertThat(getRepoNameFromRepoUrl(bitbucketHttps)).isEqualTo("repo");

    String withExtraDotGits = "git@github.com:organization/repo.with.extra.git.git";
    assertThat(getRepoNameFromRepoUrl(withExtraDotGits)).isEqualTo("repo.with.extra.git");

    String empty = "";
    assertThat(getRepoNameFromRepoUrl(empty)).isEqualTo("repository");
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDlcSetupRequired() {
    DockerStepInfo dockerStepInfo =
        DockerStepInfo.builder()
            .repo(ParameterField.createValueField("harness"))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .caching(ParameterField.createValueField(true))
            .build();

    assertThat(pluginSettingUtils.dlcSetupRequired(dockerStepInfo)).isTrue();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetDlcPrefix() {
    String accountId = "test-account-id";
    String repo = "harness";
    DockerStepInfo dockerStepInfo =
        DockerStepInfo.builder()
            .repo(ParameterField.createValueField(repo))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .caching(ParameterField.createValueField(true))
            .build();

    String expectedPrefix = String.format("%s/%s/", accountId, repo);
    String prefix = pluginSettingUtils.getDlcPrefix(accountId, "identifier", dockerStepInfo);
    assertThat(expectedPrefix).isEqualTo(prefix);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testSetupDlcArgs() {
    String repo = "harness";
    DockerStepInfo dockerStepInfo =
        DockerStepInfo.builder()
            .repo(ParameterField.createValueField(repo))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .caching(ParameterField.createValueField(true))
            .build();

    String cacheFrom = "cacheFromArg";
    String cacheTo = "cacheToArg";
    ParameterField expectedCacheFrom = ParameterField.createValueField(asList(cacheFrom));
    ParameterField expectedCacheTo = ParameterField.createValueField(cacheTo);

    pluginSettingUtils.setupDlcArgs(dockerStepInfo, "identifier", cacheFrom, cacheTo);
    assertThat(expectedCacheFrom).isEqualTo(dockerStepInfo.getCacheFrom());
    assertThat(expectedCacheTo).isEqualTo(dockerStepInfo.getCacheTo());
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testSetupDlcArgsWithCacheArgs() {
    String repo = "harness";
    String inputCacheFrom = "inputCacheFrom";
    String inputCacheTo = "inputCacheTo";
    DockerStepInfo dockerStepInfo =
        DockerStepInfo.builder()
            .repo(ParameterField.createValueField(repo))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .caching(ParameterField.createValueField(true))
            .cacheFrom(ParameterField.createValueField(asList(inputCacheFrom)))
            .cacheTo(ParameterField.createValueField(inputCacheTo))
            .build();

    String cacheFrom = "cacheFromArg";
    String cacheTo = "cacheToArg";
    ParameterField expectedCacheFrom = ParameterField.createValueField(asList(inputCacheFrom, cacheFrom));
    ParameterField expectedCacheTo = ParameterField.createValueField(cacheTo);

    pluginSettingUtils.setupDlcArgs(dockerStepInfo, "identifier", cacheFrom, cacheTo);
    assertThat(expectedCacheFrom).isEqualTo(dockerStepInfo.getCacheFrom());
    assertThat(expectedCacheTo).isEqualTo(dockerStepInfo.getCacheTo());
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testDlcSetupRequiredEcr() {
    ECRStepInfo ecrStepInfo =
        ECRStepInfo.builder()
            .imageName(ParameterField.createValueField("harness"))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .caching(ParameterField.createValueField(true))
            .build();

    assertThat(pluginSettingUtils.dlcSetupRequired(ecrStepInfo)).isTrue();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetDlcPrefixEcr() {
    String accountId = "test-account-id";
    String repo = "harness";
    ECRStepInfo ecrStepInfo =
        ECRStepInfo.builder()
            .imageName(ParameterField.createValueField(repo))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .caching(ParameterField.createValueField(true))
            .build();

    String expectedPrefix = String.format("%s/%s/", accountId, repo);
    String prefix = pluginSettingUtils.getDlcPrefix(accountId, "identifier", ecrStepInfo);
    assertThat(expectedPrefix).isEqualTo(prefix);
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testSetupDlcArgsEcr() {
    String repo = "harness";
    ECRStepInfo ecrStepInfo =
        ECRStepInfo.builder()
            .imageName(ParameterField.createValueField(repo))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .caching(ParameterField.createValueField(true))
            .build();

    String cacheFrom = "cacheFromArg";
    String cacheTo = "cacheToArg";
    ParameterField expectedCacheFrom = ParameterField.createValueField(asList(cacheFrom));
    ParameterField expectedCacheTo = ParameterField.createValueField(cacheTo);

    pluginSettingUtils.setupDlcArgs(ecrStepInfo, "identifier", cacheFrom, cacheTo);
    assertThat(expectedCacheFrom).isEqualTo(ecrStepInfo.getCacheFrom());
    assertThat(expectedCacheTo).isEqualTo(ecrStepInfo.getCacheTo());
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testSetupDlcArgsWithCacheArgsEcr() {
    String repo = "harness";
    String inputCacheFrom = "inputCacheFrom";
    String inputCacheTo = "inputCacheTo";
    ECRStepInfo ecrStepInfo =
        ECRStepInfo.builder()
            .imageName(ParameterField.createValueField(repo))
            .tags(ParameterField.createValueField(asList("tag1", "tag2")))
            .dockerfile(ParameterField.createValueField("Dockerfile"))
            .context(ParameterField.createValueField("context"))
            .target(ParameterField.createValueField("target"))
            .buildArgs(ParameterField.createValueField(Collections.singletonMap("arg1", "value1")))
            .labels(ParameterField.createValueField(Collections.singletonMap("label", "label1")))
            .caching(ParameterField.createValueField(true))
            .cacheFrom(ParameterField.createValueField(asList(inputCacheFrom)))
            .cacheTo(ParameterField.createValueField(inputCacheTo))
            .build();

    String cacheFrom = "cacheFromArg";
    String cacheTo = "cacheToArg";
    ParameterField expectedCacheFrom = ParameterField.createValueField(asList(inputCacheFrom, cacheFrom));
    ParameterField expectedCacheTo = ParameterField.createValueField(cacheTo);

    pluginSettingUtils.setupDlcArgs(ecrStepInfo, "identifier", cacheFrom, cacheTo);
    assertThat(expectedCacheFrom).isEqualTo(ecrStepInfo.getCacheFrom());
    assertThat(expectedCacheTo).isEqualTo(ecrStepInfo.getCacheTo());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testSscaEnforcementStepEnvVariables() {
    SscaEnforcementStepInfo sscaEnforcementStepInfo = getSscaEnforcementStep();

    Map<String, String> expected = new HashMap<>();
    expected.put("STEP_EXECUTION_ID", null);
    expected.put("PLUGIN_SBOMSOURCE", "image:tag");
    expected.put("PLUGIN_TYPE", "Enforce");
    expected.put("POLICY_FILE_IDENTIFIER", "file");
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, String> actual = pluginSettingUtils.getPluginCompatibleEnvVariables(
        sscaEnforcementStepInfo, "identifier", 100, ambiance, Type.K8, false, true);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testSscaEnforcementStepSecretEnvVariables() {
    SscaEnforcementStepInfo sscaEnforcementStepInfo = getSscaEnforcementStep();

    Map<String, SecretNGVariable> expected = new HashMap<>();
    SecretRefData secretRefData = SecretRefHelper.createSecretRef("publicKey");
    expected.put("COSIGN_PUBLIC_KEY",
        SecretNGVariable.builder()
            .type(NGVariableType.SECRET)
            .value(ParameterField.createValueField(secretRefData))
            .name("COSIGN_PUBLIC_KEY")
            .build());
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, SecretNGVariable> actual =
        pluginSettingUtils.getPluginCompatibleSecretVars(sscaEnforcementStepInfo, "identifier");
    assertThat(actual).isEqualTo(expected);
  }

  private SscaEnforcementStepInfo getSscaEnforcementStep() {
    return SscaEnforcementStepInfo.builder()
        .source(SbomSource.builder()
                    .type(SbomSourceType.IMAGE)
                    .sbomSourceSpec(ImageSbomSource.builder()
                                        .connector(ParameterField.createValueField("conn1"))
                                        .image(ParameterField.createValueField("image:tag"))
                                        .build())
                    .build())
        .policy(EnforcementPolicy.builder()
                    .store(PolicyStore.builder()
                               .type(StoreType.HARNESS)
                               .storeSpec(HarnessStore.builder().file(ParameterField.createValueField("file")).build())
                               .build())
                    .build())
        .verifyAttestation(
            VerifyAttestation.builder()
                .type(AttestationType.COSIGN)
                .verifyAttestationSpec(
                    CosignVerifyAttestation.builder().publicKey(ParameterField.createValueField("publicKey")).build())
                .build())
        .build();
  }
}
