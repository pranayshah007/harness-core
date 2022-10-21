/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.FETCH_ARTIFACT_FILE;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.appservice.AzureAppServiceResourceUtilities;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDeploymentContext;
import io.harness.delegate.task.azure.artifact.ArtifactDownloadContext;
import io.harness.delegate.task.azure.artifact.AzurePackageArtifactConfig;
import io.harness.delegate.task.azure.common.AutoCloseableWorkingDirectory;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureAppServiceResourceUtilitiesTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private AzureAppServiceDeploymentService azureAppServiceDeploymentService;
  @InjectMocks AzureAppServiceResourceUtilities azureAppServiceResourceUtilities;

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testToArtifactNgDownloadContext() {
    AzureLogCallbackProvider azureLogCallbackProvider = mock(AzureLogCallbackProvider.class);
    AzurePackageArtifactConfig artifactConfig = AzurePackageArtifactConfig.builder().build();
    AutoCloseableWorkingDirectory autoCloseableWorkingDirectory =
        new AutoCloseableWorkingDirectory("repositoryPath", "rootWorkingDirPath");

    ArtifactDownloadContext artifactDownloadContext = azureAppServiceResourceUtilities.toArtifactNgDownloadContext(
        artifactConfig, autoCloseableWorkingDirectory, azureLogCallbackProvider);

    assertThat(artifactDownloadContext.getArtifactConfig()).isEqualTo(artifactConfig);
    assertThat(artifactDownloadContext.getWorkingDirectory().getPath()).startsWith("rootWorkingDirPath");
    assertThat(artifactDownloadContext.getLogCallbackProvider()).isEqualTo(azureLogCallbackProvider);
    assertThat(artifactDownloadContext.getCommandUnitName()).isEqualTo(FETCH_ARTIFACT_FILE);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteSwapSlots() {
    AzureLogCallbackProvider azureLogCallbackProvider = mock(AzureLogCallbackProvider.class);
    AzureWebClientContext azureWebClientContext = AzureWebClientContext.builder()
                                                      .resourceGroupName("resourceGroupName")
                                                      .subscriptionId("subscriptionId")
                                                      .azureConfig(AzureConfig.builder().build())
                                                      .build();

    azureAppServiceResourceUtilities.swapSlots(
        azureWebClientContext, azureLogCallbackProvider, "deploymentSlot", "targetSlot", 10);

    ArgumentCaptor<AzureAppServiceDeploymentContext> azureAppServiceDeploymentContextCaptor =
        ArgumentCaptor.forClass(AzureAppServiceDeploymentContext.class);

    verify(azureAppServiceDeploymentService)
        .swapSlotsUsingCallback(
            azureAppServiceDeploymentContextCaptor.capture(), eq("targetSlot"), eq(azureLogCallbackProvider));
    AzureAppServiceDeploymentContext azureAppServiceDeploymentContext =
        azureAppServiceDeploymentContextCaptor.getValue();
    assertThat(azureAppServiceDeploymentContext.getAzureWebClientContext()).isEqualTo(azureWebClientContext);
    assertThat(azureAppServiceDeploymentContext.getLogCallbackProvider()).isEqualTo(azureLogCallbackProvider);
    assertThat(azureAppServiceDeploymentContext.getSlotName()).isEqualTo("deploymentSlot");
    assertThat(azureAppServiceDeploymentContext.getSteadyStateTimeoutInMin()).isEqualTo(10);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testExecuteSwapSlotsWhenSourceSlotIsProduction() {
    AzureLogCallbackProvider azureLogCallbackProvider = mock(AzureLogCallbackProvider.class);
    AzureWebClientContext azureWebClientContext = AzureWebClientContext.builder()
                                                      .resourceGroupName("resourceGroupName")
                                                      .subscriptionId("subscriptionId")
                                                      .azureConfig(AzureConfig.builder().build())
                                                      .build();

    azureAppServiceResourceUtilities.swapSlots(
        azureWebClientContext, azureLogCallbackProvider, "production", "testing", 10);

    ArgumentCaptor<AzureAppServiceDeploymentContext> azureAppServiceDeploymentContextCaptor =
        ArgumentCaptor.forClass(AzureAppServiceDeploymentContext.class);

    verify(azureAppServiceDeploymentService)
        .swapSlotsUsingCallback(
            azureAppServiceDeploymentContextCaptor.capture(), eq("production"), eq(azureLogCallbackProvider));
    AzureAppServiceDeploymentContext azureAppServiceDeploymentContext =
        azureAppServiceDeploymentContextCaptor.getValue();
    assertThat(azureAppServiceDeploymentContext.getAzureWebClientContext()).isEqualTo(azureWebClientContext);
    assertThat(azureAppServiceDeploymentContext.getLogCallbackProvider()).isEqualTo(azureLogCallbackProvider);
    assertThat(azureAppServiceDeploymentContext.getSlotName()).isEqualTo("testing");
    assertThat(azureAppServiceDeploymentContext.getSteadyStateTimeoutInMin()).isEqualTo(10);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetAppSettingsToRemoveBasedOnPrevUserAddedSettings() {
    // Given null values
    assertThat(azureAppServiceResourceUtilities.getAppSettingsToRemove((Set<String>) null, null)).isNull();
    assertThat(azureAppServiceResourceUtilities.getAppSettingsToRemove(new HashSet<>(), null)).isNull();

    // Given no user added settings
    assertThat(
        azureAppServiceResourceUtilities.getAppSettingsToRemove(Sets.newHashSet("test1", "test2"), new HashMap<>()))
        .containsKeys("test1", "test2");

    // Given both any matching
    assertThat(azureAppServiceResourceUtilities.getAppSettingsToRemove(
                   Sets.newHashSet("test1", "test2"), ImmutableMap.of("test2", appSetting("test2"))))
        .containsKeys("test1");

    // Given all match
    assertThat(azureAppServiceResourceUtilities.getAppSettingsToRemove(Sets.newHashSet("test1", "test2"),
                   ImmutableMap.of("test1", appSetting("test1"), "test2", appSetting("test2"))))
        .isEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetConnStringsToRemoveBasedOnPrevUserAddedSettings() {
    // Given null values
    assertThat(azureAppServiceResourceUtilities.getConnStringsToRemove((Set<String>) null, null)).isNull();
    assertThat(azureAppServiceResourceUtilities.getConnStringsToRemove(new HashSet<>(), null)).isNull();

    // Given no user added settings
    assertThat(
        azureAppServiceResourceUtilities.getConnStringsToRemove(Sets.newHashSet("test1", "test2"), new HashMap<>()))
        .containsKeys("test1", "test2");

    // Given both any matching
    assertThat(azureAppServiceResourceUtilities.getConnStringsToRemove(
                   Sets.newHashSet("test1", "test2"), ImmutableMap.of("test2", connString("test2"))))
        .containsKeys("test1");

    // Given all match
    assertThat(azureAppServiceResourceUtilities.getConnStringsToRemove(Sets.newHashSet("test1", "test2"),
                   ImmutableMap.of("test1", connString("test1"), "test2", connString("test2"))))
        .isEmpty();
  }

  private AzureAppServiceApplicationSetting appSetting(String name) {
    return AzureAppServiceApplicationSetting.builder().name(name).value("value").build();
  }

  private AzureAppServiceConnectionString connString(String name) {
    return AzureAppServiceConnectionString.builder().name(name).build();
  }
}
