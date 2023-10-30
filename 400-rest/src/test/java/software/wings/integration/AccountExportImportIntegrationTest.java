/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.rule.OwnerRule.UTKARSH;

import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.LicenseInfo;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.account.AccountStatus;
import software.wings.dl.exportimport.ExportMode;
import software.wings.dl.exportimport.ImportStatusReport;
import software.wings.service.intfc.instance.licensing.InstanceLimitProvider;

import dev.morphia.annotations.Entity;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author marklu on 10/25/18
 */
public class AccountExportImportIntegrationTest extends IntegrationTestBase {
  private String accountId;

  @Override
  @Before
  public void setUp() {
    super.loginAdminUser();

    Account account = accountService.getByName("Harness");
    assertThat(account).isNotNull();

    accountId = account.getUuid();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testAccountExportImport() throws Exception {
    byte[] exportedAccountData = exportAccountData(accountId);
    assertThat(exportedAccountData).isNotNull();

    ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(exportedAccountData));

    boolean hasAccounts = false;
    boolean hasApplications = false;
    ZipEntry zipEntry;
    while ((zipEntry = zipInputStream.getNextEntry()) != null) {
      if (zipEntry.getName().equals("accounts.json")) {
        hasAccounts = true;
      } else if (zipEntry.getName().equals("applications.json")) {
        hasApplications = true;
      }
    }
    assertThat(hasAccounts).isTrue();
    assertThat(hasApplications).isTrue();

    importAccountData(accountId, exportedAccountData);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testImportQEAccountDataFromZipFile() {
    String qaHarnessAccountId = "eWZFoTkESDSkPfnGwAp0lQ";
    String qaHarnessAccountName = "QEAccount";

    // 1. Delete the account if it exists.
    if (accountService.exists(qaHarnessAccountName)) {
      deleteAccount(qaHarnessAccountId);
    }
    // 2. Create an empty account for the account to be imported.
    createAccount(qaHarnessAccountId, qaHarnessAccountName);

    try {
      // 3. Actually import the Harness-QA account data from the exported Zip file.
      String qaHarnessAccountDataZipFile = "./exportimport/account_eWZFoTkESDSkPfnGwAp0lQ.zip";
      importAccountDataFromFile(qaHarnessAccountId, qaHarnessAccountDataZipFile);

      // 4. Verify relevant data has been imported successfully.
      Application application = appService.getAppByName(qaHarnessAccountId, "swamy-test");
      assertThat(application).isNotNull();
      Service service = serviceResourceService.getServiceByName(application.getUuid(), "Docker");
      assertThat(service).isNotNull();
      Environment environment = environmentService.getEnvironmentByName(application.getUuid(), "gcp");
      assertThat(environment).isNotNull();
      Workflow workflow = workflowService.readWorkflowByName(application.getAppId(), "DockerBasic");
      assertThat(workflow).isNotNull();
    } finally {
      // 5. Delete the imported account after done.
      deleteAccount(qaHarnessAccountId);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testSpecificExport() throws Exception {
    byte[] exportedAccountData =
        exportSpecificAccountData(accountId, Application.class.getAnnotation(Entity.class).value());
    assertThat(exportedAccountData).isNotNull();

    ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(exportedAccountData));

    boolean hasUsers = false;
    boolean hasApplications = false;
    ZipEntry zipEntry;
    while ((zipEntry = zipInputStream.getNextEntry()) != null) {
      if (zipEntry.getName().equals("users.json")) {
        hasUsers = true;
      } else if (zipEntry.getName().equals("applications.json")) {
        hasApplications = true;
      }
    }
    assertThat(hasUsers).isFalse();
    assertThat(hasApplications).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testSpecificExport_noEntityTypes_shouldFail() {
    assertThatExceptionOfType(Exception.class).isThrownBy(() -> {
      WebTarget target =
          client.target(API_BASE + "/account/export?accountId=" + accountId + "&mode=" + ExportMode.SPECIFIC);
      getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<String>>() {});
    });
  }

  private byte[] exportAccountData(String accountId) {
    WebTarget target = client.target(API_BASE + "/account/export?accountId=" + accountId);
    byte[] responseZip = getRequestBuilderWithAuthHeader(target).get(new GenericType<byte[]>() {});
    assertThat(isNotEmpty(responseZip)).isTrue();

    return responseZip;
  }

  private byte[] exportSpecificAccountData(String accountId, String entityType) {
    WebTarget target = client.target(API_BASE + "/account/export?accountId=" + accountId
        + "&mode=" + ExportMode.SPECIFIC + "&entityTypes=" + entityType);
    byte[] responseZip = getRequestBuilderWithAuthHeader(target).get(new GenericType<byte[]>() {});
    assertThat(isNotEmpty(responseZip)).isTrue();

    return responseZip;
  }

  private void importAccountData(String accountId, byte[] accountDataJson) {
    MultiPart multiPart = new MultiPart();
    FormDataBodyPart formDataBodyPart =
        new FormDataBodyPart("file", accountDataJson, MediaType.MULTIPART_FORM_DATA_TYPE);
    multiPart.bodyPart(formDataBodyPart);

    WebTarget target =
        client.target(API_BASE + "/account/import?accountId=" + accountId + "&accountName=MigratedAccount");
    RestResponse<Void> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE), new GenericType<RestResponse<Void>>() {});
    assertThat(restResponse.getResponseMessages()).isEmpty();
  }

  private void importAccountDataFromFile(String accountId, String accountDataZipFile) {
    File fileToImport = new File(getClass().getClassLoader().getResource(accountDataZipFile).getFile());

    MultiPart multiPart = new MultiPart();
    FormDataBodyPart formDataBodyPart = new FormDataBodyPart("file", fileToImport, MediaType.MULTIPART_FORM_DATA_TYPE);
    multiPart.bodyPart(formDataBodyPart);

    WebTarget target = client.target(API_BASE + "/account/import?accountId=" + accountId
        + "&disableSchemaCheck=true&adminPassword=admin&adminUser=mark.lu@harness.io");
    RestResponse<ImportStatusReport> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE), new GenericType<RestResponse<ImportStatusReport>>() {});
    // Verify vault config was successfully created.
    assertThat(restResponse.getResponseMessages()).isEmpty();
    assertThat(restResponse.getResource()).isNotNull();
    assertThat(restResponse.getResource().getStatuses().size() > 0).isTrue();
  }

  private void createAccount(String accountId, String accountName) {
    Account account = Account.Builder.anAccount()
                          .withUuid(accountId)
                          .withAccountName(accountName)
                          .withCompanyName(accountName)
                          .withLicenseInfo(LicenseInfo.builder()
                                               .accountType(AccountType.PAID)
                                               .accountStatus(AccountStatus.ACTIVE)
                                               .licenseUnits(InstanceLimitProvider.defaults(AccountType.PAID))
                                               .build())
                          .build();
    account.setForImport(true);

    WebTarget target = client.target(API_BASE + "/account/new");
    RestResponse<Account> response = getRequestBuilderWithAuthHeader(target).post(
        entity(account, MediaType.APPLICATION_JSON), new GenericType<RestResponse<Account>>() {});
    assertThat(response.getResource()).isNotNull();
    assertThat(accountService.exists(account.getAccountName())).isTrue();
    assertThat(accountService.getByName(account.getCompanyName())).isNotNull();
  }

  private void deleteAccount(String accountId) {
    WebTarget target = client.target(API_BASE + "/account/delete/" + accountId);
    RestResponse<Boolean> response =
        getRequestBuilderWithAuthHeader(target).delete(new GenericType<RestResponse<Boolean>>() {});
    assertThat(response.getResource()).isNotNull();
    assertThat(response.getResource()).isTrue();
  }
}
