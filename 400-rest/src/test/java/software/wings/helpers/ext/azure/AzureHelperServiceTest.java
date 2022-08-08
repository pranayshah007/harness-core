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
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.tag.AzureListTagsResponse;
import io.harness.azure.model.tag.TagDetails;
import io.harness.azure.model.tag.TagValue;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.network.Http;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureTagDetails;
import software.wings.beans.AzureVaultConfig;
import software.wings.helpers.ext.azure.AksGetCredentialsResponse.AksGetCredentialProperties;
import software.wings.service.intfc.security.EncryptionService;

import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.Page;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.Disallowed;
import com.microsoft.azure.management.compute.DiskSkuTypes;
import com.microsoft.azure.management.compute.Galleries;
import com.microsoft.azure.management.compute.Gallery;
import com.microsoft.azure.management.compute.GalleryImage;
import com.microsoft.azure.management.compute.GalleryImageIdentifier;
import com.microsoft.azure.management.compute.GalleryImageVersion;
import com.microsoft.azure.management.compute.GalleryImageVersionPublishingProfile;
import com.microsoft.azure.management.compute.GalleryImageVersionStorageProfile;
import com.microsoft.azure.management.compute.GalleryImageVersions;
import com.microsoft.azure.management.compute.GalleryImages;
import com.microsoft.azure.management.compute.ImagePurchasePlan;
import com.microsoft.azure.management.compute.OperatingSystemStateTypes;
import com.microsoft.azure.management.compute.OperatingSystemTypes;
import com.microsoft.azure.management.compute.RecommendedMachineConfiguration;
import com.microsoft.azure.management.compute.ReplicationStatus;
import com.microsoft.azure.management.compute.TargetRegion;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.compute.VirtualMachines;
import com.microsoft.azure.management.compute.implementation.ComputeManager;
import com.microsoft.azure.management.compute.implementation.GalleryImageInner;
import com.microsoft.azure.management.compute.implementation.GalleryImageVersionInner;
import com.microsoft.azure.management.compute.implementation.GalleryInner;
import com.microsoft.azure.management.containerservice.OSType;
import com.microsoft.azure.management.resources.Location;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.ResourceGroups;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.management.resources.SubscriptionPolicies;
import com.microsoft.azure.management.resources.SubscriptionState;
import com.microsoft.azure.management.resources.Subscriptions;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.implementation.SubscriptionInner;
import com.microsoft.rest.LogLevel;
import com.microsoft.rest.RestException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import okhttp3.OkHttpClient;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import retrofit2.Call;
import retrofit2.Response;
import rx.Observable;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Azure.class, AzureHelperService.class, AzureDelegateHelperService.class, Http.class})
@PowerMockIgnore({"javax.security.*", "javax.net.*"})
@OwnedBy(HarnessTeam.CDC)
public class AzureHelperServiceTest extends WingsBaseTest {
  @Mock private Azure.Configurable configurable;
  @Mock private Azure.Authenticated authenticated;
  @Mock private Azure azure;
  @Mock private EncryptionService encryptionService;
  @Mock private ResourceGroups resourceGroups;

  @Mock AzureManagementRestClient azureManagementRestClient;
  @Mock Call<AksGetCredentialsResponse> aksGetCredentialsCall;

  @InjectMocks private AzureHelperService azureHelperService;
  @InjectMocks private AzureDelegateHelperService azureDelegateHelperService;

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateAzureAccountCredential() throws Exception {
    try (MockedStatic<Azure> azureMockedStatic = Mockito.mockStatic(Azure.class)) {
      when(Azure.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(LogLevel.class))).thenReturn(configurable);
      when(configurable.authenticate(any(ApplicationTokenCredentials.class))).thenReturn(authenticated);
      when(authenticated.withDefaultSubscription()).thenReturn(azure);

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();
      azureHelperService.validateAzureAccountCredential(azureConfig, emptyList());

      ArgumentCaptor<ApplicationTokenCredentials> captor = ArgumentCaptor.forClass(ApplicationTokenCredentials.class);
      verify(configurable, times(1)).authenticate(captor.capture());
      ApplicationTokenCredentials tokenCredentials = captor.getValue();
      assertThat(tokenCredentials.clientId()).isEqualTo("clientId");
      assertThat(tokenCredentials.environment().managementEndpoint()).isEqualTo("https://management.core.windows.net/");

      azureConfig.setAzureEnvironmentType(AZURE);
      azureHelperService.validateAzureAccountCredential(azureConfig, emptyList());
      verify(configurable, times(2)).authenticate(captor.capture());
      tokenCredentials = captor.getValue();
      assertThat(tokenCredentials.clientId()).isEqualTo("clientId");
      assertThat(tokenCredentials.environment().managementEndpoint()).isEqualTo("https://management.core.windows.net/");

      azureConfig.setAzureEnvironmentType(AZURE_US_GOVERNMENT);
      azureHelperService.validateAzureAccountCredential(azureConfig, emptyList());
      verify(configurable, times(3)).authenticate(captor.capture());
      tokenCredentials = captor.getValue();
      assertThat(tokenCredentials.clientId()).isEqualTo("clientId");
      assertThat(tokenCredentials.environment().managementEndpoint())
          .isEqualTo("https://management.core.usgovcloudapi.net/");
    }
  }

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testListTags() throws Exception {
    ApplicationTokenCredentials tokenCredentials = mock(ApplicationTokenCredentials.class);
    whenNew(ApplicationTokenCredentials.class).withAnyArguments().thenReturn(tokenCredentials);
    when(tokenCredentials.getToken(anyString())).thenReturn("tokenValue");

    AzureDelegateHelperService spyAzureDelegateHelperService = spy(AzureDelegateHelperService.class);
    on(spyAzureDelegateHelperService).set("encryptionService", encryptionService);

    AzureManagementRestClient azureManagementRestClient = mock(AzureManagementRestClient.class);
    doReturn(azureManagementRestClient).when(spyAzureDelegateHelperService).getAzureManagementRestClient(any());
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
    ApplicationTokenCredentials tokenCredentials = mock(ApplicationTokenCredentials.class);
    whenNew(ApplicationTokenCredentials.class).withAnyArguments().thenReturn(tokenCredentials);
    when(tokenCredentials.getToken(anyString())).thenReturn("tokenValue");

    AzureConfig azureConfig =
        AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();
    azureDelegateHelperService.getAzureBearerAuthToken(azureConfig);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(tokenCredentials).getToken(captor.capture());
    assertThat(captor.getValue()).isEqualTo("https://management.core.windows.net/");

    azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE);
    azureDelegateHelperService.getAzureBearerAuthToken(azureConfig);
    captor = ArgumentCaptor.forClass(String.class);
    verify(tokenCredentials, times(2)).getToken(captor.capture());
    assertThat(captor.getValue()).isEqualTo("https://management.core.windows.net/");

    azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT);
    azureDelegateHelperService.getAzureBearerAuthToken(azureConfig);
    captor = ArgumentCaptor.forClass(String.class);
    verify(tokenCredentials, times(3)).getToken(captor.capture());
    assertThat(captor.getValue()).isEqualTo("https://management.core.usgovcloudapi.net/");
  }

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetAzureManagementRestClient() {
    try (MockedStatic<Http> httpMockedStatic = Mockito.mockStatic(Http.class)) {
      OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
      when(Http.getOkHttpClientBuilder()).thenReturn(clientBuilder);

      azureDelegateHelperService.getAzureManagementRestClient(null);
      ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
      PowerMockito.verifyStatic(Http.class);
      Http.checkAndGetNonProxyIfApplicable(captor.capture());
      assertThat(captor.getValue()).isEqualTo("https://management.azure.com/");

      azureDelegateHelperService.getAzureManagementRestClient(AzureEnvironmentType.AZURE_US_GOVERNMENT);
      captor = ArgumentCaptor.forClass(String.class);
      PowerMockito.verifyStatic(Http.class, times(2));
      Http.checkAndGetNonProxyIfApplicable(captor.capture());
      assertThat(captor.getValue()).isEqualTo("https://management.usgovcloudapi.net/");

      azureDelegateHelperService.getAzureManagementRestClient(AzureEnvironmentType.AZURE);
      captor = ArgumentCaptor.forClass(String.class);
      PowerMockito.verifyStatic(Http.class, times(3));
      Http.checkAndGetNonProxyIfApplicable(captor.capture());
      assertThat(captor.getValue()).isEqualTo("https://management.azure.com/");
    }
  }

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testURLInGetAzureClient() throws Exception {
    try (MockedStatic<Azure> azureMockedStatic = Mockito.mockStatic(Azure.class)) {
      PowerMockito.mockStatic(Azure.class);
      when(Azure.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(LogLevel.class))).thenReturn(configurable);
      when(configurable.authenticate(any(ApplicationTokenCredentials.class))).thenReturn(authenticated);
      when(authenticated.withDefaultSubscription()).thenReturn(azure);

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();
      azureDelegateHelperService.getAzureClient(azureConfig);
      ArgumentCaptor<ApplicationTokenCredentials> captor = ArgumentCaptor.forClass(ApplicationTokenCredentials.class);
      verify(configurable, times(1)).authenticate(captor.capture());
      assertThat(captor.getValue().environment().managementEndpoint())
          .isEqualTo("https://management.core.windows.net/");

      azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT);
      azureDelegateHelperService.getAzureClient(azureConfig);
      captor = ArgumentCaptor.forClass(ApplicationTokenCredentials.class);
      verify(configurable, times(2)).authenticate(captor.capture());
      assertThat(captor.getValue().environment().managementEndpoint())
          .isEqualTo("https://management.core.usgovcloudapi.net/");

      azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE);
      azureDelegateHelperService.getAzureClient(azureConfig);
      captor = ArgumentCaptor.forClass(ApplicationTokenCredentials.class);
      verify(configurable, times(3)).authenticate(captor.capture());
      assertThat(captor.getValue().environment().managementEndpoint())
          .isEqualTo("https://management.core.windows.net/");

      when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);
      azureDelegateHelperService.getAzureClient(azureConfig, "subscriptionId");
      captor = ArgumentCaptor.forClass(ApplicationTokenCredentials.class);
      verify(configurable, times(4)).authenticate(captor.capture());
      assertThat(captor.getValue().environment().managementEndpoint())
          .isEqualTo("https://management.core.windows.net/");

      azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT);
      azureDelegateHelperService.getAzureClient(azureConfig, "subscriptionId");
      captor = ArgumentCaptor.forClass(ApplicationTokenCredentials.class);
      verify(configurable, times(5)).authenticate(captor.capture());
      assertThat(captor.getValue().environment().managementEndpoint())
          .isEqualTo("https://management.core.usgovcloudapi.net/");

      azureConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE);
      azureDelegateHelperService.getAzureClient(azureConfig, "subscriptionId");
      captor = ArgumentCaptor.forClass(ApplicationTokenCredentials.class);
      verify(configurable, times(6)).authenticate(captor.capture());
      assertThat(captor.getValue().environment().managementEndpoint())
          .isEqualTo("https://management.core.windows.net/");
    }
  }

  @Test()
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testNPEInListVmsByTagsAndResourceGroup() {
    try (MockedStatic<Azure> azureMockedStatic = Mockito.mockStatic(Azure.class)) {
      when(Azure.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(LogLevel.class))).thenReturn(configurable);
      when(configurable.authenticate(any(ApplicationTokenCredentials.class))).thenReturn(authenticated);
      when(authenticated.withSubscription("subscriptionId")).thenReturn(azure);
      VirtualMachines mockVirtualMachines = mock(VirtualMachines.class);
      when(azure.virtualMachines()).thenReturn(mockVirtualMachines);
      VirtualMachine virtualMachine = mock(VirtualMachine.class);
      PagedList<VirtualMachine> virtualMachinePagedList = new PagedList<VirtualMachine>() {
        @Override
        public Page<VirtualMachine> nextPage(String nextPageLink) throws RestException {
          return null;
        }
      };
      virtualMachinePagedList.add(virtualMachine);
      when(mockVirtualMachines.listByResourceGroup("resourceGroup")).thenReturn(virtualMachinePagedList);

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
    try (MockedStatic<Azure> azureMockedStatic = Mockito.mockStatic(Azure.class)) {
      when(Azure.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(LogLevel.class))).thenReturn(configurable);
      when(configurable.authenticate(any(ApplicationTokenCredentials.class))).thenReturn(authenticated);
      when(authenticated.withDefaultSubscription()).thenReturn(azure);
      when(azure.resourceGroups()).thenReturn(resourceGroups);
      when(resourceGroups.list()).thenReturn(new PagedList<ResourceGroup>() {
        @Override
        public Page<ResourceGroup> nextPage(String nextPageLink) throws RestException {
          return null;
        }
      });

      AzureVaultConfig azureVaultConfig =
          AzureVaultConfig.builder().clientId("clientId").tenantId("tenantId").secretKey("key").build();

      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      ArgumentCaptor<ApplicationTokenCredentials> captor = ArgumentCaptor.forClass(ApplicationTokenCredentials.class);
      verify(configurable, times(1)).authenticate(captor.capture());
      ApplicationTokenCredentials tokenCredentials = captor.getValue();
      assertThat(tokenCredentials.environment().managementEndpoint()).isEqualTo("https://management.core.windows.net/");

      azureVaultConfig.setAzureEnvironmentType(AZURE_US_GOVERNMENT);
      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      verify(configurable, times(2)).authenticate(captor.capture());
      tokenCredentials = captor.getValue();
      assertThat(tokenCredentials.environment().managementEndpoint())
          .isEqualTo("https://management.core.usgovcloudapi.net/");

      azureVaultConfig.setAzureEnvironmentType(AZURE);
      azureHelperService.listVaults(ACCOUNT_ID, azureVaultConfig);
      verify(configurable, times(3)).authenticate(captor.capture());
      tokenCredentials = captor.getValue();
      assertThat(tokenCredentials.environment().managementEndpoint()).isEqualTo("https://management.core.windows.net/");
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
    try (MockedStatic<Azure> azureMockedStatic = Mockito.mockStatic(Azure.class)) {
      when(Azure.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(LogLevel.class))).thenReturn(configurable);
      when(configurable.authenticate(any(ApplicationTokenCredentials.class))).thenReturn(authenticated);
      when(authenticated.withDefaultSubscription()).thenReturn(azure);

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();

      Subscriptions subscriptions = mock(Subscriptions.class);
      when(subscriptions.list()).thenReturn(new PagedList<Subscription>() {
        @Override
        public Stream<Subscription> stream() {
          return Stream.of(new Subscription() {
            @Override
            public String subscriptionId() {
              return "subscriptionId";
            }

            @Override
            public String displayName() {
              return "subscriptionName";
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
            public PagedList<Location> listLocations() {
              return null;
            }

            @Override
            public Location getLocationByRegion(Region region) {
              return null;
            }

            @Override
            public SubscriptionInner inner() {
              return null;
            }

            @Override
            public String key() {
              return null;
            }
          });
        }

        @Override
        public Page<Subscription> nextPage(String s) throws RestException, IOException {
          return null;
        }
      });
      when(azure.subscriptions()).thenReturn(subscriptions);
      assertThat(azureDelegateHelperService.listSubscriptions(azureConfig, emptyList())).hasSize(1);
    }
  }

  @Test()
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListImageGalleries() {
    try (MockedStatic<Azure> azureMockedStatic = Mockito.mockStatic(Azure.class)) {
      when(Azure.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(LogLevel.class))).thenReturn(configurable);
      when(configurable.authenticate(any(ApplicationTokenCredentials.class))).thenReturn(authenticated);
      when(authenticated.withSubscription(anyString())).thenReturn(azure);

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();

      Galleries mockGalleries = mock(Galleries.class);
      when(azure.galleries()).thenReturn(mockGalleries);
      when(mockGalleries.listByResourceGroup(anyString())).thenReturn(new PagedList<Gallery>() {
        @Override
        public Stream<Gallery> stream() {
          return Stream.of(new Gallery() {
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
            public Observable<GalleryImage> getImageAsync(String s) {
              return null;
            }

            @Override
            public GalleryImage getImage(String s) {
              return null;
            }

            @Override
            public Observable<GalleryImage> listImagesAsync() {
              return null;
            }

            @Override
            public PagedList<GalleryImage> listImages() {
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
              return null;
            }

            @Override
            public String name() {
              return null;
            }

            @Override
            public GalleryInner inner() {
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
            public Observable<Gallery> refreshAsync() {
              return null;
            }

            @Override
            public Update update() {
              return null;
            }
          });
        }

        @Override
        public Page<Gallery> nextPage(String s) throws RestException, IOException {
          return null;
        }
      });
      assertThat(azureDelegateHelperService.listImageGalleries(
                     azureConfig, emptyList(), "someSubscriptionId", "someResourceGroup"))
          .hasSize(1);
    }
  }

  @Test()
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListImageDefinitions() {
    try (MockedStatic<Azure> azureMockedStatic = Mockito.mockStatic(Azure.class)) {
      when(Azure.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(LogLevel.class))).thenReturn(configurable);
      when(configurable.authenticate(any(ApplicationTokenCredentials.class))).thenReturn(authenticated);
      when(authenticated.withSubscription(anyString())).thenReturn(azure);

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();

      Galleries mockGalleries = mock(Galleries.class);
      GalleryImages mockImages = mock(GalleryImages.class);
      when(azure.galleries()).thenReturn(mockGalleries);
      when(azure.galleryImages()).thenReturn(mockImages);
      when(mockImages.listByGallery(anyString(), anyString())).thenReturn(new PagedList<GalleryImage>() {
        @Override
        public Stream<GalleryImage> stream() {
          return Stream.of(new GalleryImage() {
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
            public DateTime endOfLifeDate() {
              return null;
            }

            @Override
            public String eula() {
              return null;
            }

            @Override
            public String id() {
              return null;
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
              return null;
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
            public Observable<GalleryImageVersion> getVersionAsync(String s) {
              return null;
            }

            @Override
            public GalleryImageVersion getVersion(String s) {
              return null;
            }

            @Override
            public Observable<GalleryImageVersion> listVersionsAsync() {
              return null;
            }

            @Override
            public PagedList<GalleryImageVersion> listVersions() {
              return null;
            }

            @Override
            public ComputeManager manager() {
              return null;
            }

            @Override
            public GalleryImageInner inner() {
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
            public Observable<GalleryImage> refreshAsync() {
              return null;
            }

            @Override
            public Update update() {
              return null;
            }
          });
        }

        @Override
        public Page<GalleryImage> nextPage(String s) throws RestException, IOException {
          return null;
        }
      });
      assertThat(azureDelegateHelperService.listImageDefinitions(
                     azureConfig, emptyList(), "someSubscriptionId", "someResourceGroup", "someGallery"))
          .hasSize(1);
    }
  }

  @Test()
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListImageDefinitionVersions() {
    try (MockedStatic<Azure> azureMockedStatic = Mockito.mockStatic(Azure.class)) {
      when(Azure.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(LogLevel.class))).thenReturn(configurable);
      when(configurable.authenticate(any(ApplicationTokenCredentials.class))).thenReturn(authenticated);
      when(authenticated.withSubscription(anyString())).thenReturn(azure);

      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();

      GalleryImageVersions galleryImageVersion = mock(GalleryImageVersions.class);
      when(azure.galleryImageVersions()).thenReturn(galleryImageVersion);
      when(galleryImageVersion.listByGalleryImage(anyString(), anyString(), anyString()))
          .thenReturn(new PagedList<GalleryImageVersion>() {
            @Override
            public Stream<GalleryImageVersion> stream() {
              return Stream.of(new GalleryImageVersion() {
                @Override
                public String id() {
                  return null;
                }

                @Override
                public String location() {
                  return null;
                }

                @Override
                public String name() {
                  return null;
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
                public DateTime endOfLifeDate() {
                  return null;
                }

                @Override
                public Boolean isExcludedFromLatest() {
                  return true;
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
                public GalleryImageVersionInner inner() {
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
                public Observable<GalleryImageVersion> refreshAsync() {
                  return null;
                }

                @Override
                public Update update() {
                  return null;
                }
              });
            }

            @Override
            public Page<GalleryImageVersion> nextPage(String s) throws RestException, IOException {
              return null;
            }
          });

      assertThat(azureDelegateHelperService.listImageDefinitionVersions(azureConfig, emptyList(), "someSubscriptionId",
                     "someResourceGroupName", "someGalleryName", "someImageDefinitionName"))
          .hasSize(1);
    }
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRethrowInvalidRequestExceptionWhenInvalidCredentialsExceptionIsThrown() {
    try (MockedStatic<Azure> azureMockedStatic = Mockito.mockStatic(Azure.class)) {
      when(Azure.configure()).thenReturn(configurable);
      when(configurable.withLogLevel(any(LogLevel.class))).thenReturn(configurable);
      when(configurable.authenticate(any(ApplicationTokenCredentials.class)))
          .thenThrow(new AuthenticationException(new AuthenticationException("Failed to authenticate")));
      AzureConfig azureConfig =
          AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();

      assertThatThrownBy(() -> { azureDelegateHelperService.getAzureClient(azureConfig); })
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("Invalid Azure credentials.");
    }
  }
}
