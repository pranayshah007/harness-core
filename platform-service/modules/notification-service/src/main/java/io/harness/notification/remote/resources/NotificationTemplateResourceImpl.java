/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;
import static io.harness.notification.remote.mappers.TemplateMapper.toDTO;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.Team;
import io.harness.notification.entities.NotificationTemplate;
import io.harness.notification.remote.dto.TemplateDTO;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.stream.BoundedInputStream;

import com.google.inject.Inject;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class NotificationTemplateResourceImpl implements NotificationTemplateResource {
  private final NotificationTemplateService templateService;
  private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;

  public ResponseDTO<TemplateDTO> createTemplate(
      InputStream inputStream, Team team, String identifier, Boolean harnessManaged) {
    NotificationTemplate template =
        templateService.create(identifier, team, new BoundedInputStream(inputStream, MAX_FILE_SIZE), harnessManaged);
    return ResponseDTO.newResponse(toDTO(template).orElse(null));
  }

  public ResponseDTO<TemplateDTO> insertOrUpdateTemplate(
      InputStream inputStream, Team team, String identifier, Boolean harnessManaged) {
    Optional<NotificationTemplate> optionalTemplate = templateService.getByIdentifierAndTeam(identifier, team);
    NotificationTemplate template;
    BoundedInputStream boundedInputStream = new BoundedInputStream(inputStream, MAX_FILE_SIZE);
    if (optionalTemplate.isPresent()) {
      template = templateService.update(identifier, team, boundedInputStream, harnessManaged).get();
    } else {
      template = templateService.create(identifier, team, boundedInputStream, harnessManaged);
    }
    return ResponseDTO.newResponse(toDTO(template).orElse(null));
  }

  public ResponseDTO<TemplateDTO> updateTemplate(
      InputStream inputStream, Team team, String templateIdentifier, Boolean harnessManaged) {
    Optional<NotificationTemplate> templateOptional = templateService.update(
        templateIdentifier, team, new BoundedInputStream(inputStream, MAX_FILE_SIZE), harnessManaged);
    if (templateOptional.isPresent()) {
      return ResponseDTO.newResponse(toDTO(templateOptional.get()).orElse(null));
    } else {
      throw new InvalidRequestException("No such template found." + templateIdentifier, USER);
    }
  }

  public ResponseDTO<Boolean> deleteTemplate(String templateIdentifier, Team team) {
    return ResponseDTO.newResponse(templateService.delete(templateIdentifier, team));
  }

  public ResponseDTO<List<TemplateDTO>> getTemplates(Team team) {
    return ResponseDTO.newResponse(
        templateService.list(team).stream().map(x -> toDTO(x).orElse(null)).collect(Collectors.toList()));
  }

  public ResponseDTO<TemplateDTO> getTemplate(String identifier, Team team) {
    return ResponseDTO.newResponse(
        toDTO(templateService.getByIdentifierAndTeam(identifier, team).orElse(null)).orElse(null));
  }
}
