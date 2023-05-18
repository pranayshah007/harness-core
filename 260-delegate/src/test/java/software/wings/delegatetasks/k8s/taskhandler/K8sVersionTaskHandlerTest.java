/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.rule.OwnerRule.SHUBHAM_MAHESHWARI;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.config.CCMConfig;
import io.harness.k8s.apiclient.ApiClientFactoryImpl;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.RancherConfig;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.k8s.response.K8sVersionResponse;
import software.wings.settings.SettingVariableTypes;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.VersionInfo;
import io.kubernetes.client.util.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class K8sVersionTaskHandlerTest extends WingsBaseTest {
  @Mock private ApiClientFactoryImpl apiClientFactory;
  @Mock private K8sTaskParameters k8sTaskParameters;
  @Mock private K8sClusterConfig k8sClusterConfig;
  @Mock private KubernetesConfig kubernetesConfig;
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;

  @InjectMocks @Spy private K8sVersionTaskHandler k8sVersionTaskHandler;

  private KubernetesClusterConfig kubernetesClusterConfig;
  private VersionInfo versionInfo;
  private K8sTaskExecutionResponse k8sTaskExecutionResponse;
  private K8sVersionResponse k8sVersionResponse;

  @Before
  public void setUp() {
    versionInfo = new VersionInfo();
    versionInfo.setMajor("1");
    versionInfo.setMinor("11+");
    versionInfo.setPlatform("linux86");
    versionInfo.setGitVersion("1.16+gke.2");
    versionInfo.setGitCommit("qodjvzmalofrighxkq");

    k8sVersionResponse = K8sVersionResponse.builder()
                             .serverMajorVersion(versionInfo.getMajor())
                             .serverMinorVersion(versionInfo.getMinor())
                             .gitVersion(versionInfo.getGitVersion())
                             .platform(versionInfo.getPlatform())
                             .gitCommit(versionInfo.getGitCommit())
                             .build();

    k8sTaskExecutionResponse = K8sTaskExecutionResponse.builder()
                                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                   .k8sTaskResponse(k8sVersionResponse)
                                   .build();

    CCMConfig ccmConfig = CCMConfig.builder().cloudCostEnabled(true).skipK8sEventCollection(true).build();

    kubernetesClusterConfig = new KubernetesClusterConfig();
    kubernetesClusterConfig.setType(SettingVariableTypes.KUBERNETES_CLUSTER.name());
    kubernetesClusterConfig.setCcmConfig(ccmConfig);
  }

  @Test(expected = Exception.class)
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testExecuteTaskWithNULLInput() throws Exception {
    k8sVersionTaskHandler.executeTaskInternal(null, null);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithCCMnotEnabled() throws Exception {
    kubernetesClusterConfig.setCcmConfig(null);
    executeTaskInternalHelper();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal() throws Exception {
    executeTaskInternalHelper();
  }

  public void executeTaskInternalHelper() throws Exception {
    doReturn(k8sClusterConfig).when(k8sTaskParameters).getK8sClusterConfig();
    doReturn(kubernetesClusterConfig).when(k8sClusterConfig).getCloudProvider();
    doReturn(versionInfo).when(k8sVersionTaskHandler).getK8sVersionInfo(any(K8sClusterConfig.class));

    assertThat(k8sVersionTaskHandler.executeTaskInternal(k8sTaskParameters, null)).isEqualTo(k8sTaskExecutionResponse);
    verify(k8sVersionTaskHandler, times(1)).getK8sVersionInfo(any(K8sClusterConfig.class));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetKubernetesConfig() {
    doReturn(k8sClusterConfig).when(k8sTaskParameters).getK8sClusterConfig();
    doReturn(kubernetesClusterConfig).when(k8sClusterConfig).getCloudProvider();

    KubernetesClusterConfig kubernetesClusterConfig1 = K8sVersionTaskHandler.getKubernetesConfig(k8sTaskParameters);
    assertThat(kubernetesClusterConfig1.getCcmConfig().isCloudCostEnabled()).isTrue();
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetKubernetesConfigWithRancherConfig() {
    doReturn(k8sClusterConfig).when(k8sTaskParameters).getK8sClusterConfig();
    RancherConfig rancherConfig = mock(RancherConfig.class);
    doReturn(rancherConfig).when(k8sClusterConfig).getCloudProvider();

    assertThat(K8sVersionTaskHandler.getKubernetesConfig(k8sTaskParameters)).isNull();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testK8sVersionResponseBuilder() {
    K8sVersionResponse k8sVersionResponse1 = K8sVersionTaskHandler.k8sVersionResponseBuilder(versionInfo);
    assertThat(k8sVersionResponse1).isEqualTo(k8sVersionResponse);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetK8sVersionInfo() throws ApiException {
    final String MASTER_URL = "https://125.19.67.142";

    doReturn(Config.fromUrl(MASTER_URL, false)).when(apiClientFactory).getClient(kubernetesConfig);
    doReturn(kubernetesConfig).when(containerDeploymentDelegateHelper).getKubernetesConfig(k8sClusterConfig, false);
    VersionInfo versionInfo1 = k8sVersionTaskHandler.getK8sVersionInfo(k8sClusterConfig);
  }
}
