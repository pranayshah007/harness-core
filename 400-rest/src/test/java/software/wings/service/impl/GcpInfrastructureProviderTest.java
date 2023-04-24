/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.intfc.ContainerService;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GcpInfrastructureProviderTest extends CategoryTest {
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private ContainerService containerService;
  @InjectMocks private GcpInfrastructureProvider gcpInfrastructureProvider;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(containerService)
        .when(delegateProxyFactory)
        .getV2(eq(ContainerService.class), ArgumentMatchers.any(SyncTaskContext.class));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void listClusterNames() {
    SettingAttribute settingAttribute =
        aSettingAttribute().withValue(GcpConfig.builder().useDelegateSelectors(false).build()).build();

    SettingAttribute settingAttributeDelegateBased =
        aSettingAttribute()
            .withValue(GcpConfig.builder()
                           .useDelegateSelectors(true)
                           .delegateSelectors(Collections.singletonList("abc"))
                           .build())
            .build();

    gcpInfrastructureProvider.listClusterNames(settingAttribute, null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> gcpInfrastructureProvider.listClusterNames(settingAttributeDelegateBased, null))
        .withMessageContaining(
            "Infrastructure Definition Using a GCP Cloud Provider Inheriting from Delegate is not yet supported");
  }
}
