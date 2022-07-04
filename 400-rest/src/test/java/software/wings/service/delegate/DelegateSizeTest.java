/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.delegate;

import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployVariant;
import io.harness.delegate.beans.DelegateSizeDetails;
import io.harness.rule.Owner;

import software.wings.service.impl.DelegateServiceImpl;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DelegateSizeTest {
  @Mock private DelegateServiceImpl underTest;

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testFetchDefaultDelegateSize() {
    try (MockedStatic<DeployVariant> deployVariant = Mockito.mockStatic(DeployVariant.class)) {
      deployVariant.when(() -> DeployVariant.isCommunity(null)).thenReturn(false);
      when(underTest.fetchDefaultDockerDelegateSize()).thenCallRealMethod();
      DelegateSizeDetails delegateSizeDetails = underTest.fetchDefaultDockerDelegateSize();

      assertThat(delegateSizeDetails.getCpu()).isEqualTo(1);
      assertThat(delegateSizeDetails.getLabel()).isEqualTo("Default");
      assertThat(delegateSizeDetails.getRam()).isEqualTo(2048);
      assertThat(delegateSizeDetails.getReplicas()).isEqualTo(0);
      assertThat(delegateSizeDetails.getSize()).isNull();
    }
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testFetchDefaultCommunityDelegateSize() {
    try (MockedStatic<DeployVariant> deployVariant = Mockito.mockStatic(DeployVariant.class)) {
      deployVariant.when(() -> DeployVariant.isCommunity(null)).thenReturn(true);
      when(underTest.fetchDefaultDockerDelegateSize()).thenCallRealMethod();
      DelegateSizeDetails delegateSizeDetails = underTest.fetchDefaultDockerDelegateSize();

      assertThat(delegateSizeDetails.getCpu()).isEqualTo(1);
      assertThat(delegateSizeDetails.getLabel()).isEqualTo("Default Community Size");
      assertThat(delegateSizeDetails.getRam()).isEqualTo(768);
      assertThat(delegateSizeDetails.getReplicas()).isEqualTo(0);
      assertThat(delegateSizeDetails.getSize()).isNull();
    }
  }
}
