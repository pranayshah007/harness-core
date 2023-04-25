/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.container;

import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.SHUBHAM_MAHESHWARI;

import static software.wings.beans.DirectKubernetesInfrastructureMapping.Builder.aDirectKubernetesInfrastructureMapping;
import static software.wings.beans.RancherKubernetesInfrastructureMapping.Builder.aRancherKubernetesInfrastructureMapping;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.SETTING_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.api.InstanceElement;
import software.wings.api.RancherClusterElement;
import software.wings.api.ServiceTemplateElement;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.RancherConfig;
import software.wings.beans.RancherKubernetesInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContextImpl;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ContainerDeploymentManagerHelperTest extends WingsBaseTest {
  @Inject private HPersistence persistence;
  @Mock private SettingsService settingsService;
  @Mock private SecretManager secretManager;

  @InjectMocks @Inject private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetK8sClusterConfig() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withName(SETTING_NAME)
                                            .withUuid(SETTING_ID)
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(KubernetesClusterConfig.builder().build())
                                            .build();
    persistence.save(settingAttribute);

    DirectKubernetesInfrastructureMapping infraMapping = aDirectKubernetesInfrastructureMapping()
                                                             .withNamespace("default")
                                                             .withComputeProviderSettingId(SETTING_ID)
                                                             .build();
    doReturn(settingAttribute).when(settingsService).get(SETTING_ID);

    K8sClusterConfig k8sClusterConfig = containerDeploymentManagerHelper.getK8sClusterConfig(infraMapping, null);
    assertThat(k8sClusterConfig).isNotNull();
    assertThat(k8sClusterConfig.getCloudProviderName()).isEqualTo(SETTING_NAME);
    assertThat(k8sClusterConfig.getClusterName()).isNull();
    assertThat(k8sClusterConfig.getNamespace()).isEqualTo("default");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void buildInstanceElement() {
    final ServiceTemplateElement serviceTemplateElement =
        ServiceTemplateElement.Builder.aServiceTemplateElement().build();
    final InstanceElement instanceElement =
        containerDeploymentManagerHelper.buildInstanceElement(serviceTemplateElement,
            ContainerInfo.builder()
                .workloadName("workload")
                .ip("ip")
                .podName("podname")
                .hostName("hostname")
                .newContainer(true)
                .containerId("containerId")
                .build());
    assertThat(instanceElement.getServiceTemplateElement()).isEqualTo(serviceTemplateElement);
    assertThat(instanceElement.getWorkloadName()).isEqualTo("workload");
    assertThat(instanceElement.getDockerId()).isEqualTo("containerId");
    assertThat(instanceElement.isNewInstance()).isTrue();
    assertThat(instanceElement.getPodName()).isEqualTo("podname");
    assertThat(instanceElement.getHostName()).isEqualTo("hostname");
    assertThat(instanceElement.getHost().getIp()).isEqualTo("ip");
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetContainerServiceParamsForRancher() {
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    RancherKubernetesInfrastructureMapping infrastructureMapping =
        aRancherKubernetesInfrastructureMapping()
            .withNamespace("sampleNamespace")
            .withAppId("appId")
            .withComputeProviderSettingId("computeProviderSettingId")
            .build();

    doReturn(settingAttribute).when(settingsService).get("computeProviderSettingId");
    doReturn(mock(RancherConfig.class)).when(settingAttribute).getValue();
    doReturn(new RancherClusterElement("uuid", "sampleCluster")).when(context).getContextElement();
    doReturn("sampleNamespace").when(context).renderExpression("sampleNamespace");
    doReturn("workflowId").when(context).getWorkflowExecutionId();
    doReturn(Collections.emptyList())
        .when(secretManager)
        .getEncryptionDetails(any(RancherConfig.class), anyString(), anyString());

    ContainerServiceParams params =
        containerDeploymentManagerHelper.getContainerServiceParams(infrastructureMapping, "sampleServicename", context);

    assertThat(params.getClusterName()).isEqualTo("sampleCluster");
    assertThat(params.getNamespace()).isEqualTo("sampleNamespace");
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testGetK8sClusterConfigForRancher() {
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    RancherKubernetesInfrastructureMapping infrastructureMapping =
        aRancherKubernetesInfrastructureMapping()
            .withNamespace("sampleNamespace")
            .withAppId("appId")
            .withComputeProviderSettingId("computeProviderSettingId")
            .build();

    doReturn(settingAttribute).when(settingsService).get("computeProviderSettingId");
    doReturn(mock(RancherConfig.class)).when(settingAttribute).getValue();
    doReturn(new RancherClusterElement("uuid", "sampleCluster")).when(context).getContextElement();
    doReturn("sampleNamespace").when(context).renderExpression("sampleNamespace");
    doReturn("workflowId").when(context).getWorkflowExecutionId();
    doReturn(Collections.emptyList())
        .when(secretManager)
        .getEncryptionDetails(any(RancherConfig.class), anyString(), anyString());

    K8sClusterConfig config = containerDeploymentManagerHelper.getK8sClusterConfig(infrastructureMapping, context);

    assertThat(config.getClusterName()).isEqualTo("sampleCluster");
    assertThat(config.getNamespace()).isEqualTo("sampleNamespace");
  }
}
