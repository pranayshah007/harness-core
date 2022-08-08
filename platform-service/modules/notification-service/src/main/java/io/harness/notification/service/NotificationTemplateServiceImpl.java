/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;
import static io.harness.exception.WingsException.USER;

import static com.google.common.io.ByteStreams.toByteArray;

import io.harness.notification.Team;
import io.harness.notification.entities.NotificationTemplate;
import io.harness.notification.exception.NotificationException;
import io.harness.notification.repositories.NotificationTemplateRepository;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.stream.BoundedInputStream;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NotificationTemplateServiceImpl implements NotificationTemplateService {
  private final NotificationTemplateRepository notificationTemplateRepository;

  @Override
  public NotificationTemplate create(@NotNull String identifier, @NotNull Team team,
      @NotNull BoundedInputStream inputStream, @NotNull Boolean harnessManaged) {
    NotificationTemplate template = NotificationTemplate.builder().build();
    template.setTeam(team);
    template.setIdentifier(identifier);
    template.setHarnessManaged(harnessManaged);
    try {
      template.setFile(toByteArray(inputStream));
    } catch (Exception ex) {
      log.error("Exception while converting file to byte array.");
      throw new NotificationException("IO error", DEFAULT_ERROR_CODE, USER);
    }
    return notificationTemplateRepository.save(template);
  }

  @Override
  public NotificationTemplate save(NotificationTemplate notificationTemplate) {
    return notificationTemplateRepository.save(notificationTemplate);
  }

  @Override
  public Optional<NotificationTemplate> update(
      @NotNull String templateIdentifier, Team team, BoundedInputStream inputStream, Boolean harnessManaged) {
    Optional<NotificationTemplate> templateOptional = getByIdentifierAndTeam(templateIdentifier, team);
    if (templateOptional.isPresent()) {
      NotificationTemplate template = templateOptional.get();
      try {
        template.setFile(toByteArray(inputStream));
        template.setHarnessManaged(harnessManaged);
        return Optional.of(notificationTemplateRepository.save(template));
      } catch (IOException e) {
        log.error("Error while converting input stream to byte array", e);
      }
    }
    return Optional.empty();
  }

  @Override
  public List<NotificationTemplate> list(Team team) {
    return notificationTemplateRepository.findByTeam(team);
  }

  @Override
  public Optional<NotificationTemplate> getByIdentifierAndTeam(String identifier, Team team) {
    return notificationTemplateRepository.findByIdentifierAndTeam(identifier, team);
  }

  @Override
  public Optional<String> getTemplateAsString(String identifier, Team team) {
    Optional<NotificationTemplate> templateOptional = getByIdentifierAndTeam(identifier, team);
    if (Objects.nonNull(team) && !templateOptional.isPresent()) {
      templateOptional = getPredefinedTemplate(identifier);
    }
    if (templateOptional.isPresent()) {
      NotificationTemplate template = templateOptional.get();
      return Optional.of(new String(template.getFile()));
    }
    return Optional.empty();
  }

  @Override
  public boolean delete(String templateIdentifier, Team team) {
    Optional<NotificationTemplate> templateOptional = getByIdentifierAndTeam(templateIdentifier, team);
    templateOptional.ifPresent(notificationTemplateRepository::delete);
    return true;
  }

  @Override
  public void dropPredefinedTemplates() {
    notificationTemplateRepository.deleteByTeam(null);
  }

  @Override
  public Optional<NotificationTemplate> getPredefinedTemplate(String identifier) {
    return notificationTemplateRepository.findByIdentifierAndTeamExists(identifier, false);
  }
}
