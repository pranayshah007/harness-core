/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure;

import static io.harness.azure.model.AzureConstants.AZURE_AUTH_PLUGIN_INSTALL_HINT;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.VITALIE;
import static io.harness.rule.OwnerRule.VLICA;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
import io.harness.azure.model.AzureConstants;
import io.harness.azure.model.AzureHostConnectionType;
import io.harness.azure.model.AzureKubeconfigFormat;
import io.harness.azure.model.AzureOSType;
import io.harness.azure.model.VirtualMachineData;
import io.harness.azure.model.tag.TagDetails;
import io.harness.azure.utility.AzureUtils;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.azure.AzureConfigContext;
import io.harness.delegate.beans.azure.response.AzureAcrTokenTaskResponse;
import io.harness.delegate.beans.azure.response.AzureClustersResponse;
import io.harness.delegate.beans.azure.response.AzureDeploymentSlotsResponse;
import io.harness.delegate.beans.azure.response.AzureHostResponse;
import io.harness.delegate.beans.azure.response.AzureHostsResponse;
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
import io.harness.filesystem.FileIo;
import io.harness.filesystem.LazyAutoCloseableWorkingDirectory;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.kubeconfig.Exec;
import io.harness.k8s.model.kubeconfig.InteractiveMode;
import io.harness.k8s.model.kubeconfig.KubeConfigAuthPluginHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.delegatetasks.azure.AzureAsyncTaskHelper;
import software.wings.helpers.ext.azure.AzureIdentityAccessTokenResponse;

import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.http.rest.PagedResponseBase;
import com.azure.resourcemanager.appservice.models.DeploymentSlot;
import com.azure.resourcemanager.containerregistry.models.Registry;
import com.azure.resourcemanager.containerservice.models.KubernetesCluster;
import com.azure.resourcemanager.resources.models.Subscription;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import reactor.core.publisher.Mono;

@OwnedBy(HarnessTeam.CDP)
public class AzureAsyncTaskHelperTest extends CategoryTest {
  @InjectMocks AzureAsyncTaskHelper azureAsyncTaskHelper;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private NGErrorHelper ngErrorHelper;
  @Mock private AzureAuthorizationClient azureAuthorizationClient;
  @Mock private AzureManagementClient azureManagementClient;
  @Mock private AzureComputeClient azureComputeClient;
  @Mock private AzureContainerRegistryClient azureContainerRegistryClient;
  @Mock private AzureKubernetesClient azureKubernetesClient;
  @Mock private LogCallback logCallback;
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
    MockedStatic<FileIo> fileIoMocked = mockStatic(FileIo.class);
    fileIoMocked.when(() -> FileIo.writeFile(any(String.class), any())).thenAnswer((Answer<Void>) invocation -> null);
    fileIoMocked.close();
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testValidateSuccessConnectionWithSecretKey() throws IOException {
    testValidateSuccessConnectionWithServicePrincipal(
        getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testValidateSuccessConnectionWithCert() throws IOException {
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

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();

    assertThatThrownBy(() -> azureAsyncTaskHelper.getConnectorValidationResult(azureConfigContextMock))
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
  public void testGetConnectorValidationResultWithInheritFromDelegateUserAssignedMSIConnection() throws IOException {
    testValidateSuccessConnectionWithManagedIdentity(getAzureConfigUserAssignedMSI(), CLIENT_ID);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetConnectorValidationResultWithInheritFromDelegateSystemAssignedMSIConnection() throws IOException {
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
  public void testGetSubscriptionUsingServicePrincipalWithSecret() throws IOException {
    testGetSubscriptions(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetSubscriptionUsingServicePrincipalWithCertificate() throws IOException {
    testGetSubscriptions(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testGetSubscriptionsWithUserAssignedManagedIdentity() throws IOException {
    testGetSubscriptions(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetSubscriptionsWithSystemAssignedManagedIdentity() throws IOException {
    testGetSubscriptions(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetSubscriptionsThrowException() throws IOException {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);

    doThrow(new RuntimeException(ERROR)).when(azureComputeClient).listSubscriptions(any());
    when(ngErrorHelper.getErrorSummary(ERROR)).thenReturn(ERROR);

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    AzureSubscriptionsResponse azureSubscriptionsResponse =
        azureAsyncTaskHelper.listSubscriptions(azureConfigContextMock);

    assertThat(azureSubscriptionsResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(azureSubscriptionsResponse.getSubscriptions()).isNull();
    assertThat(azureSubscriptionsResponse.getErrorSummary()).isEqualTo(ERROR);
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testListResourceGroupUsingServicePrincipalWithSecret() throws IOException {
    testListResourceGroups(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListResourceGroupUsingServicePrincipalWithCertificate() throws IOException {
    testListResourceGroups(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListResourceGroupUsingUserAssignedManagedIdentity() throws IOException {
    testListResourceGroups(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListResourceGroupUsingSystemAssignedManagedIdentity() throws IOException {
    testListResourceGroups(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppNamesUsingServicePrincipalWithSecret() throws IOException {
    testListWebAppNames(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppNamesUsingServicePrincipalWithCertificate() throws IOException {
    testListWebAppNames(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppNamesUsingUserAssignedManagedIdentity() throws IOException {
    testListWebAppNames(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppNamesUsingSystemAssignedManagedIdentity() throws IOException {
    testListWebAppNames(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppDeploymentSlotsUsingServicePrincipalWithSecret() throws IOException {
    testListWebAppDeploymentSlots(
        getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppDeploymentSlotsUsingServicePrincipalWithCertificate() throws IOException {
    testListWebAppDeploymentSlots(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppDeploymentSlotsUsingUserAssignedManagedIdentity() throws IOException {
    testListWebAppDeploymentSlots(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testListWebAppDeploymentSlotsUsingSystemAssignedManagedIdentity() throws IOException {
    testListWebAppDeploymentSlots(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testListClustersUsingServicePrincipalWithSecret() throws IOException {
    testListClusters(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListClustersUsingServicePrincipalWithCertificate() throws IOException {
    testListClusters(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListClustersUsingUserAssignedManagedIdentity() throws IOException {
    testListClusters(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListClustersUsingSystemAssignedManagedIdentity() throws IOException {
    testListClusters(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testListRegistriesUsingServicePrincipalWithSecret() throws IOException {
    testListRegistries(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRegistriesUsingServicePrincipalWithCertificate() throws IOException {
    testListRegistries(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRegistriesUsingUserAssignedManagedIdentity() throws IOException {
    testListRegistries(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRegistriesUsingSystemAssignedManagedIdentity() throws IOException {
    testListRegistries(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testListRepositoriesUsingServicePrincipalWithSecret() throws IOException {
    testListRepositories(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRepositoriesUsingServicePrincipalWithCertificate() throws IOException {
    testListRepositories(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRepositoriesUsingUserAssignedManagedIdentity() throws IOException {
    testListRepositories(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testListRepositoriesUsingSystemAssignedManagedIdentity() throws IOException {
    testListRepositories(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
  }

  @Test
  @Owner(developers = {BUHA, MLUKIC})
  @Category(UnitTests.class)
  public void testClusterConfigUsingServicePrincipalWithSecret() throws IOException {
    testAdminClusterConfig(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
    testUserClusterConfig(getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY), getAzureConfigSecret());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testClusterConfigUsingServicePrincipalWithCertificate() throws IOException {
    testAdminClusterConfig(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
    testUserClusterConfig(getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT), getAzureConfigCert());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testClusterConfigUsingUserAssignedManagedIdentity() throws IOException {
    testAdminClusterConfig(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
    testUserClusterConfig(getAzureConnectorDTOWithMSI(CLIENT_ID), getAzureConfigUserAssignedMSI());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testClusterConfigUsingSystemAssignedManagedIdentity() throws IOException {
    testAdminClusterConfig(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
    testUserClusterConfig(getAzureConnectorDTOWithMSI(null), getAzureConfigSystemAssignedMSI());
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
  public void testListTags() throws IOException {
    // Given
    TagDetails tag1 = new TagDetails();
    tag1.setTagName("tag-name-1");
    TagDetails tag2 = new TagDetails();
    tag2.setTagName("tag-name-2");
    List<TagDetails> tagDetailsList = new ArrayList<>();
    tagDetailsList.add(tag1);
    tagDetailsList.add(tag2);

    PagedFlux<TagDetails> pagedFlux =
        new PagedFlux<>((Supplier<Mono<PagedResponse<TagDetails>>>) ()
                            -> Mono.just(new PagedResponseBase(null, 200, null, tagDetailsList, null, null)));

    when(azureManagementClient.listTags(any(), eq("subscriptionId"))).thenReturn(pagedFlux);

    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.SECRET_KEY);
    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();
    doReturn("subscriptionId").when(azureConfigContextMock).getSubscriptionId();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    // When
    AzureTagsResponse response = azureAsyncTaskHelper.listTags(azureConfigContextMock);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getTags()).isNotNull().hasSize(2).containsExactlyInAnyOrder("tag-name-1", "tag-name-2");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetServicePrincipalCertificateAcrLoginToken() throws IOException {
    String azureAccessToken = "AzureAccessJWTToken";
    String acrRefreshToken = "ACRRefreshJWTToken";

    AzureIdentityAccessTokenResponse azureIdentityAccessTokenResponse = new AzureIdentityAccessTokenResponse();
    azureIdentityAccessTokenResponse.setAccessToken(azureAccessToken);

    when(azureAuthorizationClient.getUserAccessToken(any(), any())).thenReturn(azureIdentityAccessTokenResponse);
    when(azureContainerRegistryClient.getAcrRefreshToken(REGISTRY, azureIdentityAccessTokenResponse.getAccessToken()))
        .thenReturn(acrRefreshToken);

    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT);
    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();
    doReturn(REGISTRY).when(azureConfigContextMock).getContainerRegistry();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    AzureAcrTokenTaskResponse azureAcrTokenTaskResponse = azureAsyncTaskHelper.getAcrLoginToken(azureConfigContextMock);

    assertThat(azureAcrTokenTaskResponse).isNotNull();
    assertThat(azureAcrTokenTaskResponse.getToken()).isNotNull();
    assertThat(azureAcrTokenTaskResponse.getToken()).isEqualTo(acrRefreshToken);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testListHosts() throws IOException {
    // Given
    when(azureComputeClient.listHosts(any(), eq("subscriptionId"), eq("resourceGroup"), eq(AzureOSType.LINUX), any(),
             eq(AzureHostConnectionType.HOSTNAME)))
        .thenReturn(Collections.singletonList(VirtualMachineData.builder().hostName("vm-hostname").build()));

    // When
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithSecretType(AzureSecretType.KEY_CERT);
    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();
    doReturn("subscriptionId").when(azureConfigContextMock).getSubscriptionId();
    doReturn("resourceGroup").when(azureConfigContextMock).getResourceGroup();
    doReturn(AzureOSType.LINUX).when(azureConfigContextMock).getAzureOSType();
    doReturn(AzureHostConnectionType.HOSTNAME).when(azureConfigContextMock).getAzureHostConnectionType();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    AzureHostsResponse response = azureAsyncTaskHelper.listHosts(azureConfigContextMock);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getHosts())
        .isNotNull()
        .hasSize(1)
        .flatExtracting(AzureHostResponse::getHostName)
        .containsExactly("vm-hostname");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex() {
    Registry registry = mock(Registry.class);
    when(registry.loginServerUrl()).thenReturn("url");

    when(azureContainerRegistryClient.findFirstContainerRegistryByNameOnSubscription(any(), anyString(), anyString()))
        .thenReturn(Optional.of(registry));

    when(azureContainerRegistryClient.listRepositoryTags(any(), anyString(), anyString()))
        .thenReturn(Arrays.asList("tag1", "tag2"));

    BuildDetailsInternal result = azureAsyncTaskHelper.getLastSuccessfulBuildFromRegex(
        getAzureConfigSystemAssignedMSI(), "subscription", "registry", "repo", "[ab]");
    assertThat(result.getNumber().equals("tag2"));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testVerifyBuildNumber() {
    String tag = "someTag";
    Registry registry = mock(Registry.class);
    when(registry.loginServerUrl()).thenReturn("url");

    when(azureContainerRegistryClient.findFirstContainerRegistryByNameOnSubscription(any(), anyString(), anyString()))
        .thenReturn(Optional.of(registry));

    when(azureContainerRegistryClient.listRepositoryTags(any(), anyString(), anyString()))
        .thenReturn(Arrays.asList("tag1", "tag2", tag));

    BuildDetailsInternal result = azureAsyncTaskHelper.verifyBuildNumber(
        getAzureConfigSystemAssignedMSI(), "subscription", "registry", "repo", tag);
    assertThat(result.getNumber().equals(tag));
  }

  private void testValidateSuccessConnectionWithServicePrincipal(
      AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) throws IOException {
    doReturn(true).when(azureAuthorizationClient).validateAzureConnection(azureConfig);

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    ConnectorValidationResult result = azureAsyncTaskHelper.getConnectorValidationResult(azureConfigContextMock);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    verify(secretDecryptionService).decrypt(any(), any());
    verify(azureAuthorizationClient).validateAzureConnection(azureConfig);
  }

  private void testValidateSuccessConnectionWithManagedIdentity(AzureConfig azureConfig, String clientId)
      throws IOException {
    AzureConnectorDTO azureConnectorDTO = getAzureConnectorDTOWithMSI(clientId);

    doReturn(true).when(azureAuthorizationClient).validateAzureConnection(any());

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    ConnectorValidationResult result = azureAsyncTaskHelper.getConnectorValidationResult(azureConfigContextMock);

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

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();

    assertThatThrownBy(() -> azureAsyncTaskHelper.getConnectorValidationResult(azureConfigContextMock))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(AzureAuthenticationException.class);

    verify(azureAuthorizationClient).validateAzureConnection(azureConfig);
  }

  private void testGetSubscriptions(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) throws IOException {
    Subscription subscription = mock(Subscription.class);
    when(azureComputeClient.listSubscriptions(any())).thenReturn(Collections.singletonList(subscription));
    when(subscription.subscriptionId()).thenReturn(SUBSCRIPTION_ID);
    when(subscription.displayName()).thenReturn(SUBSCRIPTION_NAME);

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    AzureSubscriptionsResponse azureSubscriptionsResponse =
        azureAsyncTaskHelper.listSubscriptions(azureConfigContextMock);

    verify(azureComputeClient).listSubscriptions(azureConfig);
    assertThat(azureSubscriptionsResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(azureSubscriptionsResponse.getSubscriptions().isEmpty()).isFalse();
    assertThat(azureSubscriptionsResponse.getSubscriptions().get(SUBSCRIPTION_ID)).isEqualTo(SUBSCRIPTION_NAME);
  }

  private void testListResourceGroups(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) throws IOException {
    when(azureComputeClient.listResourceGroupsNamesBySubscriptionId(any(), any()))
        .thenReturn(Collections.singletonList("resource-group"));

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();
    doReturn("subscriptionId").when(azureConfigContextMock).getSubscriptionId();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    AzureResourceGroupsResponse resourceGroups = azureAsyncTaskHelper.listResourceGroups(azureConfigContextMock);

    verify(azureComputeClient).listResourceGroupsNamesBySubscriptionId(azureConfig, "subscriptionId");
    assertThat(resourceGroups.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(resourceGroups.getResourceGroups().isEmpty()).isFalse();
    assertThat(resourceGroups.getResourceGroups().get(0)).isEqualTo("resource-group");
  }

  private void testListWebAppNames(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) throws IOException {
    when(azureComputeClient.listWebAppNamesBySubscriptionIdAndResourceGroup(any(), any(), any()))
        .thenReturn(Arrays.asList("test-web-app-1", "test-web-app-2"));

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();
    doReturn("subscriptionId").when(azureConfigContextMock).getSubscriptionId();
    doReturn("resourceGroup").when(azureConfigContextMock).getResourceGroup();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    AzureWebAppNamesResponse resourceGroups = azureAsyncTaskHelper.listWebAppNames(azureConfigContextMock);

    verify(azureComputeClient)
        .listWebAppNamesBySubscriptionIdAndResourceGroup(azureConfig, "subscriptionId", "resourceGroup");
    assertThat(resourceGroups.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(resourceGroups.getWebAppNames().size()).isEqualTo(2);

    assertThat(resourceGroups.getWebAppNames().get(0)).isEqualTo("test-web-app-1");
    assertThat(resourceGroups.getWebAppNames().get(1)).isEqualTo("test-web-app-2");
  }

  private void testListWebAppDeploymentSlots(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig)
      throws IOException {
    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);

    when(azureComputeClient.listWebAppDeploymentSlots(any(), any(), any(), any()))
        .thenReturn(Arrays.asList(deploymentSlot));

    when(deploymentSlot.name()).thenReturn("test-qa-name");

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();
    doReturn("subscriptionId").when(azureConfigContextMock).getSubscriptionId();
    doReturn("resourceGroup").when(azureConfigContextMock).getResourceGroup();
    doReturn("webAppName").when(azureConfigContextMock).getWebAppName();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    AzureDeploymentSlotsResponse deploymentSlotsResponse =
        azureAsyncTaskHelper.listDeploymentSlots(azureConfigContextMock);

    verify(azureComputeClient).listWebAppDeploymentSlots(azureConfig, "subscriptionId", "resourceGroup", "webAppName");
    assertThat(deploymentSlotsResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(deploymentSlotsResponse.getDeploymentSlots().isEmpty()).isFalse();
    assertThat(deploymentSlotsResponse.getDeploymentSlots().get(0).getName()).isEqualTo("webAppName-test-qa-name");
    assertThat(deploymentSlotsResponse.getDeploymentSlots().get(0).getType()).isEqualTo("non-production");
    assertThat(deploymentSlotsResponse.getDeploymentSlots().get(1).getName()).isEqualTo("webAppName");
    assertThat(deploymentSlotsResponse.getDeploymentSlots().get(1).getType()).isEqualTo("production");
  }

  private void testListClusters(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) throws IOException {
    KubernetesCluster cluster = mock(KubernetesCluster.class);

    when(azureKubernetesClient.listKubernetesClusters(any(), any())).thenReturn(Collections.singletonList(cluster));
    when(cluster.name()).thenReturn(CLUSTER);
    when(cluster.resourceGroupName()).thenReturn(RESOURCE_GROUP);

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();
    doReturn(SUBSCRIPTION_ID).when(azureConfigContextMock).getSubscriptionId();
    doReturn(CLUSTER).when(azureConfigContextMock).getCluster();
    doReturn(RESOURCE_GROUP).when(azureConfigContextMock).getResourceGroup();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    AzureClustersResponse clustersResponse = azureAsyncTaskHelper.listClusters(azureConfigContextMock);

    verify(azureKubernetesClient).listKubernetesClusters(azureConfig, SUBSCRIPTION_ID);
    assertThat(clustersResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(clustersResponse.getClusters().isEmpty()).isFalse();
    assertThat(clustersResponse.getClusters().get(0)).isEqualTo(CLUSTER);
  }

  private void testListRegistries(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) throws IOException {
    Registry registry = mock(Registry.class);

    when(azureContainerRegistryClient.listContainerRegistries(any(), any()))
        .thenReturn(Collections.singletonList(registry));
    when(registry.name()).thenReturn("registry");

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();
    doReturn("subscriptionId").when(azureConfigContextMock).getSubscriptionId();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    AzureRegistriesResponse registriesResponse = azureAsyncTaskHelper.listContainerRegistries(azureConfigContextMock);

    verify(azureContainerRegistryClient).listContainerRegistries(azureConfig, "subscriptionId");
    assertThat(registriesResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(registriesResponse.getContainerRegistries().isEmpty()).isFalse();
    assertThat(registriesResponse.getContainerRegistries().get(0)).isEqualTo("registry");
  }

  private void testListRepositories(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) throws IOException {
    Registry registry = mock(Registry.class);

    when(azureContainerRegistryClient.findFirstContainerRegistryByNameOnSubscription(any(), any(), any()))
        .thenReturn(Optional.of(registry));
    when(azureContainerRegistryClient.listRepositories(any(), any(), any()))
        .thenReturn(Collections.singletonList("repository"));
    when(registry.name()).thenReturn("registry");
    when(registry.loginServerUrl()).thenReturn("registry.azurecr.io");

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();
    doReturn("subscriptionId").when(azureConfigContextMock).getSubscriptionId();
    doReturn("registry").when(azureConfigContextMock).getContainerRegistry();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    AzureRepositoriesResponse azureRepositoriesResponse = azureAsyncTaskHelper.listRepositories(azureConfigContextMock);

    verify(azureContainerRegistryClient)
        .findFirstContainerRegistryByNameOnSubscription(azureConfig, "subscriptionId", "registry");
    verify(azureContainerRegistryClient).listRepositories(azureConfig, "subscriptionId", "registry.azurecr.io");
    assertThat(azureRepositoriesResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(azureRepositoriesResponse.getRepositories().isEmpty()).isFalse();
    assertThat(azureRepositoriesResponse.getRepositories().get(0)).isEqualTo("repository");
  }

  private void testAdminClusterConfig(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) throws IOException {
    String accessToken = "1234567890987654321";

    AzureIdentityAccessTokenResponse azureIdentityAccessTokenResponse =
        AzureIdentityAccessTokenResponse.builder().accessToken(accessToken).build();
    when(azureAuthorizationClient.getUserAccessToken(azureConfig,
             AzureUtils.convertToScope(
                 AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getManagementEndpoint())))
        .thenReturn(azureIdentityAccessTokenResponse);

    when(azureKubernetesClient.getClusterCredentials(any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(readResourceFileContent("azure/adminKubeConfigContent.yaml"));

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();
    doReturn(SUBSCRIPTION_ID).when(azureConfigContextMock).getSubscriptionId();
    doReturn(RESOURCE_GROUP).when(azureConfigContextMock).getResourceGroup();
    doReturn(CLUSTER).when(azureConfigContextMock).getCluster();
    doReturn("default").when(azureConfigContextMock).getNamespace();
    doReturn(true).when(azureConfigContextMock).isUseClusterAdminCredentials();
    doReturn("registry").when(azureConfigContextMock).getContainerRegistry();

    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    KubernetesConfig clusterConfig = azureAsyncTaskHelper.getClusterConfig(azureConfigContextMock, logCallback);

    assertThat(clusterConfig.getMasterUrl())
        .isEqualTo("https://cdp-test-a-cdp-test-rg-20d6a9-19a8a771.hcp.eastus.azmk8s.io:443");
    assertThat(clusterConfig.getNamespace()).isEqualTo("default");
    assertThat(clusterConfig.getCaCert())
        .isEqualTo(
            "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUU2RENDQXRDZ0F3SUJBZ0lRVXU5YmZmK2dPZjZmUjZZY3RkRTE5VEFOQmdrcWhraUc5dzBCQVFzRkFEQU4KTVFzd0NRWURWUVFERXdKallUQWdGdzB5TWpBek1URXdPREUwTXpSYUdBOHlNRFV5TURNeE1UQTRNalF6TkZvdwpEVEVMTUFrR0ExVUVBeE1DWTJFd2dnSWlNQTBHQ1NxR1NJYjNEUUVCQVFVQUE0SUNEd0F3Z2dJS0FvSUNBUUM3CktrVmI5MjFOanFNaXYxKzdjRFV4VEgzNm5QV05ieWRvY3ltQTVXUkx5SkU3SVJ3SzBQQWFkaHNEcHNiKzl5aCsKeU41cE11SktoQ09HQnFIbUQwQlBRSnhmck9CcDMrYUpiN1gyOUxrd3l6dzZhQy9BT1FxalhjK05yVjcvTElPcQpxcGFVTlpWMnB0T3FWVXE2cld5eE1GZnprbGFUeTRWMTBhNWFSMFZKcDg1Mjd3SzBLUTJPQXRwMVhIaE9vUzlTCkhvRUE2TjVFSEd0SVdzMEFBMjVsbTMraDNxM2xaZkQrMXdWQnR1bHRHL2VqS285d3VaZllpVGZSVjRrdDNUd0IKK21iU0t6cFp0c1FQcVZhRy8vcXdJbllkQ1pmUmp0dnA3VnM1bk5IQkpybTlDN3VTQzVJbnNKZk1Vd0w3Vjc4WApOS0JmZko0bGlyMXhEUFZNWHhsWW1udTZmQXFXM1lYOEk3bG9LVGQ4cDlvYkV1citYVzk3Mm1PRFpzQ3RoSURlClNPL0xheWNjU0YvUSt1SHZ2YjJOME85THowcjZsd0JSQk5zdFRvcXZseDdnRFd5dDdwQjloRThUaUNrU3FDaUcKMG5vanhtTFBYUlMxd2lEcURUUDRkcXlhRDV1aTYrOGduTWFaQjhTUTlHMk1hcCtWSWQ1cmdyRjZVbGRzOXFmUApuS3NhVWRPTTBDMlVSejFnZXBmdjM0Ym4xSC83WVJuUk5GSEZGQUU2amh1amIvM091NGpUSEdZeitSRkc4bW5CCjdYYkk0aW5GeFZjRXQ2MC96TEZpcU5MNWtBMDI3R1lMdjNtV1JNQkxKOTJ2UmYrVlZxTVN3d1NCZGVzWVVQcTkKcnF0dFIyOXpvT3ZhRWJMK1JNc2x5TVZaMmZyMkFFVWFnbHFKTEtodVNRSURBUUFCbzBJd1FEQU9CZ05WSFE4QgpBZjhFQkFNQ0FxUXdEd1lEVlIwVEFRSC9CQVV3QXdFQi96QWRCZ05WSFE0RUZnUVUwdllXVkpaazRRYzdVWi9BCjhHTXAwUWpXVDJvd0RRWUpLb1pJaHZjTkFRRUxCUUFEZ2dJQkFCbG9YOExqalROdW5wUlhnam1ERlgyb2F4RGEKajdXaUl1NDc5aXo2eHUwVXNWbVNaQXYwQ05BMTE1c0lKSDJ6aVZTdlpCM1hTL05EN2dGa0NWaXFmVDNOdDRFLwpHdEJ3SXluN2g4dGFnanRzZlEyc0FpUFpJb2QxM2dZK2czSEdzU0FMMGxJc0d6QWVPKzBmanNmVTluSmdFanIvClJiamdSQUpkUTFDL0piTXJhQzJtT1VTc281dmhrOTg5bzg2WkpyZi85WUV4MW1XYlFpT0VTQW1McUZnL205YzcKWkw0N012bWFiYzc2Y0VPVURBeGgzYmlsTFU4anFkZEFCUmFXdzltTGRTdmFSTWNVT0NxeWFZT3Q0NVBRMzlxMQpPNFJMZFhqZmI2b0R5U2FtRUxNMW1zMUFPNGh4c1B5aWZIVFd4TlB2QWVVRGJtc0h3elFobVlCWHJTNjA4ekJRCm41eTkvTEsvT3VtWHpPYW4zdzlKWmxqeFBWbkI2aWRReXFpYWVaRnMvRXdWcXRBK2lOYzdzdDdTZ0ZSZ1hQck8KMElGeUd6My9HMVdCMTFJazVMVU9ZVTFONk00bzU1MURRMW5IeWdkV1gvRG9lSmZ3dklTWk9hMVh0RjQ3TG1KNgpUZnFDTEMzMlBhbTl3V2VxbG1jeTMrRFgyWitQUFRuN1luT2NYeVZEUk9SVFhSYUl2NGlmMW1zMVdwTno2d0hmCkNWQy94YnVmenFjWS9RV1dBSWdGNHJnRG1wQzVJclRnT3pSTU1nSmpJaytEeXdNTkZOaGdsbGZoLy9BRlBlWlQKWUJOUlltdXQveG1RdGZ6TGNrbHpiYXR3cXVSemRQMDc2SFJydTRNTFA3a0RUK01oNENlbHVCaW9BMzI2cXI1cQpSOEM5Z2FSK3lwTktsd2NyCi0tLS0tRU5EIENFUlRJRklDQVRFLS0tLS0F"
                .toCharArray());
    assertThat(clusterConfig.getUsername()).isEqualTo("clusterAdmin_cdp-test-rg_cdp-test-aks".toCharArray());
    assertThat(clusterConfig.getClientCert())
        .isEqualTo(
            "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUZIVENDQXdXZ0F3SUJBZ0lRRUZUTlBybFpONWxSV2V6RzlUQ2NiVEFOQmdrcWhraUc5dzBCQVFzRkFEQU4KTVFzd0NRWURWUVFERXdKallUQWVGdzB5TWpBek1URXdPREUwTXpSYUZ3MHlOREF6TVRFd09ESTBNelJhTURBeApGekFWQmdOVkJBb1REbk41YzNSbGJUcHRZWE4wWlhKek1SVXdFd1lEVlFRREV3eHRZWE4wWlhKamJHbGxiblF3CmdnSWlNQTBHQ1NxR1NJYjNEUUVCQVFVQUE0SUNEd0F3Z2dJS0FvSUNBUURnYWxwb0FjZzcyTnVSZGJzTXJvTUYKZGhDMys1N1VhZlUyLzRsV0ZCenh1eVQwQzRpWjVjYWdTK3RKcFZZLytyYkpvVGZFNy92dm8ydmZYYnVBR05TdwpTMndCWXdwN0NkRmNkYnQyRnF1MXdYV09NcmhWTnlWVWRtbFJOa3hkUHcveGRuS1FvWFJ6QThOVldZcXk0RDlkCno2dTU0eGRnbTVweG4vYUZkR2RDcXdOY2IyOHM4VnFwbmlrTllKb3V5d0lCUk5HUWtxNlg1cHlsOFM5MGRxeEIKQnhJaktOSk9XN2xEeDFXS3Y3bkF2RU9aNFRCdkR6OTMwK2ZuckYzQzZsYXJtODMvWWRkbTVPVTRVVk56YzFFLwpRTFFVSWlFRFlMWHlOM0E2bEV0V0VuUktEWHV4YmM3S3dnMWRJSldRUGNpam9vaG1sRlJ1ZEZSQ3FRQ1poYk5LClYyZjRuVmJ1WkxLVkZnYXBxMUhUKzhSUGxPbUhoY2swMSt6QVJ0UDExMVNkeC9ZcWdwcElhQVlFVHJLUWRPejUKUXNWYTBsZ3dOTlAvSThnNEFmcTZtdWtmV2trSUg3Zkd1TTFTNTRiOFpETGVyc0xkVzhZcnBmOHREUGNCcVdDTwowOXlNVm1XajlUWUxVT1RodHhxTXN6ay9TNTRJWldFT2VzTHJlbERFS3VMRDBRWDZLbk5tZkNiQmNkbDFRclZ1Ck44S1hoaVh3MFVuaWhYZUdYN0tUR1FQOGl0MXo1Q1BSZ2tTOFhaaVI5c0hkb2tNU0p1UXBsOEhLVWpBQkR4NXcKbEtYTXR3dHEzS0h1RkE3UHhqeVduSmc1aTlONldzQkdZQzVETk1HQXplZm9obWVrSzVFSUs1cm1mbWc3azFOYgpNNXdQb0hjc05JaEsyYmRRR1VHbkh3SURBUUFCbzFZd1ZEQU9CZ05WSFE4QkFmOEVCQU1DQmFBd0V3WURWUjBsCkJBd3dDZ1lJS3dZQkJRVUhBd0l3REFZRFZSMFRBUUgvQkFJd0FEQWZCZ05WSFNNRUdEQVdnQlRTOWhaVWxtVGgKQnp0Um44RHdZeW5SQ05aUGFqQU5CZ2txaGtpRzl3MEJBUXNGQUFPQ0FnRUFnU0JKOUNlMUh0N2orbG5JLzYwWgpNYjRPaG5zZUE5UGxUZ1NtS043aVBGUU9KOVFReUlEYjBUdWRTVldzZnRKTEhYOXNNZkt6NkRlUHlhV0cvYm9NClBPYUZ1NGVwU1dkeWhNZm9nZXJJS2g4dFRhbW1tTHptOG1BZVR3bXdKSTI5aHZSVENud3FYZU9jNHppcno2bUQKWmJhbWJsN1pzVXNpZ2paenFzYUFPcHhIWGJRR0FKVkJDc2hDejNPeGU0S0tnbHZUWGVlNEl4dnFwK1lFeU5TawpsOFZUMTVjcTZHUEt5VW0zampoZXJCbnNBbXNqV3VZY1l6RVZNcU0wV2VyVzArZjlhVGZrbEhwUkVyRUNtS3d2Cm90Tkw0RUk2V1pkNURMU1pQV0JVUGx3czh2TFB6L2tsVVJ1NzdUb3l0bmpIdVlabm4zYmZPZjRxUXQ1emlSbHoKRDk5TDdGbU9QU1Qza21wMGFEbTNybTlYajQ2RnhKc1FFMkJwNUZGbHlvRmZnWkt0M05McGtMMkRraXRTa3Mrbwpnc1JKcjJNcHg3NlJVUVU5QVpYdVEya3ZjOERzV0d4Ym5wQ001QURKWDIvczM5YWVYTk5zOFFnVlZ5NVU2VktvCmhmZFdJYTU0SVFtekNUTGYxbE9oeXNvZDNLbStCeHhjZFdGYUc3Q25IV2VQTCsvRUFKZkFHdFQ1dEFTQWhoNTYKc0ttYzYzcTlqZ3ZXdUhmRzRpZzd1UllvMGJOdVh2S1lZMFR4c0lBTlNZR0ZlbjVJUFRsamF6aGl2SW0vTlVMRQp1d3ZpSW83aG1UNVdoeTg1bmk3VEd3Y0VvWkFLcy9DbVZHTlp5UzU0N2NlQmhwTlFEemx1Y0dPaUV4ZWc5ampyCkhMcmtjM0RINllnNWxEblY2V3FEL3pjPQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCF=="
                .toCharArray());
    assertThat(clusterConfig.getClientKey())
        .isEqualTo(
            "LS0tLS1CRUdJTiBSU0EgUFJJVkFURSBLRVktLS0tLQpNSUlKS3dJQkFBS0NBZ0VBNEdwYWFBSElPOWpia1hXN0RLNkRCWFlRdC91ZTFHbjFOditKVmhRYzhic2s5QXVJCm1lWEdvRXZyU2FWV1AvcTJ5YUUzeE8vNzc2TnIzMTI3Z0JqVXNFdHNBV01LZXduUlhIVzdkaGFydGNGMWpqSzQKVlRjbFZIWnBVVFpNWFQ4UDhYWnlrS0YwY3dQRFZWbUtzdUEvWGMrcnVlTVhZSnVhY1ovMmhYUm5RcXNEWEc5dgpMUEZhcVo0cERXQ2FMc3NDQVVUUmtKS3VsK2FjcGZFdmRIYXNRUWNTSXlqU1RsdTVROGRWaXIrNXdMeERtZUV3CmJ3OC9kOVBuNTZ4ZHd1cFdxNXZOLzJIWFp1VGxPRkZUYzNOUlAwQzBGQ0loQTJDMThqZHdPcFJMVmhKMFNnMTcKc1czT3lzSU5YU0NWa0QzSW82S0lacFJVYm5SVVFxa0FtWVd6U2xkbitKMVc3bVN5bFJZR3FhdFIwL3ZFVDVUcApoNFhKTk5mc3dFYlQ5ZGRVbmNmMktvS2FTR2dHQkU2eWtIVHMrVUxGV3RKWU1EVFQveVBJT0FINnVwcnBIMXBKCkNCKzN4cmpOVXVlRy9HUXkzcTdDM1Z2R0s2WC9MUXozQWFsZ2p0UGNqRlpsby9VMkMxRGs0YmNhakxNNVAwdWUKQ0dWaERuckM2M3BReENyaXc5RUYraXB6Wm53bXdYSFpkVUsxYmpmQ2w0WWw4TkZKNG9WM2hsK3lreGtEL0lyZApjK1FqMFlKRXZGMllrZmJCM2FKREVpYmtLWmZCeWxJd0FROGVjSlNsekxjTGF0eWg3aFFPejhZOGxweVlPWXZUCmVsckFSbUF1UXpUQmdNM242SVpucEN1UkNDdWE1bjVvTzVOVFd6T2NENkIzTERTSVN0bTNVQmxCcHg4Q0F3RUEKQVFLQ0FnRUFyOVhsSEZVNUpOdFh2dk4yS2d1YWtXN2V6cW1TMjNCaU9FT2t3aE5rVW11R0dzbm1zRjcvY0ozTApyNXFpcCtLejBleEdIRUxGTGhEbjlzNGttY3ZhNm45T0V4QWRLQ2FiS2t6OUl4dkVVdGRRV3FpWEVmM3hlK3FECnZxUkMxVlVTRXVueC9pempaekx0bkRSYW5xbGlQUWo0enQrR2M2VzRMNHRjeDFoYmlEc2ltUXlmR2FIS25kaFUKNWl4bzRuMGlCd2g3QTBKTEZxNFUwMWpWQy9Yb0pkTjZmSjRCbW0vNEM0bG1GeDcrVm11c3RDZGpvY0ZhdzNMLwo0K0NFWHJMcVVlLzBTa1BKV00vRVlvb21UdkZ1R1plREFidDBEb2Q2U0Z6enpKSmhMUUdzcUZGRU45T0lFZ3BXCkdqL3JzR2NZakU1UGZjQi9IMFI2dVBoZWhldmllOGVzdWhPWkEvaTVvOUxqRU9lZW9rY2NOdlFMelpSNTE5bzMKa2FERWNzekd2MThwQVYvYUNQZncvNngrdGxzazJYSFVXcVdxZjk2R2ttcFB0V0Q1bFIweU1EQmk1MDNGQVltSgpQNjdUVVJOWklFUkpRRFBuU1JJVWx2T0kyNzg2N3kwNXZhdzFKd2ZWWWhCOTVLaFl6b1VjTVBVeUhnbkJXTDB6CnF5VFRUbDNIOWRXVHduUUtIeUViTUNGQ2cwZS9aR1F5VmlZdDBXL2gyNWdiS1hGL3VZR09WZjE5QlJwOHdlTjYKWWlLc0d5L29qVmlLUjVocGg3dFh0Wk9WMW83NDk2VkVreFUvcXVORUlqT0M5RUJ3ZmJRMjE4OHZPTHVTaXJqSwowbk1PUmlkaFBRVVd6WGNNRXNlQ1hybW9yV1JPdjZjdDZkU2JqQW9EUENybDE2UTJMVUVDZ2dFQkFQU2JRaHZhCmpFVUo3KzN5RU5xRDRYMXZWaU1ORHExdHY0Q2ZLN3ZDVm5hMHNVazQyc1JiUG1LUjZYLzBuZ3FUYmZ6SHAvUTYKbGxaWEVFL05zem85UzVDanlBYVJIZXIveW9JRCtpRC8zU2dSR3NHeUxiZXB6b1l1bnh4MFBQRGlORERqSU1FSAp0S2tEc29mbTZTSEdPWUZzcEE4YTl2YnczT3FCaHRMbVBiNzVZMVFOK2RxV0lFbFdudVFzZnExZWxzU0dteGVxCjV0N2NSTDJ0d3FncjZRVGI0U2lpd015aVhRTVZHYkRTcDQwRkVsRDlqT0dwVDhMaUVxQVRZSkdUbndDQW9BSysKNlZWcDBFWEJSUEk0Tk9vVDk1UWpaN0V4UW5nT0hQenVsSUlyQStGL05iZW9MVjFDN0RvNUJoaXBxQ0pHWXZLbwpFK1Q5NEpab0Q4L2RCYThDZ2dFQkFPcmVWU0VraGRtcy9Wc0VTN1ZIQmVYZHU4RVRTeFo2Wi9Nd2NrV2ZOYjJrCittOUlhOWkyTDV4OXY1WmRmc2p2eCt3ZGhlakV5Smpaam81QWt6SFFmTXZCNjFJUldya1J4QUdIVXZqZGg0UWMKS2VaMCt0WENCZ2hZV2ZpYnFMdHpsV1RJSzRnSUtPeUQwWkptOTdtQmp1Rk5NSGpqeE1SQ2lPOU9FSGZYM21pSApwN1k1R2JCSit0cGo5emZZQ01ucGJuTW1vd3AydTc5aG1xamxGak95U0JSV1FRakd2SUczR0pXOUY4dm1sS2Q2ClY3WHdRbnVPR3pTbFoxL2xSMEg5TmQrcHNkTStrM3NlbXBVRDFGWDdqb2hPdS8vMzV5TEpnSi9yWGlLRjBCbWwKekg1SU1DcC9PWnhCTVhSYk5HcldxdjNpUWZMekU0bHgxaEVCdy9OZ1FaRUNnZ0VCQUxTcThIdE9Rd2pEUERvTgo3ekRXOC9nSUFpRkZoYS9IUGdrc2g4clkwYkEvNmlwaEdnU3FPRHZwOWdPU2xDRFBvQTl6RUxTdGlWa2dXV1g0ClV3Y1RPdnNNWGJPci8rTVJKMnc4cjhVcjl4ZWUrcHBTbHIzdmFDRm4waEhjTVI3aWxSWCt6TFNHa29PN3ZXUHYKeEFZME9VbEZDekExQkhDRW0wZUNnQ2pKOHBWWjhtbWxJUVM0bWdSUlBHN2dCbmpiUXBUSnIwZ2Q3UVJ5d1RzdwpXblNJYWtZeWVlM2Z1SFB0QUxKRUpZT2JOREpPcXFhemdCazFTenB3YkwxYlVwcHo1SjhrWWd0bEkwYjVMdUkyCnpFdjBBL0ZZNmlhNnQ5NEN1a3VlY1A3STRWdjdsWlE2dDF4OWxYUXE5L3hSSGhXZFNoaDIwS0xXVGt0MjBTbUcKbHhjNjh2VUNnZ0VCQUxXNW1YeXZXYkYzSEFFVWJjK3hTR3IzQ1pMMmJwN1J6eVJuVThOeTBJNFAzSVhHTDB2YQppelEyUjhyOFJHRU14azkyK0dtRitQL3JOVlh6dVBCT05JRWpaZ1IxMFJCcElwTmNOV0xCWlYxZXZUekhQbDJ1ClppU0cxL1ozMmpKUDJFUEdiWWd2YUJxNFU2dEhhRjFzVlRVV0dHOHhMTW4rQVIzSDlRNEZSTnowT1Z2UkNvTlEKZW53SDVQeWNkeEJqUVVadm1xODU5MEs1TG9XSDI0bmNZOUQ0ZkJGaVUvQzV6cGZ0VzBBMUJNZ2c3VVNreFl2OApCQ2pUNGd4Y3hxblVWWjdkR2U0cytNZkdnaXpTYmJTcGt6cjhVSkpaS2NuTXgyejFIRHp4OUhZanh2bmV1UVhvCnNwYW9DcS9ROGRuSWh6MHhsMzEzZnFKV0poKzZrZmI1ZjBFQ2dnRUJBSjZTN3hUaGExU0dJdndMWDBTVlh1SEQKOCt6anpkbVQ4OWZ6MnMvNmpGRnRneW1DSlZvOFpVWG5FUWxLU0d3aDlDUU9nY29wM0VoYkQxVHJWTlFIOXpUMQpZa2RUZWFoUHZEcURsTHNFRUYzRDBrSTY3QnYxU1FhYVQ1TTJEMEdTSFI1MHptTCswT2ptQTBsNmQzMFJzY3RECkdiSDRQTUlTdWdxS05oZ082WU5adnFMWU5Ma2xQYkpGUTFRR0U1UjZyM1BrYWliTWRPL1NRNWY3Wmh3U0x2T2wKMkxtQkJxYWZ1bmc0VUhJSWRIZERUYjUveUVEYlJva1Q5T3RGQmpucTJvSzZWNWZzZkIram5qMHkxZTRHRk9rcQpWb04vbHJhdTYrTTlwWDRiTGo1WGFWTEloMjVOYy90N0lPdm02WVNqbzJkNVBrV21KaW5UazRXOU1nQVFZRFE9Ci0tLS0tRU5EIFJTQSBQUklWQVRFIEtFWS0tLS0tCF=="
                .toCharArray());
    assertThat(clusterConfig.getAzureConfig()).isNull();
  }

  private void testUserClusterConfig(AzureConnectorDTO azureConnectorDTO, AzureConfig azureConfig) throws IOException {
    String accessToken = "1234567890987654321";
    String aadToken = "poiuytrewqwertyuiop";

    AzureIdentityAccessTokenResponse azureIdentityAccessTokenResponse =
        AzureIdentityAccessTokenResponse.builder().accessToken(accessToken).build();

    AzureIdentityAccessTokenResponse azureIdentityAccessTokenResponseAADToken =
        AzureIdentityAccessTokenResponse.builder().accessToken(aadToken).build();

    if (azureConnectorDTO.getCredential().getAzureCredentialType() == AzureCredentialType.MANUAL_CREDENTIALS) {
      when(azureAuthorizationClient.getUserAccessToken(azureConfig,
               AzureUtils.convertToScope(
                   AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getManagementEndpoint())))
          .thenReturn(azureIdentityAccessTokenResponse);
    } else if (azureConnectorDTO.getCredential().getAzureCredentialType()
        == AzureCredentialType.INHERIT_FROM_DELEGATE) {
      when(azureAuthorizationClient.getUserAccessToken(azureConfig, null)).thenReturn(azureIdentityAccessTokenResponse);
    }

    when(azureKubernetesClient.getClusterCredentials(
             any(), any(), any(), any(), any(), anyBoolean(), eq(AzureKubeconfigFormat.AZURE)))
        .thenReturn(readResourceFileContent("azure/userKubeConfigContent.yaml"));

    String scope = "6dae42f8-4368-4678-94ff-3960e28e3630";
    if (azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.SERVICE_PRINCIPAL_CERT
        || azureConfig.getAzureAuthenticationType() == AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET) {
      scope += "/.default";
    }
    when(azureAuthorizationClient.getUserAccessToken(azureConfig, scope))
        .thenReturn(azureIdentityAccessTokenResponseAADToken);

    AzureConfigContext azureConfigContextMock = mock(AzureConfigContext.class);
    doReturn(azureConnectorDTO).when(azureConfigContextMock).getAzureConnector();
    doReturn(SUBSCRIPTION_ID).when(azureConfigContextMock).getSubscriptionId();
    doReturn(RESOURCE_GROUP).when(azureConfigContextMock).getResourceGroup();
    doReturn(CLUSTER).when(azureConfigContextMock).getCluster();
    doReturn("default").when(azureConfigContextMock).getNamespace();
    doReturn(false).when(azureConfigContextMock).isUseClusterAdminCredentials();
    doReturn("registry").when(azureConfigContextMock).getContainerRegistry();

    MockedStatic mockedStaticKubernetesAuthPlugin = mockStatic(KubeConfigAuthPluginHelper.class);
    when(KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable(any(), any())).thenReturn(false);
    mockLazyAutoCLosableWorkingDirectory(azureConfigContextMock);

    KubernetesConfig clusterConfig = azureAsyncTaskHelper.getClusterConfig(azureConfigContextMock, logCallback);
    mockedStaticKubernetesAuthPlugin.close();

    assertThat(clusterConfig.getMasterUrl())
        .isEqualTo("https://cdp-azure-test-aks-dns-baa4bbdc.hcp.eastus.azmk8s.io:443");
    assertThat(clusterConfig.getNamespace()).isEqualTo("default");
    assertThat(clusterConfig.getCaCert())
        .isEqualTo(
            "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUU2RENDQXRDZ0F3SUJBZ0lRUU45cldqaGVpTFJReXpWRVFYUS9jakFOQmdrcWhraUc5dzBCQVFzRkFEQU4KTVFzd0NRWURWUVFERXdKallUQWdGdzB5TWpBMU1UZ3hOVEU0TURCYUdBOHlNRFV5TURVeE9ERTFNamd3TUZvdwpEVEVMTUFrR0ExVUVBeE1DWTJFd2dnSWlNQTBHQ1NxR1NJYjNEUUVCQVFVQUE0SUNEd0F3Z2dJS0FvSUNBUUNrCmdKSFhQYlV1M1pZS1Jjdi93cWVsUGordm9FY2I4MTE0M1pnWExSemZXM1FkVEVVTENBbDEwb29DTytZaTJRKy8KQjVTN3p1NG9OSjFRbVRGdEtBdW0xUHJpNWc5L003alZwYTVLdk1hZ2pMdUdxVG9QanZkQzZSZFF0UVpiamI0WAoxZC9KNEtkYXU2Zk51b2NmV0ZKMExUMnJocXpVZVk4ODhXeXZFeTI5OGRwZDFTKzRLMGNBUTdvWVhGd2dob1h6CjFKenQvaU5rMFRmRVNFTzZqNmtCZkh3SHlOQnBNK0R1UXZGamd0bmZWV2FYZk9wVVVCZDM4WGoyOTA4Z2tnQUYKL1FxeWozWHRXV0ExWFQ4S2VjZFBmN0hFK2RQMXlHeXJJOWlsdjJFVTZMbEtWOFRqd0FzQ3hvTU1aNWtQKzJhUApFcUlZbVdBZWhKVjRtb1FXeWdkZGJ6N1cyZVNxUmpoSklENUNxRjMrbzA2M1R3V2xUVzFBdXBVR0JJSHVWa2tOCkdubkJUTmNBMmk5VFpiY1VEenMzQnpMN3NEamZ3bDFOS1hUL2craTQzYkFnRkswd3JoTmYzb0phbVdVSU9uVWcKNjd5NE9iNm11K0pnb0Q2bUNFR3FCTXVjakpPaVd5SGZBMDBmZW5hODFWK2Z4cGw2RWNVSW9NZmM2MmY2S1JBawpiRG9qK29ycEF5Wm1SdmRRVzRPNDVVc3VrNWs1dytTTGNBQnI3bkFtc2JOYU85WERueGZWaEhTU2JXeTVGU1U2CnpQakJIRXRSVEkxVEZYYnp6MVZja1FJUHd1ZHJTLzF4N1I0ZGd1MG5OODlwcVd4VHJBRnpDRFQydGpWd2J3OGYKQWozZ1dJTS9wbnRPbS93cUdQMExWTXM2d21jUmF3TklkbXI3dldrVEJRSURBUUFCbzBJd1FEQU9CZ05WSFE4QgpBZjhFQkFNQ0FxUXdEd1lEVlIwVEFRSC9CQVV3QXdFQi96QWRCZ05WSFE0RUZnUVVFVFI1eFdqVjI4akZCYVY0CjZNWmxpRG9PeUU0d0RRWUpLb1pJaHZjTkFRRUxCUUFEZ2dJQkFIVGZRV0w2MUFUVkNTM0Z5a2hFYklEaVkyY2UKS2MzbHdlYmVObW9PR1NYREFqcXV3ZzRlU3BtYy9OQkJqWEh1cGZvMjczbk5NZ2RlaGEyMkNKb1JwbXpQNmNJbgpQRVoxcUsycnhjZFRuVWhZenA3eEhRU3hFYW1aS2traVZLVG5aRHl5Z1REMVpPTi9va2tYbEE4NHQyVGFGTkVpClpMdGczM2RQb0QvaUM4Z2liWDhvOVMyRzJzODRzME94N3pqMDZKSHVOZW5wdWZldGRPSEd2OWYvREVXQ3BQMDcKMzZHaCtSQUZ3TE40ZU1ZMy9JWXJDNFJvUTlCdTlReEVuZlJndmNJS0Q5bHAvOERTVVR2Z1hYYjBTZFV4aUxpcgpYTW9VTGZiZ3dBNmZNL1kvLzFRRytUeERrTjY4VVlBRVE4cGUwZjU5OEU4Q0MyZWh5NU5MRnZHTG5MckhtVUVoCjVtTlF0QmVaNWRjbFFNYktUenVOUUpZWXV6WUtsTnFCSGIwRzdpVzdsdkxoMHlHNXpyVXRBalJGcVFWQllicTcKdHJVTDRuNjFmNzNvYlBqdDMzWm1NR0RDQ1pyT1ZJb3l4aC9WcERmWURZZU1tTGw4a2RWc0hqMGhzVERoOXVpOAp0dmswV0gxYkNBOTlzeXZSdGpvY3c4NUVMTE9RMG83ZCtoSE9FQ3dkVEhVV0loZk1zSUVENzVOUXhSamJ3Nmp2Cnd5UHJxM1R6SnJYVVVpbXJBSUh5N3BrYzMrVlpnMCtscHFiTCtEOXRxQ2dWcWZNeUFWekx0Tm94Nm9aS2w1L2IKQVQydFVURk14cmk4bTFKSXNlVDcveVZ1WVJCWHhrRTFibU5NOCtkL0grTHdjUXhnMVplcEhEVmM5dGRUeHhvRAozWllsOVYrc3YyZWwwdmVjCi0tLS0tRU5EIENFUlRJRklDQVRFLS0tLS0F"
                .toCharArray());
    assertThat(clusterConfig.getUsername()).isEqualTo("clusterUser_cdp-test-rg_cdp-azure-test-aks".toCharArray());
    assertThat(clusterConfig.getAzureConfig().getAadIdToken()).isEqualTo(aadToken);

    // Using Azure Auth Plugin
    if (!AzureAuthenticationType.SERVICE_PRINCIPAL_CERT.equals(azureConfig.getAzureAuthenticationType())) {
      when(azureKubernetesClient.getClusterCredentials(
               any(), any(), any(), any(), any(), anyBoolean(), eq(AzureKubeconfigFormat.EXEC)))
          .thenReturn(readResourceFileContent("azure/userKubeConfigContentExec.yaml"));
      mockedStaticKubernetesAuthPlugin = mockStatic(KubeConfigAuthPluginHelper.class);
      when(KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable(any(), any())).thenReturn(true);

      clusterConfig = azureAsyncTaskHelper.getClusterConfig(azureConfigContextMock, logCallback);
      mockedStaticKubernetesAuthPlugin.close();

      assertThat(clusterConfig.getMasterUrl())
          .isEqualTo("https://cdp-azure-test-aks-dns-baa4bbdc.hcp.eastus.azmk8s.io:443");
      assertThat(clusterConfig.getNamespace()).isEqualTo("default");
      assertThat(clusterConfig.getCaCert())
          .isEqualTo(
              "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUU2RENDQXRDZ0F3SUJBZ0lRUU45cldqaGVpTFJReXpWRVFYUS9jakFOQmdrcWhraUc5dzBCQVFzRkFEQU4KTVFzd0NRWURWUVFERXdKallUQWdGdzB5TWpBMU1UZ3hOVEU0TURCYUdBOHlNRFV5TURVeE9ERTFNamd3TUZvdwpEVEVMTUFrR0ExVUVBeE1DWTJFd2dnSWlNQTBHQ1NxR1NJYjNEUUVCQVFVQUE0SUNEd0F3Z2dJS0FvSUNBUUNrCmdKSFhQYlV1M1pZS1Jjdi93cWVsUGordm9FY2I4MTE0M1pnWExSemZXM1FkVEVVTENBbDEwb29DTytZaTJRKy8KQjVTN3p1NG9OSjFRbVRGdEtBdW0xUHJpNWc5L003alZwYTVLdk1hZ2pMdUdxVG9QanZkQzZSZFF0UVpiamI0WAoxZC9KNEtkYXU2Zk51b2NmV0ZKMExUMnJocXpVZVk4ODhXeXZFeTI5OGRwZDFTKzRLMGNBUTdvWVhGd2dob1h6CjFKenQvaU5rMFRmRVNFTzZqNmtCZkh3SHlOQnBNK0R1UXZGamd0bmZWV2FYZk9wVVVCZDM4WGoyOTA4Z2tnQUYKL1FxeWozWHRXV0ExWFQ4S2VjZFBmN0hFK2RQMXlHeXJJOWlsdjJFVTZMbEtWOFRqd0FzQ3hvTU1aNWtQKzJhUApFcUlZbVdBZWhKVjRtb1FXeWdkZGJ6N1cyZVNxUmpoSklENUNxRjMrbzA2M1R3V2xUVzFBdXBVR0JJSHVWa2tOCkdubkJUTmNBMmk5VFpiY1VEenMzQnpMN3NEamZ3bDFOS1hUL2craTQzYkFnRkswd3JoTmYzb0phbVdVSU9uVWcKNjd5NE9iNm11K0pnb0Q2bUNFR3FCTXVjakpPaVd5SGZBMDBmZW5hODFWK2Z4cGw2RWNVSW9NZmM2MmY2S1JBawpiRG9qK29ycEF5Wm1SdmRRVzRPNDVVc3VrNWs1dytTTGNBQnI3bkFtc2JOYU85WERueGZWaEhTU2JXeTVGU1U2CnpQakJIRXRSVEkxVEZYYnp6MVZja1FJUHd1ZHJTLzF4N1I0ZGd1MG5OODlwcVd4VHJBRnpDRFQydGpWd2J3OGYKQWozZ1dJTS9wbnRPbS93cUdQMExWTXM2d21jUmF3TklkbXI3dldrVEJRSURBUUFCbzBJd1FEQU9CZ05WSFE4QgpBZjhFQkFNQ0FxUXdEd1lEVlIwVEFRSC9CQVV3QXdFQi96QWRCZ05WSFE0RUZnUVVFVFI1eFdqVjI4akZCYVY0CjZNWmxpRG9PeUU0d0RRWUpLb1pJaHZjTkFRRUxCUUFEZ2dJQkFIVGZRV0w2MUFUVkNTM0Z5a2hFYklEaVkyY2UKS2MzbHdlYmVObW9PR1NYREFqcXV3ZzRlU3BtYy9OQkJqWEh1cGZvMjczbk5NZ2RlaGEyMkNKb1JwbXpQNmNJbgpQRVoxcUsycnhjZFRuVWhZenA3eEhRU3hFYW1aS2traVZLVG5aRHl5Z1REMVpPTi9va2tYbEE4NHQyVGFGTkVpClpMdGczM2RQb0QvaUM4Z2liWDhvOVMyRzJzODRzME94N3pqMDZKSHVOZW5wdWZldGRPSEd2OWYvREVXQ3BQMDcKMzZHaCtSQUZ3TE40ZU1ZMy9JWXJDNFJvUTlCdTlReEVuZlJndmNJS0Q5bHAvOERTVVR2Z1hYYjBTZFV4aUxpcgpYTW9VTGZiZ3dBNmZNL1kvLzFRRytUeERrTjY4VVlBRVE4cGUwZjU5OEU4Q0MyZWh5NU5MRnZHTG5MckhtVUVoCjVtTlF0QmVaNWRjbFFNYktUenVOUUpZWXV6WUtsTnFCSGIwRzdpVzdsdkxoMHlHNXpyVXRBalJGcVFWQllicTcKdHJVTDRuNjFmNzNvYlBqdDMzWm1NR0RDQ1pyT1ZJb3l4aC9WcERmWURZZU1tTGw4a2RWc0hqMGhzVERoOXVpOAp0dmswV0gxYkNBOTlzeXZSdGpvY3c4NUVMTE9RMG83ZCtoSE9FQ3dkVEhVV0loZk1zSUVENzVOUXhSamJ3Nmp2Cnd5UHJxM1R6SnJYVVVpbXJBSUh5N3BrYzMrVlpnMCtscHFiTCtEOXRxQ2dWcWZNeUFWekx0Tm94Nm9aS2w1L2IKQVQydFVURk14cmk4bTFKSXNlVDcveVZ1WVJCWHhrRTFibU5NOCtkL0grTHdjUXhnMVplcEhEVmM5dGRUeHhvRAozWllsOVYrc3YyZWwwdmVjCi0tLS0tRU5EIENFUlRJRklDQVRFLS0tLS0F"
                  .toCharArray());
      assertThat(clusterConfig.getUsername()).isEqualTo("clusterUser_cdp-test-rg_cdp-azure-test-aks".toCharArray());
      assertThat(clusterConfig.getAzureConfig().getAadIdToken()).isEqualTo(aadToken);
      assertThat(clusterConfig.getExec())
          .isEqualTo(Exec.builder()
                         .command("kubelogin")
                         .apiVersion("client.authentication.k8s.io/v1beta1")
                         .installHint(AZURE_AUTH_PLUGIN_INSTALL_HINT)
                         .provideClusterInfo(false)
                         .interactiveMode(InteractiveMode.NEVER)
                         .env(null)
                         .args(getArgsForKubeconfig(azureConfig.getAzureAuthenticationType()))
                         .build());
    }
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
        //        .certFilePath(workingDirectory.workingDir().getAbsolutePath() + AzureConstants.DEFAULT_CERT_FILE_NAME
        //        + ".pem")
        .certFilePath(AzureConstants.DEFAULT_CERT_FILE_NAME + ".pem")
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

  private void mockLazyAutoCLosableWorkingDirectory(AzureConfigContext azureConfigContextMock) {
    LazyAutoCloseableWorkingDirectory lazyAutoCloseableWorkingDirectory = mock(LazyAutoCloseableWorkingDirectory.class);
    doReturn(lazyAutoCloseableWorkingDirectory).when(azureConfigContextMock).getCertificateWorkingDirectory();
    doReturn(lazyAutoCloseableWorkingDirectory).when(lazyAutoCloseableWorkingDirectory).createDirectory();
    File certFile = mock(File.class);
    doReturn(certFile).when(lazyAutoCloseableWorkingDirectory).workingDir();
    doReturn("").when(certFile).getAbsolutePath();
  }

  private List<String> getArgsForKubeconfig(AzureAuthenticationType azureAuthenticationType) {
    switch (azureAuthenticationType) {
      case SERVICE_PRINCIPAL_SECRET:
        return Arrays.asList("get-token", "--server-id", "6dae42f8-4368-4678-94ff-3960e28e3630", "--environment",
            "AzurePublicCloud", "--client-id", "clientId", "--tenant-id", "tenantId", "--client-secret", "pass",
            "--login", "spn");
      case SERVICE_PRINCIPAL_CERT:
        return Arrays.asList("get-token", "--server-id", "6dae42f8-4368-4678-94ff-3960e28e3630", "--environment",
            "AzurePublicCloud", "--client-id", "clientId", "--tenant-id", "tenantId", "--client-certificate",
            "azure-cert.pem", "--login", "spn");
      case MANAGED_IDENTITY_SYSTEM_ASSIGNED:
        return Arrays.asList("get-token", "--server-id", "6dae42f8-4368-4678-94ff-3960e28e3630", "--login", "msi");
      case MANAGED_IDENTITY_USER_ASSIGNED:
        return Arrays.asList("get-token", "--server-id", "6dae42f8-4368-4678-94ff-3960e28e3630", "--client-id",
            "clientId", "--login", "msi");
      default:
        throw new UnsupportedOperationException(
            format("AzureAuthenticationType %s is not supported", azureAuthenticationType));
    }
  }
}
