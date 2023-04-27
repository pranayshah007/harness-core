/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.common;

import static io.harness.azure.model.AzureConstants.ACR_ACCESS_KEYS_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.ACR_USERNAME_BLANK_VALIDATION_MSG;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.context.AzureContainerRegistryClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azureconnector.AzureContainerRegistryConnectorDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.azure.resourcemanager.containerregistry.models.AccessKeyType;
import com.azure.resourcemanager.containerregistry.models.Registry;
import com.azure.resourcemanager.containerregistry.models.RegistryCredentials;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureContainerRegistryServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String RESOURCE_GROUP_NAME = "resourceGroupName";
  private static final String SUBSCRIPTION_ID = "subscriptionId";
  private static final String AZURE_REGISTRY_NAME = "azureRegistryName";

  @Mock private AzureContainerRegistryClient mockAzureContainerRegistryClient;

  @Spy @InjectMocks AzureContainerRegistryService azureContainerRegistryService;

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetContainerRegistryCredentials() {
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureContainerRegistryConnectorDTO azureContainerRegistryConnectorDTO = AzureContainerRegistryConnectorDTO.builder()
                                                                                .subscriptionId(SUBSCRIPTION_ID)
                                                                                .azureRegistryName(AZURE_REGISTRY_NAME)
                                                                                .resourceGroupName("")
                                                                                .build();
    RegistryCredentials registryCredentials = mock(RegistryCredentials.class);
    ArgumentCaptor<AzureContainerRegistryClientContext> argumentCaptor =
        ArgumentCaptor.forClass(AzureContainerRegistryClientContext.class);
    doReturn(Optional.of(registryCredentials))
        .when(mockAzureContainerRegistryClient)
        .getContainerRegistryCredentials(argumentCaptor.capture());
    Registry registry = mock(Registry.class);
    doReturn(RESOURCE_GROUP_NAME).when(registry).resourceGroupName();
    doReturn(Optional.of(registry))
        .when(mockAzureContainerRegistryClient)
        .findFirstContainerRegistryByNameOnSubscription(azureConfig, SUBSCRIPTION_ID, AZURE_REGISTRY_NAME);

    RegistryCredentials resultRegistryCredentials =
        azureContainerRegistryService.getContainerRegistryCredentials(azureConfig, azureContainerRegistryConnectorDTO);

    Assertions.assertThat(resultRegistryCredentials).isEqualTo(registryCredentials);
    AzureContainerRegistryClientContext azureContainerRegistryClientContext = argumentCaptor.getValue();
    Assertions.assertThat(azureContainerRegistryClientContext.getResourceGroupName()).isEqualTo(RESOURCE_GROUP_NAME);
    Assertions.assertThat(azureContainerRegistryClientContext.getRegistryName()).isEqualTo(AZURE_REGISTRY_NAME);
    Assertions.assertThat(azureContainerRegistryClientContext.getSubscriptionId()).isEqualTo(SUBSCRIPTION_ID);
    Assertions.assertThat(azureContainerRegistryClientContext.getAzureConfig()).isEqualTo(azureConfig);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testNoContainerRegistryCredentials() {
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureContainerRegistryConnectorDTO azureContainerRegistryConnectorDTO = AzureContainerRegistryConnectorDTO.builder()
                                                                                .subscriptionId(SUBSCRIPTION_ID)
                                                                                .azureRegistryName(AZURE_REGISTRY_NAME)
                                                                                .resourceGroupName(RESOURCE_GROUP_NAME)
                                                                                .build();
    doReturn(Optional.empty()).when(mockAzureContainerRegistryClient).getContainerRegistryCredentials(any());
    doReturn(Optional.of(Registry.class))
        .when(mockAzureContainerRegistryClient)
        .findFirstContainerRegistryByNameOnSubscription(azureConfig, SUBSCRIPTION_ID, AZURE_REGISTRY_NAME);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> azureContainerRegistryService.getContainerRegistryCredentials(
                            azureConfig, azureContainerRegistryConnectorDTO))
        .withMessageContaining("Not found container registry credentials");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testNoFirstContainerRegistryByName() {
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureContainerRegistryConnectorDTO azureContainerRegistryConnectorDTO = AzureContainerRegistryConnectorDTO.builder()
                                                                                .subscriptionId(SUBSCRIPTION_ID)
                                                                                .azureRegistryName(AZURE_REGISTRY_NAME)
                                                                                .resourceGroupName("")
                                                                                .build();

    doReturn(Optional.empty())
        .when(mockAzureContainerRegistryClient)
        .findFirstContainerRegistryByNameOnSubscription(azureConfig, SUBSCRIPTION_ID, AZURE_REGISTRY_NAME);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> azureContainerRegistryService.getContainerRegistryCredentials(
                            azureConfig, azureContainerRegistryConnectorDTO))
        .withMessageContaining("Not found Azure container registry by name");
  }

  @Test
  @Owner(developers = {ANIL, IVAN})
  @Category(UnitTests.class)
  public void testExecuteTaskInternalACRFailure() {
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureContainerRegistryConnectorDTO azureContainerRegistryConnectorDTO = buildAzureContainerRegistry();

    RegistryCredentials registryCredentials = Mockito.mock(RegistryCredentials.class);
    doReturn(registryCredentials)
        .when(azureContainerRegistryService)
        .getContainerRegistryCredentials(Mockito.eq(azureConfig), any());
    doReturn("").when(registryCredentials).username();

    assertThatThrownBy(()
                           -> azureContainerRegistryService.updateACRDockerSettingByCredentials(
                               azureContainerRegistryConnectorDTO, azureConfig, Collections.emptyMap()))
        .isInstanceOf(InvalidArgumentsException.class)
        .matches(ex -> ex.getMessage().equals(ACR_USERNAME_BLANK_VALIDATION_MSG));

    doReturn("testUser").when(registryCredentials).username();
    Map<AccessKeyType, String> accessKeyTypeStringMap = new HashMap<>();
    doReturn(accessKeyTypeStringMap).when(registryCredentials).accessKeys();
    assertThatThrownBy(()
                           -> azureContainerRegistryService.updateACRDockerSettingByCredentials(
                               azureContainerRegistryConnectorDTO, azureConfig, Collections.emptyMap()))
        .isInstanceOf(InvalidArgumentsException.class)
        .matches(ex -> ex.getMessage().equals(ACR_ACCESS_KEYS_BLANK_VALIDATION_MSG));
  }

  private AzureContainerRegistryConnectorDTO buildAzureContainerRegistry() {
    return AzureContainerRegistryConnectorDTO.builder()
        .azureRegistryLoginServer("azure.registry.test.com")
        .azureRegistryName("test")
        .subscriptionId(SUBSCRIPTION_ID)
        .resourceGroupName(RESOURCE_GROUP_NAME)
        .build();
  }
}
