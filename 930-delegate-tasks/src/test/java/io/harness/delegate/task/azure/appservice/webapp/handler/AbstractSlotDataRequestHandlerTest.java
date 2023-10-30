/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.appservice.AzureAppServiceResourceUtilities;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServicePackageDeploymentContext;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AbstractSlotDataRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureRegistrySettingsAdapter;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AbstractSlotDataRequestHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private AzureRegistrySettingsAdapter azureRegistrySettingsAdapter;
  @Spy private AzureAppServiceResourceUtilities azureResourceUtilities;

  @Mock private AzureWebClientContext clientContext;
  @Mock private AzureLogCallbackProvider logCallbackProvider;

  @InjectMocks
  private AbstractSlotDataRequestHandler<AbstractSlotDataRequest> dataRequestHandler =
      new AbstractSlotDataRequestHandler<AbstractSlotDataRequest>() {
        @Override
        protected AzureWebAppRequestResponse execute(AbstractSlotDataRequest taskRequest, AzureConfig azureConfig,
            AzureLogCallbackProvider logCallbackProvider) {
          return null;
        }

        @Override
        protected Class<AbstractSlotDataRequest> getRequestType() {
          return null;
        }
      };

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToAzureAppServiceDockerDeploymentContextRemovePrevAddedConfigs() {
    final AbstractSlotDataRequest request = createRequest(ImmutableSet.of("APP1"), ImmutableSet.of("CONN1"), null,
        ImmutableSet.of("APP1", "APP2"), ImmutableSet.of("CONN1", "CONN2"), true);

    AzureAppServiceDockerDeploymentContext deploymentContext =
        dataRequestHandler.toAzureAppServiceDockerDeploymentContext(request, clientContext, logCallbackProvider);
    assertThat(deploymentContext.getAppSettingsToAdd()).containsKeys("APP1");
    assertThat(deploymentContext.getAppSettingsToRemove()).containsKeys("APP2");
    assertThat(deploymentContext.getConnSettingsToAdd()).containsKeys("CONN1");
    assertThat(deploymentContext.getConnSettingsToRemove()).containsKeys("CONN2");
    assertThat(deploymentContext.getStartupCommand()).isEqualTo("");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToAzureAppServiceDockerDeploymentContext() {
    final AbstractSlotDataRequest request = createRequest(ImmutableSet.of("APP1", "APP2"),
        ImmutableSet.of("CONN1", "CONN2"), "echo test", Collections.emptySet(), Collections.emptySet(), false);

    AzureAppServiceDockerDeploymentContext deploymentContext =
        dataRequestHandler.toAzureAppServiceDockerDeploymentContext(request, clientContext, logCallbackProvider);
    assertThat(deploymentContext.getAppSettingsToAdd()).containsKeys("APP1", "APP2");
    assertThat(deploymentContext.getAppSettingsToRemove()).isNullOrEmpty();
    assertThat(deploymentContext.getConnSettingsToAdd()).containsKeys("CONN1", "CONN2");
    assertThat(deploymentContext.getConnSettingsToRemove()).isNullOrEmpty();
    assertThat(deploymentContext.getStartupCommand()).isEqualTo("echo test");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void toAzureAppServicePackageDeploymentContextRemovePrevAddedConfigs() {
    final AbstractSlotDataRequest request = createRequest(ImmutableSet.of("APP1"), ImmutableSet.of("CONN1"), null,
        ImmutableSet.of("APP1", "APP2"), ImmutableSet.of("CONN1", "CONN2"), true);

    AzureAppServicePackageDeploymentContext deploymentContext =
        dataRequestHandler.toAzureAppServicePackageDeploymentContext(request, clientContext, null, logCallbackProvider);
    assertThat(deploymentContext.getAppSettingsToAdd()).containsKeys("APP1");
    assertThat(deploymentContext.getAppSettingsToRemove()).containsKeys("APP2");
    assertThat(deploymentContext.getConnSettingsToAdd()).containsKeys("CONN1");
    assertThat(deploymentContext.getConnSettingsToRemove()).containsKeys("CONN2");
    assertThat(deploymentContext.getStartupCommand()).isEqualTo("");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void toAzureAppServicePackageDeploymentContext() {
    final AbstractSlotDataRequest request = createRequest(ImmutableSet.of("APP1", "APP2"),
        ImmutableSet.of("CONN1", "CONN2"), "echo test", Collections.emptySet(), Collections.emptySet(), false);

    AzureAppServicePackageDeploymentContext deploymentContext =
        dataRequestHandler.toAzureAppServicePackageDeploymentContext(request, clientContext, null, logCallbackProvider);
    assertThat(deploymentContext.getAppSettingsToAdd()).containsKeys("APP1", "APP2");
    assertThat(deploymentContext.getAppSettingsToRemove()).isNullOrEmpty();
    assertThat(deploymentContext.getConnSettingsToAdd()).containsKeys("CONN1", "CONN2");
    assertThat(deploymentContext.getConnSettingsToRemove()).isNullOrEmpty();
    assertThat(deploymentContext.getStartupCommand()).isEqualTo("echo test");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testAzureAppServicePackageDeploymentContextDeployOptions() {
    final AbstractSlotDataRequest request = createRequest(ImmutableSet.of("APP1", "APP2"),
        ImmutableSet.of("CONN1", "CONN2"), "echo test", Collections.emptySet(), Collections.emptySet(), false, true);

    AzureAppServicePackageDeploymentContext deploymentContext =
        dataRequestHandler.toAzureAppServicePackageDeploymentContext(request, clientContext, null, logCallbackProvider);
    assertThat(deploymentContext.isCleanDeployment()).isTrue();
    assertThat(deploymentContext.isUseNewDeployApi()).isTrue();
    assertThat(deploymentContext.toDeployOptions().cleanDeployment()).isTrue();
  }

  private AbstractSlotDataRequest createRequest(Set<String> existingAppSettings, Set<String> existingConnStrings,
      String existingScript, Set<String> prevUserAddedAppSettings, Set<String> prevUserAddedConnStrings,
      boolean isPrevUserChangedStartupCommand) {
    return createRequest(existingAppSettings, existingConnStrings, existingScript, prevUserAddedAppSettings,
        prevUserAddedConnStrings, isPrevUserChangedStartupCommand, false);
  }

  private AbstractSlotDataRequest createRequest(Set<String> existingAppSettings, Set<String> existingConnStrings,
      String existingScript, Set<String> prevUserAddedAppSettings, Set<String> prevUserAddedConnStrings,
      boolean isPrevUserChangedStartupCommand, boolean clean) {
    return new AbstractSlotDataRequest() {
      @Override
      public AppSettingsFile getApplicationSettings() {
        return AppSettingsFile.create(createJsonAppSettingsFromStringNames(existingAppSettings));
      }

      @Override
      public AppSettingsFile getConnectionStrings() {
        return AppSettingsFile.create(createJsonAppSettingsFromStringNames(existingConnStrings));
      }

      @Override
      public AppSettingsFile getStartupCommand() {
        return existingScript != null ? AppSettingsFile.create(existingScript) : null;
      }

      @Override
      public Set<String> getPrevExecUserAddedAppSettingNames() {
        return prevUserAddedAppSettings;
      }

      @Override
      public Set<String> getPrevExecUserAddedConnStringNames() {
        return prevUserAddedConnStrings;
      }

      @Override
      public boolean isPrevExecUserChangedStartupCommand() {
        return isPrevUserChangedStartupCommand;
      }

      @Override
      public AzureWebAppRequestType getRequestType() {
        return AzureWebAppRequestType.SLOT_DEPLOYMENT;
      }

      @Override
      public AzureWebAppInfraDelegateConfig getInfrastructure() {
        return AzureWebAppInfraDelegateConfig.builder().build();
      }

      @Override
      public AzureArtifactConfig getArtifact() {
        return AzureContainerArtifactConfig.builder().image("test").tag("test").build();
      }

      @Override
      public boolean isCleanDeployment() {
        return clean;
      }
    };
  }

  private String createJsonAppSettingsFromStringNames(Set<String> names) {
    return names.stream().reduce("[",
               (prev, name)
                   -> prev
                   + (prev.equals("[") ? createJsonEntityForSettings(name) : "," + createJsonEntityForSettings(name)))
        + "]";
  }

  private String createJsonEntityForSettings(String name) {
    return String.format("{\"name\": \"%s\", \"value\": \"test\"}", name);
  }
}
