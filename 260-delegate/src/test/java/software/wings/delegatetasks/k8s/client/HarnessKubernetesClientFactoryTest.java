/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.client;

import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.AVMOHAN;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.k8s.exception.K8sClusterException;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import io.fabric8.kubernetes.client.Adapters;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.ExtensionsAPIGroupClient;
import io.fabric8.kubernetes.client.ExtensionsAPIGroupExtensionAdapter;
import io.fabric8.kubernetes.client.GenericKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class HarnessKubernetesClientFactoryTest extends WingsBaseTest {
  @InjectMocks private HarnessKubernetesClientFactory harnessKubernetesClientFactory;
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private KubernetesHelperService kubernetesHelperService;

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testMasterURLModifier() {
    String masterURL = "https://35.197.55.65";
    String modifiedURL = harnessKubernetesClientFactory.modifyMasterUrl(masterURL);
    assertThat(modifiedURL).isEqualTo(masterURL + ":443");

    masterURL = "https://35.197.55.65:90";
    modifiedURL = harnessKubernetesClientFactory.modifyMasterUrl(masterURL);
    assertThat(modifiedURL).isEqualTo(masterURL);

    masterURL = "http://35.197.55.65";
    modifiedURL = harnessKubernetesClientFactory.modifyMasterUrl(masterURL);
    assertThat(modifiedURL).isEqualTo(masterURL + ":80");
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testMasterURLModifierException() {
    String masterURL = "invalidURL";
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> harnessKubernetesClientFactory.modifyMasterUrl(masterURL));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldNotAddOrRemoveTrailingSlash() throws Exception {
    assertThat(harnessKubernetesClientFactory.modifyMasterUrl("https://test-domain.net"))
        .isEqualTo("https://test-domain.net:443");
    assertThat(harnessKubernetesClientFactory.modifyMasterUrl("https://test-domain.net/"))
        .isEqualTo("https://test-domain.net:443/");
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPreservePathSegments() throws Exception {
    String masterUrl = "https://int-capi-rancher.cncpl.us/k8s/clusters/c-pv9p9";
    assertThat(harnessKubernetesClientFactory.modifyMasterUrl(masterUrl))
        .isEqualTo("https://int-capi-rancher.cncpl.us:443/k8s/clusters/c-pv9p9");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testNewKubernetesClient() {
    String masterUrl = "https://int-capi-rancher.cncpl.us/k8s/clusters/c-pv9p9";
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().masterUrl(masterUrl).build();
    K8sClusterConfig k8sClusterConfig = K8sClusterConfig.builder().build();
    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean()))
        .thenReturn(kubernetesConfig);

    harnessKubernetesClientFactory.newKubernetesClient(k8sClusterConfig);
    verify(kubernetesHelperService, times(1)).getKubernetesClient(kubernetesConfig);
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(eq(k8sClusterConfig), anyBoolean());
    assertThat(kubernetesConfig.getMasterUrl()).isEqualTo("https://int-capi-rancher.cncpl.us:443/k8s/clusters/c-pv9p9");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldAdaptDefaultK8sClient() {
    KubernetesConfig kubernetesConfig =
        KubernetesConfig.builder().masterUrl("https://int-capi-rancher.cncpl.us:443/k8s/clusters/c-pv9p9").build();
    K8sClusterConfig k8sClusterConfig = K8sClusterConfig.builder().build();
    KubernetesClient client = new DefaultKubernetesClient();

    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean()))
        .thenReturn(kubernetesConfig);
    when(kubernetesHelperService.getKubernetesClient(any(KubernetesConfig.class))).thenReturn(client);

    Adapters.register(new ExtensionsAPIGroupExtensionAdapter());

    harnessKubernetesClientFactory.newAdaptedClient(k8sClusterConfig, ExtensionsAPIGroupClient.class);

    verify(kubernetesHelperService, times(1)).getKubernetesClient(kubernetesConfig);
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(eq(k8sClusterConfig), anyBoolean());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenNoAdaptableClient() {
    KubernetesConfig kubernetesConfig =
        KubernetesConfig.builder().masterUrl("https://int-capi-rancher.cncpl.us:443/k8s/clusters/c-pv9p9").build();
    K8sClusterConfig k8sClusterConfig = K8sClusterConfig.builder().build();
    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean()))
        .thenReturn(kubernetesConfig);

    try {
      harnessKubernetesClientFactory.newAdaptedClient(k8sClusterConfig, GenericKubernetesClient.class);
    } catch (Exception ex) {
      verify(kubernetesHelperService, times(1)).getKubernetesClient(kubernetesConfig);
      verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(eq(k8sClusterConfig), anyBoolean());
      assertThat(ex).isInstanceOf(K8sClusterException.class);
    }
  }
}
