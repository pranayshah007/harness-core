/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.CollectionUtils.trimmedLowercaseSet;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.TEMPLATE_NOT_FOUND;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.template.Template.FOLDER_ID_KEY;
import static software.wings.beans.template.Template.FOLDER_PATH_ID_KEY;
import static software.wings.beans.template.Template.NAME_KEY;
import static software.wings.beans.template.Template.REFERENCED_TEMPLATE_ID_KEY;
import static software.wings.beans.template.Template.VERSION_KEY;
import static software.wings.beans.template.TemplateGallery.GalleryKey.HARNESS_COMMAND_LIBRARY_GALLERY;
import static software.wings.beans.template.TemplateHelper.addUserKeyWords;
import static software.wings.beans.template.TemplateHelper.isAppLevelImportedCommand;
import static software.wings.beans.template.TemplateHelper.mappedEntities;
import static software.wings.beans.template.TemplateHelper.obtainTemplateFolderPath;
import static software.wings.beans.template.TemplateHelper.obtainTemplateName;
import static software.wings.beans.template.TemplateHelper.obtainTemplateNameForImportedCommands;
import static software.wings.beans.template.TemplateHelper.obtainTemplateVersion;
import static software.wings.beans.template.TemplateType.ARTIFACT_SOURCE;
import static software.wings.beans.template.TemplateType.CUSTOM_DEPLOYMENT_TYPE;
import static software.wings.beans.template.TemplateType.HTTP;
import static software.wings.beans.template.TemplateType.PCF_PLUGIN;
import static software.wings.beans.template.TemplateType.SHELL_SCRIPT;
import static software.wings.beans.template.TemplateType.SSH;
import static software.wings.beans.template.TemplateVersion.ChangeType.CREATED;
import static software.wings.beans.template.TemplateVersion.TEMPLATE_UUID_KEY;
import static software.wings.beans.template.VersionedTemplate.TEMPLATE_ID_KEY;
import static software.wings.common.TemplateConstants.APP_PREFIX;
import static software.wings.common.TemplateConstants.DEFAULT_TAG;
import static software.wings.common.TemplateConstants.HARNESS_GALLERY;
import static software.wings.common.TemplateConstants.IMPORTED_TEMPLATE_PREFIX;
import static software.wings.common.TemplateConstants.LATEST_TAG;
import static software.wings.common.TemplateConstants.PATH_DELIMITER;
import static software.wings.exception.TemplateException.templateLinkedException;

import static java.lang.String.format;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.queue.QueuePublisher;
import io.harness.validation.Create;
import io.harness.validation.PersistenceValidator;
import io.harness.validation.Update;
import io.harness.yaml.BaseYaml;

import software.wings.beans.CommandCategory;
import software.wings.beans.EntityType;
import software.wings.beans.Event.Type;
import software.wings.beans.Variable;
import software.wings.beans.template.BaseTemplate;
import software.wings.beans.template.CopiedTemplateMetadata;
import software.wings.beans.template.ImportedTemplate;
import software.wings.beans.template.ImportedTemplate.ImportedTemplateKeys;
import software.wings.beans.template.ImportedTemplateMetadata;
import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.template.TemplateFolder.TemplateFolderKeys;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.template.TemplateGalleryHelper;
import software.wings.beans.template.TemplateHelper;
import software.wings.beans.template.TemplateMetadata;
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.TemplateVersion;
import software.wings.beans.template.TemplateVersion.TemplateVersionKeys;
import software.wings.beans.template.VersionedTemplate;
import software.wings.beans.template.VersionedTemplate.VersionedTemplateBuilder;
import software.wings.beans.template.artifactsource.ArtifactSourceTemplate;
import software.wings.beans.template.command.HttpTemplate;
import software.wings.beans.template.command.PcfCommandTemplate;
import software.wings.beans.template.command.ShellScriptTemplate;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.beans.template.deploymenttype.CustomDeploymentTypeTemplate;
import software.wings.beans.template.dto.HarnessImportedTemplateDetails;
import software.wings.beans.template.dto.ImportedTemplateDetails;
import software.wings.beans.template.dto.TemplateMetaData;
import software.wings.beans.yaml.YamlType;
import software.wings.dl.WingsPersistence;
import software.wings.exception.TemplateException;
import software.wings.prune.PruneEntityListener;
import software.wings.prune.PruneEvent;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.ServiceClassLocator;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.ownership.OwnedByTemplate;
import software.wings.service.intfc.template.ImportedTemplateService;
import software.wings.service.intfc.template.TemplateFolderService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.template.TemplateVersionService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.yaml.YamlHelper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class TemplateServiceImpl implements TemplateService {
  private static final String ACCOUNT = "Account";
  private static final String APPLICATION = "Application";

  @Inject private WingsPersistence wingsPersistence;
  @Inject private Map<String, AbstractTemplateProcessor> templateProcessBinder;
  @Inject private TemplateFolderService templateFolderService;
  @Inject private ExecutorService executorService;
  @Inject private TemplateVersionService templateVersionService;
  @Inject private TemplateHelper templateHelper;
  @Inject private TemplateGalleryService templateGalleryService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private YamlPushService yamlPushService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ImportedTemplateService importedTemplateService;
  @Inject private TemplateGalleryHelper templateGalleryHelper;
  @Inject private YamlHandlerFactory yamlHandlerFactory;
  @Inject private QueuePublisher<PruneEvent> pruneQueue;
  @Inject private UserGroupService userGroupService;

  ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Override
  public PageResponse<Template> list(
      PageRequest<Template> pageRequest, List<String> galleryKeys, String accountId, boolean defaultVersion) {
    if (isNotEmpty(galleryKeys)) {
      addSearchFilterForGalleryIds(pageRequest, galleryKeys, accountId);
    }
    final PageResponse<Template> pageResponse = wingsPersistence.query(Template.class, pageRequest);

    for (Template template : pageResponse.getResponse()) {
      setDetailsOfTemplate(template, null, defaultVersion);
    }

    return pageResponse;
  }

  @Override
  public List<TemplateMetaData> listTemplatesWithMetadata(Set<String> appIds, String accountId) {
    List<TemplateMetaData> templateResponseList = new ArrayList<>();

    final List<Template> templates = wingsPersistence.createQuery(Template.class)
                                         .filter(TemplateKeys.accountId, accountId)
                                         .field(TemplateKeys.appId)
                                         .in(appIds)
                                         .asList();

    if (isNotEmpty(templates)) {
      Set<String> templateFolderIds = templates.stream().map(Template::getFolderId).collect(Collectors.toSet());
      if (isNotEmpty(templateFolderIds)) {
        final List<TemplateFolder> templateFolders =
            templateHelper.getFolderDetails(accountId, templateFolderIds, new ArrayList<>(appIds));
        if (isNotEmpty(templateFolders)) {
          final Map<String, TemplateFolder> templateFolderIdToEntityMapping =
              templateFolders.stream().collect(Collectors.toMap(TemplateFolder::getUuid, Function.identity()));
          templateResponseList =
              templates.stream()
                  .map(template
                      -> TemplateMetaData.builder()
                             .appId(template.getAppId())
                             .uuid(template.getUuid())
                             .name(template.getName())
                             .folderName(templateFolderIdToEntityMapping.get(template.getFolderId()) != null
                                     ? templateFolderIdToEntityMapping.get(template.getFolderId()).getName()
                                     : HARNESS_GALLERY)
                             .build())
                  .collect(Collectors.toList());
        }
      }
    }
    return templateResponseList;
  }

  private void addSearchFilterForGalleryIds(
      PageRequest<Template> pageRequest, List<String> galleryKeys, String accountId) {
    final List<String> galleryIds = getGalleryIds(galleryKeys, accountId);
    if (isNotEmpty(galleryIds)) {
      final SearchFilter searchFilter =
          SearchFilter.builder().fieldName(TemplateKeys.galleryId).op(IN).fieldValues(galleryIds.toArray()).build();
      pageRequest.addFilter(searchFilter);
    }
  }

  @NotNull
  private List<String> getGalleryIds(List<String> galleryKeys, String accountId) {
    return galleryKeys.stream()
        .filter(EmptyPredicate::isNotEmpty)
        .map(galleryKey -> templateGalleryHelper.getGalleryByGalleryKey(galleryKey, accountId))
        .filter(Objects::nonNull)
        .map(TemplateGallery::getUuid)
        .distinct()
        .collect(Collectors.toList());
  }

  @Override
  @ValidationGroups(Create.class)
  public Template saveReferenceTemplate(Template template) {
    if (template.getGalleryId() == null) {
      throw new InvalidRequestException("Gallery ID must be set to save imported template.");
    }
    validateTemplateVariables(template.getVariables(), null);
    setTemplateFolderForImportedTemplate(template);
    saveOrUpdate(template);
    processTemplate(template);

    template.setVersion(1L);
    // Client side keyword generation.
    template.setKeywords(getKeywords(template));
    processImportedTemplate(template, false);

    String templateUuid =
        PersistenceValidator.duplicateCheck(() -> wingsPersistence.save(template), NAME_KEY, template.getName());

    getTemplateVersionForCommand(template, templateUuid,
        getImportedTemplateVersion(template.getImportedTemplateDetails(), template.getAccountId()));
    wingsPersistence.save(buildTemplateDetails(template, templateUuid));
    Template savedTemplate = get(templateUuid);

    auditServiceHelper.reportForAuditingUsingAccountId(savedTemplate.getAccountId(), null, savedTemplate, Type.CREATE);

    return savedTemplate;
  }

  @Override
  @ValidationGroups(Update.class)
  public Template updateReferenceTemplate(Template template) {
    if (template.getGalleryId() == null) {
      throw new InvalidRequestException("Gallery ID must be set to save imported template.");
    }
    setTemplateFolderForImportedTemplate(template);
    saveOrUpdate(template);
    processTemplate(template);

    Template oldTemplate = getOldTemplate(template.getImportedTemplateDetails(), template.getAccountId(),
        template.getVersion() == 0 ? null : String.valueOf(template.getVersion()), template.getAppId());
    notNullCheck("Template " + template.getName() + " does not exist", oldTemplate);
    if (!oldTemplate.getName().equals(template.getName())) {
      throw new InvalidRequestException("Name of imported template cannot be changed.", USER);
    }
    validateScope(template, oldTemplate);
    validateOutputEnvironmentVariables(template);
    Set<String> existingKeywords = oldTemplate.getKeywords();
    Set<String> generatedKeywords = trimmedLowercaseSet(template.generateKeywords());
    if (isNotEmpty(existingKeywords)) {
      existingKeywords.remove(oldTemplate.getName().toLowerCase());
      if (oldTemplate.getDescription() != null) {
        existingKeywords.remove(oldTemplate.getDescription().toLowerCase());
      }
      existingKeywords.remove(oldTemplate.getType().toLowerCase());
      generatedKeywords.addAll(existingKeywords);
    }
    template.setKeywords(trimmedLowercaseSet(generatedKeywords));

    boolean newVersionDownload = checkForNewVersionDownload(template);
    Long templateVersionNumber = template.getVersion();
    if (newVersionDownload) {
      VersionedTemplate newVersionedTemplate = buildTemplateDetails(template, template.getUuid());
      validateTemplateVariables(newVersionedTemplate.getVariables(), oldTemplate);
      TemplateVersion templateVersion = getTemplateVersionForCommand(template, template.getUuid(),
          getImportedTemplateVersion(template.getImportedTemplateDetails(), template.getAccountId()));
      newVersionedTemplate.setVersion(templateVersion.getVersion());
      newVersionedTemplate.setImportedTemplateVersion(templateVersion.getImportedTemplateVersion());
      saveVersionedTemplate(template, newVersionedTemplate);
      processImportedTemplate(template, true);
      templateVersionNumber = templateVersion.getVersion();
    }
    ensureTemplateVersionIsLatest(template);
    PersistenceValidator.duplicateCheck(() -> wingsPersistence.save(template), NAME_KEY, template.getName());

    Template savedTemplate = get(template.getAccountId(), template.getUuid(), String.valueOf(templateVersionNumber));
    if (isDefaultChanged(oldTemplate, template)) {
      executorService.submit(() -> updateLinkedEntities(savedTemplate));
    }
    auditServiceHelper.reportForAuditingUsingAccountId(
        savedTemplate.getAccountId(), oldTemplate, savedTemplate, Type.UPDATE);

    return savedTemplate;
  }

  @Override
  @ValidationGroups(Create.class)
  public Template save(Template template) {
    validateTemplateVariables(template.getVariables(), null);
    if (template.getTemplateMetadata() != null) {
      processTemplateMetadata(template, false);
    }
    saveOrUpdate(template);
    // create initial version
    processTemplate(template);

    template.setVersion(1L);
    template.setKeywords(getKeywords(template));

    String templateUuid =
        PersistenceValidator.duplicateCheck(() -> wingsPersistence.save(template), NAME_KEY, template.getName());
    getTemplateVersion(template, templateUuid, template.getType(), template.getName(), CREATED);

    // Save Versioned template
    wingsPersistence.save(buildTemplateDetails(template, templateUuid));

    Template savedTemplate = get(templateUuid);

    yamlPushService.pushYamlChangeSet(
        template.getAccountId(), null, template, Type.CREATE, template.isSyncFromGit(), false);
    return savedTemplate;
  }

  private VersionedTemplate buildTemplateDetails(Template template, String templateUuid) {
    VersionedTemplateBuilder builder = VersionedTemplate.builder();
    builder.templateId(templateUuid)
        .accountId(template.getAccountId())
        .version(template.getVersion())
        .templateObject(template.getTemplateObject())
        .galleryId(template.getGalleryId())
        .variables(template.getVariables());
    builder.importedTemplateVersion(
        getImportedTemplateVersion(template.getImportedTemplateDetails(), template.getAccountId()));
    return builder.build();
  }

  private void saveOrUpdate(Template template) {
    TemplateFolder templateFolder;
    String galleryId = template.getGalleryId();
    if (isEmpty(galleryId)) {
      TemplateGallery templateGallery =
          templateGalleryService.getByAccount(template.getAccountId(), templateGalleryService.getAccountGalleryKey());
      notNullCheck("Template gallery does not exist", templateGallery, USER);
      galleryId = templateGallery.getUuid();
    }
    template.setGalleryId(galleryId);
    if (isEmpty(template.getFolderId())) {
      notNullCheck("Template Folder Path", template.getFolderPath());
      templateFolder =
          templateFolderService.getByFolderPath(template.getAccountId(), template.getFolderPath(), galleryId);
      notNullCheck(template.getFolderPath() + " does not exist", templateFolder, USER);
    } else {
      templateFolder = templateFolderService.get(template.getFolderId());
    }
    notNullCheck("Folder does not exist", templateFolder, USER);
    template.setFolderId(templateFolder.getUuid());
    if (templateFolder.getPathId() == null) {
      template.setFolderPathId(templateFolder.getUuid());
    } else {
      template.setFolderPathId(templateFolder.getPathId() + "/" + templateFolder.getUuid());
    }
  }

  @Override
  @ValidationGroups(Update.class)
  public Template update(Template template) {
    if (importedTemplateService.isImported(template.getUuid(), template.getAccountId())) {
      return updateReferenceTemplate(template);
    }
    if (template.getTemplateMetadata() != null) {
      processTemplateMetadata(template, true);
    }
    saveOrUpdate(template);
    processTemplate(template);
    Template oldTemplate;
    if (template.getVersion() == null) {
      oldTemplate = get(template.getUuid());
      template.setVersion(oldTemplate.getVersion());
    } else {
      oldTemplate = get(template.getAccountId(), template.getUuid(), String.valueOf(template.getVersion()));
    }
    notNullCheck("Template " + template.getName() + " does not exist", oldTemplate);
    validateScope(template, oldTemplate);
    validateOutputEnvironmentVariables(template);
    Set<String> existingKeywords = oldTemplate.getKeywords();
    Set<String> generatedKeywords = trimmedLowercaseSet(template.generateKeywords());
    if (isNotEmpty(existingKeywords)) {
      existingKeywords.remove(oldTemplate.getName().toLowerCase());
      if (oldTemplate.getDescription() != null) {
        existingKeywords.remove(oldTemplate.getDescription().toLowerCase());
      }
      existingKeywords.remove(oldTemplate.getType().toLowerCase());
      generatedKeywords.addAll(existingKeywords);
    }
    template.setKeywords(trimmedLowercaseSet(generatedKeywords));
    VersionedTemplate newVersionedTemplate = buildTemplateDetails(template, template.getUuid());
    validateTemplateVariables(newVersionedTemplate.getVariables(), oldTemplate);
    boolean templateObjectChanged = checkTemplateDetailsChanged(
        template, oldTemplate.getTemplateObject(), newVersionedTemplate.getTemplateObject());

    boolean templateVariablesChanged =
        templateHelper.variablesChanged(newVersionedTemplate.getVariables(), oldTemplate.getVariables());

    boolean templateDetailsChanged = false;
    if (templateObjectChanged || templateVariablesChanged) {
      TemplateVersion templateVersion = getTemplateVersion(
          template, template.getUuid(), template.getType(), template.getName(), TemplateVersion.ChangeType.UPDATED);
      newVersionedTemplate.setVersion(templateVersion.getVersion());
      saveVersionedTemplate(template, newVersionedTemplate);
      templateDetailsChanged = true;
    }
    ensureTemplateVersionIsLatest(template);
    PersistenceValidator.duplicateCheck(() -> wingsPersistence.save(template), NAME_KEY, template.getName());

    Template savedTemplate = get(template.getAccountId(), template.getUuid(), String.valueOf(template.getVersion()));
    if (templateDetailsChanged) {
      executorService.submit(() -> updateLinkedEntities(savedTemplate));
    }

    boolean isRename = !template.getName().equals(oldTemplate.getName());
    yamlPushService.pushYamlChangeSet(
        template.getAccountId(), oldTemplate, template, Type.UPDATE, template.isSyncFromGit(), isRename);

    return savedTemplate;
  }

  private void ensureTemplateVersionIsLatest(Template template) {
    TemplateVersion oldTemplateVersion =
        templateVersionService.lastTemplateVersion(template.getAccountId(), template.getUuid());
    if (template.getVersion() < oldTemplateVersion.getVersion()) {
      template.setVersion(oldTemplateVersion.getVersion());
    }
  }

  private void validateTemplateVariables(List<Variable> templateVariables, Template oldTemplate) {
    if (isNotEmpty(templateVariables)) {
      Set<String> variableNames = new HashSet<>();

      for (Variable variable : templateVariables) {
        String variableName = variable.getName();

        if (oldTemplate != null && oldTemplate.getVariables() != null) {
          // On update check only if new variables contain hyphens.
          // Old variables will not be checked for backward compatibility.
          if (oldTemplate.getVariables().stream().allMatch(
                  existingVariable -> !existingVariable.getName().equals(variableName))) {
            checkVariableNamesContainDashes(variableName);
            checkVariableNamesContainDots(variableName);
          }
        } else {
          checkVariableNamesContainDashes(variableName);
          checkVariableNamesContainDots(variableName);
        }

        if (!variableNames.contains(variableName)) {
          variableNames.add(variableName);
        } else {
          throw new InvalidRequestException("Template contains duplicate variables", USER);
        }
      }
    }
  }

  private void checkVariableNamesContainDashes(String variableName) {
    if (variableName.contains("-")) {
      throw new InvalidRequestException(
          format("Adding variable name %s with hyphens (dashes) is not allowed", variableName));
    }
  }

  private void checkVariableNamesContainDots(String variableName) {
    if (variableName.contains(".")) {
      throw new InvalidRequestException(format("Adding variable name %s with dots (.) is not allowed", variableName));
    }
  }

  private boolean isDefaultChanged(Template oldTemplate, Template template) {
    Long oldVersion = null, newVersion = null;
    if (oldTemplate.getTemplateMetadata() instanceof ImportedTemplateMetadata) {
      oldVersion = ((ImportedTemplateMetadata) oldTemplate.getTemplateMetadata()).getDefaultVersion();
    }
    if (template.getTemplateMetadata() instanceof ImportedTemplateMetadata) {
      newVersion = ((ImportedTemplateMetadata) template.getTemplateMetadata()).getDefaultVersion();
    }

    if ((oldVersion == null && newVersion != null)
        || (oldVersion != null && newVersion != null && !oldVersion.equals(newVersion))) {
      return true;
    }

    return false;
  }

  private TemplateMetadata setCurrentTemplateVersionAsDefault(Template template) {
    return ImportedTemplateMetadata.builder().defaultVersion(template.getVersion()).build();
  }

  private void processImportedTemplate(Template template, boolean isUpdate) {
    if (template.getTemplateMetadata() == null) {
      ImportedTemplateMetadata importedTemplateMetadata = ImportedTemplateMetadata.builder().build();
      template.setTemplateMetadata(importedTemplateMetadata);
    }
    processTemplateMetadata(template, isUpdate);
  }

  private void processTemplateMetadata(Template template, boolean isUpdate) {
    if (template.getTemplateMetadata() instanceof CopiedTemplateMetadata) {
      processCopiedTemplate(template, isUpdate);
    } else if (template.getTemplateMetadata() instanceof ImportedTemplateMetadata) {
      processImportedTemplateMetadata(template, isUpdate);
    } else {
      throw new InvalidRequestException("Template Metadata handler not specified");
    }
  }

  private void processImportedTemplateMetadata(Template template, boolean isUpdate) {
    ImportedTemplateMetadata importedTemplateMetadata = (ImportedTemplateMetadata) template.getTemplateMetadata();
    if (isUpdate) {
      if (importedTemplateMetadata.getDefaultVersion() == null) {
        template.setTemplateMetadata(getTemplateMetadata(template.getUuid()));
      }
    } else {
      template.setTemplateMetadata(setCurrentTemplateVersionAsDefault(template));
    }
  }

  private void processCopiedTemplate(Template template, boolean isUpdate) {
    if (isUpdate) {
      template.setTemplateMetadata(getTemplateMetadata(template.getUuid()));
    } else {
      CopiedTemplateMetadata copiedTemplateMetadata = (CopiedTemplateMetadata) template.getTemplateMetadata();
      resolveNameConflictOfTemplate(template);
      validateCopiedFromImportedTemplate(copiedTemplateMetadata, template.getAccountId());
      TemplateGallery templateGallery =
          templateGalleryService.getByAccount(template.getAccountId(), templateGalleryService.getAccountGalleryKey());
      TemplateFolder templateFolder =
          templateFolderService.getRootLevelFolder(template.getAccountId(), templateGallery.getUuid());
      template.setFolderId(templateFolder.getUuid());
      template.setGalleryId(templateGallery.getUuid());
    }
  }

  private void validateCopiedFromImportedTemplate(CopiedTemplateMetadata copiedTemplateMetadata, String accountId) {
    if (!importedTemplateService.isImported(copiedTemplateMetadata.getParentTemplateId(), accountId)) {
      throw new InvalidRequestException("Source template is not downloaded", USER);
    }
    Template template = get(copiedTemplateMetadata.getParentTemplateId());
    if (template.getVersion() < copiedTemplateMetadata.getParentTemplateVersion()) {
      throw new InvalidRequestException("Source template version is not downloaded", USER);
    }
  }

  private TemplateMetadata getTemplateMetadata(String templateId) {
    Template template = wingsPersistence.get(Template.class, templateId);
    return template.getTemplateMetadata();
  }

  private void resolveNameConflictOfTemplate(Template template) {
    String name = template.getName();
    int additionToName = 1;
    final String seprator = "_";
    if (fetchImportedTemplateByName(name, template.getAccountId()) == null) {
      return;
    }
    while (fetchImportedTemplateByName(name + seprator + additionToName, template.getAccountId()) != null) {
      additionToName = additionToName + 1;
    }
    template.setName(name + seprator + additionToName);
  }

  private Template fetchImportedTemplateByName(String name, String accountId) {
    TemplateGallery templateGallery = templateGalleryHelper.getGalleryByGalleryKey(
        TemplateGallery.GalleryKey.HARNESS_COMMAND_LIBRARY_GALLERY.name(), accountId);
    return wingsPersistence.createQuery(Template.class)
        .filter(TemplateKeys.name, name)
        .filter(TemplateKeys.accountId, accountId)
        .filter(TemplateKeys.galleryId, templateGallery.getUuid())
        .get();
  }

  private void validateScope(Template template, Template oldTemplate) {
    if (!template.getAppId().equals(oldTemplate.getAppId())) {
      String fromScope = oldTemplate.getAppId().equals(GLOBAL_APP_ID) ? ACCOUNT : APPLICATION;
      String toScope = fromScope.equals(APPLICATION) ? ACCOUNT : APPLICATION;
      throw new InvalidRequestException(
          format("Template %s cannot be moved from %s to %s", oldTemplate.getName(), fromScope, toScope));
    }
  }

  private void validateOutputEnvironmentVariables(Template template) {
    if (template.getTemplateObject() instanceof ShellScriptTemplate) {
      String outputVars = ((ShellScriptTemplate) template.getTemplateObject()).getOutputVars();
      String secretOutputVars = ((ShellScriptTemplate) template.getTemplateObject()).getSecretOutputVars();

      List<String> outputVarsList = new ArrayList<>();
      List<String> secretOutputVarsList = new ArrayList<>();

      if (isNotEmpty(outputVars)) {
        outputVarsList = Arrays.asList(outputVars.trim().split("\\s*,\\s*"));
        outputVarsList.replaceAll(String::trim);
      }

      if (isNotEmpty(secretOutputVars)) {
        secretOutputVarsList = Arrays.asList(secretOutputVars.split("\\s*,\\s*"));
        secretOutputVarsList.replaceAll(String::trim);
      }
      Set<String> uniqueOutputVarsList = new HashSet<>(outputVarsList);
      Set<String> uniqueSecretOutputVarsList = new HashSet<>(secretOutputVarsList);

      if (uniqueOutputVarsList.size() < outputVarsList.size()) {
        throw new InvalidRequestException("Duplicate output variables in Shell Script Template");
      }
      if (uniqueSecretOutputVarsList.size() < secretOutputVarsList.size()) {
        throw new InvalidRequestException("Duplicate Secret output variables in Shell Script Template");
      }

      Set<String> commonVars =
          outputVarsList.stream().distinct().filter(secretOutputVarsList::contains).collect(Collectors.toSet());

      if (isNotEmpty(commonVars)) {
        throw new InvalidRequestException("Output Variables cannot be Secret and String both");
      }
    }
  }

  private boolean checkForNewVersionDownload(Template template) {
    PageRequest<TemplateVersion> pageRequest =
        PageRequestBuilder.aPageRequest()
            .addFilter(TemplateVersionKeys.accountId, EQ, template.getAccountId())
            .addFilter(TemplateVersionKeys.templateUuid, EQ, template.getUuid())
            .build();
    List<TemplateVersion> templateVersions = templateVersionService.listTemplateVersions(pageRequest).getResponse();
    HarnessImportedTemplateDetails templateDetails =
        (HarnessImportedTemplateDetails) template.getImportedTemplateDetails();
    Optional<TemplateVersion> version =
        templateVersions.stream()
            .filter(templateVersion
                -> templateVersion.getImportedTemplateVersion().equals(templateDetails.getCommandVersion()))
            .findFirst();
    return !version.isPresent();
  }

  private boolean checkTemplateDetailsChanged(Template template, BaseTemplate oldTemplate, BaseTemplate newTemplate) {
    AbstractTemplateProcessor abstractTemplateProcessor = getAbstractTemplateProcessor(template);
    return abstractTemplateProcessor.checkTemplateDetailsChanged(oldTemplate, newTemplate);
  }

  private void saveVersionedTemplate(Template template, VersionedTemplate newVersionedTemplate) {
    wingsPersistence.saveAndGet(VersionedTemplate.class, newVersionedTemplate);
    template.setVersion(newVersionedTemplate.getVersion());
  }

  private void setTemplateFolderForImportedTemplate(Template template) {
    String galleryId = template.getGalleryId();
    if (isEmpty(galleryId)) {
      TemplateGallery templateGallery =
          templateGalleryService.getByAccount(template.getAccountId(), templateGalleryService.getAccountGalleryKey());
      notNullCheck("Template gallery does not exist", templateGallery, USER);
      galleryId = templateGallery.getUuid();
    }
    String folderId =
        templateFolderService.getImportedTemplateFolder(template.getAccountId(), galleryId, template.getAppId())
            .getUuid();
    template.setFolderId(folderId);
  }

  private TemplateVersion getTemplateVersion(
      Template template, String uuid, String templateType, String templateName, TemplateVersion.ChangeType updated) {
    return templateVersionService.newTemplateVersion(
        template.getAccountId(), template.getGalleryId(), uuid, templateType, templateName, updated);
  }

  private TemplateVersion getTemplateVersionForCommand(Template template, String uuid, String commandVersion) {
    return templateVersionService.newImportedTemplateVersion(template.getAccountId(), template.getGalleryId(), uuid,
        template.getType(), template.getName(), commandVersion, template.getVersionDetails());
  }

  @Override
  public Template get(String templateUuid) {
    Template template = wingsPersistence.get(Template.class, templateUuid);
    notNullCheck("Template was deleted", template);
    setDetailsOfTemplate(template, null);
    return template;
  }

  @Override
  public Template get(String accountId, String templateId, String version) {
    Query<Template> templateQuery = wingsPersistence.createQuery(Template.class)
                                        .filter(Template.ACCOUNT_ID_KEY2, accountId)
                                        .filter(ID_KEY, templateId);
    return getTemplate(version, templateQuery);
  }

  private Template getTemplate(String version, Query<Template> templateQuery) {
    Template template = templateQuery.get();
    notNullCheck("Template does not exist", template);
    setDetailsOfTemplate(template, version);
    return template;
  }

  private Template getReferencedTemplate(
      String commandId, String commandStoreId, String accountId, String version, String appId) {
    Template template = importedTemplateService.getTemplateByCommandName(commandId, commandStoreId, accountId, appId);
    notNullCheck(String.format("No Template not found for command %s.", commandId), template);
    return get(template.getUuid(), version);
  }

  @Override
  public Template get(String templateId, String version) {
    Query<Template> templateQuery = wingsPersistence.createQuery(Template.class).filter(ID_KEY, templateId);
    return getTemplate(version, templateQuery);
  }

  private void setTemplateDetails(Template template, String version) {
    Long templateVersion = fetchTemplateVersion(template, version);
    VersionedTemplate versionedTemplate =
        getVersionedTemplate(template.getAccountId(), template.getUuid(), templateVersion);
    notNullCheck(
        "Template [" + template.getName() + "] with version [" + version + "] does not exist", versionedTemplate);
    template.setTemplateObject(versionedTemplate.getTemplateObject());
    template.setVersion(templateVersion);
    template.setVariables(versionedTemplate.getVariables());
  }

  private void setReferencedTemplateDetails(Template template, String version) {
    setTemplateDetails(template, version);
    TemplateVersion templateVersion =
        getTemplateVersionObject(template.getUuid(), fetchTemplateVersion(template, version));
    template.setVersionDetails(templateVersion.getVersionDetails());
    template.setImportedTemplateDetails(setImportedDetailsOfTemplate(template, templateVersion));
  }

  private ImportedTemplateDetails setImportedDetailsOfTemplate(Template template, TemplateVersion templateVersion) {
    if (HARNESS_COMMAND_LIBRARY_GALLERY.name().equals(
            templateGalleryHelper.getGalleryKeyNameByGalleryId(template.getGalleryId()))) {
      ImportedTemplate importedCommandDetails =
          importedTemplateService.getCommandByTemplateId(template.getUuid(), template.getAccountId());
      return HarnessImportedTemplateDetails.builder()
          .commandName(importedCommandDetails.getCommandName())
          .commandVersion(templateVersion.getImportedTemplateVersion())
          .commandStoreName(importedCommandDetails.getCommandStoreName())
          .repoUrl(importedCommandDetails.getRepoUrl())
          .tags(importedCommandDetails.getTags())
          .build();
    }
    return null;
  }

  @Override
  public VersionedTemplate getVersionedTemplate(String accountId, String templateUuid, Long templateVersion) {
    return wingsPersistence.createQuery(VersionedTemplate.class)
        .filter(VersionedTemplate.ACCOUNT_ID_KEY2, accountId)
        .filter(TEMPLATE_ID_KEY, templateUuid)
        .filter(VERSION_KEY, templateVersion)
        .get();
  }

  private TemplateVersion getTemplateVersionObject(String templateId, Long version) {
    return wingsPersistence.createQuery(TemplateVersion.class)
        .filter(TemplateVersionKeys.templateUuid, templateId)
        .filter(TemplateVersionKeys.version, version)
        .get();
  }

  private Long fetchTemplateVersion(Template template, String version) {
    Long templateVersion = null;
    if (DEFAULT_TAG.equals(version)) {
      templateVersion = getDefaultVersion(template);
    }
    if (templateVersion == null) {
      templateVersion = (version == null || version.equals(LATEST_TAG)) ? template.getVersion() : Long.valueOf(version);
    }
    return templateVersion;
  }

  private Long getDefaultVersion(Template template) {
    Long version = null;
    if (template.getTemplateMetadata() instanceof ImportedTemplateMetadata) {
      version = ((ImportedTemplateMetadata) template.getTemplateMetadata()).getDefaultVersion();
    }
    notNullCheck("Template [" + template.getName() + "] with version default does not exist", version);
    return version;
  }

  @Override
  public boolean delete(String accountId, String templateUuid) {
    Template template = wingsPersistence.createQuery(Template.class)
                            .filter(Template.ACCOUNT_ID_KEY2, accountId)
                            .filter(ID_KEY, templateUuid)
                            .get();

    TemplateType templateType = TemplateType.valueOf(template.getType());
    if (templateHelper.templatesLinked(accountId, templateType, Collections.singletonList(templateUuid))) {
      throw templateLinkedException(String.format("Template : [%s] couldn't be deleted", template.getName()),
          templateType, mappedEntities(templateType));
    }
    boolean templateDeleted = wingsPersistence.delete(template);
    if (templateDeleted) {
      wingsPersistence.delete(wingsPersistence.createQuery(VersionedTemplate.class)
                                  .filter(VersionedTemplate.ACCOUNT_ID_KEY2, accountId)
                                  .filter(TEMPLATE_ID_KEY, templateUuid));
      wingsPersistence.delete(wingsPersistence.createQuery(TemplateVersion.class)
                                  .filter(TemplateVersion.ACCOUNT_ID_KEY2, accountId)
                                  .filter(TEMPLATE_UUID_KEY, templateUuid));
      wingsPersistence.delete(wingsPersistence.createQuery(ImportedTemplate.class)
                                  .filter(ImportedTemplateKeys.accountId, accountId)
                                  .filter(ImportedTemplateKeys.templateId, templateUuid));
    }

    if (templateDeleted) {
      sendPruneEvent(template);
      yamlPushService.pushYamlChangeSet(
          template.getAccountId(), template, null, Type.DELETE, template.isSyncFromGit(), false);
    }
    return templateDeleted;
  }

  private void sendPruneEvent(Template template) {
    pruneQueue.send(new PruneEvent(
        Template.class, isEmpty(template.getAppId()) ? GLOBAL_APP_ID : template.getAppId(), template.getUuid()));
  }

  @Override
  public void pruneDescendingEntities(String appId, String templateId) {
    List<OwnedByTemplate> services =
        ServiceClassLocator.descendingServices(this, TemplateServiceImpl.class, OwnedByTemplate.class);
    PruneEntityListener.pruneDescendingEntities(services, descending -> descending.pruneByTemplate(appId, templateId));
  }

  @Override
  public void loadDefaultTemplates(TemplateType templateType, String accountId, String accountName) {
    AbstractTemplateProcessor abstractTemplateProcessor = getTemplateProcessor(templateType.name());
    abstractTemplateProcessor.loadDefaultTemplates(accountId, accountName);
    log.info("Loading default templates for template type {} success", templateType);
  }

  @Override
  public Template loadYaml(TemplateType templateType, String yamlFilePath, String accountId, String accountName) {
    return getTemplateProcessor(templateType.name()).loadYaml(yamlFilePath, accountId, accountName);
  }

  @Override
  public List<CommandCategory> getCommandCategories(String accountId, String appId, String templateId) {
    return templateHelper.getCommandCategories(accountId, appId, templateId);
  }

  @Override
  public TemplateFolder getTemplateTree(String accountId, String keyword, List<String> templateTypes) {
    String galleryId =
        templateGalleryService.getByAccount(accountId, templateGalleryService.getAccountGalleryKey()).getUuid();
    return templateFolderService.getTemplateTree(accountId, keyword, templateTypes, galleryId);
  }

  @Override
  public TemplateFolder getTemplateTree(String accountId, String appId, String keyword, List<String> templateTypes) {
    String galleryId =
        templateGalleryService.getByAccount(accountId, templateGalleryService.getAccountGalleryKey()).getUuid();
    return templateFolderService.getTemplateTree(accountId, appId, keyword, templateTypes, galleryId);
  }

  @Override
  public void updateLinkedEntities(Template savedTemplate) {
    AbstractTemplateProcessor abstractTemplateProcessor = getAbstractTemplateProcessor(savedTemplate);
    abstractTemplateProcessor.updateLinkedEntities(savedTemplate);
  }

  @Override
  public boolean deleteByFolder(TemplateFolder templateFolder) {
    final String accountId = templateFolder.getAccountId();
    List<Key<Template>> templateKeys = wingsPersistence.createQuery(Template.class)
                                           .filter(Template.ACCOUNT_ID_KEY2, accountId)
                                           .field(FOLDER_PATH_ID_KEY)
                                           .contains(templateFolder.getUuid())
                                           .asKeyList();
    List<String> templateUuids =
        templateKeys.stream().map(templateKey -> templateKey.getId().toString()).collect(Collectors.toList());
    if (templateFolder.getAppId() != GLOBAL_APP_ID
        && !templateHelper.shouldAllowTemplateFolderDeletion(templateFolder.getAppId(), new HashSet<>(templateUuids))) {
      throw new InvalidRequestException(
          String.format("User not allowed to delete template folder with id %s", templateFolder.getUuid()),
          ErrorCode.ACCESS_DENIED, USER);
    }

    if (isEmpty(templateUuids)) {
      log.info("No templates under the folder {}", templateFolder.getName());
      return true;
    }
    final List<Template> templates = batchGet(templateUuids, accountId);

    // Since the template folder will be deleted only if all the folder inside it are deleted. Hence validating linkage
    // beforehand. Verify if Service Commands contains the given ids
    String errorMessage = String.format("Template Folder : [%s] couldn't be deleted", templateFolder.getName());

    if (templateHelper.templatesLinked(accountId, SSH, templateUuids)) {
      throw templateLinkedException(errorMessage, SSH, mappedEntities(SSH));
    }
    if (templateHelper.templatesLinked(accountId, HTTP, templateUuids)) {
      throw templateLinkedException(errorMessage, HTTP, mappedEntities(HTTP));
    }
    if (templateHelper.templatesLinked(accountId, SHELL_SCRIPT, templateUuids)) {
      throw templateLinkedException(errorMessage, SHELL_SCRIPT, mappedEntities(SHELL_SCRIPT));
    }
    if (templateHelper.templatesLinked(accountId, ARTIFACT_SOURCE, templateUuids)) {
      throw templateLinkedException(errorMessage, ARTIFACT_SOURCE, mappedEntities(ARTIFACT_SOURCE));
    }
    if (templateHelper.templatesLinked(accountId, PCF_PLUGIN, templateUuids)) {
      throw templateLinkedException(errorMessage, PCF_PLUGIN, mappedEntities(PCF_PLUGIN));
    }
    if (templateHelper.templatesLinked(accountId, CUSTOM_DEPLOYMENT_TYPE, templateUuids)) {
      throw templateLinkedException(errorMessage, CUSTOM_DEPLOYMENT_TYPE, mappedEntities(CUSTOM_DEPLOYMENT_TYPE));
    }
    // Delete templates
    boolean templateDeleted = wingsPersistence.delete(wingsPersistence.createQuery(Template.class)
                                                          .filter(Template.ACCOUNT_ID_KEY2, accountId)
                                                          .field(Template.ID_KEY2)
                                                          .in(templateUuids));

    if (templateDeleted) {
      wingsPersistence.delete(wingsPersistence.createQuery(VersionedTemplate.class)
                                  .filter(VersionedTemplate.ACCOUNT_ID_KEY2, accountId)
                                  .field(TEMPLATE_ID_KEY)
                                  .in(templateUuids));

      wingsPersistence.delete(wingsPersistence.createQuery(TemplateVersion.class)
                                  .filter(TemplateVersion.ACCOUNT_ID_KEY2, accountId)
                                  .field(TEMPLATE_UUID_KEY)
                                  .in(templateUuids));

      for (Template template : templates) {
        sendPruneEvent(template);
        Future<?> future = yamlPushService.pushYamlChangeSet(
            template.getAccountId(), template, null, Type.DELETE, template.isSyncFromGit(), false);
        try {
          future.get();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        } catch (ExecutionException e) {
          log.error("Couldn't delete templates.", e);
          return false;
        }
      }
    }
    return templateDeleted;
  }

  @Override
  public List<Template> batchGet(List<String> templateUuids, String accountId) {
    return wingsPersistence.createQuery(Template.class)
        .filter(Template.ACCOUNT_ID_KEY2, accountId)
        .field(Template.ID_KEY2)
        .in(templateUuids)
        .asList();
  }

  @Override
  public List<Template> listAccountLevelTemplates(String accountId) {
    Query<Template> templateQuery = wingsPersistence.createQuery(Template.class)
                                        .filter(TemplateKeys.accountId, accountId)
                                        .filter(TemplateKeys.appId, GLOBAL_APP_ID);
    List<Template> templates = templateQuery.asList();
    if (isNotEmpty(templates)) {
      for (Template template : templates) {
        setDetailsOfTemplate(template, null);
      }
    }
    return templates;
  }

  private String getImportedTemplateVersion(ImportedTemplateDetails importedTemplateDetails, String accountId) {
    if (HarnessImportedTemplateDetails.class.equals(getImportedCommandDetailClass(importedTemplateDetails))) {
      HarnessImportedTemplateDetails templateDetails = (HarnessImportedTemplateDetails) importedTemplateDetails;
      return templateDetails.getCommandVersion();
    }
    return null;
  }

  private Template getOldTemplate(
      ImportedTemplateDetails importedTemplateDetails, String accountId, String version, String appId) {
    // TODO: Refactor implementation.
    if (HarnessImportedTemplateDetails.class.equals(getImportedCommandDetailClass(importedTemplateDetails))) {
      HarnessImportedTemplateDetails templateDetails = (HarnessImportedTemplateDetails) importedTemplateDetails;
      return getReferencedTemplate(
          templateDetails.getCommandName(), templateDetails.getCommandStoreName(), accountId, version, appId);
    }
    return null;
  }

  private Class getImportedCommandDetailClass(ImportedTemplateDetails importedTemplateDetails) {
    if (importedTemplateDetails instanceof HarnessImportedTemplateDetails) {
      return HarnessImportedTemplateDetails.class;
    }
    return null;
  }

  @Override
  public Template findByFolder(TemplateFolder templateFolder, String templateName, String appId) {
    final Query<Template> templateQuery = wingsPersistence.createQuery(Template.class)
                                              .filter(TemplateKeys.accountId, templateFolder.getAccountId())
                                              .filter(Template.APP_ID_KEY, appId)
                                              .filter(FOLDER_ID_KEY, templateFolder.getUuid())
                                              .filter(NAME_KEY, templateName)
                                              .filter(Template.GALLERY_ID_KEY, templateFolder.getGalleryId());
    return templateQuery.get();
  }

  @Override
  public String fetchTemplateUri(String templateUuid) {
    StringBuilder templateFolderPath = new StringBuilder("");
    Template template = wingsPersistence.get(Template.class, templateUuid);
    if (template == null) {
      log.error("Linked template for http template  {} was deleted ", templateUuid);
      return null;
    }
    if (HARNESS_COMMAND_LIBRARY_GALLERY.name().equals(
            templateGalleryHelper.getGalleryKeyNameByGalleryId(template.getGalleryId()))) {
      return template.getName();
    }
    List<String> folderUuids = Arrays.stream(template.getFolderPathId().split("/")).collect(Collectors.toList());
    Map<String, String> templateUuidNameMap =
        templateFolderService.fetchTemplateFolderNames(template.getAccountId(), folderUuids, template.getGalleryId());
    int i = 0;
    for (String folderId : folderUuids) {
      templateFolderPath = templateFolderPath.append(templateUuidNameMap.get(folderId));
      if (i != folderUuids.size() - 1) {
        templateFolderPath = templateFolderPath.append("/");
      }
      i++;
    }
    return templateFolderPath.append("/").append(template.getName()).toString();
  }

  @Override
  public String makeNamespacedTemplareUri(String templateUuid, String version) {
    if (templateUuid == null) {
      return null;
    }
    String templateUri = fetchTemplateUri(templateUuid);
    if (templateUri == null) {
      log.error("Linked template {} was deleted.", templateUuid);
      return null;
    }
    if (version != null) {
      String templateVersion = fetchTemplateVersionFromVersion(templateUuid, version);
      templateUri = templateUri + ":" + templateVersion;
    }
    Template template = get(templateUuid);
    String galleryKeyName = templateGalleryHelper.getGalleryKeyNameByGalleryId(template.getGalleryId());
    if (!template.getAppId().equals(GLOBAL_APP_ID)) {
      templateUri = APP_PREFIX + templateUri;
    }
    if (HARNESS_COMMAND_LIBRARY_GALLERY.name().equals(galleryKeyName)) {
      templateUri = IMPORTED_TEMPLATE_PREFIX + templateUri;
    }
    return templateUri;
  }

  @Override
  public Object constructEntityFromTemplate(String templateId, String version, EntityType entityType) {
    Template template = get(templateId, version);
    AbstractTemplateProcessor abstractTemplateProcessor = getAbstractTemplateProcessor(template);
    return abstractTemplateProcessor.constructEntityFromTemplate(template, entityType);
  }

  @Override
  public Object constructEntityFromTemplate(Template template, EntityType entityType) {
    AbstractTemplateProcessor abstractTemplateProcessor = getAbstractTemplateProcessor(template);
    return abstractTemplateProcessor.constructEntityFromTemplate(template, entityType);
  }

  @Override
  public String fetchTemplateIdFromUri(String accountId, String templateUri) {
    return fetchTemplateIdFromUri(accountId, GLOBAL_APP_ID, templateUri);
  }

  @Override
  public String fetchTemplateIdFromUri(String accountId, String appId, String templateUri) {
    String galleryId =
        templateGalleryService.getByAccount(accountId, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder;
    String templateAppId = appId;
    if (templateUri.startsWith(APP_PREFIX)) { // app level folder
      String folderPath = obtainTemplateFolderPath(templateUri.substring(APP_PREFIX.length()));
      templateFolder = templateFolderService.getByFolderPath(accountId, appId, folderPath, galleryId);
    } else if (templateUri.startsWith(IMPORTED_TEMPLATE_PREFIX)) {
      return getImportedTemplate(accountId, templateUri, appId).getUuid();
    } else { // root level folder
      String folderPath = obtainTemplateFolderPath(templateUri);
      templateFolder = templateFolderService.getByFolderPath(accountId, folderPath, galleryId);
      templateAppId = GLOBAL_APP_ID;
    }
    if (templateFolder == null) {
      throw new WingsException("No template folder found with the uri  [" + templateUri + "]");
    }

    String templateName = obtainTemplateName(templateUri);
    Template template = wingsPersistence.createQuery(Template.class)
                            .project(NAME_KEY, true)
                            .project(Template.ACCOUNT_ID_KEY2, true)
                            .filter(Template.ACCOUNT_ID_KEY2, accountId)
                            .filter(NAME_KEY, templateName)
                            .filter(FOLDER_ID_KEY, templateFolder.getUuid())
                            .filter(TemplateKeys.appId, templateAppId)
                            .filter(TemplateKeys.galleryId, galleryId)
                            .get();
    if (template == null) {
      throw new WingsException("No template found for the uri [" + templateUri + "]");
    }
    return template.getUuid();
  }

  @Override
  public Template fetchTemplateFromUri(String templateUri, String accountId, String appId) {
    String folderPath;
    TemplateFolder templateFolder;
    String galleryId;
    String templateAppId = appId;
    if (templateUri.startsWith(APP_PREFIX)) { // app level folder
      galleryId =
          templateGalleryService.getByAccount(accountId, templateGalleryService.getAccountGalleryKey()).getUuid();
      folderPath = obtainTemplateFolderPath(templateUri.substring(APP_PREFIX.length()));
      templateFolder = templateFolderService.getByFolderPath(accountId, appId, folderPath, galleryId);
    } else if (templateUri.startsWith(IMPORTED_TEMPLATE_PREFIX)) {
      return getImportedTemplate(accountId, templateUri, appId);
    } else { // account level folder
      folderPath = obtainTemplateFolderPath(templateUri);
      galleryId =
          templateGalleryService.getByAccount(accountId, templateGalleryService.getAccountGalleryKey()).getUuid();
      templateFolder = templateFolderService.getByFolderPath(accountId, folderPath, galleryId);
      templateAppId = GLOBAL_APP_ID;
    }

    if (templateFolder == null) {
      return null;
    }

    String templateName = obtainTemplateName(templateUri);
    Template template = wingsPersistence.createQuery(Template.class)
                            .filter(Template.ACCOUNT_ID_KEY2, accountId)
                            .filter(NAME_KEY, templateName)
                            .filter(FOLDER_ID_KEY, templateFolder.getUuid())
                            .filter(TemplateKeys.appId, templateAppId)
                            .filter(TemplateKeys.galleryId, galleryId)
                            .get();
    if (template == null) {
      return null;
    }
    return template;
  }

  @Override
  public String fetchTemplateVersionFromVersion(String templateUuid, String templateVersion) {
    Template template = get(templateUuid);
    if (importedTemplateService.isImported(templateUuid, template.getAccountId())) {
      if (DEFAULT_TAG.equals(templateVersion)) {
        return templateVersion;
      }
      return importedTemplateService.getImportedTemplateVersionFromTemplateVersion(
          templateUuid, templateVersion, template.getAccountId());
    }
    return templateVersion;
  }

  @Override
  public String fetchTemplateVersionFromUri(String templateUuid, String templateUri) {
    Template template = get(templateUuid);
    String templateVersion = obtainTemplateVersion(templateUri);
    if (importedTemplateService.isImported(templateUuid, template.getAccountId())) {
      if (DEFAULT_TAG.equals(templateVersion)) {
        return templateVersion;
      }
      String version = importedTemplateService.getTemplateVersionFromImportedTemplateVersion(
          templateUuid, templateVersion, template.getAccountId());
      notNullCheck("Template version doesn't exist.", version);
      return version;
    }
    return templateVersion;
  }

  private Template getImportedTemplate(String accountId, String templateUri, String appId) {
    TemplateGallery gallery =
        templateGalleryHelper.getGalleryByGalleryKey(HARNESS_COMMAND_LIBRARY_GALLERY.name(), accountId);
    String templateName = obtainTemplateNameForImportedCommands(templateUri);
    String templateAppId = GLOBAL_APP_ID;
    if (isAppLevelImportedCommand(templateUri)) {
      templateAppId = appId;
    }
    return wingsPersistence.createQuery(Template.class)
        .filter(Template.ACCOUNT_ID_KEY2, accountId)
        .filter(NAME_KEY, templateName)
        .filter(TemplateKeys.appId, templateAppId)
        .filter(TemplateKeys.galleryId, gallery.getUuid())
        .get();
  }

  @Override
  public String getTemplateFolderPathString(Template template) {
    TemplateFolder templateFolder = templateFolderService.get(template.getFolderId());
    TemplateFolder rootFolder = getTemplateTree(template.getAccountId(), template.getAppId(), null, null);
    return generateFolderPath(rootFolder, templateFolder);
  }

  private String generateFolderPath(TemplateFolder folder, TemplateFolder leafFolder) {
    if (folder.getUuid().equals(leafFolder.getUuid())) {
      return folder.getName();
    }
    for (TemplateFolder children : folder.getChildren()) {
      String folderName = generateFolderPath(children, leafFolder);
      if (folderName != null) {
        return folder.getName() + PATH_DELIMITER + folderName;
      }
    }
    return null;
  }

  @Override
  public String fetchTemplateIdByNameAndFolderId(String accountId, String name, String folderId, String galleryId) {
    Template template = wingsPersistence.createQuery(Template.class)
                            .project(NAME_KEY, true)
                            .project(Template.ACCOUNT_ID_KEY2, true)
                            .filter(Template.ACCOUNT_ID_KEY2, accountId)
                            .filter(NAME_KEY, name)
                            .filter(FOLDER_ID_KEY, folderId)
                            .filter(Template.GALLERY_ID_KEY, galleryId)
                            .get();
    if (template == null) {
      throw new TemplateException("No template found with name [" + name + "]", TEMPLATE_NOT_FOUND, null, USER);
    }
    return template.getUuid();
  }

  private AbstractTemplateProcessor getAbstractTemplateProcessor(Template template) {
    if (template.getType() != null) {
      return getTemplateProcessor(template.getType());
    } else {
      TemplateType templateType = getTemplateType(template.getTemplateObject());
      template.setType(templateType.name());
      return getTemplateProcessor(templateType.name());
    }
  }

  private void processTemplate(Template template) {
    AbstractTemplateProcessor abstractTemplateProcessor = getAbstractTemplateProcessor(template);
    if (abstractTemplateProcessor != null) {
      abstractTemplateProcessor.process(template);
    }
  }

  private TemplateType getTemplateType(BaseTemplate templateObject) {
    if (templateObject instanceof SshCommandTemplate) {
      return SSH;
    } else if (templateObject instanceof HttpTemplate) {
      return HTTP;
    } else if (templateObject instanceof ShellScriptTemplate) {
      return SHELL_SCRIPT;
    } else if (templateObject instanceof ArtifactSourceTemplate) {
      return ARTIFACT_SOURCE;
    } else if (templateObject instanceof PcfCommandTemplate) {
      return PCF_PLUGIN;
    } else if (templateObject instanceof CustomDeploymentTypeTemplate) {
      return CUSTOM_DEPLOYMENT_TYPE;
    }
    throw new InvalidRequestException("Template Type not yet supported", USER);
  }

  private AbstractTemplateProcessor getTemplateProcessor(String templateType) {
    return templateProcessBinder.get(templateType);
  }

  private Set<String> getKeywords(Template template) {
    Set<String> generatedKeywords = trimmedLowercaseSet(template.generateKeywords());
    return addUserKeyWords(template.getKeywords(), generatedKeywords);
  }

  private void setDetailsOfTemplate(Template template, String version, boolean defaultVersion) {
    if (version == null && defaultVersion
        && importedTemplateService.isImported(template.getUuid(), template.getAccountId())) {
      setDetailsOfTemplate(template, DEFAULT_TAG);
      return;
    }
    setDetailsOfTemplate(template, version);
  }

  private void setDetailsOfTemplate(Template template, String version) {
    if (importedTemplateService.isImported(template.getUuid(), template.getAccountId())) {
      setReferencedTemplateDetails(template, version);
    } else {
      setTemplateDetails(template, version);
    }
  }

  @Override
  public Template fetchTemplateByKeywordForAccountGallery(@NotEmpty String accountId, String keyword) {
    Template template = null;
    if (isNotEmpty(keyword)) {
      String galleryId =
          Optional
              .ofNullable(templateGalleryService.getByAccount(accountId, templateGalleryService.getAccountGalleryKey()))
              .map(TemplateGallery::getUuid)
              .orElse(null);
      Query<Template> templateQuery = wingsPersistence.createQuery(Template.class)
                                          .filter(TemplateKeys.accountId, accountId)
                                          .filter(TemplateKeys.appId, GLOBAL_APP_ID)
                                          .filter(TemplateKeys.galleryId, galleryId)
                                          .field(TemplateKeys.keywords)
                                          .contains(keyword.toLowerCase());
      List<Template> templates = templateQuery.asList();
      if (isNotEmpty(templates)) {
        template = templates.get(0);
      }
      if (template != null) {
        setDetailsOfTemplate(template, null);
      }
    }
    return template;
  }

  @Override
  public Template fetchTemplateByKeywordForAccountGallery(
      @NotEmpty String accountId, @NotEmpty String appId, String keyword) {
    Template template = null;
    if (isNotEmpty(keyword)) {
      String galleryId =
          Optional
              .ofNullable(templateGalleryService.getByAccount(accountId, templateGalleryService.getAccountGalleryKey()))
              .map(TemplateGallery::getUuid)
              .orElse(null);

      Query<Template> templateQuery = wingsPersistence.createQuery(Template.class)
                                          .filter(TemplateKeys.accountId, accountId)
                                          .filter(TemplateKeys.appId, appId)
                                          .filter(TemplateKeys.galleryId, galleryId)
                                          .field(TemplateKeys.keywords)
                                          .contains(keyword.toLowerCase());
      List<Template> templates = templateQuery.asList();
      if (isNotEmpty(templates)) {
        template = templates.get(0);
      }
      if (template != null) {
        setDetailsOfTemplate(template, null);
      }
    }
    return template;
  }

  @Override
  public Template fetchTemplateByKeywordsForAccountGallery(@NotEmpty String accountId, Set<String> keywords) {
    Template template = null;
    if (isNotEmpty(keywords)) {
      TemplateGallery templateGallery =
          templateGalleryService.getByAccount(accountId, templateGalleryService.getAccountGalleryKey());
      Query<Template> templateQuery =
          wingsPersistence.createQuery(Template.class)
              .filter(TemplateKeys.accountId, accountId)
              .filter(TemplateKeys.appId, GLOBAL_APP_ID)
              .filter(TemplateKeys.galleryId, templateGallery.getUuid())
              .field(Template.KEYWORDS_KEY)
              .hasAllOf(keywords.stream().map(String::toLowerCase).collect(Collectors.toList()));
      List<Template> templates = templateQuery.asList();
      if (isNotEmpty(templates)) {
        template = templates.get(0);
      }
      if (template != null) {
        setDetailsOfTemplate(template, null);
      }
    }
    return template;
  }

  @Override
  public List<Template> fetchTemplatesWithReferencedTemplateId(@NotEmpty String templateId) {
    Query<Template> templateQuery = wingsPersistence.createQuery(Template.class)
                                        .filter(TemplateKeys.appId, GLOBAL_APP_ID)
                                        .filter(REFERENCED_TEMPLATE_ID_KEY, templateId);
    List<Template> templates = templateQuery.asList();
    if (isNotEmpty(templates)) {
      for (Template template : templates) {
        setDetailsOfTemplate(template, null);
      }
    }
    return templates;
  }

  @Override
  public Template convertYamlToTemplate(String templatePath) throws IOException {
    URL url = this.getClass().getClassLoader().getResource(templatePath);
    return mapper.readValue(url, Template.class);
  }

  @Override
  public void loadDefaultTemplates(List<String> templateFiles, String accountId, String accountName) {
    // First
    templateFiles.forEach(templatePath -> {
      try {
        loadAndSaveTemplate(templatePath, accountId, accountName);
      } catch (WingsException exception) {
        String msg = "Failed to save template from file [" + templatePath + "] for the account [" + accountId
            + "] . Reason:" + exception.getMessage();
        throw new WingsException(msg, exception, WingsException.USER);
      } catch (IOException exception) {
        String msg = "Failed to save template from file [" + templatePath + "]. Reason:" + exception.getMessage();
        throw new WingsException(msg, exception, WingsException.USER);
      }
    });
  }

  private Template loadAndSaveTemplate(String templatePath, String accountId, String accountName) throws IOException {
    URL url = this.getClass().getClassLoader().getResource(templatePath);
    Template template = mapper.readValue(url, Template.class);

    if (!GLOBAL_ACCOUNT_ID.equals(accountId)) {
      String referencedTemplateUri = template.getReferencedTemplateUri();
      if (isNotEmpty(referencedTemplateUri)) {
        String referencedTemplateVersion = TemplateHelper.obtainTemplateVersion(referencedTemplateUri);
        template.setReferencedTemplateId(fetchTemplateIdFromUri(GLOBAL_ACCOUNT_ID, referencedTemplateUri));
        if (!LATEST_TAG.equals(referencedTemplateVersion)) {
          if (referencedTemplateVersion != null) {
            template.setReferencedTemplateVersion(Long.parseLong(referencedTemplateVersion));
          }
        }
      }
      if (isNotEmpty(template.getFolderPath())) {
        template.setFolderPath(template.getFolderPath().replace(HARNESS_GALLERY, accountName));
      }
    }
    template.setAppId(GLOBAL_APP_ID);
    template.setAccountId(accountId);
    return save(template);
  }

  @Override
  public void deleteByAccountId(String accountId) {
    List<Template> templates =
        wingsPersistence.createQuery(Template.class).filter(TemplateKeys.accountId, accountId).asList();
    for (Template template : templates) {
      delete(accountId, template.getUuid());
    }
  }

  @Override
  public List<String> fetchTemplateProperties(Template template) {
    AbstractTemplateProcessor abstractTemplateProcessor = getAbstractTemplateProcessor(template);
    return abstractTemplateProcessor.fetchTemplateProperties();
  }

  @Override
  public void pruneByApplication(String appId) {
    // delete all templates with appId
    List<Template> templates = wingsPersistence.createQuery(Template.class).filter(TemplateKeys.appId, appId).asList();
    for (Template template : templates) {
      deleteTemplate(template);
      sendPruneEvent(template);
      yamlPushService.pushYamlChangeSet(
          template.getAccountId(), template, null, Type.DELETE, template.isSyncFromGit(), false);
    }
    // delete all template folders with appId
    List<TemplateFolder> templateFolders =
        wingsPersistence.createQuery(TemplateFolder.class).filter(TemplateFolderKeys.appId, appId).asList();
    for (TemplateFolder templateFolder : templateFolders) {
      wingsPersistence.delete(templateFolder);
      auditServiceHelper.reportDeleteForAuditing(appId, templateFolder);
    }
  }

  private void deleteTemplate(Template template) {
    wingsPersistence.delete(wingsPersistence.createQuery(VersionedTemplate.class)
                                .filter(VersionedTemplate.ACCOUNT_ID_KEY2, template.getAccountId())
                                .filter(TEMPLATE_ID_KEY, template.getUuid()));
    wingsPersistence.delete(
        wingsPersistence.createQuery(TemplateVersion.class).filter(TEMPLATE_UUID_KEY, template.getUuid()));
    wingsPersistence.delete(template);
  }

  @Override
  public String getYamlOfTemplate(String templateId, Long version) {
    return getYamlOfTemplate(templateId, String.valueOf(version));
  }

  @Override
  public String getYamlOfTemplate(String templateId, String version) {
    Template template = get(templateId, version);
    YamlType yamlType;
    if (GLOBAL_APP_ID.equals(template.getAppId())) {
      yamlType = YamlType.GLOBAL_TEMPLATE_LIBRARY;
    } else {
      yamlType = YamlType.APPLICATION_TEMPLATE_LIBRARY;
    }
    BaseYamlHandler yamlHandler = yamlHandlerFactory.getYamlHandler(yamlType, template.getType());
    BaseYaml yaml = yamlHandler.toYaml(template, template.getAppId());
    return YamlHelper.toYamlString(yaml);
  }

  @Override
  public List<Template> getTemplatesByType(String accountId, String appId, TemplateType templateType) {
    final Query<Template> query = wingsPersistence.createQuery(Template.class)
                                      .field(TemplateKeys.accountId)
                                      .equal(accountId)
                                      .field(TemplateKeys.type)
                                      .equal(templateType.toString());
    if (StringUtils.isNotBlank(appId)) {
      query.field(TemplateKeys.appId).equal(appId);
    }
    final List<Template> templates = query.asList();
    templates.forEach(template -> setDetailsOfTemplate(template, null));
    return templates;
  }
}
