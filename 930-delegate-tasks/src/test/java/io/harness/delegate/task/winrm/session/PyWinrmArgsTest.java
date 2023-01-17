/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.winrm.session.PyWinrmArgs;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PyWinrmArgsTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getArgs() {
    Map<String, String> m = new HashMap<>();
    m.put("k1", "v1");
    m.put("k2", ",=v2=,==j");
    m.put("VARIA'BLE_N?AME_F'OO", "\"SOME_%$!_VA'LUE@?=A");
    PyWinrmArgs args = PyWinrmArgs.builder()
                           .username("harness")
                           .hostname("host")
                           .workingDir("/temp")
                           .timeout(10)
                           .serverCertValidation(true)
                           .environmentMap(m)
                           .build();
    assertThat(args.getArgs("/commandFile.path"))
        .isEqualTo(
            "-e 'host' -u 'harness' -s 'true' -env $'k1=v1' $'k2=,=v2=,==j' $'VARIA\\'BLE_N?AME_F\\'OO=\"SOME_%$!_VA\\'LUE@?=A' -w '/temp' -t '10' -cfile '/commandFile.path'");
  }

  @Test
  @Owner(developers = OwnerRule.SAHIL)
  @Category(UnitTests.class)
  public void getArgsEscapedValueEnvMap() {
    Map<String, String> m = new HashMap<>();
    m.put("k1", "v1");
    m.put("k2", ",=v2=,==j' now escape quote \\' now double quote \\\\\\' now ends with \\");
    m.put("VARIA'BLE_N?AME_F'OO", "\"SOME_%$!_VA'LUE@?=A");
    PyWinrmArgs args = PyWinrmArgs.builder()
                           .username("harness")
                           .hostname("host")
                           .workingDir("/temp")
                           .timeout(10)
                           .serverCertValidation(true)
                           .environmentMap(m)
                           .build();
    assertThat(args.getArgs("/commandFile.path"))
        .isEqualTo(
            "-e 'host' -u 'harness' -s 'true' -env $'k1=v1' $'k2=,=v2=,==j\\' now escape quote \\' now double quote \\\\\\' now ends with \\\\' $'VARIA\\'BLE_N?AME_F\\'OO=\"SOME_%$!_VA\\'LUE@?=A' -w '/temp' -t '10' -cfile '/commandFile.path'");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getArgsEmptyEnvMap() {
    Map<String, String> m = new HashMap<>();
    PyWinrmArgs args = PyWinrmArgs.builder()
                           .username("harness")
                           .hostname("host")
                           .workingDir("/temp")
                           .timeout(10)
                           .serverCertValidation(true)
                           .environmentMap(m)
                           .build();
    assertThat(args.getArgs("/commandFile.path"))
        .isEqualTo("-e 'host' -u 'harness' -s 'true' -env {} -w '/temp' -t '10' -cfile '/commandFile.path'");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getArgsNullEnvMap() {
    PyWinrmArgs args = PyWinrmArgs.builder()
                           .username("harness")
                           .hostname("host")
                           .workingDir("/temp")
                           .timeout(10)
                           .serverCertValidation(true)
                           .environmentMap(null)
                           .build();
    assertThat(args.getArgs("/commandFile.path"))
        .isEqualTo("-e 'host' -u 'harness' -s 'true' -env {} -w '/temp' -t '10' -cfile '/commandFile.path'");
  }
}
