/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.helpers;

import static io.harness.delegate.task.gcp.helpers.GcpHelperService.LOCATION_DELIMITER;
import static io.harness.k8s.K8sConstants.API_VERSION;
import static io.harness.k8s.K8sConstants.GCP_AUTH_PLUGIN_BINARY;
import static io.harness.k8s.K8sConstants.GCP_AUTH_PLUGIN_INSTALL_HINT;
import static io.harness.k8s.K8sConstants.GOOGLE_APPLICATION_CREDENTIALS_FLAG;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.BOGDAN;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.GcpServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.k8s.model.GcpAccessTokenSupplier;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.kubeconfig.Exec;
import io.harness.k8s.model.kubeconfig.InteractiveMode;
import io.harness.k8s.model.kubeconfig.KubeConfigAuthPluginHelper;
import io.harness.rule.Owner;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.util.store.DataStore;
import com.google.api.services.container.Container;
import com.google.api.services.container.model.Cluster;
import com.google.api.services.container.model.CreateClusterRequest;
import com.google.api.services.container.model.ListClustersResponse;
import com.google.api.services.container.model.MasterAuth;
import com.google.api.services.container.model.NodePool;
import com.google.api.services.container.model.NodePoolAutoscaling;
import com.google.api.services.container.model.Operation;
import com.google.api.services.container.model.UpdateClusterRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDP)
public class GkeClusterHelperTest extends CategoryTest {
  @Mock private DataStore<StoredCredential> dataStore;
  @Mock private Clock clock;
  @Mock private GcpHelperService gcpHelperService;
  @Mock private Container container;
  @Mock private Container.Projects projects;
  @Mock private Container.Projects.Locations locations;
  @Mock private Container.Projects.Locations.Clusters clusters;
  @Mock private Container.Projects.Locations.Clusters.Get clustersGet;
  @Mock private Container.Projects.Locations.Clusters.List clustersList;
  @Mock private Container.Projects.Locations.Clusters.Create clustersCreate;
  @Mock private Container.Projects.Locations.Clusters.Update clustersUpdate;
  @Mock private Container.Projects.Locations.Clusters.Delete clustersDelete;
  @Mock private Container.Projects.Locations.Operations operations;
  @Mock private Container.Projects.Locations.Operations.Get operationsGet;
  @Mock private HttpHeaders httpHeaders;

  @Inject @InjectMocks private GkeClusterHelper gkeClusterHelper;
  private GoogleJsonResponseException notFoundException;

  private static final String ZONE_CLUSTER = "zone-a/foo-bar";
  private static final ImmutableMap<String, String> CREATE_CLUSTER_PARAMS = ImmutableMap.<String, String>builder()
                                                                                .put("nodeCount", "1")
                                                                                .put("masterUser", "master")
                                                                                .put("masterPwd", "password")
                                                                                .build();

  private static final String DUMMY_GCP_KEY = "{\n"
      + "      \"type\": \"service_account\",\n"
      + "      \"project_id\": \"mock-project\",\n"
      + "      \"private_key_id\": \"768b325f9fad5b898890cad91e64d44c8b9851f7\",\n"
      + "      \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDB82kQsrtIhE9Y\\nTvHIplTGcc8YF4tIFe8oqLVQD1TExYCZ8P+nNuAO7NAmMCBbvNMwozTD+N/IgqC9\\n3nfwKzNgaRDmPDBdXItWii3AnxnCrAszJRJrUD/mQUGRIUe6P5q2zazFSdKqBMrX\\nW6/4VKXYF70Uz2rDFMbygxd1ndRJvXIIFLIlulqu7U8HePhVMHSLNi0u2WCcC76S\\nPH5spmBliABYUj0fRYgOtnPTw3qukWFPcq0VhPTRdP5/ApTriMr0nkfbrbJEh+Z4\\n7+/vY6vYmm1xaHc5Knl0sgPqXiRFOeUy3oQMF8KOsuJIFf24BFOXRIv8d257ChdE\\n011eyvCbAgMBAAECggEAT/mfCVOqBm0IitGCwcpUir/DNZv/wunIhGuM2EZ6HemS\\n7eeCg+EM4xqjehu+PBXQv+2MhILLRFMZFTH3IwGtXcP1Q/rttpHCoxy3YQY6CRwI\\nQj63KakdsESYVM/0U8iGc3q8E14tkA4J1mPoW+4LtN+VCE+/JlIa90U3FzjNoNnU\\ntSN6meGXgPm2sW7qb9Gy55mtwyGvILysVqCTBsA9J1+luUwDhsX+FK64QNJUQE7V\\niquThoDpusNaQeozR3LLLkgYAe8Vosi9JiqQsO5VsOTCdss6q+t5GIXVNuk088eg\\nRmeeDQ/4kWkl1KXafD2fWxNPZnySUySNedarLFiIAQKBgQD3bzaJ8/JG5fYVO1+2\\nII7kYjEnWOY5NuyWBRcHsYryi/dtfMC/NLnXFI/lrZEcFq8bpwzvRe4WeE/9Lth0\\nwQ0Bvt8vCItR2fEuyQlGQJH4D55440kceP0UNRqxeNY5HQ94HSHuUymP4rLt+12Z\\nftWCMYGxjRRWMZ90M6nKAXk82wKBgQDIqjh+nSDiMpkHXup2gkViJZDewtt822Ki\\nYazgAH6rd2DbX98gEY9vPyURkFirHFNwT2p1Cb/xCDv+/V2cIj3E9LE+nzsCuqHv\\nK/aDOh7JxRiR7QbJVAzph7+CXsBXZ9trln7apFX4JzWKhoJKIjga+kYDRBOW2X5V\\nyDftXjGHQQKBgQDuQZagq9gFUPXuZ+e3tg4h+DMgkkfNnAegRXJxpBIJj6FHOjNX\\namvwoQoWvVTXWThwRiD8Xbfuxxcu0mb3tdTSc3rxDScqP9QvmsFldlOYK2ILQcBq\\nvE3loWT8s0CEamk03ciIdme09zQYWE0+upTY8tbRoumMPeguunip3VVitQKBgA5Q\\nbjVB+i2IlHf9IlaP1mk46supNMUEVVXmB9H21xJeMq+TeDQubH/wDjHhjSGvpJgX\\nYi21I1cLUlRPOJVBsAxTtC0WaLw6GgEYrr4PsFCOWcFXGivUbhNelp+zKJ9Tjkhv\\ndN8d5/AKw/v8umCVblEmV0Y2XftdynBOFwc8t+XBAoGANfYiTARtDfXZDQyLgxh1\\njWvdZ+9r03CjIv92DSOOjbZAscqIyD7eI9J4pnntzXZcnEyqrR/Yzh4YxD8lKnyv\\ngphCp7yU3Sjl+h+ujyD1W0CSKvk+Pk/d83UtzDAI4tfMBXI2boifoCDspI0m1fIY\\n+ztYqVJVL3nV4AwsNomqY/4=\\n-----END PRIVATE KEY-----\\n\",\n"
      + "      \"client_email\": \"mock-account-id@mock-project.iam.gserviceaccount.com\",\n"
      + "      \"client_id\": \"114729593829257735690\",\n"
      + "      \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n"
      + "      \"token_uri\": \"https://oauth2.googleapis.com/token\",\n"
      + "      \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n"
      + "      \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/mock-account-id%40mock-project.iam.gserviceaccount.com\"\n"
      + "    }";
  private static final char[] DUMMY_GCP_KEY_CHARS = DUMMY_GCP_KEY.toCharArray();

  private static final Cluster CLUSTER_1 =
      new Cluster()
          .setName("cluster-name-1")
          .setZone("zone-a")
          .setInitialNodeCount(5)
          .setStatus("RUNNING")
          .setEndpoint("1.1.1.1")
          .setCurrentMasterVersion("1.18.1")
          .setMasterAuth(new MasterAuth().setUsername("master1").setPassword("password1"))
          .setNodePools(ImmutableList.of(
              new NodePool()
                  .setName("node-pool1.1")
                  .setAutoscaling(new NodePoolAutoscaling().setEnabled(false).setMinNodeCount(1).setMaxNodeCount(2)),
              new NodePool()
                  .setName("node-pool1.2")
                  .setAutoscaling(new NodePoolAutoscaling().setEnabled(true).setMinNodeCount(1).setMaxNodeCount(3))));

  private static final Cluster CLUSTER_2 =
      new Cluster()
          .setName("cluster-name-2")
          .setZone("zone-b")
          .setInitialNodeCount(5)
          .setStatus("RUNNING")
          .setEndpoint("1.1.1.2")
          .setCurrentMasterVersion("1.18.1")
          .setMasterAuth(new MasterAuth().setUsername("master2").setPassword("password2"))
          .setNodePools(Lists.newArrayList(
              new NodePool()
                  .setName("node-pool2")
                  .setAutoscaling(new NodePoolAutoscaling().setEnabled(true).setMinNodeCount(5).setMaxNodeCount(10))));

  private static final Cluster CLUSTER_1_8 = CLUSTER_2.clone().setCurrentMasterVersion("1.8.8");
  private static final Cluster CLUSTER_1_19 = CLUSTER_2.clone().setCurrentMasterVersion("1.19.0");

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(gcpHelperService.getGkeContainerService(eq(DUMMY_GCP_KEY_CHARS), anyBoolean())).thenReturn(container);
    when(gcpHelperService.getSleepIntervalSecs()).thenReturn(0);
    when(gcpHelperService.getTimeoutMins()).thenReturn(1);
    when(gcpHelperService.getClusterProjectId(any())).thenReturn("project-a");
    when(gcpHelperService.getGoogleCredential(any(), anyBoolean()))
        .thenReturn(
            GoogleCredential.fromStream(new ByteArrayInputStream(DUMMY_GCP_KEY.getBytes(StandardCharsets.UTF_8))));
    when(container.projects()).thenReturn(projects);
    when(projects.locations()).thenReturn(locations);
    when(locations.clusters()).thenReturn(clusters);
    when(clusters.get(anyString())).thenReturn(clustersGet);
    when(clusters.list(anyString())).thenReturn(clustersList);
    when(locations.operations()).thenReturn(operations);
    when(clusters.create(anyString(), any(CreateClusterRequest.class))).thenReturn(clustersCreate);
    when(clusters.update(anyString(), any(UpdateClusterRequest.class))).thenReturn(clustersUpdate);
    when(clusters.delete(anyString())).thenReturn(clustersDelete);
    when(operations.get(anyString())).thenReturn(operationsGet);

    GoogleJsonError googleJsonError = new GoogleJsonError();
    googleJsonError.setCode(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
    notFoundException = new GoogleJsonResponseException(
        new HttpResponseException.Builder(HttpStatusCodes.STATUS_CODE_NOT_FOUND, "not found", httpHeaders),
        googleJsonError);
    on(gkeClusterHelper).set("timeLimiter", new FakeTimeLimiter());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldCreateCluster() throws Exception {
    final List<Boolean> firstTime = new ArrayList<>(1);
    when(clustersGet.execute()).thenAnswer(invocation -> {
      if (firstTime.isEmpty()) {
        firstTime.add(true);
        throw notFoundException;
      }
      return CLUSTER_1;
    });
    Operation pendingOperation = new Operation().setStatus("RUNNING");
    when(clustersCreate.execute()).thenReturn(pendingOperation);
    Operation doneOperation = new Operation().setStatus("DONE");
    when(operationsGet.execute()).thenReturn(doneOperation);

    KubernetesConfig config =
        gkeClusterHelper.createCluster(DUMMY_GCP_KEY_CHARS, false, ZONE_CLUSTER, "default", CREATE_CLUSTER_PARAMS);

    verify(clusters).create(anyString(), any(CreateClusterRequest.class));
    assertThat(config.getMasterUrl()).isEqualTo("https://1.1.1.1/");
    assertThat(config.getUsername()).isEqualTo("master1".toCharArray());
    assertThat(config.getPassword()).isEqualTo("password1".toCharArray());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotCreateClusterIfExists() throws Exception {
    when(clustersGet.execute()).thenReturn(CLUSTER_1);
    KubernetesConfig config = gkeClusterHelper.createCluster(
        DUMMY_GCP_KEY.toCharArray(), false, ZONE_CLUSTER, "default", CREATE_CLUSTER_PARAMS);

    verify(clusters, times(0)).create(anyString(), any(CreateClusterRequest.class));
    assertThat(config.getMasterUrl()).isEqualTo("https://1.1.1.1/");
    assertThat(config.getUsername()).isEqualTo("master1".toCharArray());
    assertThat(config.getPassword()).isEqualTo("password1".toCharArray());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotCreateClusterIfError() throws Exception {
    when(clustersGet.execute()).thenThrow(notFoundException);
    when(clustersCreate.execute()).thenThrow(new IOException());

    KubernetesConfig config = gkeClusterHelper.createCluster(
        DUMMY_GCP_KEY.toCharArray(), false, ZONE_CLUSTER, "default", CREATE_CLUSTER_PARAMS);

    verify(clusters).create(anyString(), any(CreateClusterRequest.class));
    assertThat(config).isNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotCreateClusterIfOperationQueryFailed() throws Exception {
    when(clustersGet.execute()).thenThrow(notFoundException);
    Operation pendingOperation = new Operation().setStatus("RUNNING");
    when(clustersCreate.execute()).thenReturn(pendingOperation);
    when(operationsGet.execute()).thenThrow(new IOException());

    KubernetesConfig config = gkeClusterHelper.createCluster(
        DUMMY_GCP_KEY.toCharArray(), false, ZONE_CLUSTER, "default", CREATE_CLUSTER_PARAMS);

    verify(clusters).create(anyString(), any(CreateClusterRequest.class));
    assertThat(config).isNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetCluster() throws Exception {
    when(clustersGet.execute()).thenReturn(CLUSTER_1);

    KubernetesConfig config = gkeClusterHelper.getCluster(DUMMY_GCP_KEY.toCharArray(), false, ZONE_CLUSTER, "default");

    verify(clusters).get(anyString());
    assertThat(config.getMasterUrl()).isEqualTo("https://1.1.1.1/");
    assertThat(config.getUsername()).isEqualTo("master1".toCharArray());
    assertThat(config.getPassword()).isEqualTo("password1".toCharArray());
    assertThat(config.getServiceAccountTokenSupplier()).isNull();
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldGetClusterWithGkeTokenSupplierIfVersion119orBigger() throws Exception {
    // given
    Instant now = Instant.now();
    when(clock.instant()).thenReturn(now);

    String accessToken = "access_token";
    StoredCredential storedCredential =
        new StoredCredential()
            .setAccessToken(accessToken)
            .setExpirationTimeMilliseconds(now.plus(Duration.ofMinutes(30)).toEpochMilli());

    when(clustersGet.execute()).thenReturn(CLUSTER_1_19);
    when(dataStore.get(eq("mock-account-id@mock-project.iam.gserviceaccount.com"))).thenReturn(storedCredential);

    GoogleCredential creds =
        GoogleCredential.fromStream(new ByteArrayInputStream(DUMMY_GCP_KEY.getBytes(StandardCharsets.UTF_8)));
    when(gcpHelperService.getGoogleCredential(eq(DUMMY_GCP_KEY_CHARS), eq(false))).thenReturn(creds);
    MockedStatic mockedStaticAuthPlugin = mockStatic(KubeConfigAuthPluginHelper.class);
    when(KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable(any(), any())).thenReturn(false);
    when(KubeConfigAuthPluginHelper.runCommand(any(), any(), any())).thenReturn(true);

    // when
    KubernetesConfig config = gkeClusterHelper.getCluster(DUMMY_GCP_KEY.toCharArray(), false, ZONE_CLUSTER, "default");
    mockedStaticAuthPlugin.close();

    // then
    verify(clusters).get(anyString());
    assertThat(config.getServiceAccountTokenSupplier().get()).isEqualTo(accessToken);
    assertThat(config.getGcpAccountKeyFileContent()).hasValue(DUMMY_GCP_KEY);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldGetClusterWithNoGkeTokenSupplierIfInheritingCredentials() throws Exception {
    // given
    GoogleCredential googleCredential =
        GoogleCredential.fromStream(new ByteArrayInputStream(DUMMY_GCP_KEY.getBytes(StandardCharsets.UTF_8)));
    try (MockedStatic<GoogleCredential> mockStatic = mockStatic(GoogleCredential.class)) {
      mockStatic.when(() -> GoogleCredential.getApplicationDefault(any(), any())).thenReturn(googleCredential);
      mockStatic.when(() -> GoogleCredential.getApplicationDefault()).thenReturn(googleCredential);
      when(clustersGet.execute()).thenReturn(CLUSTER_1_19);

      // when
      KubernetesConfig config = gkeClusterHelper.getCluster(DUMMY_GCP_KEY.toCharArray(), true, ZONE_CLUSTER, "default");

      // then
      verify(clusters).get(anyString());
      assertThat(config.getServiceAccountTokenSupplier()).isInstanceOf(GcpAccessTokenSupplier.class);
      assertThat(config.getGcpAccountKeyFileContent()).isEmpty();
    }
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldNotReturnTokensIfClusterVersionLessThan119() throws Exception {
    // given
    when(clustersGet.execute()).thenReturn(CLUSTER_1_8);

    // when
    KubernetesConfig config = gkeClusterHelper.getCluster(DUMMY_GCP_KEY.toCharArray(), false, ZONE_CLUSTER, "default");

    // then
    verify(clusters).get(anyString());
    assertThat(config.getServiceAccountTokenSupplier()).isNull();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldGetClusterWithInheritedCredentials() throws Exception {
    when(clustersGet.execute()).thenReturn(CLUSTER_1);

    KubernetesConfig config = gkeClusterHelper.getCluster(DUMMY_GCP_KEY_CHARS, true, ZONE_CLUSTER, "default");

    verify(clusters).get(anyString());
    assertThat(config.getMasterUrl()).isEqualTo("https://1.1.1.1/");
    assertThat(config.getUsername()).isEqualTo("master1".toCharArray());
    assertThat(config.getPassword()).isEqualTo("password1".toCharArray());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForInvalidClusterName() throws Exception {
    when(clustersGet.execute()).thenReturn(CLUSTER_1);
    assertThatThrownBy(() -> gkeClusterHelper.getCluster(null, true, null, "default"))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> gkeClusterHelper.getCluster(null, true, "foo", "default"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotGetClusterIfNotExists() throws Exception {
    when(clustersGet.execute()).thenThrow(notFoundException);

    try {
      gkeClusterHelper.getCluster(DUMMY_GCP_KEY_CHARS, false, ZONE_CLUSTER, "default");
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      // Expected
    }

    verify(clusters).get(anyString());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotGetClusterIfError() throws Exception {
    when(clustersGet.execute()).thenThrow(new IOException());

    try {
      gkeClusterHelper.getCluster(DUMMY_GCP_KEY_CHARS, false, ZONE_CLUSTER, "default");
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      // Expected
    }

    verify(clusters).get(anyString());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotGetClusterIfOtherJsonError() throws Exception {
    GoogleJsonError googleJsonError = new GoogleJsonError();
    googleJsonError.setCode(HttpStatusCodes.STATUS_CODE_FORBIDDEN);
    when(clustersGet.execute())
        .thenThrow(new GoogleJsonResponseException(
            new HttpResponseException.Builder(HttpStatusCodes.STATUS_CODE_FORBIDDEN, "forbidden", httpHeaders),
            googleJsonError));

    try {
      gkeClusterHelper.getCluster(DUMMY_GCP_KEY_CHARS, false, ZONE_CLUSTER, "default");
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      // Expected
    }

    verify(clusters).get(anyString());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldListClusters() throws Exception {
    List<Cluster> clusterList = ImmutableList.of(CLUSTER_1, CLUSTER_2);
    when(clustersList.execute()).thenReturn(new ListClustersResponse().setClusters(clusterList));

    List<String> result = gkeClusterHelper.listClusters(DUMMY_GCP_KEY_CHARS, false);

    verify(clusters).list(anyString());
    assertThat(result).containsExactlyInAnyOrder(CLUSTER_1.getZone() + LOCATION_DELIMITER + CLUSTER_1.getName(),
        CLUSTER_2.getZone() + LOCATION_DELIMITER + CLUSTER_2.getName());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotListClustersIfError() throws Exception {
    when(clustersList.execute()).thenThrow(new IOException());

    assertThatThrownBy(() -> gkeClusterHelper.listClusters(DUMMY_GCP_KEY_CHARS, false))
        .isInstanceOf(GcpServerException.class);

    verify(clusters).list(anyString());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void shouldNotListClustersIfResponseException() throws IOException {
    GoogleJsonError googleJsonError = new GoogleJsonError();
    googleJsonError.setMessage("Simulated Google Json Error");

    GoogleJsonResponseException responseException = new GoogleJsonResponseException(
        new HttpResponseException.Builder(HttpStatusCodes.STATUS_CODE_BAD_REQUEST, "Forbidden", httpHeaders),
        googleJsonError);
    when(clustersList.execute()).thenThrow(responseException);
    assertThatThrownBy(() -> gkeClusterHelper.listClusters(DUMMY_GCP_KEY_CHARS, false))
        .isInstanceOf(GcpServerException.class);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldGetClusterWithExec() throws Exception {
    when(clustersGet.execute()).thenReturn(CLUSTER_1_8);

    GoogleCredential creds =
        GoogleCredential.fromStream(new ByteArrayInputStream(DUMMY_GCP_KEY.getBytes(StandardCharsets.UTF_8)));
    when(gcpHelperService.getGoogleCredential(eq(DUMMY_GCP_KEY_CHARS), eq(false))).thenReturn(creds);
    MockedStatic mockedStaticAuthPlugin = mockStatic(KubeConfigAuthPluginHelper.class);
    when(KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable(any(), any())).thenReturn(true);
    when(KubeConfigAuthPluginHelper.runCommand(any(), any(), any())).thenReturn(true);

    // when
    KubernetesConfig config = gkeClusterHelper.getCluster(DUMMY_GCP_KEY.toCharArray(), false, ZONE_CLUSTER, "default");
    mockedStaticAuthPlugin.close();

    // then
    verify(clusters).get(anyString());
    assertThat(config.getServiceAccountTokenSupplier()).isNull();
    assertThat(config.getExec())
        .isEqualTo(Exec.builder()
                       .command(GCP_AUTH_PLUGIN_BINARY)
                       .apiVersion(API_VERSION)
                       .args(Collections.singletonList(GOOGLE_APPLICATION_CREDENTIALS_FLAG))
                       .installHint(GCP_AUTH_PLUGIN_INSTALL_HINT)
                       .provideClusterInfo(true)
                       .interactiveMode(InteractiveMode.NEVER)
                       .build());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldGetClusterWithExecAndGkeTokenSupplierIfVersion119orBigger() throws Exception {
    // given
    Instant now = Instant.now();
    when(clock.instant()).thenReturn(now);

    String accessToken = "access_token";
    StoredCredential storedCredential =
        new StoredCredential()
            .setAccessToken(accessToken)
            .setExpirationTimeMilliseconds(now.plus(Duration.ofMinutes(30)).toEpochMilli());

    when(clustersGet.execute()).thenReturn(CLUSTER_1_19);
    when(dataStore.get(eq("mock-account-id@mock-project.iam.gserviceaccount.com"))).thenReturn(storedCredential);

    GoogleCredential creds =
        GoogleCredential.fromStream(new ByteArrayInputStream(DUMMY_GCP_KEY.getBytes(StandardCharsets.UTF_8)));
    when(gcpHelperService.getGoogleCredential(eq(DUMMY_GCP_KEY_CHARS), eq(false))).thenReturn(creds);
    MockedStatic mockedStaticAuthPlugin = mockStatic(KubeConfigAuthPluginHelper.class);
    when(KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable(any(), any())).thenReturn(true);
    when(KubeConfigAuthPluginHelper.runCommand(any(), any(), any())).thenReturn(true);

    // when
    KubernetesConfig config = gkeClusterHelper.getCluster(DUMMY_GCP_KEY.toCharArray(), false, ZONE_CLUSTER, "default");
    mockedStaticAuthPlugin.close();

    // then
    verify(clusters).get(anyString());
    assertThat(config.getServiceAccountTokenSupplier().get()).isEqualTo(accessToken);
    assertThat(config.getGcpAccountKeyFileContent()).hasValue(DUMMY_GCP_KEY);
    assertThat(config.getExec())
        .isEqualTo(Exec.builder()
                       .command(GCP_AUTH_PLUGIN_BINARY)
                       .apiVersion(API_VERSION)
                       .args(Collections.singletonList(GOOGLE_APPLICATION_CREDENTIALS_FLAG))
                       .installHint(GCP_AUTH_PLUGIN_INSTALL_HINT)
                       .provideClusterInfo(true)
                       .interactiveMode(InteractiveMode.NEVER)
                       .build());
  }
}
