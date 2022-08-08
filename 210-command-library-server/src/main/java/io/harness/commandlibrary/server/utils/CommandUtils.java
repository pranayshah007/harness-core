/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.commandlibrary.server.utils;

import static io.harness.commandlibrary.server.utils.CommandVersionUtils.populateCommandVersionDTO;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import io.harness.commandlibrary.api.dto.CommandEntityDTO.CommandEntityDTOBuilder;

import software.wings.api.commandlibrary.EnrichedCommandVersionDTO;
import software.wings.beans.commandlibrary.CommandEntity;
import software.wings.beans.commandlibrary.CommandVersionEntity;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CommandUtils {
  public static CommandEntityDTOBuilder populateCommandEntityDTO(CommandEntityDTOBuilder commandEntityDTOBuilder,
      CommandEntity commandEntity, CommandVersionEntity latestCommandVersionEntity,
      List<CommandVersionEntity> allVersionList) {
    if (commandEntity != null) {
      commandEntityDTOBuilder.name(commandEntity.getName())
          .commandStoreName(commandEntity.getCommandStoreName())
          .type(commandEntity.getType())
          .name(commandEntity.getName())
          .description(commandEntity.getDescription())
          .tags(commandEntity.getTags())
          .imageUrl(commandEntity.getImageUrl())
          .repoUrl(commandEntity.getRepoUrl())
          .latestVersion(latestCommandVersionEntity != null
                  ? populateCommandVersionDTO(EnrichedCommandVersionDTO.builder(), latestCommandVersionEntity).build()
                  : null)
          .versionList(emptyIfNull(allVersionList)
                           .stream()
                           .map(versionEntity
                               -> populateCommandVersionDTO(EnrichedCommandVersionDTO.builder(), versionEntity).build())
                           .collect(toList()));
    }
    return commandEntityDTOBuilder;
  }
}
