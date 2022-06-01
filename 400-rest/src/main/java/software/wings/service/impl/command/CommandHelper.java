/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.command;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.CommandCategory.CommandUnit;
import static software.wings.beans.CommandCategory.Type.COMMANDS;
import static software.wings.beans.CommandCategory.Type.COPY;
import static software.wings.beans.CommandCategory.Type.SCRIPTS;
import static software.wings.beans.CommandCategory.Type.VERIFICATIONS;

import static java.util.stream.Collectors.toList;

import software.wings.beans.CommandCategory;
import software.wings.beans.command.CommandUnitDescriptor;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.command.ServiceCommand.ServiceCommandKeys;
import software.wings.dl.WingsPersistence;
import software.wings.stencils.StencilCategory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;

@Singleton
public class CommandHelper {
  @Inject WingsPersistence wingsPersistence;

  /**
   * Get Command categories of service and service command
   * @param appId
   * @param serviceId
   * @param commandName
   * @return List of Command Categories
   */
  public List<CommandCategory> getCommandCategories(
      @NotEmpty String appId, @NotEmpty String serviceId, @NotEmpty String commandName) {
    List<ServiceCommand> serviceCommands = wingsPersistence.createQuery(ServiceCommand.class)
                                               .project("name", true)
                                               .filter("appId", appId)
                                               .filter(ServiceCommandKeys.serviceId, serviceId)
                                               .asList();

    List<CommandUnit> commands =
        serviceCommands.stream()
            .filter(serviceCommand -> !commandName.equals(serviceCommand.getName()))
            .map(serviceCommand
                -> CommandUnit.builder().name(serviceCommand.getName()).type(CommandUnitType.COMMAND).build())
            .collect(toList());

    return prepareCommandCategoriesFromCommands(commands);
  }

  public static List<CommandCategory> prepareCommandCategoriesFromCommands(List<CommandUnit> commands) {
    List<CommandCategory> categories = new ArrayList<>();
    List<CommandUnit> scripts = new ArrayList<>();
    List<CommandUnit> copyUnits = new ArrayList<>();
    List<CommandUnit> verifications = new ArrayList<>();

    for (CommandUnitType commandUnitType : CommandUnitType.values()) {
      CommandUnitDescriptor commandUnitDescriptor = CommandUnitDescriptor.forType(commandUnitType);
      StencilCategory stencilCategory = commandUnitDescriptor.getStencilCategory();
      CommandUnit commandUnitItem = CommandUnit.builder().name(commandUnitType.getName()).type(commandUnitType).build();
      if (StencilCategory.SCRIPTS == stencilCategory) {
        scripts.add(commandUnitItem);
      } else if (StencilCategory.COPY == stencilCategory) {
        copyUnits.add(commandUnitItem);
      } else if (StencilCategory.VERIFICATIONS == stencilCategory) {
        verifications.add(commandUnitItem);
      }
    }

    if (isNotEmpty(scripts)) {
      categories.add(
          CommandCategory.builder().type(SCRIPTS).displayName(SCRIPTS.getDisplayName()).commandUnits(scripts).build());
    }

    if (isNotEmpty(copyUnits)) {
      categories.add(
          CommandCategory.builder().type(COPY).displayName(COPY.getDisplayName()).commandUnits(copyUnits).build());
    }

    if (isNotEmpty(commands)) {
      categories.add(CommandCategory.builder()
                         .type(COMMANDS)
                         .displayName(COMMANDS.getDisplayName())
                         .commandUnits(commands)
                         .build());
    }

    categories.add(CommandCategory.builder()
                       .type(VERIFICATIONS)
                       .displayName(VERIFICATIONS.getDisplayName())
                       .commandUnits(verifications)
                       .build());

    return categories;
  }
}
