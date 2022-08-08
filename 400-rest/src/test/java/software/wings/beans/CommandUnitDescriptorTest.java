/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.rule.OwnerRule.JOHANNES;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDescriptor;
import software.wings.beans.command.CommandUnitType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Created by peeyushaggarwal on 6/6/16.
 */
@RunWith(JUnitParamsRunner.class)
public class CommandUnitDescriptorTest extends CategoryTest {
  private static final Map<CommandUnitType, CommandUnitType> commandUnitCommandUnitTypeMapping;
  static {
    Map<CommandUnitType, CommandUnitType> map = new HashMap<>();
    map.put(CommandUnitType.ECS_SETUP_DAEMON_SCHEDULING_TYPE, CommandUnitType.ECS_SETUP);
    commandUnitCommandUnitTypeMapping = Collections.unmodifiableMap(map);
  }

  private Object[][] getData() {
    Object[][] data = new Object[CommandUnitType.values().length][1];

    for (int i = 0; i < CommandUnitType.values().length; i++) {
      data[i][0] = UPPER_UNDERSCORE.to(UPPER_CAMEL, CommandUnitType.values()[i].name());
    }
    return data;
  }

  /**
   * Ensures that the correct CommandUnitDescriptor gets returned for a given CommandUnitType.
   * Also ensures that all types have a descriptor returned.
   *
   * @param commandUnitTypeName the command unit type name
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  @Parameters(method = "getData")
  public void ensureCorrectCommandUnitDescriptorExistsForType(String commandUnitTypeName) throws Exception {
    CommandUnitType commandUnitType = CommandUnitType.valueOf(UPPER_CAMEL.to(UPPER_UNDERSCORE, commandUnitTypeName));
    assertThat(commandUnitType).isNotNull();

    // Ensure that the descriptor is matching the name and type of the requested CommandUnitType.
    CommandUnitDescriptor descriptor = CommandUnitDescriptor.forType(commandUnitType);
    assertThat(descriptor).isNotNull();
    assertThat(descriptor.getName()).isEqualTo(commandUnitType.getName());
    assertThat(descriptor.getType()).isEqualTo(commandUnitType.name());

    // Some CommandUnitTypes reuse CommandUnits from other types, we need to handle those in the test accordingly.
    CommandUnitType expectedCommandUnitTypeFromCommandUnit =
        commandUnitCommandUnitTypeMapping.containsKey(commandUnitType)
        ? commandUnitCommandUnitTypeMapping.get(commandUnitType)
        : commandUnitType;

    // ensure the descriptor is creating the correct command unit matching the requested CommandUnitType.
    CommandUnit commandUnit = descriptor.newInstance("");
    assertThat(commandUnit).isNotNull();
    assertThat(commandUnit.getCommandUnitType()).isEqualTo(expectedCommandUnitTypeFromCommandUnit);
  }
}
