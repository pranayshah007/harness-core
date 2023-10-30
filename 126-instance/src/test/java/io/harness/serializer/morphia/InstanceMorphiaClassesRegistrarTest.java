/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.DeploymentAccounts;
import io.harness.entities.DeploymentSummary;
import io.harness.entities.InfrastructureMapping;
import io.harness.entities.Instance;
import io.harness.entities.ReleaseDetailsMapping;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;
import io.harness.rule.Owner;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class InstanceMorphiaClassesRegistrarTest extends InstancesTestBase {
  @InjectMocks InstanceMorphiaClassesRegistrar instanceMorphiaClassesRegistrar;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void registerClassesTest() {
    Set<Class> set = new LinkedHashSet<>();
    instanceMorphiaClassesRegistrar.registerClasses(set);
    assertThat(set.contains(InfrastructureMapping.class)).isTrue();
    assertThat(set.contains(Instance.class)).isTrue();
    assertThat(set.contains(InstanceSyncPerpetualTaskInfo.class)).isTrue();
    assertThat(set.contains(DeploymentSummary.class)).isTrue();
    assertThat(set.contains(DeploymentAccounts.class)).isTrue();
    assertThat(set.contains(ReleaseDetailsMapping.class)).isTrue();
  }
}
