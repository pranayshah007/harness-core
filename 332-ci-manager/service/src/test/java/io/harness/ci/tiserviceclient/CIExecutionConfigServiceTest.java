/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.tiserviceclient;

import static io.harness.rule.OwnerRule.AMAN;
import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.when;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.CIExecutionConfig;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.Operation;
import io.harness.ci.config.PluginField;
import io.harness.ci.config.VmImageConfig;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.execution.DeprecatedImageInfo;
import io.harness.ci.executionplan.CIExecutionTestBase;
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
import org.mockito.Mock;

@Slf4j
public class CIExecutionConfigServiceTest extends CIExecutionTestBase {
  @Mock CIExecutionConfigRepository cIExecutionConfigRepository;
  @Inject CIExecutionConfigService ciExecutionConfigService;
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;

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
}
