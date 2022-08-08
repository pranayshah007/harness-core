/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static io.harness.data.structure.CollectionUtils.trimmedLowercaseSet;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.SRE;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.template.Template.TYPE_KEY;
import static software.wings.beans.template.TemplateFolder.GALLERY_ID_KEY;
import static software.wings.beans.template.TemplateFolder.NAME_KEY;
import static software.wings.beans.template.TemplateFolder.NodeType.FOLDER;
import static software.wings.beans.template.TemplateGallery.IMPORTED_TEMPLATE_GALLERY_NAME;
import static software.wings.common.TemplateConstants.GENERIC_COMMANDS;
import static software.wings.common.TemplateConstants.HARNESS_GALLERY;
import static software.wings.common.TemplateConstants.HTTP_VERIFICATION;
import static software.wings.common.TemplateConstants.PATH_DELIMITER;
import static software.wings.common.TemplateConstants.POWER_SHELL_COMMANDS;
import static software.wings.common.TemplateConstants.PREFIX_FOR_APP;
import static software.wings.common.TemplateConstants.TOMCAT_COMMANDS;

import static java.lang.String.format;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.validation.Create;
import io.harness.validation.PersistenceValidator;
import io.harness.validation.SuppressValidation;
import io.harness.validation.Update;

import software.wings.beans.Base;
import software.wings.beans.Event.Type;
import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.template.TemplateFolder.TemplateFolderKeys;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.template.TemplateHelper;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.security.auth.TemplateAuthHandler;
import software.wings.service.impl.security.auth.TemplateRBACListFilter;
import software.wings.service.intfc.template.TemplateFolderService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DuplicateKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class TemplateFolderServiceImpl implements TemplateFolderService {
  private static final String ACCOUNT = "Account";
  private static final String APPLICATION = "Application";

  @Inject private WingsPersistence wingsPersistence;
  @Inject private TemplateGalleryService templateGalleryService;
  @Inject private TemplateService templateService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private TemplateHelper templateHelper;
  @Inject private TemplateAuthHandler templateAuthHandler;

  @Override
  //  @Deprecated
  // galleryId should also be passed.
  public PageResponse<TemplateFolder> list(PageRequest<TemplateFolder> pageRequest) {
    return wingsPersistence.query(TemplateFolder.class, pageRequest);
  }

  @Override
  @ValidationGroups(Create.class)
  public TemplateFolder save(TemplateFolder templateFolder, String galleryId) {
    return saveInternal(templateFolder, galleryId,
        toBeSavedTemplateFolder
        -> PersistenceValidator.duplicateCheck(
            ()
                -> wingsPersistence.saveAndGet(TemplateFolder.class, toBeSavedTemplateFolder),
            TemplateFolder.NAME_KEY, toBeSavedTemplateFolder.getName()));
  }

  private TemplateFolder saveInternal(
      TemplateFolder templateFolder, String galleryId, UnaryOperator<TemplateFolder> saveStrategy) {
    templateFolder.setGalleryId(galleryId);
    if (!isEmpty(templateFolder.getParentId())) {
      TemplateFolder parentFolder = get(templateFolder.getParentId());
      notNullCheck("Parent template folder was deleted", parentFolder);
      String pathId = parentFolder.getPathId() == null
          ? parentFolder.getUuid()
          : parentFolder.getPathId() + PATH_DELIMITER + parentFolder.getUuid();
      templateFolder.setPathId(pathId);
    }
    throwExceptionIfRestrictedFolderName(templateFolder);
    templateFolder.setKeywords(getKeywords(templateFolder));
    TemplateFolder savedTemplateFolder = saveStrategy.apply(templateFolder);

    auditServiceHelper.reportForAuditingUsingAccountId(
        savedTemplateFolder.getAccountId(), null, savedTemplateFolder, Type.CREATE);
    return savedTemplateFolder;
  }

  @Override
  public TemplateFolder saveSafelyAndGet(TemplateFolder templateFolder, String galleryId) {
    try {
      return saveInternal(templateFolder, galleryId,
          toBeSavedTemplateFolder -> wingsPersistence.saveAndGet(TemplateFolder.class, toBeSavedTemplateFolder));
    } catch (DuplicateKeyException e) {
      return getExistingTemplateFolder(templateFolder);
    } catch (Exception e) {
      if (e.getCause() instanceof DuplicateKeyException) {
        return getExistingTemplateFolder(templateFolder);
      }
      throw new GeneralException(ExceptionUtils.getMessage(e), e, USER);
    }
  }

  private TemplateFolder getExistingTemplateFolder(TemplateFolder templateFolder) {
    return wingsPersistence.createQuery(TemplateFolder.class)
        .filter(TemplateFolderKeys.accountId, templateFolder.getAccountId())
        .filter(TemplateFolderKeys.appId, templateFolder.getAppId())
        .filter(TemplateFolderKeys.pathId, templateFolder.getPathId())
        .filter(TemplateFolderKeys.name, templateFolder.getName())
        .filter(TemplateFolderKeys.galleryId, templateFolder.getGalleryId())
        .get();
  }

  private Set<String> getKeywords(TemplateFolder templateFolder) {
    Set<String> generatedKeywords = trimmedLowercaseSet(templateFolder.generateKeywords());
    return TemplateHelper.addUserKeyWords(templateFolder.getKeywords(), generatedKeywords);
  }

  @Override
  @ValidationGroups(Update.class)
  public TemplateFolder update(TemplateFolder templateFolder) {
    throwExceptionIfRestrictedFolderName(templateFolder);
    TemplateFolder savedTemplateFolder = get(templateFolder.getUuid());
    if (savedTemplateFolder == null) {
      throw new WingsException("Template Folder [" + templateFolder.getName() + "] was deleted", USER);
    }
    // not allowing to move app level template to account level template and vice-versa for now - will be changed
    // in later PR that deals with export of templates
    validateScope(templateFolder, savedTemplateFolder);

    Query<TemplateFolder> query =
        wingsPersistence.createQuery(TemplateFolder.class).field(ID_KEY).equal(savedTemplateFolder.getUuid());
    UpdateOperations<TemplateFolder> operations = wingsPersistence.createUpdateOperations(TemplateFolder.class);

    Set<String> userKeywords = trimmedLowercaseSet(savedTemplateFolder.getKeywords());

    if (isNotEmpty(savedTemplateFolder.getDescription())) {
      if (isNotEmpty(userKeywords)) {
        userKeywords.remove(savedTemplateFolder.getDescription().toLowerCase());
      }
      operations.set("description", templateFolder.getDescription());
    }
    operations.set("name", templateFolder.getName());
    operations.set("keywords", getKeywords(templateFolder));
    PersistenceValidator.duplicateCheck(
        () -> wingsPersistence.update(query, operations), "name", templateFolder.getName());
    TemplateFolder updatedTemplateFolder = get(savedTemplateFolder.getUuid());
    auditServiceHelper.reportForAuditingUsingAccountId(
        updatedTemplateFolder.getAccountId(), savedTemplateFolder, updatedTemplateFolder, Type.UPDATE);
    return updatedTemplateFolder;
  }

  private void validateScope(TemplateFolder templateFolder, TemplateFolder savedTemplateFolder) {
    if (!templateFolder.getAppId().equals(savedTemplateFolder.getAppId())) {
      String fromScope = savedTemplateFolder.getAppId().equals(GLOBAL_APP_ID) ? ACCOUNT : APPLICATION;
      String toScope = fromScope.equals(APPLICATION) ? ACCOUNT : APPLICATION;
      throw new InvalidRequestException(
          format("TemplateFolder %s cannot be moved from %s to %s", savedTemplateFolder.getName(), fromScope, toScope));
    }
  }

  @Override
  public boolean delete(String templateFolderUuid) {
    // Delete all the templates
    TemplateFolder templateFolder = get(templateFolderUuid);
    if (templateFolder.getParentId() == null) {
      throw new InvalidRequestException("Root folder [" + templateFolder.getName() + "] can not be deleted", USER);
    }
    if (templateService.deleteByFolder(templateFolder)) {
      // Delete children
      Query<TemplateFolder> childFolders = wingsPersistence.createQuery(TemplateFolder.class)
                                               .filter(TemplateFolderKeys.accountId, templateFolder.getAccountId())
                                               .field(TemplateFolder.PATH_ID_KEY)
                                               .contains(templateFolderUuid)
                                               .filter(TemplateFolderKeys.galleryId, templateFolder.getGalleryId());
      List<TemplateFolder> childFoldersList = childFolders.asList();
      wingsPersistence.delete(childFolders);
      childFoldersList.forEach(childFolder -> {
        auditServiceHelper.reportDeleteForAuditingUsingAccountId(childFolder.getAccountId(), childFolder);
      });

      boolean deleted = wingsPersistence.delete(TemplateFolder.class, templateFolderUuid);
      if (deleted) {
        auditServiceHelper.reportDeleteForAuditingUsingAccountId(templateFolder.getAccountId(), templateFolder);
      }
      return deleted;
    }
    return false;
  }

  @Override
  public TemplateFolder get(String uuid) {
    return wingsPersistence.get(TemplateFolder.class, uuid);
  }

  @Override
  public TemplateFolder getRootLevelFolder(String accountId, String galleryId) {
    return wingsPersistence.createQuery(TemplateFolder.class)
        .field(GALLERY_ID_KEY)
        .equal(galleryId)
        .field(TemplateFolderKeys.accountId)
        .equal(accountId)
        .field(TemplateFolder.PARENT_ID_KEY)
        .doesNotExist()
        .get();
  }

  @Override
  @SuppressValidation
  public void loadDefaultTemplateFolders() {
    TemplateGallery templateGallery = templateGalleryService.get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    notNullCheck("Harness Template gallery was deleted", templateGallery, SRE);
    TemplateFolder harnessFolder = save(TemplateFolder.builder()
                                            .appId(GLOBAL_APP_ID)
                                            .accountId(GLOBAL_ACCOUNT_ID)
                                            .galleryId(templateGallery.getUuid())
                                            .name(HARNESS_GALLERY)
                                            .build(),
        templateGallery.getUuid());
    // Generic commands folder
    save(constructTemplateBuilder(harnessFolder, GENERIC_COMMANDS), templateGallery.getUuid());

    save(constructTemplateBuilder(harnessFolder, POWER_SHELL_COMMANDS), templateGallery.getUuid());

    // Tomcat Commands Folder
    save(constructTemplateBuilder(harnessFolder, TOMCAT_COMMANDS), templateGallery.getUuid());

    // Http Verifications
    save(constructTemplateBuilder(harnessFolder, HTTP_VERIFICATION), templateGallery.getUuid());
  }

  private TemplateFolder constructTemplateBuilder(TemplateFolder parentFolder, String folderName) {
    String pathId = parentFolder.getPathId() == null
        ? parentFolder.getUuid()
        : parentFolder.getPathId() + PATH_DELIMITER + parentFolder.getUuid();
    return TemplateFolder.builder()
        .name(folderName)
        .parentId(parentFolder.getUuid())
        .galleryId(parentFolder.getGalleryId())
        .pathId(pathId)
        .appId(GLOBAL_APP_ID)
        .accountId(GLOBAL_ACCOUNT_ID)
        .build();
  }

  @Override
  public TemplateFolder getTemplateTree(
      String accountId, String keyword, List<String> templateTypes, String galleryId) {
    Query<TemplateFolder> folderQuery = wingsPersistence.createQuery(TemplateFolder.class)
                                            .filter(TemplateFolderKeys.accountId, accountId)
                                            .filter(TemplateFolderKeys.galleryId, galleryId);

    Query<Template> templateQuery = wingsPersistence.createQuery(Template.class)
                                        .filter(TemplateFolderKeys.accountId, accountId)
                                        .filter(TemplateFolderKeys.galleryId, galleryId);

    if (isNotEmpty(keyword)) {
      folderQuery = folderQuery.field(TemplateFolder.KEYWORDS_KEY).contains(keyword.toLowerCase());
      templateQuery = templateQuery.field(TemplateFolder.KEYWORDS_KEY).contains(keyword.toLowerCase());
    }

    List<TemplateFolder> templateFolders = new ArrayList<>();
    boolean contentOnlySearch = false;
    if (isNotEmpty(templateTypes)) {
      templateQuery = templateQuery.field(TYPE_KEY).in(templateTypes);
      contentOnlySearch = true;
    }
    if (!contentOnlySearch) {
      templateFolders = folderQuery.asList();

      List<TemplateFolder> rootTemplateFolders = templateFolders.stream()
                                                     .filter(templateFolder -> isEmpty(templateFolder.getPathId()))
                                                     .collect(Collectors.toList());
      if (isNotEmpty(rootTemplateFolders)) {
        for (TemplateFolder rootTemplateFolder : rootTemplateFolders) {
          templateFolders.addAll(wingsPersistence.createQuery(TemplateFolder.class)
                                     .filter(TemplateFolderKeys.accountId, accountId)
                                     .filter(GALLERY_ID_KEY, rootTemplateFolder.getGalleryId())
                                     .asList());
        }
      }

      getParentUuids(accountId, templateFolders);
    }

    List<Template> templates = templateQuery.asList();
    List<String> templateFolderUuids = getTemplateFolderUuids(templates);

    List<TemplateFolder> templateParentFolders = getTemplateFolderUuids(accountId, templateFolderUuids);
    templateFolders.addAll(templateParentFolders);
    Map<String, Long> folderIdChildCountMap = getFolderIdChildCountMap(templateFolders, templates);
    return constructTemplateTree(templateFolders, folderIdChildCountMap);
  }

  @Override
  public TemplateFolder getTemplateTree(
      String accountId, String appId, String keyword, List<String> templateTypes, String galleryId) {
    // Get all app level template folders
    Query<TemplateFolder> folderQuery = wingsPersistence.createQuery(TemplateFolder.class)
                                            .filter(TemplateFolderKeys.accountId, accountId)
                                            .filter(TemplateFolderKeys.appId, appId)
                                            .filter(TemplateFolderKeys.galleryId, galleryId);

    // Get all templates belonging to appId
    Query<Template> templateQuery = wingsPersistence.createQuery(Template.class)
                                        .filter(TemplateKeys.accountId, accountId)
                                        .filter(TemplateFolderKeys.galleryId, galleryId);

    if (isNotEmpty(keyword)) {
      folderQuery = folderQuery.field(TemplateFolder.KEYWORDS_KEY).contains(keyword.toLowerCase());
      templateQuery = templateQuery.field(TemplateFolder.KEYWORDS_KEY).contains(keyword.toLowerCase());
    }

    List<TemplateFolder> templateFolders = new ArrayList<>();
    boolean contentOnlySearch = false;
    if (isNotEmpty(templateTypes)) {
      templateQuery = templateQuery.field(TYPE_KEY).in(templateTypes);
      contentOnlySearch = true;
    }
    if (!contentOnlySearch) {
      templateFolders = folderQuery.asList();

      // Get root template folder
      if (galleryId != null) {
        TemplateFolder rootTemplateFolder = getRootLevelFolder(accountId, galleryId);
        if (rootTemplateFolder != null) {
          templateFolders.add(rootTemplateFolder);
          templateFolders.addAll(wingsPersistence.createQuery(TemplateFolder.class)
                                     .filter(TemplateFolderKeys.accountId, accountId)
                                     .filter(TemplateFolderKeys.appId, appId)
                                     .filter(GALLERY_ID_KEY, rootTemplateFolder.getGalleryId())
                                     .asList());
        }
      }
      getParentUuids(accountId, templateFolders);
    }

    final TemplateRBACListFilter templateRBACListFilter =
        templateAuthHandler.buildTemplateListRBACFilter(Collections.singletonList(appId));

    List<Template> templates;
    if (templateRBACListFilter.empty()) {
      templates = Collections.emptyList();
    } else {
      templateRBACListFilter.addToQuery(templateQuery);
      templates = templateQuery.asList();
    }

    List<String> templateFolderUuids = getTemplateFolderUuids(templates);

    List<TemplateFolder> templateParentFolders = getTemplateFolderUuids(accountId, templateFolderUuids);
    templateFolders.addAll(templateParentFolders);
    Map<String, Long> folderIdChildCountMap = getFolderIdChildCountMap(templateFolders, templates);
    return constructTemplateTree(templateFolders, folderIdChildCountMap);
  }

  private Map<String, Long> getFolderIdChildCountMap(List<TemplateFolder> templateFolders, List<Template> templates) {
    return templateFolders.stream().collect(Collectors.toMap(Base::getUuid,
        templateFolder
        -> templates.stream().filter(t -> t.getFolderPathId().contains(templateFolder.getUuid())).count(),
        (a, b) -> b));
  }

  private List<String> getTemplateFolderUuids(List<Template> templates) {
    return templates.stream()
        .flatMap(template -> Arrays.stream(template.getFolderPathId().split("/")).distinct())
        .collect(Collectors.toList());
  }

  private void getParentUuids(String accountId, List<TemplateFolder> templateFolders) {
    List<String> parentUuids =
        templateFolders.stream()
            .filter(templateFolder -> isNotEmpty(templateFolder.getPathId()))
            .flatMap(templateFolder -> Arrays.stream(templateFolder.getPathId().split("/")).distinct())
            .collect(Collectors.toList());
    templateFolders.addAll(getTemplateFolderUuids(accountId, parentUuids));
  }

  private List<TemplateFolder> getTemplateFolderUuids(String accountId, List<String> parentUuids) {
    return wingsPersistence.createQuery(TemplateFolder.class)
        .filter(TemplateFolderKeys.accountId, accountId)
        .field("uuid")
        .in(parentUuids)
        .asList();
  }

  private TemplateFolder constructTemplateTree(
      List<TemplateFolder> templateFolders, Map<String, Long> folderIdChildCountMap) {
    Map<String, TemplateFolder> folderMap = new HashMap<>();
    templateFolders.forEach(templateFolder -> folderMap.putIfAbsent(templateFolder.getUuid(), templateFolder));
    folderMap.values().forEach(folder -> {
      folder.setTemplatesCount(folderIdChildCountMap.get(folder.getUuid()));
      if (isNotEmpty(folder.getParentId())) {
        TemplateFolder templateFolder = folderMap.get(folder.getParentId());
        if (templateFolder != null) {
          templateFolder.setNodeType(FOLDER.name());
          templateFolder.addChild(folder);
        } else {
          log.error("Failed to construct tree from partial list");
        }
      } else {
        folder.setNodeType(FOLDER.name());
      }
    });
    return folderMap.values().stream().filter(folder -> isEmpty(folder.getParentId())).findFirst().orElse(null);
  }

  @Override
  public void copyHarnessTemplateFolders(String galleryId, String accountId, String accountName) {
    // First Get the Harness template folder
    String globalGalleryId = templateGalleryService.get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY).getUuid();
    TemplateFolder harnessTemplateFolder = getTemplateTree(GLOBAL_ACCOUNT_ID, null, null, globalGalleryId);
    TemplateFolder destinationRootFolder =
        createRootTemplateFolder(harnessTemplateFolder, accountId, accountName, galleryId);
    createChildren(harnessTemplateFolder, accountId, galleryId, destinationRootFolder);
  }

  @Override
  public TemplateFolder getByFolderPath(String accountId, String folderPath, String galleryId) {
    return getByFolderPath(accountId, GLOBAL_APP_ID, folderPath, galleryId);
  }

  @Override
  public TemplateFolder getByFolderPath(String accountId, String appId, String folderPath, String galleryId) {
    String[] folderPaths = folderPath.split("/");
    int beginIndex = folderPath.lastIndexOf('/');
    List<TemplateFolder> templateFolders =
        getTemplateFolders(accountId, appId, folderPath.substring(beginIndex + 1), galleryId);
    templateFolders.add(getRootLevelFolder(accountId, galleryId));
    for (TemplateFolder templateFolder : templateFolders) {
      // Verify the length of the parent folder matches the length of the given folder path
      // Otherwise, ignore
      if (isEmpty(templateFolder.getPathId()) && templateFolder.getName().equals(folderPath)) {
        return templateFolder;
      }
      if (templateFolder.getPathId() == null) {
        continue;
      }
      List<String> parentUuids =
          Arrays.stream(templateFolder.getPathId().split("/")).distinct().collect(Collectors.toList());
      if (folderPaths.length - 1 != parentUuids.size()) {
        continue;
      }
      if (matchesPath(accountId, templateFolder, folderPaths, parentUuids, galleryId)) {
        return templateFolder;
      }
    }
    return null;
  }

  private List<TemplateFolder> getTemplateFolders(String accountId, String appId, String folderName, String galleryId) {
    return wingsPersistence.createQuery(TemplateFolder.class)
        .filter(TemplateFolderKeys.accountId, accountId)
        .filter(TemplateFolderKeys.appId, appId)
        .filter(TemplateFolderKeys.name, folderName)
        .filter(TemplateFolderKeys.galleryId, galleryId)
        .asList();
  }

  private boolean matchesPath(String accountId, TemplateFolder templateFolder, String[] folderPaths,
      List<String> parentUuids, String galleryId) {
    int i = folderPaths.length - 2;
    if (i <= -1) {
      return templateFolder.getName().equals(folderPaths[0]);
    }

    Collections.reverse(parentUuids);

    Map<String, String> uuidNameMap = fetchTemplateFolderNames(accountId, parentUuids, galleryId);

    for (String parentUuid : parentUuids) {
      if (uuidNameMap.get(parentUuid).equals(folderPaths[i])) {
        i--;
      } else {
        return false;
      }
    }
    return i == -1;
  }

  @Override
  public Map<String, String> fetchTemplateFolderNames(String accountId, List<String> parentUuids, String galleryId) {
    List<TemplateFolder> templateFolders = wingsPersistence.createQuery(TemplateFolder.class)
                                               .project(NAME_KEY, true)
                                               .filter(TemplateFolderKeys.accountId, accountId)
                                               .filter(TemplateFolderKeys.galleryId, galleryId)
                                               .field("uuid")
                                               .in(parentUuids)
                                               .asList();

    return templateFolders.stream().collect(Collectors.toMap(TemplateFolder::getUuid, TemplateFolder::getName));
  }

  private void createChildren(
      TemplateFolder templateFolder, String accountId, String galleryId, TemplateFolder parentFolder) {
    if (templateFolder == null) {
      return;
    }
    if (isEmpty(templateFolder.getChildren())) {
      return;
    }
    templateFolder.getChildren().forEach(sourceFolder -> {
      TemplateFolder destinationFolder = createTemplateFolder(sourceFolder, accountId, galleryId, parentFolder);
      createChildren(sourceFolder, accountId, galleryId, destinationFolder);
    });
  }

  private TemplateFolder createTemplateFolder(
      TemplateFolder sourceFolder, String accountId, String galleryId, TemplateFolder parentFolder) {
    TemplateFolder destinationFolder = sourceFolder.cloneInternal();
    destinationFolder.setAccountId(accountId);
    if (parentFolder != null) {
      if (parentFolder.getUuid() != null) {
        destinationFolder.setParentId(parentFolder.getUuid());
      }
      if (parentFolder.getPathId() == null) {
        destinationFolder.setPathId(parentFolder.getUuid());
      } else {
        destinationFolder.setPathId(parentFolder.getPathId() + "/" + parentFolder.getUuid());
      }
    }
    destinationFolder.setGalleryId(galleryId);
    destinationFolder.setKeywords(getKeywords(destinationFolder));
    destinationFolder = wingsPersistence.saveAndGet(TemplateFolder.class, destinationFolder);
    return destinationFolder;
  }

  private TemplateFolder createRootTemplateFolder(
      TemplateFolder sourceRootFolder, String accountId, String accountName, String galleryId) {
    TemplateFolder destinationFolder = sourceRootFolder.cloneInternal();
    destinationFolder.setAccountId(accountId);
    destinationFolder.setName(accountName);
    destinationFolder.setKeywords(getKeywords(destinationFolder));
    destinationFolder.setGalleryId(galleryId);
    destinationFolder = wingsPersistence.saveAndGet(TemplateFolder.class, destinationFolder);
    return destinationFolder;
  }

  @Override
  public TemplateFolder createRootImportedTemplateFolder(String accountId, String galleryId) {
    TemplateFolder templateFolder = TemplateFolder.builder()
                                        .galleryId(galleryId)
                                        .accountId(accountId)
                                        .appId(GLOBAL_APP_ID)
                                        .name(IMPORTED_TEMPLATE_GALLERY_NAME)
                                        .build();
    templateFolder.setKeywords(getKeywords(templateFolder));
    return wingsPersistence.saveAndGet(TemplateFolder.class, templateFolder);
  }

  @Override
  public TemplateFolder getImportedTemplateFolder(String accountId, String galleryId, String appId) {
    return getRootLevelFolder(accountId, galleryId);
  }

  private void throwExceptionIfRestrictedFolderName(TemplateFolder templateFolder) {
    if (templateFolder.getName().equals(PREFIX_FOR_APP) && templateFolder.getParentId() == null) {
      throw new InvalidRequestException("Folder name cannot be " + PREFIX_FOR_APP, USER);
    }
  }
}
