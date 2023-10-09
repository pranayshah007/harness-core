/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ClusterConfig;
import io.harness.spec.server.idp.v1.model.KubernetesConfig;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class KubernetesDslTest extends CategoryTest {
  private static final String TEST_CLUSTER = "cluster1";
  private static final String TEST_LABEL_SELECTOR = "app=myapp";
  private static final String TEST_URL = "http://192.168.0.1";
  private static final String TEST_ACCOUNT = "testAccount";
  AutoCloseable openMocks;
  @InjectMocks private KubernetesDsl kubernetesDsl;
  @Mock private DslClientFactory dslClientFactory;
  @Mock private DslClient dslClient;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetDslData() throws NoSuchAlgorithmException, KeyManagementException {
    KubernetesConfig kubernetesConfig = new KubernetesConfig();
    kubernetesConfig.setLabelSelector(TEST_LABEL_SELECTOR);
    ClusterConfig clusterConfig = new ClusterConfig();
    clusterConfig.setName(TEST_CLUSTER);
    clusterConfig.setUrl(TEST_URL);
    List<ClusterConfig> clusters = new ArrayList<>();
    clusters.add(clusterConfig);
    kubernetesConfig.setClusters(clusters);

    when(dslClientFactory.getClient(eq(TEST_ACCOUNT), anyString())).thenReturn(dslClient);
    when(dslClient.call(eq(TEST_ACCOUNT), any(ApiRequestDetails.class)))
        .thenReturn(Response.status(Response.Status.OK).entity("{items:[{a:b}]}").build());

    Map<String, Object> dslData = kubernetesDsl.getDslData(TEST_ACCOUNT, kubernetesConfig);

    assertTrue(dslData.containsKey(TEST_CLUSTER));
    assertEquals("b", ((Map) ((ArrayList) dslData.get(TEST_CLUSTER)).get(0)).get("a"));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetDslDataError() throws NoSuchAlgorithmException, KeyManagementException {
    KubernetesConfig kubernetesConfig = new KubernetesConfig();
    kubernetesConfig.setLabelSelector(TEST_LABEL_SELECTOR);
    ClusterConfig clusterConfig = new ClusterConfig();
    clusterConfig.setName(TEST_CLUSTER);
    clusterConfig.setUrl(TEST_URL);
    List<ClusterConfig> clusters = new ArrayList<>();
    clusters.add(clusterConfig);
    kubernetesConfig.setClusters(clusters);

    when(dslClientFactory.getClient(eq(TEST_ACCOUNT), anyString())).thenReturn(dslClient);
    when(dslClient.call(eq(TEST_ACCOUNT), any(ApiRequestDetails.class)))
        .thenReturn(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseMessage.builder().message("Error message").build())
                        .build());

    Map<String, Object> dslData = kubernetesDsl.getDslData(TEST_ACCOUNT, kubernetesConfig);

    assertTrue(dslData.containsKey(ERROR_MESSAGE_KEY));
    assertEquals("Error message", dslData.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetDslDataUnauthorized() throws NoSuchAlgorithmException, KeyManagementException {
    KubernetesConfig kubernetesConfig = new KubernetesConfig();
    kubernetesConfig.setLabelSelector(TEST_LABEL_SELECTOR);
    ClusterConfig clusterConfig = new ClusterConfig();
    clusterConfig.setName(TEST_CLUSTER);
    clusterConfig.setUrl(TEST_URL);
    List<ClusterConfig> clusters = new ArrayList<>();
    clusters.add(clusterConfig);
    kubernetesConfig.setClusters(clusters);

    when(dslClientFactory.getClient(eq(TEST_ACCOUNT), anyString())).thenReturn(dslClient);
    when(dslClient.call(eq(TEST_ACCOUNT), any(ApiRequestDetails.class)))
        .thenReturn(Response.status(Response.Status.UNAUTHORIZED).entity("{message: UNAUTHORIZED}").build());

    Map<String, Object> dslData = kubernetesDsl.getDslData(TEST_ACCOUNT, kubernetesConfig);

    assertTrue(dslData.containsKey(ERROR_MESSAGE_KEY));
    assertEquals("UNAUTHORIZED", dslData.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetDslDataMalformedURIException() {
    KubernetesConfig kubernetesConfig = new KubernetesConfig();
    kubernetesConfig.setLabelSelector(TEST_LABEL_SELECTOR);
    ClusterConfig clusterConfig = new ClusterConfig();
    clusterConfig.setName(TEST_CLUSTER);
    clusterConfig.setUrl("abc");
    List<ClusterConfig> clusters = new ArrayList<>();
    clusters.add(clusterConfig);
    kubernetesConfig.setClusters(clusters);

    Map<String, Object> dslData = kubernetesDsl.getDslData(TEST_ACCOUNT, kubernetesConfig);

    assertTrue(dslData.containsKey(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetDslDataKeyManagementException() throws NoSuchAlgorithmException, KeyManagementException {
    String errorMessage = "KeyManagementException";
    KubernetesConfig kubernetesConfig = new KubernetesConfig();
    kubernetesConfig.setLabelSelector(TEST_LABEL_SELECTOR);
    ClusterConfig clusterConfig = new ClusterConfig();
    clusterConfig.setName(TEST_CLUSTER);
    clusterConfig.setUrl(TEST_URL);
    List<ClusterConfig> clusters = new ArrayList<>();
    clusters.add(clusterConfig);
    kubernetesConfig.setClusters(clusters);

    when(dslClientFactory.getClient(eq(TEST_ACCOUNT), anyString())).thenReturn(dslClient);
    when(dslClient.call(eq(TEST_ACCOUNT), any(ApiRequestDetails.class)))
        .thenThrow(new KeyManagementException(errorMessage));

    Map<String, Object> dslData = kubernetesDsl.getDslData(TEST_ACCOUNT, kubernetesConfig);

    assertTrue(dslData.containsKey(ERROR_MESSAGE_KEY));
    assertEquals(errorMessage, dslData.get(ERROR_MESSAGE_KEY));
  }
}
