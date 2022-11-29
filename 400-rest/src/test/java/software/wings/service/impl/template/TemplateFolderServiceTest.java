/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.template.TemplateType.SSH;
import static software.wings.common.TemplateConstants.HARNESS_GALLERY;
import static software.wings.common.TemplateConstants.JBOSS_COMMANDS;
import static software.wings.common.TemplateConstants.LOAD_BALANCERS;
import static software.wings.common.TemplateConstants.PATH_DELIMITER;
import static software.wings.common.TemplateConstants.TOMCAT_COMMANDS;
import static software.wings.common.TemplateConstants.TOMCAT_WAR_INSTALL_PATH;
import static software.wings.utils.TemplateTestConstants.GLOBAL_FOLDER;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_DESC_CHANGED;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_FOLDER_DEC;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_FOLDER_NAME;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_FOLDER_NAME_2;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_GALLERY;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_GALLERY_DESC;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.INVALID_NAME;
import static software.wings.utils.WingsTestConstants.TEMPLATE_NAME;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.beans.EntityType;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.common.TemplateConstants;
import software.wings.utils.WingsTestConstants;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.Arrays;
import javax.validation.ConstraintViolationException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TemplateFolderServiceTest extends TemplateBaseTestHelper {
  @Inject private HPersistence persistence;

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotDeleteRootFolder() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    templateFolderService.delete(parentFolder.getUuid());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSaveTemplateFolder() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid()), templateGallery.getUuid());

    assertThat(myTemplateFolder).isNotNull();
    assertThat(myTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(myTemplateFolder.getAppId()).isEqualTo(GLOBAL_APP_ID);
    assertThat(myTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_NAME.toLowerCase());
    assertThat(myTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_DEC.toLowerCase());
    assertThat(myTemplateFolder.getPathId()).isNotEmpty();
    assertThat(myTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());
  }

  @Test(expected = ConstraintViolationException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotSaveInvalidNameTemplateFolder() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder templateFolder = constructTemplateBuilder(parentFolder.getUuid());
    templateFolder.setName(WingsTestConstants.INVALID_NAME);
    TemplateFolder myTemplateFolder = templateFolderService.save(templateFolder, templateGallery.getUuid());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTemplateFolder() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid()), templateGallery.getUuid());

    assertThat(myTemplateFolder).isNotNull();
    assertThat(myTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(myTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_NAME.toLowerCase());
    assertThat(myTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_DEC.toLowerCase());
    assertThat(myTemplateFolder.getPathId()).isNotEmpty();
    assertThat(myTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());

    TemplateFolder savedTemplateFolder = templateFolderService.get(myTemplateFolder.getUuid());
    assertThat(savedTemplateFolder).isNotNull();
    assertThat(savedTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(savedTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_NAME.toLowerCase());
    assertThat(savedTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_DEC.toLowerCase());
    assertThat(savedTemplateFolder.getPathId()).isNotEmpty();
    assertThat(savedTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteTemplateFolder() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid()), templateGallery.getUuid());

    assertThat(myTemplateFolder).isNotNull();
    assertThat(myTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(myTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_NAME.toLowerCase());
    assertThat(myTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_DEC.toLowerCase());
    assertThat(myTemplateFolder.getPathId()).isNotEmpty();
    assertThat(myTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());

    assertThat(templateFolderService.delete(myTemplateFolder.getUuid())).isTrue();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void shouldDeleteTemplateFolderByAccountId() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid()), templateGallery.getUuid());
    TemplateFolder templateFolder = templateFolderService.get(myTemplateFolder.getUuid());
    assertThat(templateFolder.getAccountId()).isEqualTo(GLOBAL_ACCOUNT_ID);
    templateFolderServiceImpl.deleteByAccountId(GLOBAL_ACCOUNT_ID);
    TemplateFolder templateFolder1 = templateFolderService.get(myTemplateFolder.getUuid());
    assertThat(templateFolder1).isNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateTemplateFolder() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid()), templateGallery.getUuid());

    assertThat(myTemplateFolder).isNotNull();
    assertThat(myTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(myTemplateFolder.getGalleryId()).isNotEmpty();
    assertThat(myTemplateFolder.getPathId()).isNotEmpty();
    assertThat(myTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());

    myTemplateFolder.setDescription(TEMPLATE_DESC_CHANGED);
    TemplateFolder updatedTemplateFolder = templateFolderService.update(myTemplateFolder);

    assertThat(updatedTemplateFolder).isNotNull();
    assertThat(updatedTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(updatedTemplateFolder.getKeywords()).contains(TEMPLATE_DESC_CHANGED.toLowerCase());
    assertThat(updatedTemplateFolder.getPathId()).isNotEmpty();
    assertThat(updatedTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());
  }

  @Test(expected = ConstraintViolationException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotUpdateWithInvalidNameTemplateFolder() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid()), templateGallery.getUuid());

    assertThat(myTemplateFolder).isNotNull();
    assertThat(myTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);

    myTemplateFolder.setDescription(TEMPLATE_DESC_CHANGED);
    myTemplateFolder.setName(INVALID_NAME);

    templateFolderService.update(myTemplateFolder);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldLoadTemplateFolders() {
    String galleryId =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder = templateFolderService.getTemplateTree(GLOBAL_ACCOUNT_ID, null, null, galleryId);
    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getChildren()).isNotEmpty();
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(HARNESS_GALLERY);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetGlobalTemplateTree() {
    String galleryId =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder = templateFolderService.getTemplateTree(GLOBAL_ACCOUNT_ID, null, null, galleryId);
    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(HARNESS_GALLERY);
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(templateFolder1 -> templateFolder1.getName().contains(TOMCAT_COMMANDS));
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(templateFolder1 -> templateFolder1.getName().contains(JBOSS_COMMANDS));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetGlobalTemplateRootTree() {
    String galleryId =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder =
        templateFolderService.getTemplateTree(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, null, galleryId);
    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getChildren()).isNotEmpty();
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(HARNESS_GALLERY);
    assertThat(templateFolder.getChildren()).isNotEmpty().extracting(TemplateFolder::getName).contains(TOMCAT_COMMANDS);
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(TemplateFolder::getName)
        .doesNotContain(JBOSS_COMMANDS);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetGlobalTemplateTreeByKeyword() {
    String galleryId =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder =
        templateFolderService.getTemplateTree(GLOBAL_ACCOUNT_ID, TOMCAT_COMMANDS, null, galleryId);
    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getChildren()).isNotEmpty();
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(HARNESS_GALLERY);
    assertThat(templateFolder.getChildren()).isNotEmpty();

    assertThat(templateFolder.getChildren()).extracting(TemplateFolder::getName).contains(TOMCAT_COMMANDS);
    assertThat(templateFolder.getChildren()).extracting(TemplateFolder::getName).doesNotContain(LOAD_BALANCERS);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldLoadDefaultCommandTemplates() {
    templateService.loadDefaultTemplates(SSH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    String galleryId =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder =
        templateFolderService.getTemplateTree(GLOBAL_ACCOUNT_ID, null, Arrays.asList(SSH.name()), galleryId);
    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getChildren()).isNotEmpty();
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(HARNESS_GALLERY);
    assertThat(templateFolder.getChildren()).isNotEmpty();

    assertThat(templateFolder.getChildren()).extracting(TemplateFolder::getName).contains(TOMCAT_COMMANDS);
    assertThat(templateFolder.getChildren()).extracting(TemplateFolder::getName).doesNotContain(LOAD_BALANCERS);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetGlobalTemplateTreeByKeywordAndTypes() {
    templateService.loadDefaultTemplates(SSH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    String galleryId =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder =
        templateFolderService.getTemplateTree(GLOBAL_ACCOUNT_ID, "Install", Arrays.asList(SSH.name()), galleryId);
    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getAccountId()).isNotNull().isEqualTo(GLOBAL_ACCOUNT_ID);
    assertThat(templateFolder.getChildren()).isNotEmpty();
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(HARNESS_GALLERY);
    assertThat(templateFolder.getChildren()).isNotEmpty();

    assertThat(templateFolder.getChildren()).extracting(TemplateFolder::getName).contains(TOMCAT_COMMANDS);
    assertThat(templateFolder.getChildren()).extracting(TemplateFolder::getName).doesNotContain(LOAD_BALANCERS);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetGlobalTemplateTreeByKeywordAndTypesNotMatching() {
    templateService.loadDefaultTemplates(SSH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    String galleryId =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder =
        templateFolderService.getTemplateTree(GLOBAL_ACCOUNT_ID, "Install3", Arrays.asList(SSH.name()), galleryId);
    assertThat(templateFolder).isNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCopyHarnessTemplateFolders() {
    TemplateGallery templateGallery = templateGalleryService.get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    templateService.loadDefaultTemplates(SSH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);

    templateFolderService.copyHarnessTemplateFolders(templateGallery.getUuid(), ACCOUNT_ID, HARNESS_GALLERY);
    String galleryId =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder = templateFolderService.getTemplateTree(ACCOUNT_ID, null, null, galleryId);

    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getAccountId()).isNotNull().isEqualTo(ACCOUNT_ID);
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(HARNESS_GALLERY);
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(templateFolder1 -> templateFolder1.getName().contains(TOMCAT_COMMANDS));
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(templateFolder1 -> templateFolder1.getName().contains(LOAD_BALANCERS));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetAccountTemplateTree() {
    TemplateGallery templateGallery = templateGalleryService.get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    templateService.loadDefaultTemplates(SSH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    templateFolderService.copyHarnessTemplateFolders(templateGallery.getUuid(), ACCOUNT_ID, HARNESS_GALLERY);
    String galleryId =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder = templateFolderService.getTemplateTree(ACCOUNT_ID, null, null, galleryId);
    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getAccountId()).isNotNull().isEqualTo(ACCOUNT_ID);
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(HARNESS_GALLERY);
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(templateFolder1 -> templateFolder1.getName().contains(TOMCAT_COMMANDS));
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(templateFolder1 -> templateFolder1.getName().contains(LOAD_BALANCERS));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldLoadTomcatStandardInstallCommand() {
    TemplateGallery accountGallery =
        templateGalleryService.save(TemplateGallery.builder()
                                        .name(TEMPLATE_GALLERY)
                                        .accountId(ACCOUNT_ID)
                                        .description(TEMPLATE_GALLERY_DESC)
                                        .appId(GLOBAL_APP_ID)
                                        .galleryKey(templateGalleryService.getAccountGalleryKey().name())
                                        .keywords(ImmutableSet.of("CD"))
                                        .build());
    templateService.loadDefaultTemplates(SSH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    templateFolderService.copyHarnessTemplateFolders(accountGallery.getUuid(), ACCOUNT_ID, TEMPLATE_GALLERY);
    templateService.loadYaml(SSH, TOMCAT_WAR_INSTALL_PATH, ACCOUNT_ID, TEMPLATE_GALLERY);
    String galleryId = templateGalleryService.getByAccount(ACCOUNT_ID, accountGallery.getUuid()).getUuid();
    TemplateFolder templateFolder =
        templateFolderService.getTemplateTree(ACCOUNT_ID, "Install", Arrays.asList(SSH.name()), galleryId);
    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getAccountId()).isNotNull().isEqualTo(ACCOUNT_ID);
    assertThat(templateFolder.getChildren()).isNotEmpty();
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(TEMPLATE_GALLERY);
    assertThat(templateFolder.getChildren()).isNotEmpty();

    assertThat(templateFolder.getChildren()).extracting(TemplateFolder::getName).contains(TOMCAT_COMMANDS);

    assertThat(templateFolder.getChildren()).extracting(TemplateFolder::getName).doesNotContain(LOAD_BALANCERS);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetRootFolder() {
    TemplateGallery templateGallery = templateGalleryService.get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    templateService.loadDefaultTemplates(SSH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    templateFolderService.copyHarnessTemplateFolders(templateGallery.getUuid(), ACCOUNT_ID, HARNESS_GALLERY);

    TemplateFolder templateFolder =
        templateFolderService.getByFolderPath(ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());

    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getName()).isEqualTo(HARNESS_GALLERY);
    assertThat(templateFolder.getPathId()).isNull();
    assertThat(templateFolder.getKeywords()).contains(HARNESS_GALLERY.toLowerCase());
    assertThat(templateFolder.getAccountId()).isNotNull().isEqualTo(ACCOUNT_ID);
    assertThat(templateFolder.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetFolderByPath() {
    TemplateGallery templateGallery = templateGalleryService.get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    templateService.loadDefaultTemplates(SSH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    templateFolderService.copyHarnessTemplateFolders(templateGallery.getUuid(), ACCOUNT_ID, HARNESS_GALLERY);
    TemplateFolder templateFolder = templateFolderService.getByFolderPath(
        ACCOUNT_ID, HARNESS_GALLERY + "/" + TOMCAT_COMMANDS, templateGallery.getUuid());

    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getName()).isEqualTo(TOMCAT_COMMANDS);
    assertThat(templateFolder.getKeywords()).contains(TOMCAT_COMMANDS.toLowerCase());
    assertThat(templateFolder.getAccountId()).isNotNull().isEqualTo(ACCOUNT_ID);
    assertThat(templateFolder.getPathId()).isNotNull();

    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    assertThat(parentFolder.getName()).isEqualTo(HARNESS_GALLERY);
    assertThat(parentFolder.getPathId()).isNull();
    assertThat(templateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());
  }

  private TemplateFolder constructTemplateBuilder(String parentId) {
    return constructTemplateBuilder(parentId, GLOBAL_APP_ID);
  }

  private TemplateFolder constructTemplateBuilder(String parentId, String appId) {
    return TemplateFolder.builder()
        .name(TEMPLATE_FOLDER_NAME)
        .description(TEMPLATE_FOLDER_DEC)
        .parentId(parentId)
        .appId(appId)
        .accountId(GLOBAL_ACCOUNT_ID)
        .build();
  }

  private TemplateFolder constructTemplateBuilder(String folderName, String parentId, String appId) {
    return TemplateFolder.builder()
        .name(folderName)
        .description(TEMPLATE_FOLDER_DEC)
        .parentId(parentId)
        .appId(appId)
        .accountId(GLOBAL_ACCOUNT_ID)
        .build();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldSaveTemplateFolderAtApplicationLevel() {
    TemplateFolder myTemplateFolder = createAppLevelTemplateFolder();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldSaveTemplateFoldersWithSameNameAccountAndApplicationLevel() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myAppTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid(), APP_ID), templateGallery.getUuid());

    assertThat(myAppTemplateFolder).isNotNull();
    assertThat(myAppTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(myAppTemplateFolder.getAppId()).isEqualTo(APP_ID);
    assertThat(myAppTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_NAME.toLowerCase());
    assertThat(myAppTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_DEC.toLowerCase());
    assertThat(myAppTemplateFolder.getPathId()).isNotEmpty();
    assertThat(myAppTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());

    TemplateFolder myAccountTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid()), templateGallery.getUuid());

    assertThat(myAccountTemplateFolder).isNotNull();
    assertThat(myAccountTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(myAccountTemplateFolder.getAppId()).isEqualTo(GLOBAL_APP_ID);
    assertThat(myAccountTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_NAME.toLowerCase());
    assertThat(myAccountTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_DEC.toLowerCase());
    assertThat(myAccountTemplateFolder.getPathId()).isNotEmpty();
    assertThat(myAccountTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());
  }

  @Test(expected = WingsException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldNotSaveTemplateFoldersWithSameNameApplicationLevel() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myAppTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid(), APP_ID), templateGallery.getUuid());

    assertThat(myAppTemplateFolder).isNotNull();
    assertThat(myAppTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(myAppTemplateFolder.getAppId()).isEqualTo(APP_ID);
    assertThat(myAppTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_NAME.toLowerCase());
    assertThat(myAppTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_DEC.toLowerCase());
    assertThat(myAppTemplateFolder.getPathId()).isNotEmpty();
    assertThat(myAppTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());

    templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid(), APP_ID), templateGallery.getUuid());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateTemplateFolderAtApplicationLevel() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid(), APP_ID), templateGallery.getUuid());

    assertThat(myTemplateFolder).isNotNull();
    assertThat(myTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(myTemplateFolder.getAppId()).isEqualTo(APP_ID);
    assertThat(myTemplateFolder.getGalleryId()).isNotEmpty();
    assertThat(myTemplateFolder.getPathId()).isNotEmpty();
    assertThat(myTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());

    myTemplateFolder.setDescription(TEMPLATE_DESC_CHANGED);
    TemplateFolder updatedTemplateFolder = templateFolderService.update(myTemplateFolder);

    assertThat(updatedTemplateFolder).isNotNull();
    assertThat(updatedTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(myTemplateFolder.getAppId()).isEqualTo(APP_ID);
    assertThat(updatedTemplateFolder.getKeywords()).contains(TEMPLATE_DESC_CHANGED.toLowerCase());
    assertThat(updatedTemplateFolder.getPathId()).isNotEmpty();
    assertThat(updatedTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteTemplateFolderApplicationLevel() {
    TemplateFolder myTemplateFolder = createAppLevelTemplateFolder();

    assertThat(templateFolderService.delete(myTemplateFolder.getUuid())).isTrue();
  }

  private TemplateFolder createAppLevelTemplateFolder() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid(), APP_ID), templateGallery.getUuid());

    assertThat(myTemplateFolder).isNotNull();
    assertThat(myTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(myTemplateFolder.getAppId()).isEqualTo(APP_ID);
    assertThat(myTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_NAME.toLowerCase());
    assertThat(myTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_DEC.toLowerCase());
    assertThat(myTemplateFolder.getPathId()).isNotEmpty();
    assertThat(myTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());
    return myTemplateFolder;
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTemplateFolderForApplication() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myTemplateFolder = createAppLevelTemplateFolder();
    TemplateFolder savedTemplateFolder = templateFolderService.get(myTemplateFolder.getUuid());
    assertThat(savedTemplateFolder).isNotNull();
    assertThat(savedTemplateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(myTemplateFolder.getAppId()).isEqualTo(APP_ID);
    assertThat(savedTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_NAME.toLowerCase());
    assertThat(savedTemplateFolder.getKeywords()).contains(TEMPLATE_FOLDER_DEC.toLowerCase());
    assertThat(savedTemplateFolder.getPathId()).isNotEmpty();
    assertThat(savedTemplateFolder.getPathId().split("/")[0]).isEqualTo(parentFolder.getUuid());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotMoveFolderFromApplicationToAccount() {
    TemplateFolder myTemplateFolder = createAppLevelTemplateFolder();
    myTemplateFolder.setAppId(GLOBAL_APP_ID);
    templateFolderService.update(myTemplateFolder);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetAccountTemplateTreeUsingGlobalAppId() {
    TemplateGallery templateGallery = templateGalleryService.get(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    templateService.loadDefaultTemplates(SSH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    templateFolderService.copyHarnessTemplateFolders(templateGallery.getUuid(), ACCOUNT_ID, HARNESS_GALLERY);
    String galleryId = templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGallery.getUuid()).getUuid();
    TemplateFolder templateFolder =
        templateFolderService.getTemplateTree(ACCOUNT_ID, GLOBAL_APP_ID, null, null, galleryId);
    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getAccountId()).isNotNull().isEqualTo(ACCOUNT_ID);
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(HARNESS_GALLERY);
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(templateFolder1 -> templateFolder1.getName().contains(TOMCAT_COMMANDS));
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(templateFolder1 -> templateFolder1.getName().contains(LOAD_BALANCERS));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetAppTemplateTree() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    templateFolderService.save(
        constructTemplateBuilder(GLOBAL_FOLDER, parentFolder.getUuid(), GLOBAL_APP_ID), templateGallery.getUuid());
    templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid(), APP_ID), templateGallery.getUuid());
    templateFolderService.save(
        constructTemplateBuilder(TEMPLATE_FOLDER_NAME_2, parentFolder.getUuid(), APP_ID), templateGallery.getUuid());
    String galleryId =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder =
        templateFolderService.getTemplateTree(GLOBAL_ACCOUNT_ID, APP_ID, null, null, galleryId);
    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getAccountId()).isNotNull().isEqualTo(GLOBAL_ACCOUNT_ID);
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(HARNESS_GALLERY);
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(templateFolder1 -> templateFolder1.getName().contains(TEMPLATE_FOLDER_NAME));
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(templateFolder1 -> templateFolder1.getName().contains(TEMPLATE_FOLDER_NAME_2));
    assertThat(templateFolder.getChildren())
        .isNotEmpty()
        .extracting(TemplateFolder::getName)
        .doesNotContain(GLOBAL_FOLDER);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetAppTemplateTreeByKeyword() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    templateFolderService.save(
        constructTemplateBuilder(GLOBAL_FOLDER, parentFolder.getUuid(), GLOBAL_APP_ID), templateGallery.getUuid());
    templateFolderService.save(TemplateFolder.builder()
                                   .name(TEMPLATE_FOLDER_NAME)
                                   .description(TEMPLATE_DESC_CHANGED)
                                   .parentId(parentFolder.getUuid())
                                   .appId(APP_ID)
                                   .accountId(GLOBAL_ACCOUNT_ID)
                                   .build(),
        templateGallery.getUuid());
    templateFolderService.save(
        constructTemplateBuilder(TEMPLATE_FOLDER_NAME_2, parentFolder.getUuid(), APP_ID), templateGallery.getUuid());
    String galleryId =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey()).getUuid();
    TemplateFolder templateFolder = templateFolderService.getTemplateTree(GLOBAL_ACCOUNT_ID, "super", null, galleryId);
    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getChildren()).isNotEmpty();
    assertThat(templateFolder).extracting(TemplateFolder::getName).isEqualTo(HARNESS_GALLERY);
    assertThat(templateFolder.getChildren().size()).isEqualTo(1);
    assertThat(templateFolder.getChildren().get(0)).extracting(TemplateFolder::getName).isEqualTo(TEMPLATE_FOLDER_NAME);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetAppLevelFolderByPath() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder accountLevelFolder = templateFolderService.save(
        constructTemplateBuilder(parentFolder.getUuid(), GLOBAL_APP_ID), templateGallery.getUuid());
    TemplateFolder appLevelFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid(), APP_ID), templateGallery.getUuid());
    templateFolderService.save(
        constructTemplateBuilder(TEMPLATE_FOLDER_NAME_2, parentFolder.getUuid(), APP_ID), templateGallery.getUuid());
    TemplateFolder templateFolder = templateFolderService.getByFolderPath(
        GLOBAL_ACCOUNT_ID, HARNESS_GALLERY + "/" + TEMPLATE_FOLDER_NAME, templateGallery.getUuid());

    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(templateFolder.getKeywords()).contains(TEMPLATE_FOLDER_NAME.toLowerCase());
    assertThat(templateFolder.getAccountId()).isNotNull().isEqualTo(GLOBAL_ACCOUNT_ID);
    assertThat(templateFolder.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(templateFolder.getPathId()).isNotNull();
    assertThat(templateFolder.getUuid()).isEqualTo(accountLevelFolder.getUuid());

    templateFolder = templateFolderService.getByFolderPath(
        GLOBAL_ACCOUNT_ID, APP_ID, HARNESS_GALLERY + "/" + TEMPLATE_FOLDER_NAME, templateGallery.getUuid());

    assertThat(templateFolder).isNotNull();
    assertThat(templateFolder.getName()).isEqualTo(TEMPLATE_FOLDER_NAME);
    assertThat(templateFolder.getKeywords()).contains(TEMPLATE_FOLDER_NAME.toLowerCase());
    assertThat(templateFolder.getAccountId()).isNotNull().isEqualTo(GLOBAL_ACCOUNT_ID);
    assertThat(templateFolder.getAppId()).isNotNull().isEqualTo(APP_ID);
    assertThat(templateFolder.getPathId()).isNotNull();
    assertThat(templateFolder.getUuid()).isEqualTo(appLevelFolder.getUuid());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shouldDeleteTemplateFolderAndGenerateYamlPush() {
    YamlGitConfig yamlGitConfig = YamlGitConfig.builder()
                                      .entityType(EntityType.ACCOUNT)
                                      .entityId(GLOBAL_ACCOUNT_ID)
                                      .accountId(GLOBAL_ACCOUNT_ID)
                                      .enabled(true)
                                      .syncMode(YamlGitConfig.SyncMode.BOTH)
                                      .build();
    persistence.save(yamlGitConfig);
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    TemplateFolder myTemplateFolder =
        templateFolderService.save(constructTemplateBuilder(parentFolder.getUuid()), templateGallery.getUuid());
    TemplateFolder childFolder = templateFolderService.save(
        constructTemplateBuilder("Child Folder", myTemplateFolder.getUuid(), myTemplateFolder.getAppId()),
        templateGallery.getUuid());
    templateService.save(Template.builder()
                             .folderId(childFolder.getUuid())
                             .accountId(GLOBAL_ACCOUNT_ID)
                             .appId(GLOBAL_APP_ID)
                             .type(TemplateConstants.SSH)
                             .templateObject(SshCommandTemplate.builder().build())
                             .version(1)
                             .name(TEMPLATE_NAME)
                             .build());
    templateFolderService.delete(childFolder.getUuid());
    assertThat(persistence.createQuery(YamlChangeSet.class).get().getGitFileChanges().get(0).getFilePath())
        .isEqualTo("Setup/Template Library/" + parentFolder.getName() + PATH_DELIMITER + myTemplateFolder.getName()
            + PATH_DELIMITER + childFolder.getName() + PATH_DELIMITER + TEMPLATE_NAME + ".yaml");
  }
}
