/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.rule.Owner;

import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;

import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CloudProviderServiceImplTest extends CategoryTest {
  @InjectMocks private CloudProviderServiceImpl cloudProviderService;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;

  private static final String GCP_CLOUD_PROVIDER_ID = "gcpCloudProviderId";
  private static final String AWS_CLOUD_PROVIDER_ID = "awsCloudProviderId";
  private static final String AZURE_CLOUD_PROVIDER_ID = "azureCloudProviderId";
  private static final String GCP_PROVIDER_ID = "gce://ccm-play/us-east4-a/gke-ccm-test-default-pool-d13df1f8-zk7p";
  private static final String AWS_PROVIDER_ID = "aws:///us-east-1c/i-0126084a1977d6fab";
  private static final String AZURE_PROVIDER_ID = "azure:///subscriptions/12d2db62-5aa9-471d-84bb-faa489b3e319";

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testK8SClusterCloudProviderType() throws Exception {
    when(cloudToHarnessMappingService.getSettingAttribute(any()))
        .thenReturn(Optional.of(kubernetesClusterSettingAttribute()));
    CloudProvider gcpK8SCloudProvider =
        cloudProviderService.getK8SCloudProvider(GCP_CLOUD_PROVIDER_ID, GCP_PROVIDER_ID);
    assertThat(gcpK8SCloudProvider).isEqualTo(CloudProvider.GCP);

    CloudProvider awsK8SCloudProvider =
        cloudProviderService.getK8SCloudProvider(AWS_CLOUD_PROVIDER_ID, AWS_PROVIDER_ID);
    assertThat(awsK8SCloudProvider).isEqualTo(CloudProvider.AWS);

    CloudProvider azureK8SCloudProvider =
        cloudProviderService.getK8SCloudProvider(AZURE_CLOUD_PROVIDER_ID, AZURE_PROVIDER_ID);
    assertThat(azureK8SCloudProvider).isEqualTo(CloudProvider.AZURE);
  }

  private SettingAttribute kubernetesClusterSettingAttribute() {
    KubernetesClusterConfig kubernetesClusterConfig = new KubernetesClusterConfig();
    kubernetesClusterConfig.setType(SettingVariableTypes.KUBERNETES_CLUSTER.name());
    return settingAttribute(kubernetesClusterConfig);
  }

  private SettingAttribute settingAttribute(SettingValue settingValue) {
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setValue(settingValue);
    settingAttribute.setCategory(SettingCategory.CLOUD_PROVIDER);
    return settingAttribute;
  }
}
