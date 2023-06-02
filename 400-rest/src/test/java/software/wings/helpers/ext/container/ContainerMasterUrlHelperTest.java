/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.container;

import static software.wings.beans.AzureKubernetesInfrastructureMapping.Builder.anAzureKubernetesInfrastructureMapping;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.beans.AzureConfig;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.RancherKubernetesInfrastructureMapping;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.dto.SettingAttribute;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.MasterUrlFetchTaskParameter;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.settings.SettingValue;
import software.wings.sm.ExecutionContext;
import software.wings.utils.WingsTestConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ContainerMasterUrlHelperTest extends WingsBaseTest {
  @Mock private ExecutionContext executionContext;
  @Mock private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private ContainerService containerService;
  @Mock private EnvironmentService environmentService;
  @InjectMocks private ContainerMasterUrlHelper masterUrlHelper;

  private static final String MASTER_URL = "http://master-url";
  @Before
  public void setUp() throws Exception {
    doReturn(containerService).when(delegateProxyFactory).getV2(eq(ContainerService.class), any(SyncTaskContext.class));
    doReturn(MASTER_URL).when(containerService).fetchMasterUrl(any(MasterUrlFetchTaskParameter.class));
    Environment environment = new Environment();
    environment.setEnvironmentType(EnvironmentType.PROD);
    doReturn(environment).when(environmentService).get(anyString(), anyString());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testFetchMasterUrlForGcpK8s() {
    final ContainerInfrastructureMapping infraMapping =
        buildInfraMapping(InfrastructureMappingType.GCP_KUBERNETES, null);
    final ContainerServiceParams containerParams = ContainerServiceParams.builder().build();
    doReturn(containerParams)
        .when(containerDeploymentManagerHelper)
        .getContainerServiceParams(infraMapping, null, executionContext);

    assertThat(masterUrlHelper.fetchMasterUrl(containerParams, infraMapping)).isEqualTo(MASTER_URL);

    verify(delegateProxyFactory, times(1))
        .getV2(ContainerService.class,
            SyncTaskContext.builder()
                .accountId(ACCOUNT_ID)
                .envId(ENV_ID)
                .envType(EnvironmentType.PROD)
                .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                .infrastructureMappingId(WingsTestConstants.INFRA_MAPPING_ID)
                .serviceId(SERVICE_ID)
                .appId(WingsTestConstants.APP_ID)
                .infraStructureDefinitionId(WingsTestConstants.INFRA_DEFINITION_ID)
                .infrastructureMappingId(WingsTestConstants.INFRA_MAPPING_ID)
                .build());
    verify(containerService, times(1))
        .fetchMasterUrl(MasterUrlFetchTaskParameter.builder().containerServiceParams(containerParams).build());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testFetchMasterUrlForDirectK8s() {
    testDirectK8sWithMasterUrl();
    testDirectK8sWithDelegate();
  }

  private void testDirectK8sWithDelegate() {
    final ContainerInfrastructureMapping infraMapping =
        buildInfraMapping(InfrastructureMappingType.DIRECT_KUBERNETES, null);
    final ContainerServiceParams containerParams =
        ContainerServiceParams.builder()
            .settingAttribute(
                buildSettingAttribute(KubernetesClusterConfig.builder().delegateName("k8s-delegate").build()))
            .build();
    doReturn(containerParams)
        .when(containerDeploymentManagerHelper)
        .getContainerServiceParams(infraMapping, null, executionContext);

    assertThat(masterUrlHelper.fetchMasterUrl(containerParams, infraMapping)).isEqualTo(null);

    verify(delegateProxyFactory, never()).getV2(any(Class.class), any());
    verify(containerService, never()).fetchMasterUrl(any());
  }

  private void testDirectK8sWithMasterUrl() {
    final ContainerInfrastructureMapping infraMapping =
        buildInfraMapping(InfrastructureMappingType.DIRECT_KUBERNETES, null);
    final ContainerServiceParams containerParams =
        ContainerServiceParams.builder()
            .settingAttribute(buildSettingAttribute(KubernetesClusterConfig.builder().masterUrl(MASTER_URL).build()))
            .build();
    doReturn(containerParams)
        .when(containerDeploymentManagerHelper)
        .getContainerServiceParams(infraMapping, null, executionContext);

    assertThat(masterUrlHelper.fetchMasterUrl(containerParams, infraMapping)).isEqualTo(MASTER_URL);

    verify(delegateProxyFactory, never()).getV2(any(Class.class), any());
    verify(containerService, never()).fetchMasterUrl(any());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testFetchMasterUrlForAzureK8s() {
    final ContainerInfrastructureMapping infraMapping =
        buildInfraMapping(InfrastructureMappingType.AZURE_KUBERNETES, null);
    final ContainerServiceParams containerParams =
        ContainerServiceParams.builder().settingAttribute(buildSettingAttribute(AzureConfig.builder().build())).build();
    doReturn(containerParams)
        .when(containerDeploymentManagerHelper)
        .getContainerServiceParams(infraMapping, null, executionContext);

    assertThat(masterUrlHelper.fetchMasterUrl(containerParams, infraMapping)).isEqualTo(MASTER_URL);

    verify(delegateProxyFactory, times(1))
        .getV2(ContainerService.class,
            SyncTaskContext.builder()
                .accountId(ACCOUNT_ID)
                .envId(ENV_ID)
                .envType(EnvironmentType.PROD)
                .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                .infrastructureMappingId(WingsTestConstants.INFRA_MAPPING_ID)
                .serviceId(SERVICE_ID)
                .appId(WingsTestConstants.APP_ID)
                .infraStructureDefinitionId(WingsTestConstants.INFRA_DEFINITION_ID)
                .infrastructureMappingId(WingsTestConstants.INFRA_MAPPING_ID)
                .build());
    verify(containerService, times(1))
        .fetchMasterUrl(MasterUrlFetchTaskParameter.builder().containerServiceParams(containerParams).build());
  }

  @Test
  @Owner(developers = OwnerRule.SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testFetchMasterUrlForRancher() {
    assertThat(masterUrlHelper.fetchMasterUrl(
                   ContainerServiceParams.builder().build(), new RancherKubernetesInfrastructureMapping()))
        .isEqualTo("");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void masterUrlRequired() {
    assertThat(masterUrlHelper.masterUrlRequired(buildInfraMapping(InfrastructureMappingType.GCP_KUBERNETES, null)))
        .isTrue();
    assertThat(masterUrlHelper.masterUrlRequired(buildInfraMapping(InfrastructureMappingType.AZURE_KUBERNETES, null)))
        .isTrue();
    assertThat(
        masterUrlHelper.masterUrlRequired(buildInfraMapping(InfrastructureMappingType.GCP_KUBERNETES, MASTER_URL)))
        .isFalse();
    assertThat(
        masterUrlHelper.masterUrlRequired(buildInfraMapping(InfrastructureMappingType.AZURE_KUBERNETES, MASTER_URL)))
        .isFalse();
    assertThat(masterUrlHelper.masterUrlRequired(buildInfraMapping(InfrastructureMappingType.DIRECT_KUBERNETES, null)))
        .isFalse();
    assertThat(masterUrlHelper.masterUrlRequired(buildInfraMapping(InfrastructureMappingType.RANCHER_KUBERNETES, null)))
        .isFalse();

    ContainerInfrastructureMapping infraMapping = buildInfraMapping(InfrastructureMappingType.GCP_KUBERNETES, null);
    infraMapping.setProvisionerId(PROVISIONER_ID);
    assertThat(masterUrlHelper.masterUrlRequiredWithProvisioner(infraMapping)).isFalse();
  }

  private ContainerInfrastructureMapping buildInfraMapping(InfrastructureMappingType mappingType, String masterUrl) {
    ContainerInfrastructureMapping infrastructureMapping = null;
    switch (mappingType) {
      case DIRECT_KUBERNETES:
        infrastructureMapping =
            DirectKubernetesInfrastructureMapping.builder().cloudProviderId(COMPUTE_PROVIDER_ID).build();
        break;
      case GCP_KUBERNETES:
        infrastructureMapping = GcpKubernetesInfrastructureMapping.builder()
                                    .computeProviderSettingId(COMPUTE_PROVIDER_ID)
                                    .masterUrl(masterUrl)
                                    .build();
        break;
      case AZURE_KUBERNETES:
        infrastructureMapping = anAzureKubernetesInfrastructureMapping()
                                    .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
                                    .withMasterUrl(masterUrl)
                                    .build();
        break;
      default:
        return null;
    }
    infrastructureMapping.setAccountId(WingsTestConstants.ACCOUNT_ID);
    infrastructureMapping.setAppId(WingsTestConstants.APP_ID);
    infrastructureMapping.setEnvId(WingsTestConstants.ENV_ID);
    infrastructureMapping.setUuid(WingsTestConstants.INFRA_MAPPING_ID);
    infrastructureMapping.setServiceId(SERVICE_ID);
    infrastructureMapping.setInfrastructureDefinitionId(WingsTestConstants.INFRA_DEFINITION_ID);
    return infrastructureMapping;
  }

  private SettingAttribute buildSettingAttribute(SettingValue value) {
    return SettingAttribute.builder().value(value).build();
  }
}
