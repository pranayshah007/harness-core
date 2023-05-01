/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.persistence.HQuery.excludeAuthorityCount;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.shell.ScriptType.POWERSHELL;

import static software.wings.beans.Account.AccountKeys;
import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.Application.ApplicationKeys;
import static software.wings.beans.Base.ID_KEY2;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.command.CommandType.INSTALL;
import static software.wings.beans.command.CommandUnitType.DOWNLOAD_ARTIFACT;
import static software.wings.beans.command.DownloadArtifactCommandUnit.Builder.aDownloadArtifactCommandUnit;
import static software.wings.beans.template.TemplateGallery.GalleryKey;
import static software.wings.common.TemplateConstants.HARNESS_GALLERY;
import static software.wings.common.TemplateConstants.POWER_SHELL_COMMANDS;
import static software.wings.common.TemplateConstants.POWER_SHELL_IIS_APP_V2_INSTALL_PATH;
import static software.wings.common.TemplateConstants.POWER_SHELL_IIS_WEBSITE_INSTALL_PATH;
import static software.wings.common.TemplateConstants.POWER_SHELL_IIS_WEBSITE_V2_INSTALL_PATH;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_CUSTOM_KEYWORD;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_DESC;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_GALLERY;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_GALLERY_DESC;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_GALLERY_DESC_CHANGED;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_GALLERY_IMPORTED;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.COMPANY_NAME;
import static software.wings.utils.WingsTestConstants.INVALID_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.template.TemplateFolderService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolationException;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class TemplateGalleryServiceTest extends WingsBaseTest {
  @Inject @InjectMocks protected TemplateGalleryService templateGalleryService;
  @Inject private TemplateService templateService;
  @Inject private TemplateFolderService templateFolderService;
  @Inject private HPersistence persistence;

  @Mock private AccountService accountService;

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSaveTemplateGallery() {
    TemplateGallery savedTemplateGallery = templateGalleryService.save(prepareTemplateGallery());
    assertTemplateGallery(savedTemplateGallery);
  }

  @Test(expected = ConstraintViolationException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("Enable when validations on accountName are in place(https://harness.atlassian.net/browse/CD-3700)")
  public void shouldNotSaveInvalidNameTemplateGallery() {
    TemplateGallery templateGallery = prepareTemplateGallery();
    templateGallery.setName(INVALID_NAME);
    templateGalleryService.save(templateGallery);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateTemplateGallery() {
    TemplateGallery savedTemplateGallery = templateGalleryService.save(prepareTemplateGallery());

    assertTemplateGallery(savedTemplateGallery);

    savedTemplateGallery.getKeywords().add("CV");
    savedTemplateGallery.setDescription(TEMPLATE_GALLERY_DESC_CHANGED);

    TemplateGallery updatedTemplateGallery = templateGalleryService.update(savedTemplateGallery);

    assertThat(updatedTemplateGallery).isNotNull();
    assertThat(updatedTemplateGallery.getKeywords()).contains("cv");
    assertThat(updatedTemplateGallery.getKeywords())
        .contains(TEMPLATE_GALLERY.trim().toLowerCase(), TEMPLATE_GALLERY_DESC_CHANGED.trim().toLowerCase());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteTemplateGallery() {
    TemplateGallery savedTemplateGallery = templateGalleryService.save(prepareTemplateGallery());
    assertTemplateGallery(savedTemplateGallery);

    templateGalleryService.delete(savedTemplateGallery.getUuid());

    TemplateGallery deletedGallery = templateGalleryService.get(savedTemplateGallery.getUuid());
    assertThat(deletedGallery).isNull();
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateTemplateGalleryNotExists() {
    TemplateGallery savedTemplateGallery = templateGalleryService.save(prepareTemplateGallery());

    assertTemplateGallery(savedTemplateGallery);

    templateGalleryService.delete(savedTemplateGallery.getUuid());

    templateGalleryService.update(savedTemplateGallery);
  }

  @Test(expected = ConstraintViolationException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  @Ignore("Enable when validations on accountName are in place(https://harness.atlassian.net/browse/CD-3700)")
  public void shouldNotUpdateInvalidTemplateGallery() {
    TemplateGallery savedTemplateGallery = templateGalleryService.save(prepareTemplateGallery());

    assertTemplateGallery(savedTemplateGallery);

    savedTemplateGallery.setName(INVALID_NAME);

    templateGalleryService.update(savedTemplateGallery);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTemplateGallery() {
    TemplateGallery savedTemplateGallery = templateGalleryService.save(prepareTemplateGallery());
    assertThat(savedTemplateGallery).isNotNull();
    assertThat(savedTemplateGallery.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);

    TemplateGallery templateGallery = templateGalleryService.get(savedTemplateGallery.getUuid());
    assertTemplateGallery(templateGallery);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTemplateGalleryByAccount() {
    TemplateGallery savedTemplateGallery = templateGalleryService.save(prepareTemplateGallery());
    assertThat(savedTemplateGallery).isNotNull();
    assertThat(savedTemplateGallery.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);

    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(savedTemplateGallery.getAccountId(), savedTemplateGallery.getUuid());
    assertTemplateGallery(templateGallery);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shouldGetImportedTemplateGalleryByAccount() {
    TemplateGallery savedTemplateGallery = templateGalleryService.save(prepareImportedTemplateGallery());
    assertThat(savedTemplateGallery).isNotNull();
    assertThat(savedTemplateGallery.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);

    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(savedTemplateGallery.getAccountId(), savedTemplateGallery.getUuid());
    assertThat(templateGallery).isEqualTo(savedTemplateGallery);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTemplateGalleryByName() {
    TemplateGallery savedTemplateGallery = templateGalleryService.save(prepareTemplateGallery());
    assertThat(savedTemplateGallery).isNotNull();
    assertThat(savedTemplateGallery.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);

    TemplateGallery templateGallery =
        templateGalleryService.get(savedTemplateGallery.getAccountId(), savedTemplateGallery.getName());

    assertTemplateGallery(templateGallery);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListTemplateGalleries() {
    TemplateGallery savedTemplateGallery = templateGalleryService.save(prepareTemplateGallery());
    assertThat(savedTemplateGallery).isNotNull();
    assertThat(savedTemplateGallery.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);

    PageRequest<TemplateGallery> pageRequest =
        aPageRequest().addFilter("appId", SearchFilter.Operator.EQ, GLOBAL_APP_ID).build();
    List<TemplateGallery> templateGalleries = templateGalleryService.list(pageRequest);

    assertThat(templateGalleries).isNotEmpty();
    TemplateGallery templateGallery = templateGalleries.stream().findFirst().get();
    assertTemplateGallery(templateGallery);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldLoadHarnessGallery() {
    templateGalleryService.loadHarnessGallery();

    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    assertThat(templateGallery).isNotNull();
    assertThat(templateGallery.getName()).isEqualTo(HARNESS_GALLERY);
    assertThat(templateGallery.isGlobal()).isTrue();
    assertThat(templateGallery.getReferencedGalleryId()).isNull();

    TemplateFolder harnessTemplateFolder = templateService.getTemplateTree(
        GLOBAL_ACCOUNT_ID, null, asList(TemplateType.SSH.name(), TemplateType.HTTP.name()));
    assertThat(harnessTemplateFolder).isNotNull();
    assertThat(harnessTemplateFolder.getName()).isEqualTo(HARNESS_GALLERY);

    PageRequest<Template> pageRequest = aPageRequest().addFilter("appId", EQ, GLOBAL_APP_ID).build();
    assertTemplates(pageRequest, GLOBAL_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSaveHarnessGallery() {
    TemplateGallery harnessGallery = templateGalleryService.saveHarnessGallery();
    assertThat(harnessGallery).isNotNull();
    assertThat(harnessGallery.isGlobal()).isTrue();
    assertThat(harnessGallery.getReferencedGalleryId()).isNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCopyHarnessTemplates() {
    templateGalleryService.loadHarnessGallery();
    persistence.save(Account.Builder.anAccount().withUuid(ACCOUNT_ID).withAccountName(ACCOUNT_NAME).build());
    Query query = persistence.createQuery(Account.class, excludeAuthorityCount)
                      .project(ID_KEY2, true)
                      .project(AccountKeys.accountName, true)
                      .project(AccountKeys.companyName, true)
                      .filter(ApplicationKeys.appId, GLOBAL_APP_ID);

    when(accountService.getBasicAccountQuery()).thenReturn(query);

    templateGalleryService.copyHarnessTemplates();

    assertAccountGallery();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteByAccountId() {
    templateGalleryService.loadHarnessGallery();

    persistence.save(Account.Builder.anAccount().withUuid(ACCOUNT_ID).withAccountName(ACCOUNT_NAME).build());
    Query query = persistence.createQuery(Account.class, excludeAuthorityCount)
                      .project(ID_KEY2, true)
                      .project(AccountKeys.accountName, true)
                      .project(AccountKeys.companyName, true)
                      .filter(ApplicationKeys.appId, GLOBAL_APP_ID);

    when(accountService.getBasicAccountQuery()).thenReturn(query);

    templateGalleryService.copyHarnessTemplates();
    assertAccountGallery();

    templateGalleryService.deleteByAccountId(ACCOUNT_ID);
    assertThat(templateGalleryService.getByAccount(ACCOUNT_ID, templateGalleryService.getAccountGalleryKey())).isNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldCopyHarnessTemplatesToAccount() {
    templateGalleryService.loadHarnessGallery();
    templateGalleryService.copyHarnessTemplatesToAccount(ACCOUNT_ID, ACCOUNT_NAME);
    assertAccountGallery();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shouldCreateImportedTemplateGalleryInAccount() {
    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .withAppId(GLOBAL_APP_ID)
                          .build();
    persistence.save(account);
    templateGalleryService.saveHarnessCommandLibraryGalleryToAccount(ACCOUNT_ID, ACCOUNT_NAME);
    assertThat(templateGalleryService.getByAccount(ACCOUNT_ID, GalleryKey.HARNESS_COMMAND_LIBRARY_GALLERY)).isNotNull();
  }

  private void assertAccountGallery() {
    TemplateFolder harnessTemplateFolder =
        templateService.getTemplateTree(ACCOUNT_ID, null, asList(TemplateType.SSH.name(), TemplateType.HTTP.name()));
    assertThat(harnessTemplateFolder).isNotNull();
    assertThat(harnessTemplateFolder.getName()).isEqualTo(ACCOUNT_NAME);

    PageRequest<Template> pageRequest = aPageRequest()
                                            .addFilter(TemplateKeys.accountId, EQ, ACCOUNT_ID)
                                            .addFilter(TemplateKeys.appId, EQ, GLOBAL_APP_ID)
                                            .build();
    assertTemplates(pageRequest, ACCOUNT_ID);
  }

  private void assertTemplates(PageRequest<Template> pageRequest, String accountId) {
    List<Template> templates = templateService.list(
        pageRequest, Collections.singletonList(templateGalleryService.getAccountGalleryKey().name()), accountId, false);

    assertThat(templates).isNotEmpty();
    assertThat(templates.stream()
                   .filter(template1 -> template1.getType().equals(TemplateType.SSH.name()))
                   .collect(Collectors.toList()))
        .isNotEmpty();

    assertThat(templates.stream()
                   .filter(template1 -> template1.getType().equals(TemplateType.HTTP.name()))
                   .collect(Collectors.toList()))
        .isNotEmpty();
  }

  private TemplateGallery prepareTemplateGallery() {
    return TemplateGallery.builder()
        .name(TEMPLATE_GALLERY)
        .accountId(ACCOUNT_ID)
        .description(TEMPLATE_GALLERY_DESC)
        .galleryKey(templateGalleryService.getAccountGalleryKey().name())
        .appId(GLOBAL_APP_ID)
        .keywords(ImmutableSet.of("CD"))
        .build();
  }

  private TemplateGallery prepareImportedTemplateGallery() {
    return TemplateGallery.builder()
        .name(TEMPLATE_GALLERY_IMPORTED)
        .accountId(ACCOUNT_ID)
        .description(TEMPLATE_GALLERY_DESC)
        .galleryKey(templateGalleryService.getAccountGalleryKey().name())
        .appId(GLOBAL_APP_ID)
        .keywords(ImmutableSet.of("CD"))
        .build();
  }

  private void assertTemplateGallery(TemplateGallery templateGallery) {
    assertThat(templateGallery).isNotNull().extracting("uuid").isNotNull();
    assertThat(templateGallery.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(templateGallery.getKeywords()).contains("cd");
    assertThat(templateGallery.getKeywords()).contains(TEMPLATE_GALLERY.trim().toLowerCase());
    assertThat(templateGallery.getKeywords()).contains(TEMPLATE_GALLERY_DESC.trim().toLowerCase());

    TemplateGallery harnessGallery = templateGalleryService.get(ACCOUNT_ID, templateGallery.getName());
    assertThat(harnessGallery).isNotNull();
    assertThat(templateGallery.getReferencedGalleryId()).isNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldCopyHarnessTemplateFromGalleryToAccounts() {
    templateGalleryService.loadHarnessGallery();
    templateGalleryService.copyHarnessTemplatesToAccount(ACCOUNT_ID, ACCOUNT_NAME);
    templateService.loadYaml(
        TemplateType.SSH, POWER_SHELL_IIS_WEBSITE_INSTALL_PATH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    when(accountService.getAccountsWithBasicInfo(false))
        .thenReturn(asList(Account.Builder.anAccount().withUuid(ACCOUNT_ID).withAccountName(ACCOUNT_NAME).build()));

    templateGalleryService.copyHarnessTemplateFromGalleryToAccounts(
        POWER_SHELL_COMMANDS, TemplateType.SSH, "Install IIS Website", POWER_SHELL_IIS_WEBSITE_INSTALL_PATH);
    Template createdTemplate = templateService.fetchTemplateByKeywordForAccountGallery(ACCOUNT_ID, "iiswebsite");
    assertThat(createdTemplate).isNotNull();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shouldCopyHarnessTemplateFromGalleryToAccountsV2() {
    // Yaml V2 of IIS Website
    templateGalleryService.loadHarnessGallery();
    templateGalleryService.copyHarnessTemplatesToAccount(ACCOUNT_ID, ACCOUNT_NAME);
    templateService.loadYaml(
        TemplateType.SSH, POWER_SHELL_IIS_WEBSITE_V2_INSTALL_PATH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    when(accountService.getAccountsWithBasicInfo(false))
        .thenReturn(asList(Account.Builder.anAccount().withUuid(ACCOUNT_ID).withAccountName(ACCOUNT_NAME).build()));

    templateGalleryService.copyHarnessTemplateFromGalleryToAccounts(
        POWER_SHELL_COMMANDS, TemplateType.SSH, "Install IIS Website", POWER_SHELL_IIS_WEBSITE_V2_INSTALL_PATH);
    Template createdTemplate = templateService.fetchTemplateByKeywordForAccountGallery(ACCOUNT_ID, "iiswebsite");
    assertThat(createdTemplate).isNotNull();

    // Yaml V2 of IIS Application
    templateService.loadYaml(TemplateType.SSH, POWER_SHELL_IIS_APP_V2_INSTALL_PATH, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    when(accountService.getAccountsWithBasicInfo(false))
        .thenReturn(asList(Account.Builder.anAccount().withUuid(ACCOUNT_ID).withAccountName(ACCOUNT_NAME).build()));

    templateGalleryService.copyHarnessTemplateFromGalleryToAccounts(
        POWER_SHELL_COMMANDS, TemplateType.SSH, "Install IIS Application", POWER_SHELL_IIS_APP_V2_INSTALL_PATH);
    createdTemplate = templateService.fetchTemplateByKeywordForAccountGallery(ACCOUNT_ID, "iisapp");
    assertThat(createdTemplate).isNotNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldCopyNewVersionFromGlobalToAllAccounts() {
    templateGalleryService.loadHarnessGallery();
    templateGalleryService.copyHarnessTemplatesToAccount(ACCOUNT_ID, ACCOUNT_NAME);

    //    Template template = templateService.fetchTemplateByKeyword(GLOBAL_ACCOUNT_ID, "iis");
    SshCommandTemplate sshCommandTemplate = SshCommandTemplate.builder()
                                                .commandType(INSTALL)
                                                .commandUnits(asList(aDownloadArtifactCommandUnit()
                                                                         .withName("Download Artifact")
                                                                         .withCommandPath("${DownloadDirectory}")
                                                                         .withScriptType(POWERSHELL)
                                                                         .withCommandUnitType(DOWNLOAD_ARTIFACT)
                                                                         .build()))
                                                .build();
    Template template = Template.builder()
                            .accountId(GLOBAL_ACCOUNT_ID)
                            .appId(GLOBAL_APP_ID)
                            .gallery(HARNESS_GALLERY)
                            .name("Install")
                            .type("SSH")
                            .description(TEMPLATE_DESC)
                            .folderPath("Harness/Power Shell Commands")
                            .keywords(ImmutableSet.of(TEMPLATE_CUSTOM_KEYWORD))
                            .templateObject(sshCommandTemplate)
                            .build();

    when(accountService.listAllActiveAccounts())
        .thenReturn(asList(Account.Builder.anAccount().withUuid(ACCOUNT_ID).withAccountName(ACCOUNT_NAME).build()));

    templateGalleryService.copyNewVersionFromGlobalToAllAccounts(template, "iis");

    Template template1 = templateService.fetchTemplateByKeywordForAccountGallery(ACCOUNT_ID, "iis");
    assertThat(template1).isNotNull();
    assertThat(template1.getVersion()).isEqualTo(2L);
  }
}
