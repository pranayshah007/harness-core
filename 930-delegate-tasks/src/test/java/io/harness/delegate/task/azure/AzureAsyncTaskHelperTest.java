/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure;

import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.client.AzureKubernetesClient;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.azure.response.AzureClustersResponse;
import io.harness.delegate.beans.azure.response.AzureRegistriesResponse;
import io.harness.delegate.beans.azure.response.AzureRepositoriesResponse;
import io.harness.delegate.beans.azure.response.AzureResourceGroupsResponse;
import io.harness.delegate.beans.azure.response.AzureSubscriptionsResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.delegatetasks.azure.AzureAsyncTaskHelper;

import com.google.common.io.Resources;
import com.microsoft.azure.management.containerregistry.Registry;
import com.microsoft.azure.management.containerservice.KubernetesCluster;
import com.microsoft.azure.management.resources.Subscription;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class AzureAsyncTaskHelperTest extends CategoryTest {
  @InjectMocks AzureAsyncTaskHelper azureAsyncTaskHelper;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private NGErrorHelper ngErrorHelper;
  @Mock private AzureAuthorizationClient azureAuthorizationClient;
  @Mock private AzureComputeClient azureComputeClient;
  @Mock private AzureContainerRegistryClient azureContainerRegistryClient;
  @Mock private AzureKubernetesClient azureKubernetesClient;
  String clientId = "clientId";
  String tenantId = "tenantId";
  String secretIdentifier = "secretKey";
  String pass = "pass";
  String error = "Failed to do something";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidateSuccessConnectionWithSecretKey() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);
    AzureConfig azureConfig = getAzureConfigSecret();

    doReturn(true).when(azureAuthorizationClient).validateAzureConnection(azureConfig);

    ConnectorValidationResult result = azureAsyncTaskHelper.getConnectorValidationResult(null, azureConnectorDTO);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    verify(secretDecryptionService).decrypt(any(), any());
    verify(azureAuthorizationClient).validateAzureConnection(azureConfig);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidateSuccessConnectionWithCert() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT);
    AzureConfig azureConfig = getAzureConfigCert();

    doReturn(true).when(azureAuthorizationClient).validateAzureConnection(azureConfig);

    ConnectorValidationResult result = azureAsyncTaskHelper.getConnectorValidationResult(null, azureConnectorDTO);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    verify(secretDecryptionService).decrypt(any(), any());
    verify(azureAuthorizationClient).validateAzureConnection(azureConfig);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidationFailedWithSecretKey() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);
    AzureConfig azureConfig = getAzureConfigSecret();
    doThrow(NestedExceptionUtils.hintWithExplanationException("Failed to validate connection for Azure connector.",
                "Please check your Azure connector configuration.",
                new AzureAuthenticationException("Invalid Azure credentials.")))
        .when(azureAuthorizationClient)
        .validateAzureConnection(azureConfig);

    assertThatThrownBy(() -> azureAsyncTaskHelper.getConnectorValidationResult(null, azureConnectorDTO))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(AzureAuthenticationException.class);

    verify(secretDecryptionService).decrypt(any(), any());
    verify(azureAuthorizationClient).validateAzureConnection(azureConfig);
  }

  private AzureConnectorDTO getAzureConnectorDTOWithSecretType(AzureSecretType type) {
    SecretRefData secretRef = SecretRefData.builder()
                                  .identifier(secretIdentifier)
                                  .scope(Scope.ACCOUNT)
                                  .decryptedValue(pass.toCharArray())
                                  .build();
    return AzureConnectorDTO.builder()
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .credential(
            AzureCredentialDTO.builder()
                .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                .config(AzureManualDetailsDTO.builder()
                            .clientId(clientId)
                            .tenantId(tenantId)
                            .authDTO(AzureAuthDTO.builder()
                                         .azureSecretType(type)
                                         .credentials(type == AzureSecretType.KEY_CERT
                                                 ? AzureClientKeyCertDTO.builder().clientCertRef(secretRef).build()
                                                 : AzureClientSecretKeyDTO.builder().secretKey(secretRef).build())
                                         .build())
                            .build())

                .build())
        .build();
  }

  private AzureConnectorDTO getAzureConnectorDTOWithMSI(String clientId) {
    AzureMSIAuthDTO azureMSIAuthDTO;

    if (EmptyPredicate.isNotEmpty(clientId)) {
      AzureUserAssignedMSIAuthDTO azureUserAssignedMSIAuthDTO =
          AzureUserAssignedMSIAuthDTO.builder().clientId(clientId).build();
      azureMSIAuthDTO = AzureMSIAuthUADTO.builder()
                            .azureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
                            .credentials(azureUserAssignedMSIAuthDTO)
                            .build();
    } else {
      azureMSIAuthDTO = AzureMSIAuthSADTO.builder()
                            .azureManagedIdentityType(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
                            .build();
    }

    return AzureConnectorDTO.builder()
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .credential(AzureCredentialDTO.builder()
                        .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                        .config(AzureInheritFromDelegateDetailsDTO.builder().authDTO(azureMSIAuthDTO).build())

                        .build())
        .build();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationResultWithInheritFromDelegateUserAssignedMSIConnection() {
    AzureConfig azureNGConfig = AzureConfig.builder()
                                    .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                    .clientId("testClientId")
                                    .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED)
                                    .build();

    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithMSI("testClientId");

    doReturn(true).when(azureAuthorizationClient).validateAzureConnection(any());

    ConnectorValidationResult result = azureAsyncTaskHelper.getConnectorValidationResult(null, azureConnectorDTO);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);

    verify(azureAuthorizationClient).validateAzureConnection(azureNGConfig);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationResultWithInheritFromDelegateSystemAssignedMSIConnection() {
    AzureConfig azureNGConfig = AzureConfig.builder()
                                    .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                    .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED)
                                    .build();

    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithMSI(null);

    doReturn(true).when(azureAuthorizationClient).validateAzureConnection(any());

    ConnectorValidationResult result = azureAsyncTaskHelper.getConnectorValidationResult(null, azureConnectorDTO);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);

    verify(azureAuthorizationClient).validateAzureConnection(azureNGConfig);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationResultWithInheritFromDelegateUserAssignedMSIConnectionFailure() {
    AzureConfig azureConfig = AzureConfig.builder()
                                  .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                  .clientId("badTestClientId")
                                  .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED)
                                  .build();

    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithMSI("badTestClientId");

    doThrow(NestedExceptionUtils.hintWithExplanationException("Failed to validate connection for Azure connector.",
                "Please check your Azure connector configuration.",
                new AzureAuthenticationException("Failed to connect to Azure cluster.")))
        .when(azureAuthorizationClient)
        .validateAzureConnection(azureConfig);

    assertThatThrownBy(() -> azureAsyncTaskHelper.getConnectorValidationResult(null, azureConnectorDTO))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(AzureAuthenticationException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationResultWithInheritFromDelegateSystemAssignedMSIConnectionFailure() {
    AzureConfig azureConfig = AzureConfig.builder()
                                  .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                  .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED)
                                  .build();
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithMSI(null);
    doThrow(NestedExceptionUtils.hintWithExplanationException("Failed to validate connection for Azure connector.",
                "Please check your Azure connector configuration.",
                new AzureAuthenticationException("Failed to connect to Azure cluster.")))
        .when(azureAuthorizationClient)
        .validateAzureConnection(azureConfig);

    assertThatThrownBy(() -> azureAsyncTaskHelper.getConnectorValidationResult(null, azureConnectorDTO))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(AzureAuthenticationException.class);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSubscriptionWithServicePrincipal() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);
    AzureConfig azureConfig = getAzureConfigSecret();

    Subscription subscription = mock(Subscription.class);
    when(azureComputeClient.listSubscriptions(any())).thenReturn(Collections.singletonList(subscription));
    when(subscription.subscriptionId()).thenReturn("subscriptionId");
    when(subscription.displayName()).thenReturn("subscriptionName");

    AzureSubscriptionsResponse azureSubscriptionsResponse =
        azureAsyncTaskHelper.listSubscriptions(null, azureConnectorDTO);

    verify(azureComputeClient).listSubscriptions(azureConfig);
    assertThat(azureSubscriptionsResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(azureSubscriptionsResponse.getSubscriptions().isEmpty()).isFalse();
    assertThat(azureSubscriptionsResponse.getSubscriptions().get("subscriptionId")).isEqualTo("subscriptionName");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSubscriptionsWithManagedIdentity() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithMSI(clientId);
    AzureConfig azureConfig = AzureConfig.builder()
                                  .clientId(clientId)
                                  .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED)
                                  .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                  .build();

    Subscription subscription = mock(Subscription.class);
    when(azureComputeClient.listSubscriptions(any())).thenReturn(Collections.singletonList(subscription));
    when(subscription.subscriptionId()).thenReturn("subscriptionId");
    when(subscription.displayName()).thenReturn("subscriptionName");

    AzureSubscriptionsResponse azureSubscriptionsResponse =
        azureAsyncTaskHelper.listSubscriptions(null, azureConnectorDTO);

    verify(azureComputeClient).listSubscriptions(azureConfig);
    assertThat(azureSubscriptionsResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(azureSubscriptionsResponse.getSubscriptions().isEmpty()).isFalse();
    assertThat(azureSubscriptionsResponse.getSubscriptions().get("subscriptionId")).isEqualTo("subscriptionName");
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSubscriptionsThrowException() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);

    doThrow(new RuntimeException(error)).when(azureComputeClient).listSubscriptions(any());
    when(ngErrorHelper.getErrorSummary(error)).thenReturn(error);

    AzureSubscriptionsResponse azureSubscriptionsResponse =
        azureAsyncTaskHelper.listSubscriptions(null, azureConnectorDTO);

    assertThat(azureSubscriptionsResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(azureSubscriptionsResponse.getSubscriptions()).isNull();
    assertThat(azureSubscriptionsResponse.getErrorSummary()).isEqualTo(error);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testListResourceGroupWithServicePrincipal() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);
    AzureConfig azureConfig = getAzureConfigSecret();

    when(azureComputeClient.listResourceGroupsNamesBySubscriptionId(any(), any()))
        .thenReturn(Collections.singletonList("resource-group"));

    AzureResourceGroupsResponse resourceGroups =
        azureAsyncTaskHelper.listResourceGroups(null, azureConnectorDTO, "subscriptionId");

    verify(azureComputeClient).listResourceGroupsNamesBySubscriptionId(azureConfig, "subscriptionId");
    assertThat(resourceGroups.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(resourceGroups.getResourceGroups().isEmpty()).isFalse();
    assertThat(resourceGroups.getResourceGroups().get(0)).isEqualTo("resource-group");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testListClusters() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);
    AzureConfig azureConfig = getAzureConfigSecret();

    KubernetesCluster cluster = mock(KubernetesCluster.class);

    when(azureKubernetesClient.listKubernetesClusters(any(), any())).thenReturn(Collections.singletonList(cluster));
    when(cluster.name()).thenReturn("cluster");
    when(cluster.resourceGroupName()).thenReturn("resource-group");

    AzureClustersResponse clustersResponse =
        azureAsyncTaskHelper.listClusters(null, azureConnectorDTO, "subscriptionId", "resource-group");

    verify(azureKubernetesClient).listKubernetesClusters(azureConfig, "subscriptionId");
    assertThat(clustersResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(clustersResponse.getClusters().isEmpty()).isFalse();
    assertThat(clustersResponse.getClusters().get(0)).isEqualTo("cluster");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testListRegistries() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);
    AzureConfig azureConfig = getAzureConfigSecret();

    Registry registry = mock(Registry.class);

    when(azureContainerRegistryClient.listContainerRegistries(any(), any()))
        .thenReturn(Collections.singletonList(registry));
    when(registry.name()).thenReturn("registry");

    AzureRegistriesResponse registriesResponse =
        azureAsyncTaskHelper.listContainerRegistries(null, azureConnectorDTO, "subscriptionId");

    verify(azureContainerRegistryClient).listContainerRegistries(azureConfig, "subscriptionId");
    assertThat(registriesResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(registriesResponse.getContainerRegistries().isEmpty()).isFalse();
    assertThat(registriesResponse.getContainerRegistries().get(0)).isEqualTo("registry");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testListRepositories() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);
    AzureConfig azureConfig = getAzureConfigSecret();

    Registry registry = mock(Registry.class);

    when(azureContainerRegistryClient.findFirstContainerRegistryByNameOnSubscription(any(), any(), any()))
        .thenReturn(Optional.of(registry));
    when(azureContainerRegistryClient.listRepositories(any(), any(), any()))
        .thenReturn(Collections.singletonList("repository"));
    when(registry.name()).thenReturn("registry");
    when(registry.loginServerUrl()).thenReturn("registry.azurecr.io");

    AzureRepositoriesResponse azureRepositoriesResponse =
        azureAsyncTaskHelper.listRepositories(null, azureConnectorDTO, "subscriptionId", "registry");

    verify(azureContainerRegistryClient)
        .findFirstContainerRegistryByNameOnSubscription(azureConfig, "subscriptionId", "registry");
    verify(azureContainerRegistryClient).listRepositories(azureConfig, "subscriptionId", "registry.azurecr.io");
    assertThat(azureRepositoriesResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(azureRepositoriesResponse.getRepositories().isEmpty()).isFalse();
    assertThat(azureRepositoriesResponse.getRepositories().get(0)).isEqualTo("repository");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testClusterConfig() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);
    AzureConfig azureConfig = getAzureConfigSecret();

    KubernetesCluster cluster = mock(KubernetesCluster.class);

    when(azureKubernetesClient.listKubernetesClusters(any(), any())).thenReturn(Collections.singletonList(cluster));
    when(cluster.name()).thenReturn("cluster");
    when(cluster.resourceGroupName()).thenReturn("resource-group");
    when(cluster.userKubeConfigContent())
        .thenReturn(readResourceFileContent("azure/kubeConfigContent.yaml").getBytes());

    KubernetesConfig clusterConfig = azureAsyncTaskHelper.getClusterConfig(
        azureConnectorDTO, "subscriptionId", "resource-group", "cluster", "default", null);

    verify(azureKubernetesClient).listKubernetesClusters(azureConfig, "subscriptionId");
    assertThat(clusterConfig.getMasterUrl()).isEqualTo("https://dummy.hcp.eastus.azmk8s.io:443");
    assertThat(clusterConfig.getNamespace()).isEqualTo("default");
    assertThat(clusterConfig.getCaCert()).isEqualTo("dummycertificateauthoritydata".toCharArray());
    assertThat(clusterConfig.getUsername()).isEqualTo("clusterAdmin_cdp-test-rg_cdp-test-aks".toCharArray());
    assertThat(clusterConfig.getClientCert()).isEqualTo("dummycertificatedata".toCharArray());
    assertThat(clusterConfig.getClientKey()).isEqualTo("dummyclientkeydata".toCharArray());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    AzureConfig azureConfig = getAzureConfigSecret();

    Registry registry = mock(Registry.class);
    List<String> acrResponse = Arrays.asList("v1", "v2", "v3");

    when(azureContainerRegistryClient.findFirstContainerRegistryByNameOnSubscription(any(), any(), any()))
        .thenReturn(Optional.of(registry));
    when(azureContainerRegistryClient.listRepositoryTags(any(), any(), any())).thenReturn(acrResponse);
    when(registry.name()).thenReturn("registry");
    when(registry.loginServerUrl()).thenReturn("registry.azurecr.io");

    List<BuildDetailsInternal> builds =
        azureAsyncTaskHelper.getImageTags(azureConfig, "subscriptionId", "registry", "repository");

    verify(azureContainerRegistryClient)
        .findFirstContainerRegistryByNameOnSubscription(azureConfig, "subscriptionId", "registry");
    verify(azureContainerRegistryClient).listRepositoryTags(azureConfig, "registry.azurecr.io", "repository");
    assertThat(builds).isNotEmpty();
    builds.forEach(build -> {
      assertThat(build.getBuildUrl().startsWith("registry.azurecr.io/repository:v")).isTrue();
      assertThat(acrResponse.contains(build.getNumber())).isTrue();
      assertThat(build.getMetadata().get(ArtifactMetadataKeys.IMAGE).startsWith("registry.azurecr.io/repository:v"))
          .isTrue();
      assertThat(build.getMetadata().get(ArtifactMetadataKeys.TAG).startsWith("v")).isTrue();
      assertThat(build.getMetadata().get(ArtifactMetadataKeys.REGISTRY_HOSTNAME)).isEqualTo("registry.azurecr.io");
    });
  }

  private AzureConfig getAzureConfigSecret() {
    return AzureConfig.builder()
        .clientId(clientId)
        .tenantId(tenantId)
        .key(pass.toCharArray())
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .build();
  }

  private AzureConfig getAzureConfigCert() {
    return AzureConfig.builder()
        .clientId(clientId)
        .tenantId(tenantId)
        .cert(pass.getBytes())
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .azureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT)
        .build();
  }

  public static String readResourceFileContent(String resourceFilePath) {
    ClassLoader classLoader = AzureAsyncTaskHelperTest.class.getClassLoader();
    try {
      return Resources.toString(
          Objects.requireNonNull(classLoader.getResource(resourceFilePath)), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      Assert.fail("Failed reading the file from " + resourceFilePath + " with error " + ex.getMessage());
    }
    return null;
  }
}
