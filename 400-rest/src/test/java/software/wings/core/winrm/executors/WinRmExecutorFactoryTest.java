/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.core.winrm.executors;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.DINESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CDP)
public class WinRmExecutorFactoryTest extends CategoryTest {
  @Mock WinRmExecutorFactory winRmExecutorFactory = new WinRmExecutorFactory();
  WinRmSessionConfig winRmSessionConfig;

  @Test
  @Owner(developers = DINESH)
  @Category(UnitTests.class)
  public void shouldGetWinRmExecutor() {
    winRmSessionConfig = WinRmSessionConfig.builder().build();
    assertThat(winRmExecutorFactory.getExecutor(winRmSessionConfig, false, false))
        .isNotNull()
        .isInstanceOf(WinRmExecutor.class);
  }
}
