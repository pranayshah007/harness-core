/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.rule.OwnerRule.AMAN;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.EOIN_MCAFEE;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.when;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.beans.steps.stepinfo.GARStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.CIExecutionConfig;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.ContainerlessPluginConfig;
import io.harness.ci.config.Operation;
import io.harness.ci.config.PluginField;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.config.VmContainerlessStepConfig;
import io.harness.ci.config.VmImageConfig;
import io.harness.ci.execution.DeprecatedImageInfo;
import io.harness.ci.execution.buildstate.PluginSettingUtils;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIExecutionConfigRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@Slf4j
public class CIExecutionConfigServiceTest extends CIExecutionTestBase {
  @Mock CIExecutionConfigRepository cIExecutionConfigRepository;
  @Inject CIExecutionConfigService ciExecutionConfigService;
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;

  @Mock private CIExecutionServiceConfig ciExecutionServiceConfigMock;
  @Mock private PluginSettingUtils pluginSettingUtils;
  @InjectMocks private CIExecutionConfigService ciExecutionConfigServiceWithMocks;

  @Before
  public void setUp() {
    on(ciExecutionConfigService).set("configRepository", cIExecutionConfigRepository);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getAddonImageTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("dockerImage")
                                            .addOnImage("addon:1.3.4")
                                            .liteEngineImage("le:1,4.4")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct1")).thenReturn(Optional.empty());
    String customAddonImage = ciExecutionConfigService.getAddonImage("acct");
    String defaultAddonImage = ciExecutionConfigService.getAddonImage("acct1");
    assertThat(customAddonImage).isEqualTo("addon:1.3.4");
    assertThat(defaultAddonImage).isEqualTo(ciExecutionServiceConfig.getAddonImage());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLEImageTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("dockerImage")
                                            .addOnImage("addon:1.3.4")
                                            .liteEngineImage("le:1.4.4")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct1")).thenReturn(Optional.empty());
    String customAddonImage = ciExecutionConfigService.getLiteEngineImage("acct");
    String defaultAddonImage = ciExecutionConfigService.getAddonImage("acct1");
    assertThat(customAddonImage).isEqualTo("le:1.4.4");
    assertThat(defaultAddonImage).isEqualTo(ciExecutionServiceConfig.getAddonImage());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getDeprecatedImages_NoDeprecatedImagesTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.2.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushGARImage("bpgar:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos =
        Arrays.asList(DeprecatedImageInfo.builder().tag("CacheS3Image").version("caches3:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("ArtifactoryUploadImage").version("art:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("CacheGCSImage").version("cachegcs:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("S3UploadImage").version("s3upload:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("CacheS3Image").version("caches3:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("GCSUploadImage").version("gcsUpload:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("SecurityImage").version("gcsUpload:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushDockerImage").version("bpdr:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("GitCloneImage").version("gc:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushECRConfigImage").version("bpecr:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushGCRConfigImage").version("bpgcr:1.2.3").build());
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(Arrays.asList());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLE_LEIsDeprecatedTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.0.1")
                                            .liteEngineImage("harness/ci-lite-engine:1.0.1")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos =
        Arrays.asList(DeprecatedImageInfo.builder().tag("AddonImage").version("harness/ci-addon:1.0.1").build(),
            DeprecatedImageInfo.builder().tag("LiteEngineImage").version("harness/ci-lite-engine:1.0.1").build());
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(deprecatedImageInfos);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLE_LEIsNotDeprecatedTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.2.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos = Arrays.asList();
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(deprecatedImageInfos);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLE_AddonIsDeprecatedLENotDeprecatedTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos =
        Arrays.asList(DeprecatedImageInfo.builder().tag("AddonImage").version("harness/ci-addon:1.1.0").build());
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(deprecatedImageInfos);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getPluginVersionTestCustomConfig_ShouldPass() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .addOnImage("harness/ci-addon:1.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushGARImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, "acct").getImage())
        .isEqualTo("gc:abc");
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, "acct").getImage())
        .isEqualTo("bpdr:1.2.4");
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GAR, "acct").getImage())
        .isEqualTo("bpgcr:1.2.3");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getDeprecatedImagesTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .addOnImage("harness/ci-addon:1.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    CIExecutionImages deprecatedImages = ciExecutionConfigService.getDeprecatedImages("acct");
    assertThat(deprecatedImages.getBuildAndPushECRTag()).isNull();
    assertThat(deprecatedImages.getGcsUploadTag()).isNull();
    assertThat(deprecatedImages.getLiteEngineTag()).isNull();
    assertThat(deprecatedImages.getAddonTag()).isNull();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getDeprecatedImagesWithDeprecatedTagsTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:0.2.4")
                                            .addOnImage("harness/ci-addon:0.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.1.0")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:0.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:0.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:0.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    CIExecutionImages deprecatedImages = ciExecutionConfigService.getDeprecatedImages("acct");
    assertThat(deprecatedImages.getBuildAndPushECRTag()).isNull();
    assertThat(deprecatedImages.getGcsUploadTag()).isNull();
    assertThat(deprecatedImages.getLiteEngineTag()).isNull();
    assertThat(deprecatedImages.getAddonTag()).isEqualTo("harness/ci-addon:0.1.0");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getDefaultTest() {
    CIExecutionImages ciExecutionImages = ciExecutionConfigService.getDefaultConfig(StageInfraDetails.Type.VM);
    assertThat(ciExecutionImages.getAddonTag()).isNull();
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("vm-gitClone");
    assertThat(ciExecutionImages.getArtifactoryUploadTag()).isEqualTo("vm-artifactoryUpload");
    assertThat(ciExecutionImages.getCacheGCSTag()).isEqualTo("vm-cacheGCS");
    assertThat(ciExecutionImages.getSecurityTag()).isEqualTo("vm-security");

    ciExecutionImages = ciExecutionConfigService.getDefaultConfig(StageInfraDetails.Type.K8);
    assertThat(ciExecutionImages.getAddonTag()).isEqualTo("harness/ci-addon:1.4.0");
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("gc:1.2.3");
    assertThat(ciExecutionImages.getArtifactoryUploadTag()).isEqualTo("art:1.2.3");
    assertThat(ciExecutionImages.getCacheGCSTag()).isEqualTo("cachegcs:1.2.3");
    assertThat(ciExecutionImages.getSecurityTag()).isEqualTo("sc:1.2.3");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getPluginVersionVMTestCustomConfig_ShouldPass() {
    VmImageConfig vmImageConfig =
        VmImageConfig.builder().gitClone("vm_git_clone").buildAndPushDockerRegistry("docker").build();
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushACRImage("bpacr:1.2.3")
                                            .buildAndPushGARImage("bpgar:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .vmImageConfig(vmImageConfig)
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GIT_CLONE, "acct"))
        .isEqualTo("vm_git_clone");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.DOCKER, "acct")).isEqualTo("docker");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ECR, "acct"))
        .isEqualTo("vm-buildAndPushECR");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GCR, "acct"))
        .isEqualTo("vm-buildAndPushGCR");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GAR, "acct"))
        .isEqualTo("vm-buildAndPushGAR");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_S3, "acct"))
        .isEqualTo("vm-cacheS3");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_GCS, "acct"))
        .isEqualTo("vm-cacheGCS");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_S3, "acct"))
        .isEqualTo("vm-cacheS3");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_ARTIFACTORY, "acct"))
        .isEqualTo("vm-artifactoryUpload");
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SECURITY, "acct"))
        .isEqualTo("vm-security");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getCustomerConfigTest() {
    VmImageConfig vmImageConfig =
        VmImageConfig.builder().gitClone("vm_git_clone").buildAndPushDockerRegistry("docker").build();
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .addOnImage("addon")
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushACRImage("bpacr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushGARImage("bpgar:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .vmImageConfig(vmImageConfig)
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    // For VM overrides only
    CIExecutionImages ciExecutionImages =
        ciExecutionConfigService.getCustomerConfig("acct", StageInfraDetails.Type.VM, true);

    assertThat(ciExecutionImages.getAddonTag()).isNull();
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("vm_git_clone");
    assertThat(ciExecutionImages.getBuildAndPushDockerRegistryTag()).isEqualTo("docker");
    assertThat(ciExecutionImages.getCacheGCSTag()).isNull();
    assertThat(ciExecutionImages.getSecurityTag()).isNull();

    // For K8 overrides only
    ciExecutionImages = ciExecutionConfigService.getCustomerConfig("acct", StageInfraDetails.Type.K8, true);

    assertThat(ciExecutionImages.getAddonTag()).isEqualTo("addon");
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("gc:abc");
    assertThat(ciExecutionImages.getBuildAndPushDockerRegistryTag()).isEqualTo("bpdr:1.2.4");
    assertThat(ciExecutionImages.getCacheGCSTag()).isNull();
    assertThat(ciExecutionImages.getSecurityTag()).isNull();

    // For VM whole config
    ciExecutionImages = ciExecutionConfigService.getCustomerConfig("acct", StageInfraDetails.Type.VM, false);

    assertThat(ciExecutionImages.getAddonTag()).isNull();
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("vm_git_clone");
    assertThat(ciExecutionImages.getBuildAndPushDockerRegistryTag()).isEqualTo("docker");
    assertThat(ciExecutionImages.getCacheGCSTag()).isEqualTo("vm-cacheGCS");
    assertThat(ciExecutionImages.getSecurityTag()).isEqualTo("vm-security");
    assertThat(ciExecutionImages.getCacheS3Tag()).isEqualTo("vm-cacheS3");

    // For K8 whole config
    ciExecutionImages = ciExecutionConfigService.getCustomerConfig("acct", StageInfraDetails.Type.K8, false);

    assertThat(ciExecutionImages.getAddonTag()).isEqualTo("addon");
    assertThat(ciExecutionImages.getGitCloneTag()).isEqualTo("gc:abc");
    assertThat(ciExecutionImages.getBuildAndPushDockerRegistryTag()).isEqualTo("bpdr:1.2.4");
    assertThat(ciExecutionImages.getCacheGCSTag()).isEqualTo("cachegcs:1.2.3");
    assertThat(ciExecutionImages.getSecurityTag()).isEqualTo("sc:1.2.3");
    assertThat(ciExecutionImages.getSscaOrchestrationTag()).isEqualTo("sscaorchestrate:0.0.1");
    assertThat(ciExecutionImages.getSscaEnforcementTag()).isEqualTo("sscaEnforcement:0.0.1");
    assertThat(ciExecutionImages.getSlsaVerificationTag()).isEqualTo("slsaVerification:0.0.1");
    assertThat(ciExecutionImages.getProvenanceTag()).isEqualTo("provenance:0.0.1");
    assertThat(ciExecutionImages.getProvenanceGcrTag()).isEqualTo("provenanceGcr:0.0.1");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void updateTest() {
    VmImageConfig vmImageConfig =
        VmImageConfig.builder().gitClone("vm_git_clone").buildAndPushDockerRegistry("docker").build();
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .addOnImage("addon")
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushACRImage("bpacr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .vmImageConfig(vmImageConfig)
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    ArrayList<Operation> operations = new ArrayList<>();

    Operation operation1 = new Operation();
    operation1.setField(PluginField.BUILD_PUSH_ECR.getLabel());
    operation1.setValue("ecr_vm");

    Operation operation2 = new Operation();
    operation2.setField(PluginField.GIT_CLONE.getLabel());
    operation2.setValue("vm_git_clone_changed");

    operations.add(operation1);
    operations.add(operation2);

    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.VM);
    assertThat(executionConfig.getVmImageConfig().getGitClone()).isEqualTo("vm_git_clone_changed");
    assertThat(executionConfig.getVmImageConfig().getBuildAndPushECR()).isEqualTo("ecr_vm");
    assertThat(executionConfig.getGitCloneImage()).isEqualTo("gc:abc");

    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.K8);
    assertThat(executionConfig.getGitCloneImage()).isEqualTo("vm_git_clone_changed");
    assertThat(executionConfig.getBuildAndPushECRImage()).isEqualTo("ecr_vm");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getPluginVersionTestDefaultConfig_ShouldPass() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .addOnImage("harness/ci-addon:1.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .buildAndPushGARImage("bpgcr:1.2.3")
                                            .buildAndPushACRImage("bpacr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.empty());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, "acct").getImage())
        .isEqualTo("gc:1.2.3");
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, "acct").getImage())
        .isEqualTo("bpdr:1.2.3");
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMDockerBuildx() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxConfig(ContainerlessPluginConfig.builder().name("dockerBuildxConfig").build())
                    .build())
            .build();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder()
                                        .repo(ParameterField.createValueField("harness"))
                                        .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                        .caching(ParameterField.createValueField(true))
                                        .build();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(dockerStepInfo)).thenReturn(true);
    String pluginName =
        ciExecutionConfigServiceWithMocks.getContainerlessPluginNameForVM(CIStepInfoType.DOCKER, dockerStepInfo);
    assertThat(pluginName).isNotEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMEcrBuildx() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxEcrConfig(ContainerlessPluginConfig.builder().name("dockerBuildxEcrConfig").build())
                    .build())
            .build();
    ECRStepInfo ecrStepInfo = ECRStepInfo.builder()
                                  .imageName(ParameterField.createValueField("harness"))
                                  .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                  .caching(ParameterField.createValueField(true))
                                  .build();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(ecrStepInfo)).thenReturn(true);
    String pluginName =
        ciExecutionConfigServiceWithMocks.getContainerlessPluginNameForVM(CIStepInfoType.ECR, ecrStepInfo);
    assertThat(pluginName).isNotEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMDocker() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxConfig(ContainerlessPluginConfig.builder().name("dockerBuildxConfig").build())
                    .build())
            .build();
    DockerStepInfo dockerStepInfo = DockerStepInfo.builder()
                                        .repo(ParameterField.createValueField("harness"))
                                        .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                        .caching(ParameterField.createValueField(true))
                                        .build();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(dockerStepInfo)).thenReturn(false);
    String pluginName =
        ciExecutionConfigServiceWithMocks.getContainerlessPluginNameForVM(CIStepInfoType.DOCKER, dockerStepInfo);
    assertThat(pluginName).isNull();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMEcr() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxEcrConfig(ContainerlessPluginConfig.builder().name("dockerBuildxEcrConfig").build())
                    .build())
            .build();
    ECRStepInfo ecrStepInfo = ECRStepInfo.builder()
                                  .imageName(ParameterField.createValueField("harness"))
                                  .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                  .caching(ParameterField.createValueField(true))
                                  .build();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(ecrStepInfo)).thenReturn(false);
    String pluginName =
        ciExecutionConfigServiceWithMocks.getContainerlessPluginNameForVM(CIStepInfoType.ECR, ecrStepInfo);
    assertThat(pluginName).isNull();
  }

  @Test
  @Owner(developers = EOIN_MCAFEE)
  @Category(UnitTests.class)
  public void testGetContainerlessPluginNameForVMGAR() {
    CIStepConfig ciStepConfig =
        CIStepConfig.builder()
            .vmContainerlessStepConfig(
                VmContainerlessStepConfig.builder()
                    .dockerBuildxEcrConfig(ContainerlessPluginConfig.builder().name("dockerBuildxGarConfig").build())
                    .build())
            .build();
    GARStepInfo garStepInfo = GARStepInfo.builder()
                                  .imageName(ParameterField.createValueField("harness"))
                                  .tags(ParameterField.createValueField(Arrays.asList("tag1", "tag2")))
                                  .caching(ParameterField.createValueField(true))
                                  .build();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(ciStepConfig);
    when(pluginSettingUtils.buildxRequired(garStepInfo)).thenReturn(false);
    String pluginName =
        ciExecutionConfigServiceWithMocks.getContainerlessPluginNameForVM(CIStepInfoType.GAR, garStepInfo);
    assertThat(pluginName).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForK8_sscaPluginsWithGlobalAccountId() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .sscaOrchestrationTag("sscaOrchestrationTag")
                                            .sscaEnforcementTag("sscaEnforcementTag")
                                            .provenanceTag("provenanceTag")
                                            .provenanceGcrTag("provenanceGcrTag")
                                            .slsaVerificationTag("slsaVerificationTag")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("__GLOBAL_ACCOUNT_ID__"))
        .thenReturn(Optional.of(executionConfig));
    StepImageConfig actualSscaOrchestrationExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ORCHESTRATION, "acct");
    assertThat(actualSscaOrchestrationExecutionConfig).isNotNull();
    assertThat(actualSscaOrchestrationExecutionConfig.getImage()).isEqualTo("sscaOrchestrationTag");

    StepImageConfig actualSscaEnforcementExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ENFORCEMENT, "acct");
    assertThat(actualSscaEnforcementExecutionConfig).isNotNull();
    assertThat(actualSscaEnforcementExecutionConfig.getImage()).isEqualTo("sscaEnforcementTag");

    StepImageConfig actualProvenanceExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE, "acct");
    assertThat(actualProvenanceExecutionConfig).isNotNull();
    assertThat(actualProvenanceExecutionConfig.getImage()).isEqualTo("provenanceTag");

    StepImageConfig actualProvenanceGcrExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE_GCR, "acct");
    assertThat(actualProvenanceGcrExecutionConfig).isNotNull();
    assertThat(actualProvenanceGcrExecutionConfig.getImage()).isEqualTo("provenanceGcrTag");

    StepImageConfig actualSalsaVerificationExecutionConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SLSA_VERIFICATION, "acct");
    assertThat(actualSalsaVerificationExecutionConfig).isNotNull();
    assertThat(actualSalsaVerificationExecutionConfig.getImage()).isEqualTo("slsaVerificationTag");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForVM_sscaPluginsWithGlobalAccountId() {
    VmImageConfig vmImageConfig = VmImageConfig.builder()
                                      .sscaOrchestration("sscaOrchestrationTag")
                                      .sscaEnforcement("sscaEnforcementTag")
                                      .slsaVerification("slsaVerificationTag")
                                      .build();
    CIExecutionConfig executionConfig =
        CIExecutionConfig.builder().accountIdentifier("acct").vmImageConfig(vmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("__GLOBAL_ACCOUNT_ID__"))
        .thenReturn(Optional.of(executionConfig));
    String actualSscaOrchestrationExecutionConfig =
        ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ORCHESTRATION, "acct");
    assertThat(actualSscaOrchestrationExecutionConfig).isNotNull();
    assertThat(actualSscaOrchestrationExecutionConfig).isEqualTo("sscaOrchestrationTag");

    String actualSscaEnforcementExecutionConfig =
        ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ENFORCEMENT, "acct");
    assertThat(actualSscaEnforcementExecutionConfig).isNotNull();
    assertThat(actualSscaEnforcementExecutionConfig).isEqualTo("sscaEnforcementTag");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForK8_noConfigsExist() {
    on(ciExecutionConfigService).set("ciExecutionServiceConfig", ciExecutionServiceConfigMock);
    CIStepConfig defaultStepConfig = getDefaultStepeConfig();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(getDefaultStepeConfig());
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId)).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId)).thenReturn(Optional.empty());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ORCHESTRATION, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSscaOrchestrationConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ENFORCEMENT, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSscaEnforcementConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE, accountId).getImage())
        .isEqualTo(defaultStepConfig.getProvenanceConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE_GCR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getProvenanceGcrConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SLSA_VERIFICATION, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSlsaVerificationConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId).getImage())
        .isEqualTo(defaultStepConfig.getArtifactoryUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, accountId).getImage())
        .isEqualTo(defaultStepConfig.getGitCloneConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ECR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushECRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ACR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushACRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GCR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushGCRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GAR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushGARConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getS3UploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushDockerRegistryConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SECURITY, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSecurityConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getGcsUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForK8_onlyGlobalConfigExists() {
    on(ciExecutionConfigService).set("ciExecutionServiceConfig", ciExecutionServiceConfigMock);
    CIStepConfig defaultStepConfig = getDefaultStepeConfig();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(getDefaultStepeConfig());
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    CIExecutionConfig globalExecutionConfig = getCiExecutionConfig("global");
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId)).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId))
        .thenReturn(Optional.of(globalExecutionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ORCHESTRATION, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getSscaOrchestrationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ENFORCEMENT, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getSscaEnforcementTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getProvenanceTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE_GCR, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getProvenanceGcrTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SLSA_VERIFICATION, accountId).getImage())
        .isEqualTo(globalExecutionConfig.getSlsaVerificationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId).getImage())
        .isEqualTo(defaultStepConfig.getArtifactoryUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, accountId).getImage())
        .isEqualTo(defaultStepConfig.getGitCloneConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ECR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushECRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ACR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushACRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GCR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushGCRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GAR, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushGARConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getS3UploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_S3, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, accountId).getImage())
        .isEqualTo(defaultStepConfig.getBuildAndPushDockerRegistryConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SECURITY, accountId).getImage())
        .isEqualTo(defaultStepConfig.getSecurityConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getGcsUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_GCS, accountId).getImage())
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
  }
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForK8_onlyAccountLevelConfigExists() {
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    CIExecutionConfig accountLevelExecutionConfig = getCiExecutionConfig("accountLevel");
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId))
        .thenReturn(Optional.of(accountLevelExecutionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId)).thenReturn(Optional.empty());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ORCHESTRATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaOrchestrationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ENFORCEMENT, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaEnforcementTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getProvenanceTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE_GCR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getProvenanceGcrTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SLSA_VERIFICATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSlsaVerificationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getArtifactoryUploadTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getGitCloneImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ECR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushECRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ACR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushACRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GCR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushGCRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GAR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushGARImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getS3UploadImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheS3Tag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheS3Tag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushDockerRegistryImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SECURITY, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSecurityImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheGCSTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getGcsUploadImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheGCSTag());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForK8_BothAccountLevelAndGlobalConfigsExist() {
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    CIExecutionConfig accountLevelExecutionConfig = getCiExecutionConfig("accountLevel");
    CIExecutionConfig globalExecutionConfig = getCiExecutionConfig("global");
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId))
        .thenReturn(Optional.of(accountLevelExecutionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId))
        .thenReturn(Optional.of(globalExecutionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ORCHESTRATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaOrchestrationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SSCA_ENFORCEMENT, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSscaEnforcementTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getProvenanceTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.PROVENANCE_GCR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getProvenanceGcrTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SLSA_VERIFICATION, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSlsaVerificationTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getArtifactoryUploadTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getGitCloneImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ECR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushECRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.ACR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushACRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GCR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushGCRImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GAR, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushGARImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getS3UploadImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheS3Tag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_S3, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheS3Tag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getBuildAndPushDockerRegistryImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SECURITY, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getSecurityImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.RESTORE_CACHE_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheGCSTag());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.UPLOAD_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getGcsUploadImage());
    assertThat(ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.SAVE_CACHE_GCS, accountId).getImage())
        .isEqualTo(accountLevelExecutionConfig.getCacheGCSTag());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForVM_BothAccountLevelAndGlobalConfigsExist() {
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    VmImageConfig accountLevelVmImageConfig = getVmImageConfig("accountLevel");
    VmImageConfig globalVmImageConfig = getVmImageConfig("global");
    CIExecutionConfig accountLevelExecutionConfig =
        CIExecutionConfig.builder().accountIdentifier(accountId).vmImageConfig(accountLevelVmImageConfig).build();
    CIExecutionConfig globalExecutionConfig =
        CIExecutionConfig.builder().accountIdentifier(globalAccountId).vmImageConfig(globalVmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId))
        .thenReturn(Optional.of(accountLevelExecutionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId))
        .thenReturn(Optional.of(globalExecutionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ORCHESTRATION, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ENFORCEMENT, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaEnforcement());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId))
        .isEqualTo(accountLevelVmImageConfig.getArtifactoryUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GIT_CLONE, accountId))
        .isEqualTo(accountLevelVmImageConfig.getGitClone());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ECR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushECR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ACR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushACR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GCR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushGCR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GAR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushGAR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getS3Upload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.DOCKER, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushDockerRegistry());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SECURITY, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSecurity());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheGCS());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getGcsUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheGCS());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmTerraform());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForVM_onlyGlobalConfigExists() {
    on(ciExecutionConfigService).set("ciExecutionServiceConfig", ciExecutionServiceConfigMock);
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    VmImageConfig globalVmImageConfig = getVmImageConfig("global");
    CIStepConfig defaultStepConfig = getDefaultStepeConfig();
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(getDefaultStepeConfig());
    CIExecutionConfig globalExecutionConfig =
        CIExecutionConfig.builder().accountIdentifier(globalAccountId).vmImageConfig(globalVmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId)).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId))
        .thenReturn(Optional.of(globalExecutionConfig));
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ORCHESTRATION, accountId))
        .isEqualTo(globalVmImageConfig.getSscaOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ENFORCEMENT, accountId))
        .isEqualTo(globalVmImageConfig.getSscaEnforcement());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId))
        .isEqualTo(defaultStepConfig.getArtifactoryUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GIT_CLONE, accountId))
        .isEqualTo(defaultStepConfig.getGitCloneConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ECR, accountId))
        .isEqualTo(defaultStepConfig.getBuildAndPushECRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ACR, accountId))
        .isEqualTo(defaultStepConfig.getBuildAndPushACRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GCR, accountId))
        .isEqualTo(defaultStepConfig.getBuildAndPushGCRConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GAR, accountId))
        .isEqualTo(defaultStepConfig.getBuildAndPushGARConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_S3, accountId))
        .isEqualTo(defaultStepConfig.getS3UploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_S3, accountId))
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_S3, accountId))
        .isEqualTo(defaultStepConfig.getCacheS3Config().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.DOCKER, accountId))
        .isEqualTo(defaultStepConfig.getBuildAndPushDockerRegistryConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SECURITY, accountId))
        .isEqualTo(defaultStepConfig.getSecurityConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_GCS, accountId))
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_GCS, accountId))
        .isEqualTo(defaultStepConfig.getGcsUploadConfig().getImage());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_GCS, accountId))
        .isEqualTo(defaultStepConfig.getCacheGCSConfig().getImage());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForVM_noConfigsExist() {
    on(ciExecutionConfigService).set("ciExecutionServiceConfig", ciExecutionServiceConfigMock);
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    VmImageConfig defaultVmImageConfig = getVmImageConfig("default");
    when(ciExecutionServiceConfigMock.getStepConfig()).thenReturn(getDefaultStepeConfig());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId)).thenReturn(Optional.empty());
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId)).thenReturn(Optional.empty());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ORCHESTRATION, accountId))
        .isEqualTo(defaultVmImageConfig.getSscaOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ENFORCEMENT, accountId))
        .isEqualTo(defaultVmImageConfig.getSscaEnforcement());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId))
        .isEqualTo(defaultVmImageConfig.getArtifactoryUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GIT_CLONE, accountId))
        .isEqualTo(defaultVmImageConfig.getGitClone());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ECR, accountId))
        .isEqualTo(defaultVmImageConfig.getBuildAndPushECR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ACR, accountId))
        .isEqualTo(defaultVmImageConfig.getBuildAndPushACR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GCR, accountId))
        .isEqualTo(defaultVmImageConfig.getBuildAndPushGCR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GAR, accountId))
        .isEqualTo(defaultVmImageConfig.getBuildAndPushGAR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_S3, accountId))
        .isEqualTo(defaultVmImageConfig.getS3Upload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_S3, accountId))
        .isEqualTo(defaultVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_S3, accountId))
        .isEqualTo(defaultVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.DOCKER, accountId))
        .isEqualTo(defaultVmImageConfig.getBuildAndPushDockerRegistry());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SECURITY, accountId))
        .isEqualTo(defaultVmImageConfig.getSecurity());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_GCS, accountId))
        .isEqualTo(defaultVmImageConfig.getCacheGCS());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_GCS, accountId))
        .isEqualTo(defaultVmImageConfig.getGcsUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_GCS, accountId))
        .isEqualTo(defaultVmImageConfig.getCacheGCS());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetPluginVersionForVM_onlyAccountLevelConfigExists() {
    String accountId = "acc";
    String globalAccountId = "__GLOBAL_ACCOUNT_ID__";
    VmImageConfig accountLevelVmImageConfig = getVmImageConfig("accountLevel");
    CIExecutionConfig accountLevelExecutionConfig =
        CIExecutionConfig.builder().accountIdentifier(globalAccountId).vmImageConfig(accountLevelVmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(accountId))
        .thenReturn(Optional.of(accountLevelExecutionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier(globalAccountId)).thenReturn(Optional.empty());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ORCHESTRATION, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaOrchestration());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SSCA_ENFORCEMENT, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSscaEnforcement());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_ARTIFACTORY, accountId))
        .isEqualTo(accountLevelVmImageConfig.getArtifactoryUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GIT_CLONE, accountId))
        .isEqualTo(accountLevelVmImageConfig.getGitClone());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ECR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushECR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.ACR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushACR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GCR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushGCR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.GAR, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushGAR());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getS3Upload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_S3, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheS3());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.DOCKER, accountId))
        .isEqualTo(accountLevelVmImageConfig.getBuildAndPushDockerRegistry());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SECURITY, accountId))
        .isEqualTo(accountLevelVmImageConfig.getSecurity());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.RESTORE_CACHE_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheGCS());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.UPLOAD_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getGcsUpload());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.SAVE_CACHE_GCS, accountId))
        .isEqualTo(accountLevelVmImageConfig.getCacheGCS());
    assertThat(ciExecutionConfigService.getPluginVersionForVM(CIStepInfoType.IACM, accountId))
        .isEqualTo(accountLevelVmImageConfig.getIacmTerraform());
  }

  private static VmImageConfig getVmImageConfig(String level) {
    return VmImageConfig.builder()
        .sscaOrchestration(level + "sscaOrchestrationTag")
        .sscaEnforcement(level + "sscaEnforcementTag")
        .slsaVerification(level + "slsaVerificationTag")
        .artifactoryUpload(level + "artifactoryUpload")
        .gitClone(level + "gitClone")
        .buildAndPushACR(level + "buildAndPushACR")
        .buildAndPushECR(level + "buildAndPushECR")
        .buildAndPushGAR(level + "buildAndPushGAR")
        .buildAndPushGCR(level + "buildAndPushGCR")
        .s3Upload(level + "s3Upload")
        .cacheS3(level + "cacheS3")
        .buildAndPushDockerRegistry(level + "buildAndPushDockerRegistry")
        .security(level + "security")
        .gcsUpload(level + "gcsUpload")
        .cacheGCS(level + "cacheGCS")
        .iacmTerraform(level + "iacmTerraform")
        .build();
  }

  private static CIStepConfig getDefaultStepeConfig() {
    return CIStepConfig.builder()
        .sscaOrchestrationConfig(getStepImageConfig("defaultsscaOrchestrationTag"))
        .sscaEnforcementConfig(getStepImageConfig("defaultsscaEnforcementTag"))
        .slsaVerificationConfig(getStepImageConfig("defaultslsaVerificationTag"))
        .provenanceConfig(getStepImageConfig("defaultprovenanceTag"))
        .provenanceGcrConfig(getStepImageConfig("defaultprovenanceGcrTag"))
        .artifactoryUploadConfig(getStepImageConfig("defaultartifactoryUpload"))
        .gitCloneConfig(getStepImageConfig("defaultgitClone"))
        .buildAndPushACRConfig(getStepImageConfig("defaultbuildAndPushACR"))
        .buildAndPushECRConfig(getStepImageConfig("defaultbuildAndPushECR"))
        .buildAndPushGARConfig(getStepImageConfig("defaultbuildAndPushGAR"))
        .buildAndPushGCRConfig(getStepImageConfig("defaultbuildAndPushGCR"))
        .s3UploadConfig(getStepImageConfig("defaults3Upload"))
        .cacheS3Config(getStepImageConfig("defaultcacheS3"))
        .buildAndPushDockerRegistryConfig(getStepImageConfig("defaultbuildAndPushDockerRegistry"))
        .securityConfig(getStepImageConfig("defaultsecurity"))
        .gcsUploadConfig(getStepImageConfig("defaultgcsUpload"))
        .cacheGCSConfig(getStepImageConfig("defaultcacheGCS"))
        .iacmTerraform(getStepImageConfig("defaultiacmTerraform"))
        .vmImageConfig(getVmImageConfig("default"))
        .build();
  }
  private static CIExecutionConfig getCiExecutionConfig(String level) {
    return CIExecutionConfig.builder()
        .sscaOrchestrationTag(level + "sscaOrchestrationTag")
        .sscaEnforcementTag(level + "sscaEnforcementTag")
        .provenanceTag(level + "provenanceTag")
        .provenanceGcrTag(level + "provenanceGcrTag")
        .slsaVerificationTag(level + "slsaVerificationTag")
        .gcsUploadImage(level + "gcsUploadImage")
        .securityImage(level + "securityImage")
        .artifactoryUploadTag(level + "artifactoryUploadTag")
        .buildAndPushGARImage(level + "buildAndPushGARImage")
        .buildAndPushDockerRegistryImage(level + "buildAndPushDockerRegistryImage")
        .buildAndPushGCRImage(level + "buildAndPushGCRImage")
        .buildAndPushECRImage(level + "buildAndPushECRImage")
        .buildAndPushACRImage(level + "buildAndPushACRImage")
        .addOnImage(level + "addOnImage")
        .liteEngineImage(level + "liteEngineImage")
        .cacheS3Tag(level + "cacheS3Tag")
        .s3UploadImage(level + "s3UploadImage")
        .cacheGCSTag(level + "cacheGCSTag")
        .gitCloneImage(level + "gitCloneImage")
        .build();
  }
  private static StepImageConfig getStepImageConfig(String image) {
    return StepImageConfig.builder()
        .image(image)
        .entrypoint(List.of("entrypoint"))
        .windowsEntrypoint(List.of("windowsEntrypoint"))
        .build();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testUpdateCIContainerTags_sscaPlugins_forVM() {
    VmImageConfig vmImageConfig =
        VmImageConfig.builder().sscaEnforcement("sscaEnforcement").buildAndPushDockerRegistry("docker").build();
    CIExecutionConfig executionConfig = CIExecutionConfig.builder().vmImageConfig(vmImageConfig).build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    ArrayList<Operation> operations = new ArrayList<>();
    Operation operation1 = new Operation();
    operation1.setField(PluginField.SSCA_ENFORCEMENT.getLabel());
    operation1.setValue("tag3");
    Operation operation2 = new Operation();
    operation2.setField(PluginField.SSCA_ORCHESTRATION.getLabel());
    operation2.setValue("tag4");
    operations.add(operation1);
    operations.add(operation2);

    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.VM);
    assertThat(executionConfig.getVmImageConfig().getBuildAndPushDockerRegistry()).isEqualTo("docker");
    assertThat(executionConfig.getVmImageConfig().getSscaEnforcement()).isEqualTo("tag3");
    assertThat(executionConfig.getVmImageConfig().getSscaOrchestration()).isEqualTo("tag4");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testUpdateCIContainerTags_sscaPlugins_forK8() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .addOnImage("addon")
                                            .accountIdentifier("acct")
                                            .sscaEnforcementTag("sscaEnforcementTag")
                                            .sscaOrchestrationTag("sscaOrchestrationTag")
                                            .slsaVerificationTag("slsaVerificationTag")
                                            .provenanceTag("provenanceTag")
                                            .provenanceGcrTag("provenanceGcrTag")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));

    ArrayList<Operation> operations = new ArrayList<>();
    Operation operation1 = new Operation();
    operation1.setField(PluginField.PROVENANCE.getLabel());
    operation1.setValue("tag1");
    Operation operation2 = new Operation();
    operation2.setField(PluginField.PROVENANCE_GCR.getLabel());
    operation2.setValue("tag2");
    Operation operation3 = new Operation();
    operation3.setField(PluginField.SSCA_ENFORCEMENT.getLabel());
    operation3.setValue("tag3");
    Operation operation4 = new Operation();
    operation4.setField(PluginField.SSCA_ORCHESTRATION.getLabel());
    operation4.setValue("tag4");
    Operation operation5 = new Operation();
    operation5.setField(PluginField.SLSA_VERIFICATION.getLabel());
    operation5.setValue("tag5");
    operations.add(operation1);
    operations.add(operation2);
    operations.add(operation3);
    operations.add(operation4);
    operations.add(operation5);

    ciExecutionConfigService.updateCIContainerTags("acct", operations, StageInfraDetails.Type.K8);
    assertThat(executionConfig.getAddOnImage()).isEqualTo("addon");
    assertThat(executionConfig.getSscaEnforcementTag()).isEqualTo("tag3");
    assertThat(executionConfig.getSscaOrchestrationTag()).isEqualTo("tag4");
    assertThat(executionConfig.getSlsaVerificationTag()).isEqualTo("tag5");
    assertThat(executionConfig.getProvenanceGcrTag()).isEqualTo("tag2");
    assertThat(executionConfig.getProvenanceTag()).isEqualTo("tag1");
  }
}
