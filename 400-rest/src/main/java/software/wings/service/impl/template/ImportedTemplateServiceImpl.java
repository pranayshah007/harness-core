/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static java.util.Collections.emptyMap;

import io.harness.commandlibrary.api.dto.CommandEntityDTO;
import io.harness.commandlibrary.client.CommandLibraryServiceClientUtils;
import io.harness.commandlibrary.client.CommandLibraryServiceHttpClient;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.api.commandlibrary.EnrichedCommandVersionDTO;
import software.wings.beans.template.ImportedTemplate;
import software.wings.beans.template.ImportedTemplate.ImportedTemplateKeys;
import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.template.TemplateGallery.GalleryKey;
import software.wings.beans.template.TemplateGalleryHelper;
import software.wings.beans.template.TemplateVersion;
import software.wings.beans.template.TemplateVersion.TemplateVersionKeys;
import software.wings.beans.template.dto.HarnessImportedTemplateDetails;
import software.wings.beans.template.dto.ImportedCommand;
import software.wings.beans.template.dto.ImportedCommand.ImportedCommandBuilder;
import software.wings.beans.template.dto.ImportedCommandVersion;
import software.wings.beans.template.dto.ImportedCommandVersion.ImportedCommandVersionBuilder;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.template.ImportedTemplateService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.template.TemplateVersionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImportedTemplateServiceImpl implements ImportedTemplateService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private TemplateService templateService;
  @Inject private TemplateVersionService templateVersionService;
  @Inject private TemplateGalleryService templateGalleryService;
  @Inject private TemplateGalleryHelper templateGalleryHelper;
  @Inject private CommandLibraryServiceHttpClient serviceHttpClient;

  @Override
  public boolean isImported(String templateId, String accountId) {
    // TODO: When copy is implemented we will have to query templateversion and check if template is imported.
    return wingsPersistence.createQuery(ImportedTemplate.class)
               .filter(ImportedTemplateKeys.templateId, templateId)
               .filter(ImportedTemplateKeys.accountId, accountId)
               .get()
        != null;
  }

  @Override
  public String getImportedTemplateVersionFromTemplateVersion(String templateId, String version, String accountId) {
    TemplateVersion templateVersion = wingsPersistence.createQuery(TemplateVersion.class)
                                          .filter(TemplateVersionKeys.templateUuid, templateId)
                                          .filter(TemplateVersionKeys.accountId, accountId)
                                          .filter(TemplateVersionKeys.version, Long.valueOf(version))
                                          .get();
    return templateVersion.getImportedTemplateVersion();
  }

  @Override
  public String getTemplateVersionFromImportedTemplateVersion(
      String templateId, String importedVersion, String accountId) {
    TemplateVersion templateVersion = wingsPersistence.createQuery(TemplateVersion.class)
                                          .filter(TemplateVersionKeys.templateUuid, templateId)
                                          .filter(TemplateVersionKeys.accountId, accountId)
                                          .filter(TemplateVersionKeys.importedTemplateVersion, importedVersion)
                                          .get();
    if (templateVersion == null) {
      return null;
    }
    return String.valueOf(templateVersion.getVersion());
  }

  @Override
  public List<Template> getTemplatesByCommandNames(
      List<String> commandNames, String commandStoreName, String accountId, String appId) {
    List<String> templateIds = wingsPersistence.createQuery(ImportedTemplate.class)
                                   .filter(ImportedTemplateKeys.commandStoreName, commandStoreName)
                                   .field(ImportedTemplateKeys.commandName)
                                   .in(commandNames)
                                   .filter(ImportedTemplateKeys.accountId, accountId)
                                   .filter(ImportedTemplateKeys.appId, appId)
                                   .project(ImportedTemplateKeys.templateId, true)
                                   .asList()
                                   .stream()
                                   .map(ImportedTemplate::getTemplateId)
                                   .collect(Collectors.toList());

    return wingsPersistence.createQuery(Template.class)
        .field(TemplateKeys.uuid)
        .in(templateIds)
        .filter(TemplateKeys.accountId, accountId)
        .asList();
  }

  @Override
  public Template getTemplateByCommandName(
      String commandName, String commandStoreName, String accountId, String appId) {
    Template template = null;
    try {
      template =
          getTemplatesByCommandNames(Collections.singletonList(commandName), commandStoreName, accountId, appId).get(0);
    } catch (Exception e) {
      log.info(String.format(
                   "Template with command Id %s and command store id %s not found.", commandName, commandStoreName),
          e);
    }
    return template;
  }

  @Override
  public ImportedTemplate getCommandByTemplateId(String templateId, String accountId) {
    return wingsPersistence.createQuery(ImportedTemplate.class)
        .filter(ImportedTemplateKeys.templateId, templateId)
        .filter(ImportedTemplateKeys.accountId, accountId)
        .get();
  }

  @Override
  public Map<String, Template> getCommandNameTemplateMap(
      List<String> commandNames, String commandStoreName, String accountId, String appId) {
    List<Template> templates = getTemplatesByCommandNames(commandNames, commandStoreName, accountId, appId);
    if (templates == null) {
      return null;
    }
    return commandNames.stream().collect(Collectors.toMap(
        Function.identity(), commandName -> getTemplateByCommandName(commandName, commandStoreName, accountId, appId)));
  }

  @Override
  public List<ImportedCommand> makeImportedCommandObjectWithLatestVersion(
      Map<String, TemplateVersion> templateUuidLatestTemplateVersionMap, List<String> commandNames,
      final String commandStoreName, Map<String, Template> commandNameTemplateMap, String accountId) {
    if (commandNames == null) {
      return null;
    }
    return commandNames.stream()
        .map(commandName -> {
          Template template = commandNameTemplateMap.get(commandName);
          if (template == null) {
            return null;
          }
          String latestVersion =
              String.valueOf(templateUuidLatestTemplateVersionMap.get(template.getUuid()).getImportedTemplateVersion());
          ImportedTemplate importedTemplate = get(commandName, commandStoreName, accountId, template.getAppId());
          return ImportedCommand.builder()
              .commandName(commandName)
              .commandStoreName(commandStoreName)
              .highestVersion(latestVersion)
              .templateId(template.getUuid())
              .appId(template.getAppId())
              .description(template.getDescription())
              .name(template.getName())
              .build();
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public ImportedCommand makeImportedCommandObject(String commandName, String commandStoreName,
      List<TemplateVersion> templateVersions, String accountId, Template template) {
    if (template == null) {
      return null;
    }
    ImportedTemplate importedTemplate = get(commandName, commandStoreName, accountId, template.getAppId());
    List<ImportedCommandVersion> importedCommandVersionList = new ArrayList<>();

    if (templateVersions != null) {
      templateVersions.forEach(templateVersion -> {
        Template templateWithDetails =
            templateService.get(template.getUuid(), String.valueOf(templateVersion.getVersion()));
        ImportedCommandVersionBuilder importedCommandVersionBuilder =
            ImportedCommandVersion.builder()
                .commandName(commandName)
                .commandStoreName(commandStoreName)
                .createdAt(String.valueOf(templateVersion.getCreatedAt()))
                .version(templateVersion.getImportedTemplateVersion())
                .description(templateVersion.getVersionDetails())
                .templateObject(templateWithDetails.getTemplateObject())
                .variables(templateWithDetails.getVariables())
                .yamlContent(templateService.getYamlOfTemplate(template.getUuid(), templateWithDetails.getVersion()))
                .templateId(templateVersion.getTemplateUuid());
        if (templateVersion.getCreatedBy() != null) {
          importedCommandVersionBuilder.createdBy(templateVersion.getCreatedBy().getName());
        }
        importedCommandVersionList.add(importedCommandVersionBuilder.build());
      });
    }
    ImportedCommandBuilder importedCommandBuilder = ImportedCommand.builder()
                                                        .appId(importedTemplate.getAppId())
                                                        .commandName(commandName)
                                                        .importedCommandVersionList(importedCommandVersionList)
                                                        .commandStoreName(commandStoreName)
                                                        .name(template.getName())
                                                        .repoUrl(importedTemplate.getRepoUrl())
                                                        .tags(importedTemplate.getTags())
                                                        .description(template.getDescription())
                                                        .templateId(template.getUuid());

    importedCommandBuilder.commandStoreName(importedTemplate.getCommandStoreName());
    return importedCommandBuilder.build();
  }

  @Override
  public ImportedTemplate get(String commandName, String commandStoreName, String accountId, String appId) {
    return wingsPersistence.createQuery(ImportedTemplate.class)
        .filter(ImportedTemplateKeys.commandName, commandName)
        .filter(ImportedTemplateKeys.accountId, accountId)
        .filter(ImportedTemplateKeys.commandStoreName, commandStoreName)
        .filter(ImportedTemplateKeys.appId, appId)
        .get();
  }

  @Override
  public Template getAndSaveImportedTemplate(
      String version, String commandName, String commandStoreName, String accountId, String appId) {
    CommandEntityDTO commandEntityDTO = downloadAndGetCommandEntityDTO(commandStoreName, commandName);
    ImportedTemplate importedTemplate = getImportedTemplate(commandEntityDTO, accountId, appId);
    if (importedTemplate == null) {
      return saveNewCommand(version, commandName, commandStoreName, accountId, commandEntityDTO, appId);
    } else {
      return downloadAndSaveVersionOfExistingCommand(
          version, commandName, commandStoreName, accountId, importedTemplate, appId);
    }
  }

  private ImportedTemplate getImportedTemplate(CommandEntityDTO commandEntityDTO, String accountId, String appId) {
    return get(commandEntityDTO.getName(), commandEntityDTO.getCommandStoreName(), accountId, appId);
  }

  private Template saveNewCommand(String version, String commandName, String commandStoreName, String accountId,
      CommandEntityDTO commandEntityDTO, String appId) {
    ImportedTemplate importedTemplate = ImportedTemplate.builder()
                                            .commandStoreName(commandEntityDTO.getCommandStoreName())
                                            .commandName(commandEntityDTO.getName())
                                            .accountId(accountId)
                                            .appId(appId)
                                            .templateId(null)
                                            .description(commandEntityDTO.getDescription())
                                            .name(commandEntityDTO.getName())
                                            .imageUrl(commandEntityDTO.getImageUrl())
                                            .repoUrl(commandEntityDTO.getRepoUrl())
                                            .tags(commandEntityDTO.getTags())
                                            .build();
    Template template =
        downloadAndSaveNewCommandVersion(version, commandName, commandStoreName, accountId, importedTemplate, appId);
    importedTemplate.setTemplateId(template.getUuid());
    wingsPersistence.save(importedTemplate);
    return templateService.get(template.getUuid());
  }

  private Template downloadAndSaveNewCommandVersion(String version, String commandName, String commandStoreName,
      String accountId, ImportedTemplate importedTemplate, String appId) {
    EnrichedCommandVersionDTO commandVersionDTO =
        downloadAndGetCommandVersionDTO(commandStoreName, commandName, version);
    Template template = createTemplateFromCommandVersionDTO(commandVersionDTO, importedTemplate, accountId, appId);
    return templateService.saveReferenceTemplate(template);
  }

  private Template downloadAndSaveVersionOfExistingCommand(String version, String commandName, String commandStoreName,
      String accountId, ImportedTemplate importedTemplate, String appId) {
    throwExceptionIfCommandVersionAlreadyDownloaded(version, importedTemplate);
    EnrichedCommandVersionDTO commandVersionDTO =
        downloadAndGetCommandVersionDTO(commandStoreName, commandName, version);
    Template template = createTemplateFromCommandVersionDTO(commandVersionDTO, importedTemplate, accountId, appId);
    template.setUuid(importedTemplate.getTemplateId());
    return templateService.updateReferenceTemplate(template);
  }

  @VisibleForTesting
  EnrichedCommandVersionDTO downloadAndGetCommandVersionDTO(
      String commandStoreName, String commandName, String version) {
    return CommandLibraryServiceClientUtils
        .executeHttpRequest(serviceHttpClient.getVersionDetails(commandStoreName, commandName, version, emptyMap()))
        .getResource();
  }

  @VisibleForTesting
  CommandEntityDTO downloadAndGetCommandEntityDTO(String commandStoreName, String commandName) {
    return CommandLibraryServiceClientUtils
        .executeHttpRequest(serviceHttpClient.getCommandDetails(commandStoreName, commandName, emptyMap()))
        .getResource();
  }

  private void throwExceptionIfCommandVersionAlreadyDownloaded(String version, ImportedTemplate importedTemplate) {
    ImportedCommand importedCommand =
        templateVersionService.listImportedTemplateVersions(importedTemplate.getCommandName(),
            importedTemplate.getCommandStoreName(), importedTemplate.getAccountId(), importedTemplate.getAppId());
    boolean present = importedCommand.getImportedCommandVersionList()
                          .stream()
                          .filter(importedCommandVersion -> importedCommandVersion.getVersion().equals(version))
                          .findFirst()
                          .isPresent();
    if (present) {
      throw new InvalidRequestException(
          String.format("Version %s of command already exists.", version), WingsException.USER);
    }
  }

  @VisibleForTesting
  Template createTemplateFromCommandVersionDTO(
      EnrichedCommandVersionDTO commandVersionDTO, ImportedTemplate importedTemplate, String accountId, String appId) {
    Template template = Template.builder()
                            .templateObject(commandVersionDTO.getTemplateObject())
                            .variables(commandVersionDTO.getVariables())
                            .build();
    template.setAppId(appId);
    template.setAccountId(accountId);
    template.setVersionDetails(commandVersionDTO.getDescription());
    // TODO: Assuming only harness command gallery. May have to maintain a mapping for store ID to Gallery.
    TemplateGallery templateGallery =
        templateGalleryHelper.getGalleryByGalleryKey(GalleryKey.HARNESS_COMMAND_LIBRARY_GALLERY.name(), accountId);
    template.setGalleryId(templateGallery.getUuid());
    template.setName(importedTemplate.getName());
    template.setDescription(importedTemplate.getDescription());
    HarnessImportedTemplateDetails harnessImportedTemplateDetails =
        HarnessImportedTemplateDetails.builder()
            .commandVersion(commandVersionDTO.getVersion())
            .commandStoreName(commandVersionDTO.getCommandStoreName())
            .commandName(commandVersionDTO.getCommandName())
            .build();
    template.setImportedTemplateDetails(harnessImportedTemplateDetails);
    return template;
  }
}
