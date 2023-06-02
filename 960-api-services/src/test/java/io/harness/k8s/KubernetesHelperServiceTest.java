/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.rule.OwnerRule.ANSHUL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.oidc.OidcTokenRetriever;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import io.fabric8.openshift.client.OpenShiftClient;
import okhttp3.OkHttpClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class KubernetesHelperServiceTest extends CategoryTest {
  public static final String MASTER_URL = "http://masterUrl/";
  public static final String OC_URL = "http://masterUrl/oapi/v1/";
  public static final char[] USERNAME = "username".toCharArray();
  char[] PASSWORD = "PASSWORD".toCharArray();

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private OkHttpClient okHttpClient;
  @Mock private OidcTokenRetriever oidcTokenRetriever;

  @InjectMocks private KubernetesHelperService helperService;
  @InjectMocks private KubernetesHelperService kubernetesHelperService = spy(KubernetesHelperService.class);

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetOpenShiftClient() {
    ocClientWithNullNamespace();
    ocClientWithDefaultNamespace();
    ocClientWithNonDefaultNamespace();
  }

  private void ocClientWithNullNamespace() {
    final String namespace = null;
    OpenShiftClient ocClient = kubernetesHelperService.getOpenShiftClient(configWithNameSpace(namespace));
    assertThat(ocClient.getOpenshiftUrl().toString()).isEqualTo(OC_URL);
    assertThat(ocClient.getMasterUrl().toString()).isEqualTo(MASTER_URL);
  }

  private void ocClientWithDefaultNamespace() {
    final String namespace = "default";
    OpenShiftClient ocClient = kubernetesHelperService.getOpenShiftClient(configWithNameSpace(namespace));
    assertThat(ocClient.getNamespace()).isEqualTo(namespace);
    assertThat(ocClient.getOpenshiftUrl().toString()).isEqualTo(OC_URL);
    assertThat(ocClient.getMasterUrl().toString()).isEqualTo(MASTER_URL);
  }

  private void ocClientWithNonDefaultNamespace() {
    final String namespace = "newSpace";
    OpenShiftClient ocClient = kubernetesHelperService.getOpenShiftClient(configWithNameSpace(namespace));
    assertThat(ocClient.getNamespace()).isEqualTo(namespace);
    assertThat(ocClient.getOpenshiftUrl().toString()).isEqualTo(OC_URL);
    assertThat(ocClient.getMasterUrl().toString()).isEqualTo(MASTER_URL);
  }

  private KubernetesConfig configWithNameSpace(String namespace) {
    return KubernetesConfig.builder()
        .masterUrl(MASTER_URL)
        .namespace(namespace)
        .username(USERNAME)
        .password(PASSWORD)
        .build();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetKubernetesClient() {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    kubernetesConfig.setAuthType(KubernetesClusterAuthType.OIDC);
    when(oidcTokenRetriever.getOidcIdToken(kubernetesConfig)).thenReturn(null);

    helperService.getKubernetesClient(kubernetesConfig);
    verify(oidcTokenRetriever, times(1)).getOidcIdToken(kubernetesConfig);
  }

  @Test
  @Owner(developers = OwnerRule.SATYAM)
  @Category(UnitTests.class)
  public void testValidateCluster() {
    assertThatThrownBy(() -> helperService.validateCluster("")).isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MLUKIC)
  @Category(UnitTests.class)
  public void testValidateSubscription() {
    assertThatThrownBy(() -> helperService.validateSubscription("")).isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = OwnerRule.MLUKIC)
  @Category(UnitTests.class)
  public void testValidateResourceGroup() {
    assertThatThrownBy(() -> helperService.validateResourceGroup("")).isInstanceOf(InvalidArgumentsException.class);
  }
}
