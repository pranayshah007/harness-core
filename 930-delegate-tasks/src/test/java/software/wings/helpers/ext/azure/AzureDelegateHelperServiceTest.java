/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure;

import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.tag.AzureListTagsResponse;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import software.wings.beans.AzureConfig;
import software.wings.service.intfc.security.EncryptionService;

import com.azure.core.management.profile.AzureProfile;
import java.io.IOException;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
public class AzureDelegateHelperServiceTest {
  @InjectMocks private AzureDelegateHelperService azureDelegateHelperService;
  @Mock AzureProfile azureProfile;
  @Mock SecretManagerClientService secretManagerClientService;

  @Mock EncryptionService encryptionService;

  @Before
  public void before() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void shouldHandleNullListResultWhenGetHelmChartNames() throws IOException {
    AzureDelegateHelperService azureDelegateHelperService1 = spy(azureDelegateHelperService);
    AzureConfig azureConfig = AzureConfig.builder()
                                  .key("key".toCharArray())
                                  .clientId("clientId")
                                  .tenantId("tenantId")
                                  .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                  .build();

    AzureManagementRestClient azureManagementRestClient = mock(AzureManagementRestClient.class);
    doReturn(azureManagementRestClient).when(azureDelegateHelperService1).getAzureManagementRestClient(any());
    doReturn("token").when(azureDelegateHelperService1).getAzureBearerAuthToken(any());
    Call<AzureListTagsResponse> responseCall = (Call<AzureListTagsResponse>) mock(Call.class);
    doReturn(responseCall).when(azureManagementRestClient).listTags(anyString(), anyString());
    ResponseBody body = mock(ResponseBody.class);
    Response<AcrGetRepositoriesResponse> cleanupResponse = Response.error(400, body);
    doReturn(cleanupResponse).when(responseCall).execute();
    assertThatThrownBy(() -> azureDelegateHelperService1.listTags(azureConfig, null, "registryName"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to connect to Azure cluster. HINT. EXPLANATION. INVALID_ARTIFACT_SERVER");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void listTagsBySubscriptionlistTagsBySubscription() throws IOException {
    AzureDelegateHelperService azureDelegateHelperService1 = spy(azureDelegateHelperService);
    AzureConfig azureConfig = AzureConfig.builder()
                                  .key("key".toCharArray())
                                  .clientId("clientId")
                                  .tenantId("tenantId")
                                  .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                  .build();

    AzureManagementRestClient azureManagementRestClient = mock(AzureManagementRestClient.class);
    doReturn(azureManagementRestClient).when(azureDelegateHelperService1).getAzureManagementRestClient(any());
    doReturn("token").when(azureDelegateHelperService1).getAzureBearerAuthToken(any());
    Call<AzureListTagsResponse> responseCall = (Call<AzureListTagsResponse>) mock(Call.class);
    doReturn(responseCall).when(azureManagementRestClient).listTags(anyString(), anyString());
    ResponseBody body = mock(ResponseBody.class);
    Response<AcrGetRepositoriesResponse> cleanupResponse = Response.error(400, body);
    doReturn(cleanupResponse).when(responseCall).execute();
    assertThatThrownBy(() -> azureDelegateHelperService1.listTagsBySubscription("subscriptionId", azureConfig, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to connect to Azure cluster. HINT. EXPLANATION. INVALID_ARTIFACT_SERVER");
  }
}
