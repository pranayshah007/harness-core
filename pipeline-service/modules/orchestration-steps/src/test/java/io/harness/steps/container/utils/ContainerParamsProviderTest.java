/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.commonconstants.ContainerExecutionConstants;
import io.harness.ci.remote.CiServiceResourceClient;
import io.harness.delegate.beans.ci.pod.CICommonConstants;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.k8s.model.ImageDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.rule.Owner;
import io.harness.steps.container.execution.ContainerDetailsSweepingOutput;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.HashMap;
import java.util.Map;
import org.joor.Reflect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ContainerParamsProviderTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String HARNESS_DEFAULT_LITE_ENGINE_IMAGE = "harness/default-lite-engine";
  private static final String HARNESS_DEFAULT_ADDON_TAG_IMAGE = "harness/default-addon-tag";
  private ContainerExecutionConfig mockConfig = ContainerExecutionConfig.builder()
                                                    .liteEngineImage(HARNESS_DEFAULT_LITE_ENGINE_IMAGE)
                                                    .addonImage(HARNESS_DEFAULT_ADDON_TAG_IMAGE)
                                                    .build();
  @Mock private PmsFeatureFlagHelper mockFeatureFlagHelper;
  @Mock private CiServiceResourceClient mockCiResourceClient;
  @InjectMocks private ContainerParamsProvider containerParamsProvider;

  private AutoCloseable mocks;
  private final Ambiance testAmbiance = testAmbiance();
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    Reflect.on(containerParamsProvider).set("containerExecutionConfig", mockConfig);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getLiteEngineContainerParams() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    CIK8ContainerParams resultParams = containerParamsProvider.getLiteEngineContainerParams(connectorDetails,
        ContainerDetailsSweepingOutput.builder().build(), 1, 1, Map.of("k1", "v1"), Map.of("path", "/volume"), "/work",
        ContainerSecurityContext.builder().build(), "test", testAmbiance, null, "");

    verifyContainerParams(resultParams, HARNESS_DEFAULT_LITE_ENGINE_IMAGE);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getLiteEngineContainerParamsOverriden() {
    String overridenLiteEngineTag = "harness/my_le_tag";
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    CIK8ContainerParams resultParams = containerParamsProvider.getLiteEngineContainerParams(connectorDetails,
        ContainerDetailsSweepingOutput.builder().build(), 1, 1, Map.of("k1", "v1"), Map.of("path", "/volume"), "/work",
        ContainerSecurityContext.builder().build(), "test", testAmbiance,
        CIExecutionImages.builder().liteEngineTag(overridenLiteEngineTag).build(), "");

    verifyContainerParams(resultParams, overridenLiteEngineTag);
  }

  private static void verifyContainerParams(CIK8ContainerParams resultParams, String imageName) {
    assertThat(resultParams.getName()).isEqualTo(CICommonConstants.LITE_ENGINE_CONTAINER_NAME);
    assertThat(resultParams.getVolumeToMountPath()).isEqualTo(Map.of("path", "/volume"));
    assertThat(resultParams.getWorkingDir()).isEqualTo("/work");
    assertThat(resultParams.getEnvVars()).isNotEmpty();
    assertThat(resultParams.getContainerSecrets())
        .isEqualTo(ContainerSecrets.builder()
                       .plainTextSecretsByName(Map.of("k1",
                           SecretParams.builder().value("djE=").secretKey("k1").type(SecretParams.Type.TEXT).build()))
                       .build());
    assertThat(resultParams.getContainerResourceParams())
        .isEqualTo(ContainerResourceParams.builder()
                       .resourceRequestMemoryMiB(101)
                       .resourceLimitMemoryMiB(101)
                       .resourceLimitMilliCpu(101)
                       .resourceRequestMilliCpu(101)
                       .build());
    assertThat(resultParams.getImageDetailsWithConnector())
        .isEqualTo(ImageDetailsWithConnector.builder()
                       .imageConnectorDetails(ConnectorDetails.builder().build())
                       .imageDetails(ImageDetails.builder().name(imageName).build())
                       .build());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getSetupAddonContainerParams() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    CIK8ContainerParams resultParams = containerParamsProvider.getSetupAddonContainerParams(connectorDetails,
        Map.of("path", "/volume"), "/work", ContainerSecurityContext.builder().build(), OSType.Linux, null, "");

    verifyAddonParams(resultParams, HARNESS_DEFAULT_ADDON_TAG_IMAGE);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getSetupAddonContainerParamsOverriden() {
    String overridenAddOnTag = "harness/my_addon_tag";

    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    CIK8ContainerParams resultParams = containerParamsProvider.getSetupAddonContainerParams(connectorDetails,
        Map.of("path", "/volume"), "/work", ContainerSecurityContext.builder().build(), OSType.Linux,
        CIExecutionImages.builder().addonTag(overridenAddOnTag).build(), "");

    verifyAddonParams(resultParams, overridenAddOnTag);
  }

  private void verifyAddonParams(CIK8ContainerParams resultParams, String imageTag) {
    assertThat(resultParams.getName()).isEqualTo(ContainerExecutionConstants.SETUP_ADDON_CONTAINER_NAME);
    assertThat(resultParams.getVolumeToMountPath()).isEqualTo(Map.of("path", "/volume"));
    assertThat(resultParams.getEnvVars().get(ContainerExecutionConstants.HARNESS_WORKSPACE)).isEqualTo("/work");
    assertThat(resultParams.getArgs()).isNotEmpty();
    assertThat(resultParams.getContainerResourceParams())
        .isEqualTo(ContainerResourceParams.builder()
                       .resourceRequestMemoryMiB(100)
                       .resourceLimitMemoryMiB(100)
                       .resourceLimitMilliCpu(100)
                       .resourceRequestMilliCpu(100)
                       .build());

    assertThat(resultParams.getImageDetailsWithConnector())
        .isEqualTo(ImageDetailsWithConnector.builder()
                       .imageConnectorDetails(ConnectorDetails.builder().build())
                       .imageDetails(ImageDetails.builder().name(imageTag).build())
                       .build());
  }

  private Ambiance testAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", ACCOUNT_ID);
    setupAbstractions.put("projectIdentifier", "projectId");
    setupAbstractions.put("orgIdentifier", "orgId");
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setExecutionUuid(generateUuid())
                                              .setRunSequence(1)
                                              .setPipelineIdentifier("pipeline")
                                              .build();
    return Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setMetadata(executionMetadata).build();
  }
}
