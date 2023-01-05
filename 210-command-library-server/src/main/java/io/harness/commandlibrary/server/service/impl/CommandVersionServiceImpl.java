/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.commandlibrary.server.service.impl;

import static io.harness.persistence.HQuery.excludeAuthority;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.commandlibrary.server.beans.CommandArchiveContext;
import io.harness.commandlibrary.server.beans.CommandManifest;
import io.harness.commandlibrary.server.beans.CommandManifest.CommandManifestKeys;
import io.harness.commandlibrary.server.service.intfc.CommandArchiveHandler;
import io.harness.commandlibrary.server.service.intfc.CommandService;
import io.harness.commandlibrary.server.service.intfc.CommandVersionService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnsupportedOperationException;

import software.wings.beans.commandlibrary.CommandEntity;
import software.wings.beans.commandlibrary.CommandVersionEntity;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Sort;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@Slf4j
public class CommandVersionServiceImpl implements CommandVersionService {
  public static final Pattern VESRION_REGEX_PATTERN = Pattern.compile("^(\\d+\\.)(\\d+\\.)(\\*|\\d+)$");
  private final WingsPersistence wingsPersistence;
  private final Set<CommandArchiveHandler> commandArchiveHandlers;
  private final CommandService commandService;
  @Inject
  public CommandVersionServiceImpl(WingsPersistence wingsPersistence, Set<CommandArchiveHandler> commandArchiveHandlers,
      CommandService commandService) {
    this.wingsPersistence = wingsPersistence;
    this.commandArchiveHandlers = commandArchiveHandlers;
    this.commandService = commandService;
  }

  @Override
  public Optional<CommandVersionEntity> getCommandVersionEntity(
      String commandStoreName, String commandName, String version) {
    return ofNullable(wingsPersistence.createQuery(CommandVersionEntity.class)
                          .filter(CommandVersionEntity.CommandVersionsKeys.commandStoreName, commandStoreName)
                          .filter(CommandVersionEntity.CommandVersionsKeys.commandName, commandName)
                          .filter(CommandVersionEntity.CommandVersionsKeys.version, version)
                          .get());
  }

  @Override
  public List<CommandVersionEntity> getAllVersionEntitiesForCommand(String commandStoreName, String commandName) {
    return emptyIfNull(wingsPersistence.createQuery(CommandVersionEntity.class, excludeAuthority)
                           .filter(CommandVersionEntity.CommandVersionsKeys.commandStoreName, commandStoreName)
                           .filter(CommandVersionEntity.CommandVersionsKeys.commandName, commandName)
                           .order(Sort.descending(CommandVersionEntity.CommandVersionsKeys.createdAt))
                           .asList());
  }

  @Override
  public String save(CommandVersionEntity commandVersionEntity) {
    return wingsPersistence.save(commandVersionEntity);
  }

  @Override
  public Optional<CommandVersionEntity> getEntityById(String commandVersionId) {
    return Optional.ofNullable(wingsPersistence.get(CommandVersionEntity.class, commandVersionId));
  }

  @Override
  public String createNewVersionFromArchive(CommandArchiveContext commandArchiveContext) {
    validateManifest(commandArchiveContext);
    final CommandArchiveHandler archiveHandler = getArchiveHandler(commandArchiveContext);
    return archiveHandler.createNewCommandVersion(commandArchiveContext);
  }

  private CommandArchiveHandler getArchiveHandler(CommandArchiveContext commandArchiveContext) {
    return commandArchiveHandlers.stream()
        .filter(commandArchiveHandler -> commandArchiveHandler.supports(commandArchiveContext))
        .findFirst()
        .orElseThrow(() -> new UnsupportedOperationException("archive handler could not be found"));
  }

  private void validateManifest(CommandArchiveContext commandArchiveContext) {
    final CommandManifest commandManifest = commandArchiveContext.getCommandManifest();
    final String type = commandManifest.getType();
    final String commandStoreName = commandArchiveContext.getCommandStoreName();
    validateMandatoryParamsPresentInManifest(commandManifest);
    validateCommandType(type, commandStoreName);
    validateVersion(commandStoreName, commandManifest);
  }

  private void validateMandatoryParamsPresentInManifest(CommandManifest commandManifest) {
    ensureNotEmpty(commandManifest.getName(), CommandManifestKeys.name);
    ensureNotEmpty(commandManifest.getVersion(), CommandManifestKeys.version);
    ensureNotEmpty(commandManifest.getType(), CommandManifestKeys.type);
  }
  private void ensureNotEmpty(String value, String fieldName) {
    if (isBlank(value)) {
      throw new InvalidArgumentsException(Pair.of(fieldName, value));
    }
  }

  private void validateVersion(String commandStoreName, CommandManifest commandManifest) {
    validateVersionFormat(commandManifest);
    validateVersionDoesNotExist(commandStoreName, commandManifest);
    validateVersionHigherThanLast(commandStoreName, commandManifest);
  }

  private void validateVersionHigherThanLast(String commandStoreName, CommandManifest commandManifest) {
    final Optional<CommandEntity> commandEntityOpt =
        commandService.getCommandEntity(commandStoreName, commandManifest.getName());

    commandEntityOpt.map(CommandEntity::getLatestVersion).filter(EmptyPredicate::isNotEmpty).ifPresent(lastVersion -> {
      if (versionCompare(lastVersion, commandManifest.getVersion()) > 0) {
        throw new InvalidArgumentsException(
            format("version [%s] should be higher than latest version [%s]", commandManifest.getVersion(), lastVersion),
            null);
      }
    });
  }

  private void validateVersionFormat(CommandManifest commandManifest) {
    final String version = commandManifest.getVersion();
    if (!VESRION_REGEX_PATTERN.matcher(version).matches()) {
      throw new InvalidRequestException(format("version [%s] does not meet the required syntax", version));
    }
  }

  private void validateVersionDoesNotExist(String commandStoreName, CommandManifest manifest) {
    final Optional<CommandVersionEntity> commandVersionEntityOpt =
        getCommandVersionEntity(commandStoreName, manifest.getName(), manifest.getVersion());
    if (commandVersionEntityOpt.isPresent()) {
      throw new InvalidRequestException(
          format("Version already exist with details store = [%s], command =[%s], version =[%s]", commandStoreName,
              manifest.getName(), manifest.getVersion()));
    }
  }

  private void validateCommandType(String type, String commandStoreName) {
    if (!commandService.isCommandTypeSupported(commandStoreName, type)) {
      throw new InvalidRequestException(format("command type =[%s] not supported", type));
    }
  }

  private int versionCompare(String str1, String str2) {
    try (Scanner s1 = new Scanner(str1); Scanner s2 = new Scanner(str2)) {
      s1.useDelimiter("\\.");
      s2.useDelimiter("\\.");

      while (s1.hasNextInt() && s2.hasNextInt()) {
        int v1 = s1.nextInt();
        int v2 = s2.nextInt();
        if (v1 < v2) {
          return -1;
        } else if (v1 > v2) {
          return 1;
        }
      }
    }
    return 0;
  }
}
