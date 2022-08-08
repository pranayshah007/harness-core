/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.commandlibrary.server.service.impl;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.CONTAINS;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.commandlibrary.server.utils.CommandUtils.populateCommandEntityDTO;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.commandlibrary.api.dto.CommandEntityDTO;
import io.harness.commandlibrary.api.dto.CommandStoreDTO;
import io.harness.commandlibrary.server.service.intfc.CommandService;
import io.harness.commandlibrary.server.service.intfc.CommandStoreService;
import io.harness.commandlibrary.server.service.intfc.CommandVersionService;
import io.harness.data.structure.EmptyPredicate;

import software.wings.beans.commandlibrary.CommandEntity;
import software.wings.beans.commandlibrary.CommandEntity.CommandEntityKeys;
import software.wings.beans.commandlibrary.CommandVersionEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class CommandStoreServiceImpl implements CommandStoreService {
  public static final String HARNESS = "harness";

  private final CommandService commandService;
  private final CommandVersionService commandVersionService;

  @Inject
  public CommandStoreServiceImpl(CommandService commandService, CommandVersionService commandVersionService) {
    this.commandService = commandService;
    this.commandVersionService = commandVersionService;
  }

  @Override
  public PageResponse<CommandEntityDTO> listCommandsForStore(
      String commandStoreName, PageRequest<CommandEntity> pageRequest, String tag) {
    pageRequest.addFilter(CommandEntityKeys.commandStoreName, EQ, commandStoreName);

    if (EmptyPredicate.isNotEmpty(tag)) {
      pageRequest.addFilter(CommandEntityKeys.tags, CONTAINS, tag);
    }

    addFilterForCommandsWithVersionsOnly(pageRequest);

    final PageResponse<CommandEntity> commandEntitiesPR = commandService.listCommandEntity(pageRequest);
    return convert(commandEntitiesPR);
  }
  private void addFilterForCommandsWithVersionsOnly(PageRequest<CommandEntity> pageRequest) {
    pageRequest.addFilter(SearchFilter.builder()
                              .fieldName(CommandEntityKeys.latestVersion)
                              .op(SearchFilter.Operator.NOT_EQ)
                              .fieldValues(new Object[] {null})
                              .build());
  }

  @Override
  public List<CommandStoreDTO> getCommandStores() {
    return Collections.singletonList(harnessStore());
  }

  private CommandStoreDTO harnessStore() {
    return CommandStoreDTO.builder()
        .name(HARNESS)
        .description("Harness Command Library")
        .displayName("Harness Inc")
        .build();
  }

  @Override
  public Optional<CommandStoreDTO> getStoreByName(String name) {
    final CommandStoreDTO commandStoreDTO = harnessStore();

    if (commandStoreDTO.getName().equals(name)) {
      return Optional.of(commandStoreDTO);
    }
    return Optional.empty();
  }

  private PageResponse<CommandEntityDTO> convert(PageResponse<CommandEntity> commandEntitiesPR) {
    return aPageResponse()
        .withTotal(commandEntitiesPR.getTotal())
        .withOffset(commandEntitiesPR.getOffset())
        .withLimit(commandEntitiesPR.getLimit())
        .withResponse(emptyIfNull(commandEntitiesPR.getResponse())
                          .stream()
                          .map(this::convertToCommandEntityDTO)
                          .collect(toList()))
        .build();
  }

  private CommandEntityDTO convertToCommandEntityDTO(CommandEntity commandEntity) {
    return populateCommandEntityDTO(
        CommandEntityDTO.builder(), commandEntity, getLatestVersionEntity(commandEntity), null)
        .build();
  }

  private CommandVersionEntity getLatestVersionEntity(CommandEntity commandEntity) {
    return commandVersionService
        .getCommandVersionEntity(
            commandEntity.getCommandStoreName(), commandEntity.getName(), commandEntity.getLatestVersion())
        .orElse(null);
  }
}
