/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.rule.OwnerRule.JOHANNES;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.beans.ArtifactVariable;
import software.wings.beans.ManifestVariable;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CommandMapperTest extends CategoryTest {
  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void smokeTest() {
    Command command = Command.Builder.aCommand()
                          .withArtifactNeeded(true)
                          .withExecutionResult(CommandExecutionStatus.SUCCESS)
                          .withCommandType(CommandType.INSTALL)
                          .withCommandUnits(Arrays.asList(new CleanupSshCommandUnit()))
                          .withName("someName")
                          .withTemplateVariables(Arrays.asList(new ArtifactVariable()))
                          .build();

    command.setVariables(Arrays.asList(new ManifestVariable()));
    command.setCommandUnitType(CommandUnitType.CODE_DEPLOY);

    software.wings.beans.dto.Command commandDTO = CommandMapper.toCommandDTO(command);

    assertThat(commandDTO).isNotNull();
    assertThat(commandDTO.isArtifactNeeded()).isEqualTo(command.isArtifactNeeded());
    assertThat(commandDTO.getCommandExecutionStatus()).isEqualTo(command.getCommandExecutionStatus());
    assertThat(commandDTO.getCommandType()).isEqualTo(command.getCommandType());
    assertThat(commandDTO.getCommandUnits()).isEqualTo(command.getCommandUnits());
    assertThat(commandDTO.getName()).isEqualTo(command.getName());
    assertThat(commandDTO.getTemplateVariables()).isEqualTo(command.getTemplateVariables());
    assertThat(commandDTO.getVariables()).isEqualTo(command.getVariables());
    assertThat(commandDTO.getCommandUnitType()).isEqualTo(command.getCommandUnitType());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testNull() {
    assertThat(CommandMapper.toCommandDTO(null)).isNull();
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void ensureAllCommandCommandUnitsInTreeAreMapped() {
    // Root
    Command rootCommand = Command.Builder.aCommand().withName("rootCommand").build();
    Command childOfRootCommand = Command.Builder.aCommand().withName("childOfRootCommand").build();
    Command childOfRootCommand2 = Command.Builder.aCommand().withName("childOfRootCommand2").build();
    Command childOfChildOfRootCommand = Command.Builder.aCommand().withName("childOfNonRootCommand").build();
    CleanupSshCommandUnit nonCommand = new CleanupSshCommandUnit();
    nonCommand.setName("nonCommand");

    rootCommand.setCommandUnits(Arrays.asList(childOfRootCommand, nonCommand, childOfRootCommand2));
    childOfRootCommand.setCommandUnits(Arrays.asList(childOfChildOfRootCommand));

    // map rootCommand
    software.wings.beans.dto.Command rootCommandDto = CommandMapper.toCommandDTO(rootCommand);

    // verify depth 0
    assertThat(rootCommandDto.getName()).isEqualTo(rootCommand.getName());

    // verify depth 1
    assertThat(rootCommandDto.getCommandUnits().size()).isEqualTo(3);
    assertThat(rootCommandDto.getCommandUnits().get(0).getClass()).isEqualTo(software.wings.beans.dto.Command.class);
    assertThat(rootCommandDto.getCommandUnits().get(0).getName()).isEqualTo(childOfRootCommand.getName());
    assertThat(rootCommandDto.getCommandUnits().get(1).getClass()).isEqualTo(CleanupSshCommandUnit.class);
    assertThat(rootCommandDto.getCommandUnits().get(1).getName()).isEqualTo(nonCommand.getName());
    assertThat(rootCommandDto.getCommandUnits().get(2).getClass()).isEqualTo(software.wings.beans.dto.Command.class);
    assertThat(rootCommandDto.getCommandUnits().get(2).getName()).isEqualTo(childOfRootCommand2.getName());

    // verify depth 2
    software.wings.beans.dto.Command childOfRootCommandDto =
        (software.wings.beans.dto.Command) rootCommandDto.getCommandUnits().get(0);
    assertThat(childOfRootCommandDto.getCommandUnits().size()).isEqualTo(1);
    assertThat(childOfRootCommandDto.getCommandUnits().get(0).getClass())
        .isEqualTo(software.wings.beans.dto.Command.class);
    assertThat(childOfRootCommandDto.getCommandUnits().get(0).getName()).isEqualTo(childOfChildOfRootCommand.getName());

    software.wings.beans.dto.Command childOfRootCommand2Dto =
        (software.wings.beans.dto.Command) rootCommandDto.getCommandUnits().get(2);
    assertThat(childOfRootCommand2Dto.getCommandUnits().size()).isEqualTo(0);
  }
}
