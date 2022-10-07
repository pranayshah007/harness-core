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
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.beans.artifact.ArtifactSummary;
import software.wings.beans.template.TemplateReference;

import com.esotericsoftware.kryo.Kryo;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CommandKryoDtoTest extends CategoryTest {
  private static final int REGISTRATION_ID = 421;

  private static final KryoSerializer originalSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(OriginalRegistrar.class)), true);
  private static final KryoSerializer dtoSerializer =
      new KryoSerializer(new HashSet<>(Arrays.asList(DtoRegistrar.class)), true);

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testSerializationFromOriginalToDto() {
    Command command =
        Command.Builder.aCommand()
            .withArtifactNeeded(true)
            .withExecutionResult(CommandExecutionStatus.SUCCESS)
            .withCommandType(CommandType.INSTALL)
            // set it to null, which is different to default value and doesn't require extra kryo registrations.
            .withCommandUnits(null)
            .withName("someName")
            .withTemplateVariables(Arrays.asList(new Variable()))
            .build();

    command.setVariables(Arrays.asList(new Variable()));
    command.setCommandUnitType(CommandUnitType.CODE_DEPLOY);

    // serialize and deserialize to dto
    software.wings.beans.dto.Command commandDTO =
        (software.wings.beans.dto.Command) dtoSerializer.asObject(originalSerializer.asBytes(command));

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
  public void testSerializationFromDtoToOriginal() {
    software.wings.beans.dto.Command commandDTO =
        software.wings.beans.dto.Command.builder()
            .artifactNeeded(true)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .commandType(CommandType.INSTALL)
            // set it to null, which is different to default value but doesn't require extra kryo registrations.
            .commandUnits(null)
            .commandUnitType(CommandUnitType.CODE_DEPLOY)
            .name("someName")
            .templateVariables(Arrays.asList(new Variable()))
            .variables(Arrays.asList(new Variable()))
            .build();

    // serialize and deserialize to dto
    Command command = (Command) originalSerializer.asObject(dtoSerializer.asBytes(commandDTO));

    assertThat(command.isArtifactNeeded()).isEqualTo(commandDTO.isArtifactNeeded());
    assertThat(command.getCommandExecutionStatus()).isEqualTo(commandDTO.getCommandExecutionStatus());
    assertThat(command.getCommandType()).isEqualTo(commandDTO.getCommandType());
    assertThat(command.getCommandUnits()).isEqualTo(commandDTO.getCommandUnits());
    assertThat(command.getName()).isEqualTo(commandDTO.getName());
    assertThat(command.getTemplateVariables()).isEqualTo(commandDTO.getTemplateVariables());
    assertThat(command.getVariables()).isEqualTo(commandDTO.getVariables());
    assertThat(command.getCommandUnitType()).isEqualTo(commandDTO.getCommandUnitType());
  }

  public static void registerCommons(Kryo kryo) {
    // These IDs are not related to prod IDs.
    int id = 10000;
    kryo.register(CommandExecutionStatus.class, id++);
    kryo.register(CommandType.class, id++);
    kryo.register(CommandUnitType.class, id++);
    kryo.register(CommandUnit.class, id++);
    kryo.register(Variable.class, id++);
    kryo.register(ArtifactStreamSummary.class, id++);
    kryo.register(ArtifactSummary.class, id++);
    kryo.register(TemplateReference.class, id++);
    kryo.register(VariableType.class, id++);
  }

  public static class OriginalRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      registerCommons(kryo);
      kryo.register(Command.class, REGISTRATION_ID);
    }
  }

  public static class DtoRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      registerCommons(kryo);
      kryo.register(software.wings.beans.dto.Command.class, REGISTRATION_ID);
    }
  }
}
