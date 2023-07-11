/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.shellscript.HarnessFileStoreSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CommandStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExtractFileRefs() {
    List<CommandUnitWrapper> commandUnitWrapperList =
        List.of(CommandUnitWrapper.builder()
                    .type(CommandUnitSpecType.SCRIPT)
                    .name("Execute")
                    .spec(ScriptCommandUnitSpec.builder()
                              .tailFiles(Arrays.asList(TailFilePattern.builder()
                                                           .tailFile(ParameterField.createValueField("nohup.out"))
                                                           .tailPattern(ParameterField.createValueField("*Successfull"))
                                                           .build()))
                              .shell(ShellType.Bash)
                              .workingDirectory(ParameterField.createValueField("Dirs"))
                              .source(ShellScriptSourceWrapper.builder()
                                          .spec(HarnessFileStoreSource.builder()
                                                    .file(ParameterField.createValueField("/script.sh"))
                                                    .build())
                                          .type("Inline")
                                          .build())
                              .build())
                    .identifier("command")
                    .build());
    CommandStepInfo commandStepInfo = CommandStepInfo.infoBuilder().commandUnits(commandUnitWrapperList).build();
    Map<String, ParameterField<List<String>>> fileMap;
    fileMap = commandStepInfo.extractFileRefs();
    assertThat(fileMap.get("commandUnits.command.spec.source.spec.file").getValue().size()).isEqualTo(1);
    assertThat(fileMap.get("commandUnits.command.spec.source.spec.file").getValue().get(0)).isEqualTo("/script.sh");
  }
}
