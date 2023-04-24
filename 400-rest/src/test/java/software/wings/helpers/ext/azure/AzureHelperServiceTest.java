/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure;

import static io.harness.azure.AzureEnvironmentType.AZURE;
import static io.harness.azure.AzureEnvironmentType.AZURE_US_GOVERNMENT;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.RICHA;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstructionWithAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.tag.AzureListTagsResponse;
import io.harness.azure.model.tag.TagDetails;
import io.harness.azure.model.tag.TagValue;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.network.Http;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureTagDetails;
import software.wings.beans.AzureVaultConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.Host;
import software.wings.helpers.ext.azure.AksGetCredentialsResponse.AksGetCredentialProperties;
import software.wings.infra.AzureInstanceInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.security.EncryptionService;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.RetryPolicy;
import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.fluent.models.GalleryImageInner;
import com.azure.resourcemanager.compute.fluent.models.GalleryImageVersionInner;
import com.azure.resourcemanager.compute.fluent.models.GalleryInner;
import com.azure.resourcemanager.compute.models.Disallowed;
import com.azure.resourcemanager.compute.models.DiskSkuTypes;
import com.azure.resourcemanager.compute.models.Galleries;
import com.azure.resourcemanager.compute.models.Gallery;
import com.azure.resourcemanager.compute.models.GalleryImage;
import com.azure.resourcemanager.compute.models.GalleryImageIdentifier;
import com.azure.resourcemanager.compute.models.GalleryImageVersion;
import com.azure.resourcemanager.compute.models.GalleryImageVersionPublishingProfile;
import com.azure.resourcemanager.compute.models.GalleryImageVersionStorageProfile;
import com.azure.resourcemanager.compute.models.GalleryImageVersions;
import com.azure.resourcemanager.compute.models.GalleryImages;
import com.azure.resourcemanager.compute.models.ImagePurchasePlan;
import com.azure.resourcemanager.compute.models.OperatingSystemStateTypes;
import com.azure.resourcemanager.compute.models.OperatingSystemTypes;
import com.azure.resourcemanager.compute.models.RecommendedMachineConfiguration;
import com.azure.resourcemanager.compute.models.ReplicationStatus;
import com.azure.resourcemanager.compute.models.TargetRegion;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.compute.models.VirtualMachines;
import com.azure.resourcemanager.containerservice.models.OSType;
import com.azure.resourcemanager.keyvault.KeyVaultManager;
import com.azure.resourcemanager.keyvault.fluent.models.VaultInner;
import com.azure.resourcemanager.keyvault.models.AccessPolicy;
import com.azure.resourcemanager.keyvault.models.CheckNameAvailabilityResult;
import com.azure.resourcemanager.keyvault.models.CreateMode;
import com.azure.resourcemanager.keyvault.models.DeletedVault;
import com.azure.resourcemanager.keyvault.models.Keys;
import com.azure.resourcemanager.keyvault.models.NetworkRuleSet;
import com.azure.resourcemanager.keyvault.models.Secrets;
import com.azure.resourcemanager.keyvault.models.Sku;
import com.azure.resourcemanager.keyvault.models.Vault;
import com.azure.resourcemanager.keyvault.models.Vaults;
import com.azure.resourcemanager.network.models.NetworkInterface;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.fluent.models.ResourceGroupInner;
import com.azure.resourcemanager.resources.fluent.models.SubscriptionInner;
import com.azure.resourcemanager.resources.fluentcore.arm.models.PrivateLinkResource;
import com.azure.resourcemanager.resources.fluentcore.model.Accepted;
import com.azure.resourcemanager.resources.fluentcore.model.Creatable;
import com.azure.resourcemanager.resources.fluentcore.model.CreatedResources;
import com.azure.resourcemanager.resources.fluentcore.utils.PagedConverter;
import com.azure.resourcemanager.resources.models.ForceDeletionResourceType;
import com.azure.resourcemanager.resources.models.Location;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.azure.resourcemanager.resources.models.ResourceGroupExportResult;
import com.azure.resourcemanager.resources.models.ResourceGroupExportTemplateOptions;
import com.azure.resourcemanager.resources.models.ResourceGroups;
import com.azure.resourcemanager.resources.models.Subscription;
import com.azure.resourcemanager.resources.models.SubscriptionPolicies;
import com.azure.resourcemanager.resources.models.SubscriptionState;
import com.azure.resourcemanager.resources.models.Subscriptions;
import com.azure.security.keyvault.keys.KeyAsyncClient;
import com.azure.security.keyvault.secrets.SecretAsyncClient;
import com.microsoft.aad.msal4j.MsalException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDC)
public class AzureHelperServiceTest extends WingsBaseTest {
  @Mock private AzureResourceManager.Configurable configurable;
  @Mock private AzureResourceManager.Authenticated authenticated;
  @Mock private AzureResourceManager azure;
  @Mock private EncryptionService encryptionService;
  @Mock private VirtualMachine vm;
  @Mock private NetworkInterface networkInterface;

  @Mock AzureManagementRestClient azureManagementRestClient;
  @Mock Call<AksGetCredentialsResponse> aksGetCredentialsCall;

  @InjectMocks private AzureHelperService azureHelperService;
  @InjectMocks private AzureDelegateHelperService azureDelegateHelperService;

  @Before
  public void setup() {
    when(authenticated.subscriptions())
        .thenReturn(getSubscriptions(getSubscription("subscriptionId", "Azure Test Subscription 1")));
  }

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testListTags() throws Exception {
    AzureDelegateHelperService spyAzureDelegateHelperService = spy(AzureDelegateHelperService.class);
    on(spyAzureDelegateHelperService).set("encryptionService", encryptionService);

    AzureManagementRestClient azureManagementRestClient = mock(AzureManagementRestClient.class);
    doReturn(azureManagementRestClient).when(spyAzureDelegateHelperService).getAzureManagementRestClient(any());
    doReturn("token").when(spyAzureDelegateHelperService).getAzureBearerAuthToken(any());
    Call<AzureListTagsResponse> responseCall = (Call<AzureListTagsResponse>) mock(Call.class);
    doReturn(responseCall).when(azureManagementRestClient).listTags(anyString(), anyString());

    AzureListTagsResponse azureListTagsResponse = new AzureListTagsResponse();
    io.harness.azure.model.tag.TagDetails tagDetails = new TagDetails();
    tagDetails.setTagName("tagName");
    io.harness.azure.model.tag.TagValue tagValue = new TagValue();
    tagValue.setTagValue("tagValue");
    tagDetails.setValues(asList(tagValue));
    azureListTagsResponse.setValue(asList(tagDetails));

    Response<AzureListTagsResponse> response = Response.success(azureListTagsResponse);
    when(responseCall.execute()).thenReturn(response);

    AzureConfig azureConfig =
        AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();
    List<AzureTagDetails> azureTagDetails =
        spyAzureDelegateHelperService.listTags(azureConfig, emptyList(), "subscripId");
    assertThat(azureTagDetails.get(0).getTagName()).isEqualTo("tagName");
    assertThat(azureTagDetails.get(0).getValues()).isEqualTo(asList("tagValue"));

    Set<String> tags = spyAzureDelegateHelperService.listTagsBySubscription("subscripId", azureConfig, emptyList());
    assertThat(tags).contains("tagName");
  }

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetAzureBearerAuthToken() throws Exception {
    ClientSecretCredentialBuilder clientSecretCredentialBuilder = mock(ClientSecretCredentialBuilder.class);
    ClientSecretCredential tokenCredentials = mock(ClientSecretCredential.class);
    try (MockedConstruction<ClientSecretCredentialBuilder> construction = mockConstructionWithAnswer(
             ClientSecretCredentialBuilder.class, invocationOnMock -> clientSecretCredentialBuilder);
         MockedConstruction<ClientSecretCredential> construction1 =
             mockConstructionWithAnswer(ClientSecretCredential.class, invocationOnMock -> tokenCredentials)) {
      when(clientSecretCredentialBuilder.httpClient(any())).thenReturn(clientSecretCredentialBuilder);
      when(clientSecretCredentialBuilder.clientSecret(any())).thenReturn(clientSecretCredentialBuilder);
      when(clientSecretCredentialBuilder.clientId(any())).thenReturn(clientSecretCredentialBuilder);
      when(clientSecretCredentialBuilder.tenantId(any())).thenReturn(clientSecretCredentialBuilder);
      when(clientSecretCredentialBuilder.authorityHost(any())).thenReturn(clientSecretCredentialBuilder);

      when(clientSecretCredentialBuilder.build()).thenReturn(tokenCredentials);

      Mono<AccessToken> accessTokenMono = mock(Mono.class);
      when(tokenCredentials.getToken(any())).thenReturn(accessTokenMono);

      AccessToken accessToken = mock(AccessToken.class);
      when(accessTokenMono.block()).thenReturn(accessToken);

      when(accessToken.getToken()).thenReturn("token");

      AzureConfig azureConfig = AzureConfig.builder()
                                    .clientId("clientId")
                                    .tenantId("tenantId")
                                    .key("key".toCharArray())
                                    .azureEnvironmentType(AZURE)
                                    .build();
      azureDelegateHelperService.getAzureBearerAuthToken(azureConfig);
      ArgumentCaptor<TokenRequestContext> captor = ArgumentCaptor.forClass(TokenRequestContext.class);
      verify(tokenCredentials).getToken(captor.capture());
      assertThat(captor.getValue().getScopes().size()).isEqualTo(1);
      assertThat(captor.getValue().getScopes().get(0)).isEqualTo("https://management.core.windows.net//.default");

      azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE);
      azureDelegateHelperService.getAzureBearerAuthToken(azureConfig);
      captor = ArgumentCaptor.forClass(TokenRequestContext.class);
      verify(tokenCredentials, times(2)).getToken(captor.capture());
      assertThat(captor.getValue().getScopes().size()).isEqualTo(1);
      assertThat(captor.getValue().getScopes().get(0)).isEqualTo("https://management.core.windows.net//.default");

      azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT);
      azureDelegateHelperService.getAzureBearerAuthToken(azureConfig);
      captor = ArgumentCaptor.forClass(TokenRequestContext.class);
      verify(tokenCredentials, times(3)).getToken(captor.capture());
      assertThat(captor.getValue().getScopes().size()).isEqualTo(1);
      assertThat(captor.getValue().getScopes().get(0)).isEqualTo("https://management.core.usgovcloudapi.net//.default");
    }
  }

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetAzureManagementRestClient() {
    try (MockedStatic<Http> httpMockedStatic = Mockito.mockStatic(Http.class)) {
      OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
      when(Http.getOkHttpClientBuilder()).thenReturn(clientBuilder);

      azureDelegateHelperService.getAzureManagementRestClient(null);
      httpMockedStatic.verify(() -> Http.getHttpProxyHost("https://management.azure.com/"));

      azureDelegateHelperService.getAzureManagementRestClient(AzureEnvironmentType.AZURE_US_GOVERNMENT);
      httpMockedStatic.verify(() -> Http.getHttpProxyHost("https://management.usgovcloudapi.net/"));

      azureDelegateHelperService.getAzureManagementRestClient(AzureEnvironmentType.AZURE);
      httpMockedStatic.verify(() -> Http.getHttpProxyHost("https://management.azure.com/"), times(2));
    }
  }

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testURLInGetAzureClient() throws Exception {
    try (MockedStatic<AzureResourceManager> azureMockedStatic = Mockito.mockStatic(AzureResourceManager.class)) {
      azureMockedStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
          .thenReturn(authenticated);
      when(AzureResourceManager.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
      when(configurable.withRetryPolicy(any(RetryPolicy.class))).thenReturn(configurable);
      when(configurable.authenticate(any(TokenCredential.class), any(AzureProfile.class))).thenReturn(authenticated);
      when(authenticated.withDefaultSubscription()).thenReturn(azure);

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();
      azureDelegateHelperService.getAzureClient(azureConfig);
      ArgumentCaptor<HttpPipeline> captor = ArgumentCaptor.forClass(HttpPipeline.class);
      ArgumentCaptor<AzureProfile> captor2 = ArgumentCaptor.forClass(AzureProfile.class);
      ArgumentCaptor<HttpPipeline> finalCaptor = captor;
      ArgumentCaptor<AzureProfile> finalCaptor2 = captor2;
      azureMockedStatic.verify(
          () -> AzureResourceManager.authenticate(finalCaptor.capture(), finalCaptor2.capture()), times(1));
      assertThat(captor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.windows.net/");

      azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT);
      azureDelegateHelperService.getAzureClient(azureConfig);
      azureMockedStatic.verify(
          () -> AzureResourceManager.authenticate(finalCaptor.capture(), finalCaptor2.capture()), times(2));
      assertThat(captor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.usgovcloudapi.net/");

      azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE);
      azureDelegateHelperService.getAzureClient(azureConfig);
      azureMockedStatic.verify(
          () -> AzureResourceManager.authenticate(finalCaptor.capture(), finalCaptor2.capture()), times(3));
      assertThat(finalCaptor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.windows.net/");

      when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);
      azureDelegateHelperService.getAzureClient(azureConfig, "subscriptionId");
      azureMockedStatic.verify(
          () -> AzureResourceManager.authenticate(finalCaptor.capture(), finalCaptor2.capture()), times(4));
      assertThat(finalCaptor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.windows.net/");

      azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT);
      azureDelegateHelperService.getAzureClient(azureConfig, "subscriptionId");
      azureMockedStatic.verify(
          () -> AzureResourceManager.authenticate(finalCaptor.capture(), finalCaptor2.capture()), times(5));
      assertThat(finalCaptor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.usgovcloudapi.net/");

      azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE);
      azureDelegateHelperService.getAzureClient(azureConfig, "subscriptionId");
      azureMockedStatic.verify(
          () -> AzureResourceManager.authenticate(finalCaptor.capture(), finalCaptor2.capture()), times(6));
      assertThat(finalCaptor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.windows.net/");
    }
  }

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testNPEInListVmsByTagsAndResourceGroup() {
    try (MockedStatic<AzureResourceManager> azureMockedStatic = Mockito.mockStatic(AzureResourceManager.class)) {
      azureMockedStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
          .thenReturn(authenticated);
      when(AzureResourceManager.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
      when(configurable.withRetryPolicy(any(RetryPolicy.class))).thenReturn(configurable);
      when(configurable.authenticate(any(TokenCredential.class), any(AzureProfile.class))).thenReturn(authenticated);
      when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);
      VirtualMachines mockVirtualMachines = mock(VirtualMachines.class);
      when(azure.virtualMachines()).thenReturn(mockVirtualMachines);
      VirtualMachine virtualMachine = mock(VirtualMachine.class);
      when(mockVirtualMachines.listByResourceGroup("resourceGroup"))
          .thenReturn(getPagedIterable(generateResponse(virtualMachine)));

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();
      List<VirtualMachine> virtualMachines = azureHelperService.listVmsByTagsAndResourceGroup(
          azureConfig, emptyList(), "subscriptionId", "resourceGroup", emptyMap(), OSType.LINUX);
      assertThat(virtualMachines).isEmpty();
    }
  }

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testListVaults() throws Exception {
    try (MockedStatic<AzureResourceManager> azureMockedStatic = Mockito.mockStatic(AzureResourceManager.class)) {
      when(AzureResourceManager.configure()).thenReturn(configurable);
      azureMockedStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
          .thenReturn(authenticated);
      when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
      when(configurable.withRetryPolicy(any(RetryPolicy.class))).thenReturn(configurable);
      when(configurable.authenticate(any(TokenCredential.class), any(AzureProfile.class))).thenReturn(authenticated);
      when(authenticated.withSubscription(any())).thenReturn(azure);

      when(azure.resourceGroups())
          .thenReturn(getResourceGroups(getResourceGroup("resourceGroupId", "Test ResourceGroup")));
      when(azure.vaults()).thenReturn(getVaults(getVault("vaultId", "some vault name")));

      AzureVaultConfig azureVaultConfig =
          AzureVaultConfig.builder().clientId("clientId").tenantId("tenantId").secretKey("key").build();

      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      ArgumentCaptor<HttpPipeline> captor = ArgumentCaptor.forClass(HttpPipeline.class);
      ArgumentCaptor<AzureProfile> captor2 = ArgumentCaptor.forClass(AzureProfile.class);
      azureMockedStatic.verify(() -> AzureResourceManager.authenticate(captor.capture(), captor2.capture()), times(1));
      assertThat(captor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.windows.net/");

      azureVaultConfig.setAzureEnvironmentType(AZURE_US_GOVERNMENT);
      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      azureMockedStatic.verify(() -> AzureResourceManager.authenticate(captor.capture(), captor2.capture()), times(2));
      assertThat(captor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.usgovcloudapi.net/");

      azureVaultConfig.setAzureEnvironmentType(AZURE);
      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      azureMockedStatic.verify(() -> AzureResourceManager.authenticate(captor.capture(), captor2.capture()), times(3));
      assertThat(captor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.windows.net/");
    }
  }

  @Test()
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void testListVaultsForSystemManagedIdentity() throws Exception {
    try (MockedStatic<AzureResourceManager> azureMockedStatic = Mockito.mockStatic(AzureResourceManager.class)) {
      when(AzureResourceManager.configure()).thenReturn(configurable);
      azureMockedStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
          .thenReturn(authenticated);
      when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
      when(configurable.withRetryPolicy(any(RetryPolicy.class))).thenReturn(configurable);
      when(configurable.authenticate(any(TokenCredential.class), any(AzureProfile.class))).thenReturn(authenticated);
      when(authenticated.withSubscription(any())).thenReturn(azure);

      when(azure.resourceGroups())
          .thenReturn(getResourceGroups(getResourceGroup("resourceGroupId", "Test ResourceGroup")));
      when(azure.vaults()).thenReturn(getVaults(getVault("vaultId", "some vault name")));

      AzureVaultConfig azureVaultConfig =
          AzureVaultConfig.builder()
              .useManagedIdentity(true)
              .azureManagedIdentityType(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
              .build();

      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      ArgumentCaptor<HttpPipeline> captor = ArgumentCaptor.forClass(HttpPipeline.class);
      ArgumentCaptor<AzureProfile> captor2 = ArgumentCaptor.forClass(AzureProfile.class);
      azureMockedStatic.verify(() -> AzureResourceManager.authenticate(captor.capture(), captor2.capture()), times(1));
      assertThat(captor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.windows.net/");

      azureVaultConfig.setAzureEnvironmentType(AZURE_US_GOVERNMENT);
      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      azureMockedStatic.verify(() -> AzureResourceManager.authenticate(captor.capture(), captor2.capture()), times(2));
      assertThat(captor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.usgovcloudapi.net/");

      azureVaultConfig.setAzureEnvironmentType(AZURE);
      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      azureMockedStatic.verify(() -> AzureResourceManager.authenticate(captor.capture(), captor2.capture()), times(3));
      assertThat(captor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.windows.net/");
    }
  }

  @Test()
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void testListVaultsForUserManagedIdentity() throws Exception {
    try (MockedStatic<AzureResourceManager> azureMockedStatic = Mockito.mockStatic(AzureResourceManager.class)) {
      when(AzureResourceManager.configure()).thenReturn(configurable);
      azureMockedStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
          .thenReturn(authenticated);
      when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
      when(configurable.withRetryPolicy(any(RetryPolicy.class))).thenReturn(configurable);
      when(configurable.authenticate(any(TokenCredential.class), any(AzureProfile.class))).thenReturn(authenticated);
      when(authenticated.withSubscription(any())).thenReturn(azure);

      when(azure.resourceGroups())
          .thenReturn(getResourceGroups(getResourceGroup("resourceGroupId", "Test ResourceGroup")));
      when(azure.vaults()).thenReturn(getVaults(getVault("vaultId", "some vault name")));

      AzureVaultConfig azureVaultConfig =
          AzureVaultConfig.builder()
              .useManagedIdentity(true)
              .azureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
              .managedClientId("managedClientId")
              .build();

      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      ArgumentCaptor<HttpPipeline> captor = ArgumentCaptor.forClass(HttpPipeline.class);
      ArgumentCaptor<AzureProfile> captor2 = ArgumentCaptor.forClass(AzureProfile.class);
      azureMockedStatic.verify(() -> AzureResourceManager.authenticate(captor.capture(), captor2.capture()), times(1));
      assertThat(captor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.windows.net/");

      azureVaultConfig.setAzureEnvironmentType(AZURE_US_GOVERNMENT);
      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      azureMockedStatic.verify(() -> AzureResourceManager.authenticate(captor.capture(), captor2.capture()), times(2));
      assertThat(captor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.usgovcloudapi.net/");

      azureVaultConfig.setAzureEnvironmentType(AZURE);
      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      azureMockedStatic.verify(() -> AzureResourceManager.authenticate(captor.capture(), captor2.capture()), times(3));
      assertThat(captor2.getValue().getEnvironment().getManagementEndpoint())
          .isEqualTo("https://management.core.windows.net/");
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetKubernetesClusterConfig() throws Exception {
    String kubeConfig = "apiVersion: v1\n"
        + "clusters:\n"
        + "- cluster:\n"
        + "    server: https://master-url\n"
        + "    certificate-authority-data: certificate-authority-data\n"
        + "    insecure-skip-tls-verify: true\n"
        + "  name: cluster\n"
        + "contexts:\n"
        + "- context:\n"
        + "    cluster: cluster\n"
        + "    user: admin\n"
        + "    namespace: namespace\n"
        + "  name: current\n"
        + "current-context: current\n"
        + "kind: Config\n"
        + "preferences: {}\n"
        + "users:\n"
        + "- name: admin\n"
        + "  user:\n"
        + "    client-certificate-data: client-certificate-data\n"
        + "    client-key-data: client-key-data\n";

    AzureConfig azureConfig = AzureConfig.builder().azureEnvironmentType(AZURE).build();
    AzureDelegateHelperService spyOnAzureHelperService = spy(azureDelegateHelperService);
    AksGetCredentialsResponse credentials = new AksGetCredentialsResponse();
    AksGetCredentialProperties properties = credentials.new AksGetCredentialProperties();
    properties.setKubeConfig(encodeBase64(kubeConfig));
    credentials.setProperties(properties);

    doReturn("token").when(spyOnAzureHelperService).getAzureBearerAuthToken(any(AzureConfig.class));
    doReturn(azureManagementRestClient).when(spyOnAzureHelperService).getAzureManagementRestClient(AZURE);
    doReturn(aksGetCredentialsCall)
        .when(azureManagementRestClient)
        .getAdminCredentials(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(credentials)).when(aksGetCredentialsCall).execute();

    KubernetesConfig clusterConfig = spyOnAzureHelperService.getKubernetesClusterConfig(
        azureConfig, emptyList(), "subscriptionId", "resourceGroup", "clusterName", "namespace", false);
    assertThat(clusterConfig.getMasterUrl()).isEqualTo("https://master-url");
    assertThat(clusterConfig.getCaCert()).isEqualTo("certificate-authority-data".toCharArray());
    assertThat(clusterConfig.getUsername()).isEqualTo("admin".toCharArray());
    assertThat(clusterConfig.getClientCert()).isEqualTo("client-certificate-data".toCharArray());
    assertThat(clusterConfig.getClientKey()).isEqualTo("client-key-data".toCharArray());
  }

  @Test()
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListSubscriptions() throws IOException {
    try (MockedStatic<AzureResourceManager> azureMockedStatic = Mockito.mockStatic(AzureResourceManager.class)) {
      when(AzureResourceManager.configure()).thenReturn(configurable);
      azureMockedStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
          .thenReturn(authenticated);
      when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
      when(configurable.withRetryPolicy(any(RetryPolicy.class))).thenReturn(configurable);
      when(configurable.authenticate(any(TokenCredential.class), any(AzureProfile.class))).thenReturn(authenticated);
      when(authenticated.withSubscription(any())).thenReturn(azure);
      when(azure.subscriptions())
          .thenReturn(getSubscriptions(getSubscription("subscriptionId", "Azure Test Subscription 1")));

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();

      assertThat(azureDelegateHelperService.listSubscriptions(azureConfig, emptyList())).hasSize(1);
    }
  }

  @Test()
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListImageGalleries() {
    try (MockedStatic<AzureResourceManager> azureMockedStatic = Mockito.mockStatic(AzureResourceManager.class)) {
      when(AzureResourceManager.configure()).thenReturn(configurable);
      azureMockedStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
          .thenReturn(authenticated);
      when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
      when(configurable.withRetryPolicy(any(RetryPolicy.class))).thenReturn(configurable);
      when(configurable.authenticate(any(TokenCredential.class), any(AzureProfile.class))).thenReturn(authenticated);
      when(authenticated.withSubscription(anyString())).thenReturn(azure);

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();

      Galleries mockGalleries = mock(Galleries.class);
      when(azure.galleries()).thenReturn(mockGalleries);
      when(mockGalleries.listByResourceGroup(anyString()))
          .thenReturn(getPagedIterable(generateResponse(getGallery("galleryId", "Gallery 1"))));
      assertThat(azureDelegateHelperService.listImageGalleries(
                     azureConfig, emptyList(), "someSubscriptionId", "someResourceGroup"))
          .hasSize(1);
    }
  }

  @Test()
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListImageDefinitions() {
    try (MockedStatic<AzureResourceManager> azureMockedStatic = Mockito.mockStatic(AzureResourceManager.class)) {
      when(AzureResourceManager.configure()).thenReturn(configurable);
      azureMockedStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
          .thenReturn(authenticated);
      when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
      when(configurable.withRetryPolicy(any(RetryPolicy.class))).thenReturn(configurable);
      when(configurable.authenticate(any(TokenCredential.class), any(AzureProfile.class))).thenReturn(authenticated);
      when(authenticated.withSubscription(anyString())).thenReturn(azure);

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();

      Galleries mockGalleries = mock(Galleries.class);
      GalleryImages mockImages = mock(GalleryImages.class);
      when(azure.galleries()).thenReturn(mockGalleries);
      when(azure.galleryImages()).thenReturn(mockImages);
      when(mockImages.listByGallery(anyString(), anyString()))
          .thenReturn(getPagedIterable(generateResponse(getGalleryImage("galleryImageId", "Gallery Image 1"))));
      assertThat(azureDelegateHelperService.listImageDefinitions(
                     azureConfig, emptyList(), "someSubscriptionId", "someResourceGroup", "someGallery"))
          .hasSize(1);
    }
  }

  @Test()
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListImageDefinitionVersions() {
    try (MockedStatic<AzureResourceManager> azureMockedStatic = Mockito.mockStatic(AzureResourceManager.class)) {
      azureMockedStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
          .thenReturn(authenticated);
      when(AzureResourceManager.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
      when(configurable.withRetryPolicy(any(RetryPolicy.class))).thenReturn(configurable);
      when(configurable.authenticate(any(TokenCredential.class), any(AzureProfile.class))).thenReturn(authenticated);
      when(authenticated.withSubscription(anyString())).thenReturn(azure);

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();

      GalleryImageVersions galleryImageVersion = mock(GalleryImageVersions.class);
      when(azure.galleryImageVersions()).thenReturn(galleryImageVersion);
      when(galleryImageVersion.listByGalleryImage(anyString(), anyString(), anyString()))
          .thenReturn(
              getPagedIterable(generateResponse(getGalleryImageVersion("galleryVersionId", "Gallery Version 1"))));

      assertThat(azureDelegateHelperService.listImageDefinitionVersions(azureConfig, emptyList(), "someSubscriptionId",
                     "someResourceGroupName", "someGalleryName", "someImageDefinitionName"))
          .hasSize(1);
    }
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRethrowInvalidRequestExceptionWhenInvalidCredentialsExceptionIsThrown() {
    try (MockedStatic<AzureResourceManager> azureMockedStatic = Mockito.mockStatic(AzureResourceManager.class)) {
      when(AzureResourceManager.configure()).thenReturn(configurable);
      azureMockedStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
          .thenThrow(new MsalException("Failed to authenticate", "AADXXXXXXX"));
      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();

      assertThatThrownBy(() -> { azureDelegateHelperService.getAzureClient(azureConfig); })
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Failed to connect to Azure cluster. MsalException: Failed to authenticate");
    }
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void listHostsWithPrivateIp() {
    AzureHelperService spy = spy(azureHelperService);
    AzureConfig azureConfig =
        AzureConfig.builder().clientId("clientId").tenantId("13r1415114").key(new char[] {'t', 'e', 's', 't'}).build();

    AzureInstanceInfrastructure azureInstanceInfrastructure =
        AzureInstanceInfrastructure.builder().subscriptionId("dummy").resourceGroup("dummy").usePrivateIp(true).build();
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder().infrastructure(azureInstanceInfrastructure).build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withValue(azureConfig).build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    DeploymentType deploymentType = DeploymentType.SSH;

    List<VirtualMachine> vms = Collections.singletonList(vm);
    Mockito.doReturn("vm1").when(vm).name();
    Mockito.doReturn(networkInterface).when(vm).getPrimaryNetworkInterface();
    Mockito.doReturn("privateIpAddress").when(networkInterface).primaryPrivateIP();
    Mockito.doReturn(vms).when(spy).listVms(
        azureInstanceInfrastructure, settingAttribute, encryptedDataDetails, deploymentType);
    PageResponse<Host> hosts =
        spy.listHosts(infrastructureDefinition, settingAttribute, encryptedDataDetails, deploymentType);

    assertThat(hosts).isNotEmpty();
    assertThat(hosts.get(0).getHostName()).isEqualTo("vm1");
    assertThat(hosts.get(0).getPublicDns()).isEqualTo("privateIpAddress");
  }

  private Subscription getSubscription(String id, String displayName) {
    return new Subscription() {
      @Override
      public String subscriptionId() {
        return id;
      }

      @Override
      public String displayName() {
        return displayName;
      }

      @Override
      public SubscriptionState state() {
        return null;
      }

      @Override
      public SubscriptionPolicies subscriptionPolicies() {
        return null;
      }

      @Override
      public PagedIterable<Location> listLocations() {
        return null;
      }

      @Override
      public Location getLocationByRegion(Region region) {
        return null;
      }

      @Override
      public SubscriptionInner innerModel() {
        return null;
      }

      @Override
      public String key() {
        return null;
      }
    };
  }

  private Subscriptions getSubscriptions(Subscription subscription) {
    return new Subscriptions() {
      @Override
      public Subscription getById(String s) {
        return subscription;
      }

      @Override
      public Mono<Subscription> getByIdAsync(String s) {
        return Mono.just(subscription);
      }

      @Override
      public PagedIterable<Subscription> list() {
        return getPagedIterable(generateResponse(subscription));
      }

      @Override
      public PagedFlux<Subscription> listAsync() {
        return null;
      }
    };
  }

  private Vaults getVaults(Vault vault) {
    return new Vaults() {
      @Override
      public PagedIterable<DeletedVault> listDeleted() {
        return null;
      }

      @Override
      public PagedFlux<DeletedVault> listDeletedAsync() {
        return null;
      }

      @Override
      public DeletedVault getDeleted(String s, String s1) {
        return null;
      }

      @Override
      public Mono<DeletedVault> getDeletedAsync(String s, String s1) {
        return null;
      }

      @Override
      public void purgeDeleted(String s, String s1) {}

      @Override
      public Mono<Void> purgeDeletedAsync(String s, String s1) {
        return null;
      }

      @Override
      public CheckNameAvailabilityResult checkNameAvailability(String s) {
        return null;
      }

      @Override
      public Mono<CheckNameAvailabilityResult> checkNameAvailabilityAsync(String s) {
        return null;
      }

      @Override
      public Vault recoverSoftDeletedVault(String s, String s1, String s2) {
        return null;
      }

      @Override
      public Mono<Vault> recoverSoftDeletedVaultAsync(String s, String s1, String s2) {
        return null;
      }

      @Override
      public void deleteByResourceGroup(String s, String s1) {}

      @Override
      public Mono<Void> deleteByResourceGroupAsync(String s, String s1) {
        return null;
      }

      @Override
      public Vault getById(String s) {
        return vault;
      }

      @Override
      public Mono<Vault> getByIdAsync(String s) {
        return Mono.just(vault);
      }

      @Override
      public Vault getByResourceGroup(String s, String s1) {
        return vault;
      }

      @Override
      public Mono<Vault> getByResourceGroupAsync(String s, String s1) {
        return Mono.just(vault);
      }

      @Override
      public PagedIterable<Vault> listByResourceGroup(String s) {
        return getPagedIterable(generateResponse(vault));
      }

      @Override
      public PagedFlux<Vault> listByResourceGroupAsync(String s) {
        return null;
      }

      @Override
      public KeyVaultManager manager() {
        return null;
      }

      @Override
      public Vault.DefinitionStages.Blank define(String s) {
        return null;
      }

      @Override
      public void deleteById(String s) {}

      @Override
      public Mono<Void> deleteByIdAsync(String s) {
        return null;
      }
    };
  }

  private Vault getVault(String vaultId, String vaultName) {
    return new Vault() {
      @Override
      public SecretAsyncClient secretClient() {
        return null;
      }

      @Override
      public KeyAsyncClient keyClient() {
        return null;
      }

      @Override
      public HttpPipeline vaultHttpPipeline() {
        return null;
      }

      @Override
      public Keys keys() {
        return null;
      }

      @Override
      public Secrets secrets() {
        return null;
      }

      @Override
      public String vaultUri() {
        return null;
      }

      @Override
      public String tenantId() {
        return null;
      }

      @Override
      public Sku sku() {
        return null;
      }

      @Override
      public List<AccessPolicy> accessPolicies() {
        return null;
      }

      @Override
      public boolean roleBasedAccessControlEnabled() {
        return false;
      }

      @Override
      public boolean enabledForDeployment() {
        return false;
      }

      @Override
      public boolean enabledForDiskEncryption() {
        return false;
      }

      @Override
      public boolean enabledForTemplateDeployment() {
        return false;
      }

      @Override
      public boolean softDeleteEnabled() {
        return false;
      }

      @Override
      public boolean purgeProtectionEnabled() {
        return false;
      }

      @Override
      public CreateMode createMode() {
        return null;
      }

      @Override
      public NetworkRuleSet networkRuleSet() {
        return null;
      }

      @Override
      public KeyVaultManager manager() {
        return null;
      }

      @Override
      public String resourceGroupName() {
        return null;
      }

      @Override
      public String type() {
        return null;
      }

      @Override
      public String regionName() {
        return null;
      }

      @Override
      public Region region() {
        return null;
      }

      @Override
      public Map<String, String> tags() {
        return null;
      }

      @Override
      public String id() {
        return vaultId;
      }

      @Override
      public String name() {
        return vaultName;
      }

      @Override
      public PagedIterable<PrivateLinkResource> listPrivateLinkResources() {
        return null;
      }

      @Override
      public PagedFlux<PrivateLinkResource> listPrivateLinkResourcesAsync() {
        return null;
      }

      @Override
      public void approvePrivateEndpointConnection(String s) {}

      @Override
      public Mono<Void> approvePrivateEndpointConnectionAsync(String s) {
        return null;
      }

      @Override
      public void rejectPrivateEndpointConnection(String s) {}

      @Override
      public Mono<Void> rejectPrivateEndpointConnectionAsync(String s) {
        return null;
      }

      @Override
      public VaultInner innerModel() {
        return null;
      }

      @Override
      public String key() {
        return null;
      }

      @Override
      public Vault refresh() {
        return null;
      }

      @Override
      public Mono<Vault> refreshAsync() {
        return null;
      }

      @Override
      public Update update() {
        return null;
      }
    };
  }

  private ResourceGroups getResourceGroups(ResourceGroup resourceGroup) {
    return new ResourceGroups() {
      @Override
      public boolean contain(String s) {
        return false;
      }

      @Override
      public Accepted<Void> beginDeleteByName(String s) {
        return null;
      }

      @Override
      public Accepted<Void> beginDeleteByName(String s, Collection<ForceDeletionResourceType> collection) {
        return null;
      }

      @Override
      public void deleteByName(String s, Collection<ForceDeletionResourceType> collection) {}

      @Override
      public Mono<Void> deleteByNameAsync(String s, Collection<ForceDeletionResourceType> collection) {
        return null;
      }

      @Override
      public ResourceGroup getByName(String s) {
        return resourceGroup;
      }

      @Override
      public Mono<ResourceGroup> getByNameAsync(String s) {
        return Mono.just(resourceGroup);
      }

      @Override
      public ResourceManager manager() {
        return null;
      }

      @Override
      public CreatedResources<ResourceGroup> create(Creatable<ResourceGroup>... creatables) {
        return null;
      }

      @Override
      public CreatedResources<ResourceGroup> create(List<? extends Creatable<ResourceGroup>> list) {
        return null;
      }

      @Override
      public Flux<ResourceGroup> createAsync(Creatable<ResourceGroup>... creatables) {
        return null;
      }

      @Override
      public Flux<ResourceGroup> createAsync(List<? extends Creatable<ResourceGroup>> list) {
        return null;
      }

      @Override
      public ResourceGroup.DefinitionStages.Blank define(String s) {
        return null;
      }

      @Override
      public void deleteByName(String s) {}

      @Override
      public Mono<Void> deleteByNameAsync(String s) {
        return null;
      }

      @Override
      public PagedIterable<ResourceGroup> list() {
        return getPagedIterable(generateResponse(resourceGroup));
      }

      @Override
      public PagedFlux<ResourceGroup> listAsync() {
        return null;
      }

      @Override
      public PagedIterable<ResourceGroup> listByTag(String s, String s1) {
        return null;
      }

      @Override
      public PagedFlux<ResourceGroup> listByTagAsync(String s, String s1) {
        return null;
      }
    };
  }

  private ResourceGroup getResourceGroup(String id, String resourceGroupName) {
    return new ResourceGroup() {
      @Override
      public String provisioningState() {
        return null;
      }

      @Override
      public ResourceGroupExportResult exportTemplate(
          ResourceGroupExportTemplateOptions resourceGroupExportTemplateOptions) {
        return null;
      }

      @Override
      public Mono<ResourceGroupExportResult> exportTemplateAsync(
          ResourceGroupExportTemplateOptions resourceGroupExportTemplateOptions) {
        return null;
      }

      @Override
      public String type() {
        return null;
      }

      @Override
      public String regionName() {
        return null;
      }

      @Override
      public Region region() {
        return null;
      }

      @Override
      public Map<String, String> tags() {
        return null;
      }

      @Override
      public String id() {
        return id;
      }

      @Override
      public String name() {
        return resourceGroupName;
      }

      @Override
      public ResourceGroupInner innerModel() {
        return null;
      }

      @Override
      public String key() {
        return null;
      }

      @Override
      public ResourceGroup refresh() {
        return null;
      }

      @Override
      public Mono<ResourceGroup> refreshAsync() {
        return null;
      }

      @Override
      public Update update() {
        return null;
      }
    };
  }

  private Gallery getGallery(String id, String name) {
    return new Gallery() {
      @Override
      public String description() {
        return null;
      }

      @Override
      public String uniqueName() {
        return null;
      }

      @Override
      public String provisioningState() {
        return null;
      }

      @Override
      public Mono<GalleryImage> getImageAsync(String s) {
        return null;
      }

      @Override
      public GalleryImage getImage(String s) {
        return null;
      }

      @Override
      public PagedFlux<GalleryImage> listImagesAsync() {
        return null;
      }

      @Override
      public PagedIterable<GalleryImage> listImages() {
        return null;
      }

      @Override
      public ComputeManager manager() {
        return null;
      }

      @Override
      public String resourceGroupName() {
        return null;
      }

      @Override
      public String type() {
        return null;
      }

      @Override
      public String regionName() {
        return null;
      }

      @Override
      public Region region() {
        return null;
      }

      @Override
      public Map<String, String> tags() {
        return null;
      }

      @Override
      public String id() {
        return id;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public GalleryInner innerModel() {
        return null;
      }

      @Override
      public String key() {
        return null;
      }

      @Override
      public Gallery refresh() {
        return null;
      }

      @Override
      public Mono<Gallery> refreshAsync() {
        return null;
      }

      @Override
      public Update update() {
        return null;
      }
    };
  }

  private GalleryImage getGalleryImage(String id, String name) {
    return new GalleryImage() {
      @Override
      public String description() {
        return null;
      }

      @Override
      public List<DiskSkuTypes> unsupportedDiskTypes() {
        return null;
      }

      @Override
      public Disallowed disallowed() {
        return null;
      }

      @Override
      public OffsetDateTime endOfLifeDate() {
        return null;
      }

      @Override
      public String eula() {
        return null;
      }

      @Override
      public String id() {
        return id;
      }

      @Override
      public GalleryImageIdentifier identifier() {
        return null;
      }

      @Override
      public String location() {
        return null;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public OperatingSystemStateTypes osState() {
        return null;
      }

      @Override
      public OperatingSystemTypes osType() {
        return OperatingSystemTypes.LINUX;
      }

      @Override
      public String privacyStatementUri() {
        return null;
      }

      @Override
      public String provisioningState() {
        return null;
      }

      @Override
      public ImagePurchasePlan purchasePlan() {
        return null;
      }

      @Override
      public RecommendedMachineConfiguration recommendedVirtualMachineConfiguration() {
        return null;
      }

      @Override
      public String releaseNoteUri() {
        return null;
      }

      @Override
      public Map<String, String> tags() {
        return null;
      }

      @Override
      public String type() {
        return null;
      }

      @Override
      public Mono<GalleryImageVersion> getVersionAsync(String s) {
        return null;
      }

      @Override
      public GalleryImageVersion getVersion(String s) {
        return null;
      }

      @Override
      public PagedFlux<GalleryImageVersion> listVersionsAsync() {
        return null;
      }

      @Override
      public PagedIterable<GalleryImageVersion> listVersions() {
        return null;
      }

      @Override
      public ComputeManager manager() {
        return null;
      }

      @Override
      public GalleryImageInner innerModel() {
        return null;
      }

      @Override
      public String key() {
        return null;
      }

      @Override
      public GalleryImage refresh() {
        return null;
      }

      @Override
      public Mono<GalleryImage> refreshAsync() {
        return null;
      }

      @Override
      public Update update() {
        return null;
      }
    };
  }

  private GalleryImageVersion getGalleryImageVersion(String id, String name) {
    return new GalleryImageVersion() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public String location() {
        return null;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public String provisioningState() {
        return "Succeeded";
      }

      @Override
      public GalleryImageVersionPublishingProfile publishingProfile() {
        return null;
      }

      @Override
      public List<TargetRegion> availableRegions() {
        return null;
      }

      @Override
      public OffsetDateTime endOfLifeDate() {
        return null;
      }

      @Override
      public Boolean isExcludedFromLatest() {
        return Boolean.FALSE;
      }

      @Override
      public ReplicationStatus replicationStatus() {
        return null;
      }

      @Override
      public GalleryImageVersionStorageProfile storageProfile() {
        return null;
      }

      @Override
      public Map<String, String> tags() {
        return null;
      }

      @Override
      public String type() {
        return null;
      }

      @Override
      public ComputeManager manager() {
        return null;
      }

      @Override
      public GalleryImageVersionInner innerModel() {
        return null;
      }

      @Override
      public String key() {
        return null;
      }

      @Override
      public GalleryImageVersion refresh() {
        return null;
      }

      @Override
      public Mono<GalleryImageVersion> refreshAsync() {
        return null;
      }

      @Override
      public Update update() {
        return null;
      }
    };
  }

  private <T> com.azure.core.http.rest.Response generateResponse(T responseListItem) {
    List<T> responseList = new ArrayList<>();
    responseList.add(responseListItem);
    return new SimpleResponse(null, 200, null, responseList);
  }

  @NotNull
  private <T> PagedIterable<T> getPagedIterable(com.azure.core.http.rest.Response<List<T>> response) {
    return new PagedIterable<T>(PagedConverter.convertListToPagedFlux(Mono.just(response)));
  }
}
