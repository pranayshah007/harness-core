/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure;

import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.VLICA;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
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
import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.tag.TagDetails;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.azure.response.AzureAcrTokenTaskResponse;
import io.harness.delegate.beans.azure.response.AzureClustersResponse;
import io.harness.delegate.beans.azure.response.AzureDeploymentSlotsResponse;
import io.harness.delegate.beans.azure.response.AzureRegistriesResponse;
import io.harness.delegate.beans.azure.response.AzureRepositoriesResponse;
import io.harness.delegate.beans.azure.response.AzureResourceGroupsResponse;
import io.harness.delegate.beans.azure.response.AzureSubscriptionsResponse;
import io.harness.delegate.beans.azure.response.AzureTagsResponse;
import io.harness.delegate.beans.azure.response.AzureWebAppNamesResponse;
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
import software.wings.helpers.ext.azure.AzureIdentityAccessTokenResponse;

import com.google.common.io.Resources;
import com.microsoft.azure.management.appservice.DeploymentSlot;
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
import org.assertj.core.util.Lists;
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
  @Mock private AzureManagementClient azureManagementClient;
  private static String CLIENT_ID = "clientId";
  private static String BAD_CLIENT_ID = "badclientId";
  private static String TENANT_ID = "tenantId";
  private static String SECRET_KEY = "secretKey";
  private static String PASS = "pass";
  private static String ERROR = "Failed to do something";
  private static String SUBSCRIPTION_ID = "123456-6543-3456-654321";
  private static String SUBSCRIPTION_NAME = "TEST-SUBSCRIPTION";
  private static String RESOURCE_GROUP = "test-rg";
  private static String CLUSTER = "aks-test-cluster";
  private static String REPOSITORY = "test/appimage";
  private static String REGISTRY = "testreg";
  private static String REGISTRY_URL = format("%s.azurecr.io", REGISTRY.toLowerCase());

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testValidateSuccessConnectionWithSecretKey() {
    testValidateSuccessConnectionWithServicePrincipal(
        getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testValidateSuccessConnectionWithCert() {
    testValidateSuccessConnectionWithServicePrincipal(
        getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
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

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationResultWithInheritFromDelegateUserAssignedMSIConnection() {
    testValidateSuccessConnectionWithManagedIdentity(getAzureConfigUserAssignedMSI(), CLIENT_ID);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationResultWithInheritFromDelegateSystemAssignedMSIConnection() {
    testValidateSuccessConnectionWithManagedIdentity(getAzureConfigSystemAssignedMSI(), null);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationResultWithInheritFromDelegateUserAssignedMSIConnectionFailure() {
    testValidateSuccessConnectionWithManagedIdentityFailure(
        getAzureConfigUserAssignedMSI(BAD_CLIENT_ID), BAD_CLIENT_ID);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationResultWithInheritFromDelegateSystemAssignedMSIConnectionFailure() {
    testValidateSuccessConnectionWithManagedIdentityFailure(getAzureConfigSystemAssignedMSI(), null);
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testGetSubscriptionUsingServicePrincipalWithSecret() {
    testGetSubscriptions(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetSubscriptionUsingServicePrincipalWithCertificate() {
    testGetSubscriptions(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testGetSubscriptionsWithUserAssignedManagedIdentity() {
    testGetSubscriptions(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetSubscriptionsWithSystemAssignedManagedIdentity() {
    testGetSubscriptions(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSubscriptionsThrowException() {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);

    doThrow(new RuntimeException(ERROR)).when(azureComputeClient).listSubscriptions(any());
    when(ngErrorHelper.getErrorSummary(ERROR)).thenReturn(ERROR);

    AzureSubscriptionsResponse azureSubscriptionsResponse =
        azureAsyncTaskHelper.listSubscriptions(null, azureConnectorDTO);

    assertThat(azureSubscriptionsResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(azureSubscriptionsResponse.getSubscriptions()).isNull();
    assertThat(azureSubscriptionsResponse.getErrorSummary()).isEqualTo(ERROR);
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testListResourceGroupUsingServicePrincipalWithSecret() {
    testListResourceGroups(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListResourceGroupUsingServicePrincipalWithCertificate() {
    testListResourceGroups(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListResourceGroupUsingUserAssignedManagedIdentity() {
    testListResourceGroups(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListResourceGroupUsingSystemAssignedManagedIdentity() {
    testListResourceGroups(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppNamesUsingServicePrincipalWithSecret() {
    testListWebAppNames(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppNamesUsingServicePrincipalWithCertificate() {
    testListWebAppNames(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppNamesUsingUserAssignedManagedIdentity() {
    testListWebAppNames(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppNamesUsingSystemAssignedManagedIdentity() {
    testListWebAppNames(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppDeploymentSlotsUsingServicePrincipalWithSecret() {
    testListWebAppDeploymentSlots(
        getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppDeploymentSlotsUsingServicePrincipalWithCertificate() {
    testListWebAppDeploymentSlots(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppDeploymentSlotsUsingUserAssignedManagedIdentity() {
    testListWebAppDeploymentSlots(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppDeploymentSlotsUsingSystemAssignedManagedIdentity() {
    testListWebAppDeploymentSlots(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testListClustersUsingServicePrincipalWithSecret() {
    testListClusters(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListClustersUsingServicePrincipalWithCertificate() {
    testListClusters(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListClustersUsingUserAssignedManagedIdentity() {
    testListClusters(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListClustersUsingSystemAssignedManagedIdentity() {
    testListClusters(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testListRegistriesUsingServicePrincipalWithSecret() {
    testListRegistries(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRegistriesUsingServicePrincipalWithCertificate() {
    testListRegistries(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRegistriesUsingUserAssignedManagedIdentity() {
    testListRegistries(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRegistriesUsingSystemAssignedManagedIdentity() {
    testListRegistries(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testListRepositoriesUsingServicePrincipalWithSecret() {
    testListRepositories(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRepositoriesUsingServicePrincipalWithCertificate() {
    testListRepositories(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRepositoriesUsingUserAssignedManagedIdentity() {
    testListRepositories(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRepositoriesUsingSystemAssignedManagedIdentity() {
    testListRepositories(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testClusterConfigUsingServicePrincipalWithSecret() {
    testClusterConfig(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testClusterConfigUsingServicePrincipalWithCertificate() {
    testClusterConfig(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testClusterConfigUsingUserAssignedManagedIdentity() {
    testClusterConfig(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testClusterConfigUsingSystemAssignedManagedIdentity() {
    testClusterConfig(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testGetImageTagsUsingServicePrincipalWithSecret() {
    testGetImageTags(getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetImageTagsUsingServicePrincipalWithCertificate() {
    testGetImageTags(getAzureConfigCert());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetImageTagsUsingUserAssignedManagedIdentity() {
    testGetImageTags(getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetImageTagsUsingSystemAssignedManagedIdentity() {
    testGetImageTags(getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testListTags() {
    // Given
    TagDetails tag1 = new TagDetails();
    tag1.setTagName("tag-name-1");
    TagDetails tag2 = new TagDetails();
    tag2.setTagName("tag-name-2");

    when(azureManagementClient.listTags(any(), eq("subscriptionId"))).thenReturn(Arrays.asList(tag1, tag2));

    // When
    AzureTagsResponse response = azureAsyncTaskHelper.listTags(
        Collections.emptyList(), getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), "subscriptionId");

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getTags()).isNotNull().hasSize(2).containsExactlyInAnyOrder("tag-name-1", "tag-name-2");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetServicePrincipalCertificateAcrLoginToken() {
    String azureAccessToken = "AzureAccessJWTToken";
    String acrRefreshToken = "ACRRefreshJWTToken";

    AzureIdentityAccessTokenResponse azureIdentityAccessTokenResponse = new AzureIdentityAccessTokenResponse();
    azureIdentityAccessTokenResponse.setAccessToken(azureAccessToken);

    when(azureAuthorizationClient.getUserAccessToken(any())).thenReturn(azureIdentityAccessTokenResponse);
    when(azureContainerRegistryClient.getAcrRefreshToken(REGISTRY, azureIdentityAccessTokenResponse.getAccessToken()))
        .thenReturn(acrRefreshToken);

    AzureAcrTokenTaskResponse azureAcrTokenTaskResponse =
        azureAsyncTaskHelper.getServicePrincipalCertificateAcrLoginToken(
            REGISTRY, Lists.emptyList(), getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT));

    assertThat(azureAcrTokenTaskResponse).isNotNull();
    assertThat(azureAcrTokenTaskResponse.getToken()).isNotNull();
    assertThat(azureAcrTokenTaskResponse.getToken()).isEqualTo(acrRefreshToken);
  }

  private void testValidateSuccessConnectionWithServicePrincipal(
      AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) {
    doReturn(true).when(azureAuthorizationClient).validateAzureConnection(azureConfig);

    ConnectorValidationResult result = azureAsyncTaskHelper.getConnectorValidationResult(null, azureConnectorDTO);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    verify(secretDecryptionService).decrypt(any(), any());
    verify(azureAuthorizationClient).validateAzureConnection(azureConfig);
  }

  private void testValidateSuccessConnectionWithManagedIdentity(AzureConfig azureConfig, String clientId) {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithMSI(clientId);

    doReturn(true).when(azureAuthorizationClient).validateAzureConnection(any());

    ConnectorValidationResult result = azureAsyncTaskHelper.getConnectorValidationResult(null, azureConnectorDTO);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);

    verify(azureAuthorizationClient).validateAzureConnection(azureConfig);
  }

  private void testValidateSuccessConnectionWithManagedIdentityFailure(AzureConfig azureConfig, String clientId) {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithMSI(clientId);
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

    verify(azureAuthorizationClient).validateAzureConnection(azureConfig);
  }

  private void testGetSubscriptions(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) {
    Subscription subscription = mock(Subscription.class);
    when(azureComputeClient.listSubscriptions(any())).thenReturn(Collections.singletonList(subscription));
    when(subscription.subscriptionId()).thenReturn(SUBSCRIPTION_ID);
    when(subscription.displayName()).thenReturn(SUBSCRIPTION_NAME);

    AzureSubscriptionsResponse azureSubscriptionsResponse =
        azureAsyncTaskHelper.listSubscriptions(null, azureConnectorDTO);

    verify(azureComputeClient).listSubscriptions(azureConfig);
    assertThat(azureSubscriptionsResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(azureSubscriptionsResponse.getSubscriptions().isEmpty()).isFalse();
    assertThat(azureSubscriptionsResponse.getSubscriptions().get(SUBSCRIPTION_ID)).isEqualTo(SUBSCRIPTION_NAME);
  }

  private void testListResourceGroups(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) {
    when(azureComputeClient.listResourceGroupsNamesBySubscriptionId(any(), any()))
        .thenReturn(Collections.singletonList("resource-group"));

    AzureResourceGroupsResponse resourceGroups =
        azureAsyncTaskHelper.listResourceGroups(null, azureConnectorDTO, "subscriptionId");

    verify(azureComputeClient).listResourceGroupsNamesBySubscriptionId(azureConfig, "subscriptionId");
    assertThat(resourceGroups.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(resourceGroups.getResourceGroups().isEmpty()).isFalse();
    assertThat(resourceGroups.getResourceGroups().get(0)).isEqualTo("resource-group");
  }

  private void testListWebAppNames(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) {
    when(azureComputeClient.listWebAppNamesBySubscriptionIdAndResourceGroup(any(), any(), any()))
        .thenReturn(Arrays.asList("test-web-app-1", "test-web-app-2"));

    AzureWebAppNamesResponse resourceGroups =
        azureAsyncTaskHelper.listWebAppNames(null, azureConnectorDTO, "subscriptionId", "resourceGroup");

    verify(azureComputeClient)
        .listWebAppNamesBySubscriptionIdAndResourceGroup(azureConfig, "subscriptionId", "resourceGroup");
    assertThat(resourceGroups.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(resourceGroups.getWebAppNames().size()).isEqualTo(2);

    assertThat(resourceGroups.getWebAppNames().get(0)).isEqualTo("test-web-app-1");
    assertThat(resourceGroups.getWebAppNames().get(1)).isEqualTo("test-web-app-2");
  }

  private void testListWebAppDeploymentSlots(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) {
    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);

    when(azureComputeClient.listWebAppDeploymentSlots(any(), any(), any(), any()))
        .thenReturn(Arrays.asList(deploymentSlot));

    when(deploymentSlot.name()).thenReturn("test-qa-name");

    AzureDeploymentSlotsResponse deploymentSlotsResponse = azureAsyncTaskHelper.listDeploymentSlots(
        null, azureConnectorDTO, "subscriptionId", "resourceGroup", "webAppName");

    verify(azureComputeClient).listWebAppDeploymentSlots(azureConfig, "subscriptionId", "resourceGroup", "webAppName");
    assertThat(deploymentSlotsResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(deploymentSlotsResponse.getDeploymentSlots().isEmpty()).isFalse();
    assertThat(deploymentSlotsResponse.getDeploymentSlots().get(0).getName()).isEqualTo("webAppName-test-qa-name");
    assertThat(deploymentSlotsResponse.getDeploymentSlots().get(0).getType()).isEqualTo("non-production");
    assertThat(deploymentSlotsResponse.getDeploymentSlots().get(1).getName()).isEqualTo("webAppName");
    assertThat(deploymentSlotsResponse.getDeploymentSlots().get(1).getType()).isEqualTo("production");
  }

  private void testListClusters(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) {
    KubernetesCluster cluster = mock(KubernetesCluster.class);

    when(azureKubernetesClient.listKubernetesClusters(any(), any())).thenReturn(Collections.singletonList(cluster));
    when(cluster.name()).thenReturn(CLUSTER);
    when(cluster.resourceGroupName()).thenReturn(RESOURCE_GROUP);

    AzureClustersResponse clustersResponse =
        azureAsyncTaskHelper.listClusters(null, azureConnectorDTO, SUBSCRIPTION_ID, RESOURCE_GROUP);

    verify(azureKubernetesClient).listKubernetesClusters(azureConfig, SUBSCRIPTION_ID);
    assertThat(clustersResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(clustersResponse.getClusters().isEmpty()).isFalse();
    assertThat(clustersResponse.getClusters().get(0)).isEqualTo(CLUSTER);
  }

  private void testListRegistries(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) {
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

  private void testListRepositories(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) {
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

  private void testClusterConfig(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) {
    KubernetesCluster cluster = mock(KubernetesCluster.class);

    when(azureKubernetesClient.listKubernetesClusters(any(), any())).thenReturn(Collections.singletonList(cluster));
    when(cluster.name()).thenReturn(CLUSTER);
    when(cluster.resourceGroupName()).thenReturn(RESOURCE_GROUP);
    when(cluster.adminKubeConfigContent())
        .thenReturn(readResourceFileContent("azure/kubeConfigContent.yaml").getBytes());

    KubernetesConfig clusterConfig = azureAsyncTaskHelper.getClusterConfig(
        azureConnectorDTO, SUBSCRIPTION_ID, RESOURCE_GROUP, CLUSTER, "default", null);

    verify(azureKubernetesClient).listKubernetesClusters(azureConfig, SUBSCRIPTION_ID);
    assertThat(clusterConfig.getMasterUrl()).isEqualTo("https://dummy.hcp.eastus.azmk8s.io:443");
    assertThat(clusterConfig.getNamespace()).isEqualTo("default");
    assertThat(clusterConfig.getCaCert()).isEqualTo("dummycertificateauthoritydata".toCharArray());
    assertThat(clusterConfig.getUsername()).isEqualTo("clusterAdmin_cdp-test-rg_cdp-test-aks".toCharArray());
    assertThat(clusterConfig.getClientCert()).isEqualTo("dummycertificatedata".toCharArray());
    assertThat(clusterConfig.getClientKey()).isEqualTo("dummyclientkeydata".toCharArray());
  }

  private void testGetImageTags(AzureConfig azureConfig) {
    Registry registry = mock(Registry.class);
    List<String> acrResponse = Arrays.asList("v1", "v2", "v3");

    when(azureContainerRegistryClient.findFirstContainerRegistryByNameOnSubscription(any(), any(), any()))
        .thenReturn(Optional.of(registry));
    when(azureContainerRegistryClient.listRepositoryTags(any(), any(), any())).thenReturn(acrResponse);
    when(registry.name()).thenReturn(REGISTRY);
    when(registry.loginServerUrl()).thenReturn(REGISTRY_URL);

    List<BuildDetailsInternal> builds =
        azureAsyncTaskHelper.getImageTags(azureConfig, SUBSCRIPTION_ID, REGISTRY, REPOSITORY);

    verify(azureContainerRegistryClient)
        .findFirstContainerRegistryByNameOnSubscription(azureConfig, SUBSCRIPTION_ID, REGISTRY);
    verify(azureContainerRegistryClient).listRepositoryTags(azureConfig, REGISTRY_URL, REPOSITORY);
    assertThat(builds).isNotEmpty();
    builds.forEach(build -> {
      assertThat(build.getBuildUrl().startsWith(format("%s/%s:%s", REGISTRY_URL, REPOSITORY, "v"))).isTrue();
      assertThat(acrResponse.contains(build.getNumber())).isTrue();
      assertThat(build.getMetadata()
                     .get(ArtifactMetadataKeys.IMAGE)
                     .startsWith(format("%s/%s:%s", REGISTRY_URL, REPOSITORY, "v")))
          .isTrue();
      assertThat(build.getMetadata().get(ArtifactMetadataKeys.TAG).startsWith("v")).isTrue();
      assertThat(build.getMetadata().get(ArtifactMetadataKeys.REGISTRY_HOSTNAME)).isEqualTo(REGISTRY_URL);
    });
  }

  private AzureConnectorDTO getAzureConnectorDTOWithSecretType(AzureSecretType type) {
    SecretRefData secretRef =
        SecretRefData.builder().identifier(SECRET_KEY).scope(Scope.ACCOUNT).decryptedValue(PASS.toCharArray()).build();
    return AzureConnectorDTO.builder()
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .credential(
            AzureCredentialDTO.builder()
                .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                .config(AzureManualDetailsDTO.builder()
                            .clientId(CLIENT_ID)
                            .tenantId(TENANT_ID)
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

  private AzureConfig getAzureConfigSecret() {
    return AzureConfig.builder()
        .clientId(CLIENT_ID)
        .tenantId(TENANT_ID)
        .key(PASS.toCharArray())
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .build();
  }

  private AzureConfig getAzureConfigCert() {
    return AzureConfig.builder()
        .clientId(CLIENT_ID)
        .tenantId(TENANT_ID)
        .cert(PASS.getBytes())
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .azureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT)
        .build();
  }

  private AzureConfig getAzureConfigUserAssignedMSI() {
    return getAzureConfigUserAssignedMSI(CLIENT_ID);
  }

  private AzureConfig getAzureConfigUserAssignedMSI(String clientId) {
    return AzureConfig.builder()
        .clientId(clientId)
        .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED)
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .build();
  }

  private AzureConfig getAzureConfigSystemAssignedMSI() {
    return AzureConfig.builder()
        .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED)
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .build();
  }

  private String readResourceFileContent(String resourceFilePath) {
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
