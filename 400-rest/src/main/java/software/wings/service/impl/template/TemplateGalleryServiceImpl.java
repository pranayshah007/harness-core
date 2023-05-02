/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static io.harness.data.structure.CollectionUtils.trimmedLowercaseSet;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.validation.PersistenceValidator.duplicateCheck;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.template.TemplateGallery.ACCOUNT_ID_KEY2;
import static software.wings.beans.template.TemplateGallery.GALLERY_KEY;
import static software.wings.beans.template.TemplateGallery.GalleryKey;
import static software.wings.beans.template.TemplateGallery.NAME_KEY;
import static software.wings.common.TemplateConstants.GENERIC_JSON_PATH;
import static software.wings.common.TemplateConstants.HARNESS_GALLERY;
import static software.wings.common.TemplateConstants.PATH_DELIMITER;
import static software.wings.common.TemplateConstants.POWER_SHELL_COMMANDS;
import static software.wings.common.TemplateConstants.POWER_SHELL_IIS_APP_V5_INSTALL_PATH;
import static software.wings.common.TemplateConstants.POWER_SHELL_IIS_WEBSITE_V5_INSTALL_PATH;
import static software.wings.common.TemplateConstants.TOMCAT_WAR_INSTALL_PATH;
import static software.wings.common.TemplateConstants.TOMCAT_WAR_START_PATH;
import static software.wings.common.TemplateConstants.TOMCAT_WAR_STOP_PATH;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.Arrays.asList;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.persistence.HIterator;
import io.harness.scheduler.PersistentScheduler;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.Account;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.template.TemplateHelper;
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.TemplateVersion;
import software.wings.beans.template.VersionedTemplate;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.template.TemplateFolderService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Set;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@Singleton
@ValidateOnExecution
@Slf4j
public class TemplateGalleryServiceImpl implements TemplateGalleryService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private TemplateFolderService templateFolderService;
  @Inject private TemplateService templateService;
  @Inject private TemplateGalleryService templateGalleryService;
  @Inject private AccountService accountService;

  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;

  @Override
  public PageResponse<TemplateGallery> list(PageRequest<TemplateGallery> pageRequest) {
    return wingsPersistence.query(TemplateGallery.class, pageRequest);
  }

  @Override
  @ValidationGroups(Create.class)
  public TemplateGallery save(TemplateGallery templateGallery) {
    templateGallery.setKeywords(getKeywords(templateGallery));
    TemplateGallery finalTemplateGallery = templateGallery;
    return duplicateCheck(()
                              -> wingsPersistence.saveAndGet(TemplateGallery.class, finalTemplateGallery),
        NAME_KEY, templateGallery.getName());
  }

  @Override
  //  @Deprecated
  // Gallery Name should be replaced by gallery key.
  public TemplateGallery get(String accountId, String galleryName) {
    return wingsPersistence.createQuery(TemplateGallery.class)
        .filter(TemplateGallery.ACCOUNT_ID_KEY2, accountId)
        .filter(NAME_KEY, galleryName.trim())
        .get();
  }

  @Override
  public TemplateGallery get(String uuid) {
    return wingsPersistence.get(TemplateGallery.class, uuid);
  }

  @Override
  public GalleryKey getAccountGalleryKey() {
    return GalleryKey.ACCOUNT_TEMPLATE_GALLERY;
  }

  @Override
  public TemplateGallery getByAccount(String accountId, GalleryKey galleryKey) {
    return wingsPersistence.createQuery(TemplateGallery.class)
        .filter(ACCOUNT_ID_KEY2, accountId)
        .filter(GALLERY_KEY, galleryKey.name())
        .get();
  }

  @Override
  public TemplateGallery getByAccount(String accountId, String galleryId) {
    return wingsPersistence.createQuery(TemplateGallery.class)
        .filter(ACCOUNT_ID_KEY2, accountId)
        .filter(ID_KEY, galleryId)
        .get();
  }

  @Override
  @ValidationGroups(Update.class)
  public TemplateGallery update(TemplateGallery templateGallery) {
    TemplateGallery savedGallery = get(templateGallery.getUuid());
    notNullCheck("Template Gallery [" + templateGallery.getName() + "] was deleted", savedGallery, USER);

    Query<TemplateGallery> query =
        wingsPersistence.createQuery(TemplateGallery.class).field(ID_KEY).equal(templateGallery.getUuid());
    UpdateOperations<TemplateGallery> operations = wingsPersistence.createUpdateOperations(TemplateGallery.class);

    Set<String> userKeywords = trimmedLowercaseSet(templateGallery.getKeywords());
    if (isNotEmpty(templateGallery.getDescription())) {
      if (isNotEmpty(userKeywords)) {
        userKeywords.remove(savedGallery.getDescription().toLowerCase());
      }
      operations.set("description", templateGallery.getDescription());
    }
    operations.set("keywords", getKeywords(templateGallery));
    wingsPersistence.update(query, operations);
    return get(savedGallery.getUuid());
  }

  @Override
  public void delete(String galleryUuid) {
    TemplateGallery templateGallery = get(galleryUuid);
    if (templateGallery == null) {
      return;
    }
    deleteGalleryContents(templateGallery.getAccountId(), templateGallery.getUuid());
    wingsPersistence.delete(TemplateGallery.class, templateGallery.getUuid());
  }

  @Override
  public void loadHarnessGallery() {
    log.info("Loading Harness Inc Gallery");
    deleteAccountGalleryByName(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    log.info("Creating harness gallery");
    TemplateGallery gallery = saveHarnessGallery();
    log.info("Harness template gallery created successfully");
    log.info("Loading Harness default template folders");
    templateFolderService.loadDefaultTemplateFolders();
    log.info("Loading Harness default template folders success");
    log.info("Loading default templates for command");
    templateService.loadDefaultTemplates(TemplateType.SSH, GLOBAL_ACCOUNT_ID, gallery.getName());
    log.info("Loading default templates for command success");
    log.info("Loading default templates for http");
    templateService.loadDefaultTemplates(TemplateType.HTTP, GLOBAL_ACCOUNT_ID, gallery.getName());
    log.info("Loading default templates for http success");
    log.info("Loading default templates for shell script");
    templateService.loadDefaultTemplates(TemplateType.SHELL_SCRIPT, GLOBAL_ACCOUNT_ID, gallery.getName());
    log.info("Loading default templates for shell script success");
  }

  @Override
  public TemplateGallery saveHarnessGallery() {
    return wingsPersistence.saveAndGet(TemplateGallery.class,
        TemplateGallery.builder()
            .name(HARNESS_GALLERY)
            .description("Harness gallery")
            .accountId(GLOBAL_ACCOUNT_ID)
            .global(true)
            .appId(GLOBAL_APP_ID)
            .galleryKey(getAccountGalleryKey().name())
            .build());
  }

  @Override
  public void copyHarnessTemplates() {
    Query<Account> query = accountService.getBasicAccountQuery().limit(NO_LIMIT);
    try (HIterator<Account> iterator = new HIterator<>(query.fetch())) {
      for (Account account : iterator) {
        if (!GLOBAL_ACCOUNT_ID.equals(account.getUuid())) {
          deleteByAccountId(account.getUuid());
          copyHarnessTemplatesToAccount(account.getUuid(), account.getAccountName());
        }
      }
    }
  }

  @Override
  public void createCommandLibraryGallery() {
    Query<Account> query = accountService.getBasicAccountQuery().limit(NO_LIMIT);
    try (HIterator<Account> iterator = new HIterator<>(query.fetch())) {
      for (Account account : iterator) {
        if (!GLOBAL_ACCOUNT_ID.equals(account.getUuid())) {
          saveHarnessCommandLibraryGalleryToAccount(account.getUuid(), account.getAccountName());
        }
      }
    }
  }

  @Override
  //  @Deprecated
  // Deleting gallery by name is not safe.
  public void deleteAccountGalleryByName(String accountId, String galleryName) {
    TemplateGallery accountGallery = get(accountId, galleryName);
    if (accountGallery != null) {
      deleteGalleryContents(accountId, accountGallery.getUuid());
      wingsPersistence.delete(TemplateGallery.class, accountGallery.getUuid());
    }
  }

  private void deleteGalleryContents(String accountId, String galleryId) {
    wingsPersistence.delete(wingsPersistence.createQuery(TemplateFolder.class)
                                .filter(TemplateFolder.GALLERY_ID_KEY, galleryId)
                                .filter(TemplateFolder.ACCOUNT_ID_KEY2, accountId));
    wingsPersistence.delete(wingsPersistence.createQuery(Template.class)
                                .filter(Template.GALLERY_ID_KEY, galleryId)
                                .filter(TemplateFolder.ACCOUNT_ID_KEY2, accountId));
    wingsPersistence.delete(wingsPersistence.createQuery(VersionedTemplate.class)
                                .filter(Template.GALLERY_ID_KEY, galleryId)
                                .filter(TemplateFolder.ACCOUNT_ID_KEY2, accountId));
    wingsPersistence.delete(wingsPersistence.createQuery(TemplateVersion.class)
                                .filter(Template.GALLERY_ID_KEY, galleryId)
                                .filter(TemplateFolder.ACCOUNT_ID_KEY2, accountId));
  }

  @Override
  public void saveHarnessCommandLibraryGalleryToAccount(String accountId, String accountName) {
    log.info("Creating command library gallery for the account {}", accountName);
    TemplateGallery templateGallery =
        save(TemplateGallery.builder()
                 .name(GalleryKey.HARNESS_COMMAND_LIBRARY_GALLERY.name()) /* Setting name as gallery key*/
                 .appId(GLOBAL_APP_ID)
                 .accountId(accountId)
                 .galleryKey(GalleryKey.HARNESS_COMMAND_LIBRARY_GALLERY.name())
                 .build());
    templateFolderService.createRootImportedTemplateFolder(accountId, templateGallery.getUuid());
    log.info(
        "Created command library gallery for account {} with galleryId {}", accountName, templateGallery.getUuid());
  }

  @Override
  public void copyHarnessTemplatesToAccount(String accountId, String accountName) {
    log.info("Copying Harness templates for the account {}", accountName);

    TemplateGallery harnessTemplateGallery = get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    if (harnessTemplateGallery == null) {
      log.info("Harness global gallery does not exist. Not copying templates");
      return;
    }
    log.info("Creating Account gallery");
    TemplateGallery accountGallery = save(TemplateGallery.builder()
                                              .name(accountName)
                                              .appId(GLOBAL_APP_ID)
                                              .accountId(accountId)
                                              .galleryKey(getAccountGalleryKey().name())
                                              .referencedGalleryId(harnessTemplateGallery.getUuid())
                                              .build());
    log.info("Creating Account gallery success with galleryId {}", accountGallery.getUuid());
    log.info("Copying harness template folders to account {}", accountName);
    templateFolderService.copyHarnessTemplateFolders(accountGallery.getUuid(), accountId, accountName);
    log.info("Copying harness template folders to account {} success", accountName);
    log.info("Copying default templates for account {}", accountName);
    templateService.loadDefaultTemplates(TemplateType.SSH, accountId, accountName);
    templateService.loadDefaultTemplates(TemplateType.HTTP, accountId, accountName);
    //    templateService.loadDefaultTemplates(TemplateType.SHELL_SCRIPT, accountId, accountName);
    log.info("Copying default templates for account {} success", accountName);
  }

  @Override
  public void copyHarnessTemplatesToAccountV2(String accountId, String accountName) {
    log.info("Copying Harness templates for the account {}", accountName);

    TemplateGallery harnessTemplateGallery = get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    if (harnessTemplateGallery == null) {
      log.info("Harness global gallery does not exist. Not copying templates");
      return;
    }
    log.info("Creating Account gallery");
    TemplateGallery accountGallery = save(TemplateGallery.builder()
                                              .name(accountName)
                                              .appId(GLOBAL_APP_ID)
                                              .accountId(accountId)
                                              .galleryKey(getAccountGalleryKey().name())
                                              .referencedGalleryId(harnessTemplateGallery.getUuid())
                                              .build());
    log.info("Creating Account gallery success");
    log.info("Copying harness template folders to account {}", accountName);
    templateFolderService.copyHarnessTemplateFolders(accountGallery.getUuid(), accountId, accountName);
    log.info("Copying harness template folders to account {} success", accountName);
    log.info("Copying default templates for account {}", accountName);
    //    templateService.loadDefaultTemplates(TemplateType.SSH, accountId, accountName);
    templateService.loadDefaultTemplates(TemplateType.HTTP, accountId, accountName);
    templateService.loadDefaultTemplates(
        asList(TOMCAT_WAR_STOP_PATH, TOMCAT_WAR_START_PATH, TOMCAT_WAR_INSTALL_PATH, GENERIC_JSON_PATH,
            POWER_SHELL_IIS_WEBSITE_V5_INSTALL_PATH, POWER_SHELL_IIS_APP_V5_INSTALL_PATH),
        accountId, accountName);
    templateGalleryService.copyHarnessTemplateFromGalleryToAccount(POWER_SHELL_COMMANDS, TemplateType.SSH,
        "Install IIS Application", POWER_SHELL_IIS_APP_V5_INSTALL_PATH, accountId, accountName,
        accountGallery.getUuid());
    templateGalleryService.copyHarnessTemplateFromGalleryToAccount(POWER_SHELL_COMMANDS, TemplateType.SSH,
        "Install IIS Website", POWER_SHELL_IIS_WEBSITE_V5_INSTALL_PATH, accountId, accountName,
        accountGallery.getUuid());
    log.info("Copying default templates for account {} success", accountName);
  }
  private Set<String> getKeywords(TemplateGallery templateGallery) {
    Set<String> generatedKeywords = trimmedLowercaseSet(templateGallery.generateKeywords());
    return TemplateHelper.addUserKeyWords(templateGallery.getKeywords(), generatedKeywords);
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(wingsPersistence.createQuery(TemplateGallery.class).filter(ACCOUNT_ID_KEY2, accountId));
    wingsPersistence.delete(
        wingsPersistence.createQuery(TemplateFolder.class).filter(TemplateFolder.ACCOUNT_ID_KEY2, accountId));
    wingsPersistence.delete(wingsPersistence.createQuery(Template.class).filter(Template.ACCOUNT_ID_KEY2, accountId));
    wingsPersistence.delete(
        wingsPersistence.createQuery(VersionedTemplate.class).filter(VersionedTemplate.ACCOUNT_ID_KEY2, accountId));
    wingsPersistence.delete(
        wingsPersistence.createQuery(TemplateVersion.class).filter(TemplateVersion.ACCOUNT_ID_KEY2, accountId));
  }

  @Override
  public void copyHarnessTemplateFromGalleryToAccounts(
      String sourceFolderPath, TemplateType templateType, String templateName, String yamlFilePath) {
    log.info("Copying Harness template [{}] from global account to all accounts", templateName);

    TemplateGallery harnessTemplateGallery = get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    if (harnessTemplateGallery == null) {
      log.info("Harness global gallery does not exist. Not copying templates");
      return;
    }

    List<Account> accounts = accountService.getAccountsWithBasicInfo(false);
    for (Account account : accounts) {
      try {
        log.info("Copying template [{}] started for account [{}]", templateName, account.getUuid());
        TemplateGallery templateGallery = wingsPersistence.createQuery(TemplateGallery.class)
                                              .filter(GALLERY_KEY, GalleryKey.ACCOUNT_TEMPLATE_GALLERY)
                                              .field("accountId")
                                              .equal(account.getUuid())
                                              .get();
        if (templateGallery != null) {
          if (!GLOBAL_ACCOUNT_ID.equals(account.getUuid())) {
            TemplateFolder destTemplateFolder = templateFolderService.getByFolderPath(
                account.getUuid(), account.getAccountName() + "/" + sourceFolderPath, templateGallery.getUuid());
            if (destTemplateFolder != null) {
              templateService.loadYaml(templateType, yamlFilePath, account.getUuid(), account.getAccountName());
              log.info("Template copied to account [{}]", account.getUuid());
            }
          }
        } else {
          log.info("Template gallery does not exist for account [{}]. Do nothing", account.getUuid());
        }
      } catch (Exception ex) {
        log.error("Copy Harness template failed for account [{}]", account.getUuid(), ex);
      }
    }
  }

  @Override
  public void copyHarnessTemplateFromGalleryToAccount(String sourceFolderPath, TemplateType templateType,
      String templateName, String yamlFilePath, String accountId, String accountName, String galleryId) {
    log.info("Copying Harness template [{}] from global account to all accounts", templateName);

    TemplateGallery harnessTemplateGallery = get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    if (harnessTemplateGallery == null) {
      log.info("Harness global gallery does not exist. Not copying templates");
      return;
    }

    try {
      log.info("Copying template [{}] started for account [{}]", templateName, accountId);
      TemplateGallery templateGallery = wingsPersistence.createQuery(TemplateGallery.class)
                                            .filter(GALLERY_KEY, GalleryKey.ACCOUNT_TEMPLATE_GALLERY)
                                            .field(ACCOUNT_ID_KEY2)
                                            .equal(accountId)
                                            .get();
      if (templateGallery != null) {
        if (!GLOBAL_ACCOUNT_ID.equals(accountId)) {
          TemplateFolder destTemplateFolder = templateFolderService.getByFolderPath(
              accountId, accountId + "/" + sourceFolderPath, templateGallery.getUuid());
          if (destTemplateFolder != null) {
            templateService.loadYaml(templateType, yamlFilePath, accountId, accountName);
          }
          log.info("Template copied to account [{}]", accountId);
        }
      } else {
        log.info("Template gallery does not exist for account [{}]. Do nothing", accountId);
      }
    } catch (Exception ex) {
      log.error("Copy Harness template failed for account [{}]", accountId, ex);
    }
  }

  @Override
  public void copyNewVersionFromGlobalToAllAccounts(Template globalTemplate, String keyword) {
    // Note: Only updating active accounts. This is mainly done for freemium cluster.
    // There are many expired accounts in freemium which is slowing down migration
    List<Account> accounts = accountService.listAllActiveAccounts();
    for (Account account : accounts) {
      if (!GLOBAL_ACCOUNT_ID.equals(account.getUuid())) {
        Template existingTemplate = templateService.fetchTemplateByKeywordForAccountGallery(account.getUuid(), keyword);
        if (existingTemplate != null) {
          existingTemplate.setReferencedTemplateVersion(globalTemplate.getVersion());
          existingTemplate.setReferencedTemplateId(globalTemplate.getUuid());
          existingTemplate.setTemplateObject(globalTemplate.getTemplateObject());
          existingTemplate.setVariables(globalTemplate.getVariables());
          log.info("Updating template in account [{}]", account.getUuid());
          templateService.update(existingTemplate);
          log.info("Template updated in account [{}]", account.getUuid());
        } else {
          log.info("Template gallery does not exist for account id: [{}] and name:[{}] . Do nothing", account.getUuid(),
              account.getAccountName());
        }
      }
    }
  }

  @Override
  public void copyNewFolderAndTemplatesFromGlobalToAccounts(
      String sourceFolderPath, TemplateType templateType, List<String> yamlFilePaths) {
    log.info("Trying to copy new folder and templates from global account to all accounts");

    TemplateGallery harnessTemplateGallery = get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    if (harnessTemplateGallery == null) {
      log.info("Harness global gallery does not exist. Not copying templates");
      return;
    }

    List<Account> accounts = accountService.getAccountsWithBasicInfo(false);
    for (Account account : accounts) {
      try {
        log.info("Copying templates started for account [{}]", account.getUuid());
        TemplateGallery templateGallery = get(account.getUuid(), account.getAccountName());
        if (templateGallery != null) {
          TemplateFolder templateFolder =
              templateFolderService.getRootLevelFolder(account.getUuid(), templateGallery.getUuid());
          if (templateFolder != null) {
            TemplateFolder newTemplateFolder = constructTemplateBuilder(templateFolder, sourceFolderPath);
            TemplateFolder destTemplateFolder =
                templateFolderService.save(newTemplateFolder, templateGallery.getUuid());
            if (destTemplateFolder != null) {
              for (String path : yamlFilePaths) {
                templateService.loadYaml(templateType, path, account.getUuid(), account.getAccountName());
              }
            } else {
              log.info("Folder [{}] could not be added to account. Not copying templates", sourceFolderPath);
            }
          } else {
            log.info("Parent Template folder does not exist for account [{}]. Cannot proceed with new Folder creation",
                account.getUuid());
          }
        } else {
          log.info("Template gallery does not exist for account [{}]. Do nothing.", account.getUuid());
        }
      } catch (Exception ex) {
        log.error("Copy Harness template failed for account [{}]", account.getUuid(), ex);
      }
    }
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
}
