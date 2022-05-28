/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.commandlibrary.server.service.intfc;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.commandlibrary.api.dto.CommandEntityDTO;
import io.harness.commandlibrary.server.beans.CommandManifest;

import software.wings.beans.commandlibrary.CommandEntity;

import java.util.Optional;

public interface CommandService {
  Optional<CommandEntityDTO> getCommandDetails(String commandStoreName, String commandName);

  Optional<CommandEntity> getCommandEntity(String commandStoreName, String commandName);

  PageResponse<CommandEntity> listCommandEntity(PageRequest<CommandEntity> pageRequest);

  String save(CommandEntity commandEntity);

  CommandEntity saveAndGet(CommandEntity commandEntity);

  Optional<CommandEntity> getEntityById(String commandId);

  CommandEntity createFromManifest(String commandStoreName, CommandManifest manifest, String accountId);

  boolean isCommandTypeSupported(String commandStoreName, String commandType);
}
