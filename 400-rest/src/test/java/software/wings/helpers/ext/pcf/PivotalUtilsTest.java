/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.pcf;

import static io.harness.rule.OwnerRule.ADWAIT;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pcf.PcfUtils;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfCliVersion;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

@RunWith(MockitoJUnitRunner.class)
@PowerMockIgnore({"javax.security.*", "javax.net.*"})
@OwnedBy(HarnessTeam.CDP)
public class PivotalUtilsTest extends WingsBaseTest {
  public static final String DEFAULT_CF_CLI_PATH = "cf";

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCheckIfAppAutoscalarInstalled() throws Exception {
    MockedStatic<PcfUtils> pcfUtilsMockedStatic = mockStatic(PcfUtils.class);
    ProcessExecutor processExecutor = mock(ProcessExecutor.class);
    ProcessResult processResult = mock(ProcessResult.class);
    doReturn(processResult).when(processExecutor).execute();
    doReturn("asd").doReturn(null).doReturn(EMPTY).when(processResult).outputUTF8();

    //    when(PcfUtils.createExecutorForAutoscalarPluginCheck(anyString(), anyMap())).thenReturn(processExecutor);
    pcfUtilsMockedStatic.when(() -> PcfUtils.createExecutorForAutoscalarPluginCheck(anyString(), anyMap()))
        .thenAnswer(e -> processExecutor);

    pcfUtilsMockedStatic.when(() -> PcfUtils.checkIfAppAutoscalarInstalled(DEFAULT_CF_CLI_PATH, CfCliVersion.V6))
        .thenCallRealMethod();
    assertThat(PcfUtils.checkIfAppAutoscalarInstalled(DEFAULT_CF_CLI_PATH, CfCliVersion.V6)).isTrue(); // asd

    assertThat(PcfUtils.checkIfAppAutoscalarInstalled(DEFAULT_CF_CLI_PATH, CfCliVersion.V6)).isFalse(); // null
    assertThat(PcfUtils.checkIfAppAutoscalarInstalled(DEFAULT_CF_CLI_PATH, CfCliVersion.V6)).isFalse(); // empty

    doAnswer(invocation -> { throw new Exception(); }).when(processExecutor).execute();
    try {
      PcfUtils.checkIfAppAutoscalarInstalled(DEFAULT_CF_CLI_PATH, CfCliVersion.V6);
    } catch (Exception e) {
      assertThat(e instanceof PivotalClientApiException).isTrue();
    }
  }
}
