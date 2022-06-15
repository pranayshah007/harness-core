/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.StartupMode;
import io.harness.rule.Owner;
import io.harness.serializer.kryo.KryoPoolConfiguration;

import software.wings.WingsBaseTest;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class WingsModuleTest extends WingsBaseTest {
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCreateDefaultKryoPoolConfigurationWhenNull() {
    final MainConfiguration mainConfig = Mockito.mock(MainConfiguration.class);
    Mockito.when(mainConfig.getKryoPoolConfig()).thenReturn(null);

    final WingsModule module = new WingsModule(mainConfig, StartupMode.MANAGER);
    final KryoPoolConfiguration kpConfig = module.kryoPoolConfiguration();

    assertThat(kpConfig).isNotNull();
  }
}
