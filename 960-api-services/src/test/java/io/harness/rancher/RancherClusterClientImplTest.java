/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rancher;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.ngexception.RancherClientRuntimeException;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;
import retrofit2.Response;

public class RancherClusterClientImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private RancherRestClientFactory rancherRestClientFactory;

  @Mock private RancherRestClient rancherRestClient;

  @Spy @InjectMocks RancherClusterClientImpl clusterClient;

  @Mock private List<RancherListClustersResponse.RancherClusterItem> clusters;

  @Mock Call<RancherListClustersResponse> mockListClustersCall;

  @Mock Request mockRequest;
  @Mock HttpUrl mockHttpUrl;
  @Mock Response<RancherListClustersResponse> mockListClustersResponseWrapper;

  @Mock Call<RancherGenerateKubeconfigResponse> mockGenerateKubeconfigCall;
  @Mock Response<RancherGenerateKubeconfigResponse> mockGenerateKubeconfigResponseWrapper;

  @Mock Call<RancherDeleteKubeconfigTokenResponse> mockDeleteKubeconfigCall;
  @Mock Response<RancherDeleteKubeconfigTokenResponse> mockDeleteKubeconfigResponseWrapper;

  @Before
  public void setup() {
    doReturn(rancherRestClient).when(rancherRestClientFactory).getRestClient(any(), any());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testListClustersSuccess() throws IOException {
    RancherListClustersResponse clustersResponse = RancherListClustersResponse.builder().data(clusters).build();
    doReturn(mockListClustersCall).when(rancherRestClient).listClusters(any());
    doReturn(mockRequest).when(mockListClustersCall).request();
    doReturn(mockHttpUrl).when(mockRequest).url();
    doReturn("ENDPOINT").when(mockHttpUrl).toString();
    doReturn(mockListClustersResponseWrapper).when(mockListClustersCall).execute();
    doReturn(true).when(mockListClustersResponseWrapper).isSuccessful();
    doReturn(200).when(mockListClustersResponseWrapper).code();
    doReturn(clustersResponse).when(mockListClustersResponseWrapper).body();

    RancherListClustersResponse actualResponse = clusterClient.listClusters("TOKEN", "URL", emptyMap());
    assertThat(actualResponse.getData()).isEqualTo(clusters);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testListClustersUnsuccessfulOrEmptyResponse() throws IOException {
    doReturn(mockListClustersCall).when(rancherRestClient).listClusters(any());
    doReturn(mockRequest).when(mockListClustersCall).request();
    doReturn(mockHttpUrl).when(mockRequest).url();
    doReturn("ENDPOINT").when(mockHttpUrl).toString();
    doReturn(mockListClustersResponseWrapper).when(mockListClustersCall).execute();
    doReturn(false).when(mockListClustersResponseWrapper).isSuccessful();
    doReturn(1234).when(mockListClustersResponseWrapper).code();
    assertThatThrownBy(() -> clusterClient.listClusters("TOKEN", "URL", emptyMap()))
        .isInstanceOf(RancherClientRuntimeException.class)
        .hasMessageContaining("Status code: [1234]")
        .hasMessageContaining("Endpoint: [ENDPOINT]");

    doReturn(true).when(mockListClustersResponseWrapper).isSuccessful();
    doReturn(null).when(mockListClustersResponseWrapper).body();
    doReturn(5678).when(mockListClustersResponseWrapper).code();
    assertThatThrownBy(() -> clusterClient.listClusters("TOKEN", "URL", emptyMap()))
        .isInstanceOf(RancherClientRuntimeException.class)
        .hasMessageContaining("Status code: [5678]")
        .hasMessageContaining("Endpoint: [ENDPOINT]");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testListClustersFailure() {
    doThrow(RuntimeException.class).when(rancherRestClient).listClusters(any());
    assertThatThrownBy(() -> clusterClient.listClusters("TOKEN", "URL", emptyMap()))
        .isInstanceOf(RancherClientRuntimeException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGenerateKubeconfigSuccess() throws IOException {
    RancherGenerateKubeconfigResponse kubeconfigResponse =
        RancherGenerateKubeconfigResponse.builder().config("KUBECONFIG").build();
    doReturn(mockGenerateKubeconfigCall).when(rancherRestClient).generateKubeconfig(any());
    doReturn(mockRequest).when(mockGenerateKubeconfigCall).request();
    doReturn(mockHttpUrl).when(mockRequest).url();
    doReturn("ENDPOINT").when(mockHttpUrl).toString();
    doReturn(mockGenerateKubeconfigResponseWrapper).when(mockGenerateKubeconfigCall).execute();
    doReturn(true).when(mockGenerateKubeconfigResponseWrapper).isSuccessful();
    doReturn(200).when(mockGenerateKubeconfigResponseWrapper).code();
    doReturn(kubeconfigResponse).when(mockGenerateKubeconfigResponseWrapper).body();

    RancherGenerateKubeconfigResponse actualResponse = clusterClient.generateKubeconfig("TOKEN", "URL", "CLUSTER");
    assertThat(actualResponse.getConfig()).isEqualTo("KUBECONFIG");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGenerateKubeconfigUnsuccessfulOrEmptyResponse() throws IOException {
    doReturn(mockGenerateKubeconfigCall).when(rancherRestClient).generateKubeconfig(any());
    doReturn(mockRequest).when(mockGenerateKubeconfigCall).request();
    doReturn(mockHttpUrl).when(mockRequest).url();
    doReturn("ENDPOINT").when(mockHttpUrl).toString();
    doReturn(mockGenerateKubeconfigResponseWrapper).when(mockGenerateKubeconfigCall).execute();
    doReturn(false).when(mockGenerateKubeconfigResponseWrapper).isSuccessful();
    doReturn(1234).when(mockGenerateKubeconfigResponseWrapper).code();
    assertThatThrownBy(() -> clusterClient.generateKubeconfig("TOKEN", "URL", "CLUSTER"))
        .isInstanceOf(RancherClientRuntimeException.class)
        .hasMessageContaining("Status code: [1234]")
        .hasMessageContaining("Endpoint: [ENDPOINT]");

    doReturn(true).when(mockGenerateKubeconfigResponseWrapper).isSuccessful();
    doReturn(null).when(mockGenerateKubeconfigResponseWrapper).body();
    doReturn(5678).when(mockGenerateKubeconfigResponseWrapper).code();
    assertThatThrownBy(() -> clusterClient.generateKubeconfig("TOKEN", "URL", "CLUSTER"))
        .isInstanceOf(RancherClientRuntimeException.class)
        .hasMessageContaining("Status code: [5678]")
        .hasMessageContaining("Endpoint: [ENDPOINT]");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testGenerateKubeconfigFailure() {
    doThrow(RuntimeException.class).when(rancherRestClient).generateKubeconfig(any());
    assertThatThrownBy(() -> clusterClient.generateKubeconfig("TOKEN", "URL", "CLUSTER"))
        .isInstanceOf(RancherClientRuntimeException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testDeleteKubeconfig() throws IOException {
    RancherDeleteKubeconfigTokenResponse deleteKubeconfigTokenResponse =
        RancherDeleteKubeconfigTokenResponse.builder().build();

    doReturn(mockDeleteKubeconfigCall).when(rancherRestClient).deleteKubeconfigToken(any());
    doReturn(mockRequest).when(mockDeleteKubeconfigCall).request();
    doReturn(mockHttpUrl).when(mockRequest).url();
    doReturn(mockDeleteKubeconfigResponseWrapper).when(mockDeleteKubeconfigCall).execute();
    doReturn(false).when(mockDeleteKubeconfigResponseWrapper).isSuccessful();
    doReturn(403).when(mockDeleteKubeconfigResponseWrapper).code();
    doReturn(RancherDeleteKubeconfigTokenResponse.builder().build()).when(mockDeleteKubeconfigResponseWrapper).body();

    assertThatCode(() -> clusterClient.deleteKubeconfigToken("token", "url", "token")).doesNotThrowAnyException();
    verify(rancherRestClient, times(1)).deleteKubeconfigToken(any());
  }
}
