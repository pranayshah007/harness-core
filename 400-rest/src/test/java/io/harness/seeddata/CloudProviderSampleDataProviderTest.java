/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.seeddata;

import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.seeddata.SampleDataProviderConstants.K8S_CLOUD_PROVIDER_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.SettingAttribute;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingVariableTypes;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CloudProviderSampleDataProviderTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().build();
  @Mock private SettingsService settingsService;
  @InjectMocks private CloudProviderSampleDataProvider cloudProviderSampleDataProvider;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    when(settingsService.fetchSettingAttributeByName(
             eq(accountId), eq(K8S_CLOUD_PROVIDER_NAME), eq(SettingVariableTypes.KUBERNETES_CLUSTER)))
        .thenReturn(settingAttribute);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldNotCreateKubernetesClusterConfigIfPresent() {
    SettingAttribute createdSettingAttribute = cloudProviderSampleDataProvider.createKubernetesClusterConfig(accountId);
    assertThat(createdSettingAttribute).isEqualTo(settingAttribute);
  }
}
