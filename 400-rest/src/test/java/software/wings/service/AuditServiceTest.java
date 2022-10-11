/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.audit.AuditHeader;
import software.wings.beans.Account;
import software.wings.beans.LicenseInfo;
import software.wings.beans.User;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.features.AuditTrailFeature;
import software.wings.features.api.PremiumFeature;
import software.wings.licensing.LicenseService;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuditService;
import software.wings.service.intfc.FileService;
import software.wings.utils.WingsTestConstants;

import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AuditServiceTest extends WingsBaseTest {
  @Inject private AccountService accountService;
  @Inject AuditServiceTestHelper auditServiceTestHelper;
  @Inject private LicenseService licenseService;
  @Inject protected FileService fileService;
  @Inject protected AuditService auditService;

  @Inject @Named(AuditTrailFeature.FEATURE_NAME) private PremiumFeature auditTrailFeature;

  private static final String appId = generateUuid();

  private String accountId = "some-account-uuid-" + RandomStringUtils.randomAlphanumeric(5);

  private void setUserRequestContext() {
    User user = User.Builder.anUser().name(USER_NAME).uuid(USER_ID).build();
    user.setUserRequestContext(UserRequestContext.builder().accountId(accountId).build());
    UserThreadLocal.set(user);
  }

  @Before
  public void setupMocks() {
    on(auditService).set("timeLimiter", new FakeTimeLimiter());

    Account account = anAccount()
                          .withUuid(accountId)
                          .withAccountName(WingsTestConstants.ACCOUNT_NAME)
                          .withCompanyName(WingsTestConstants.COMPANY_NAME)
                          .withLicenseInfo(getLicenseInfo())
                          .build();
    accountService.save(account, false, false);
    accountId = account.getUuid();
    setUserRequestContext();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCreate() throws Exception {
    auditServiceTestHelper.createAuditHeader(appId);
  }

  /**
   * Should list.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldList() throws Exception {
    auditServiceTestHelper.createAuditHeader(appId);
    auditServiceTestHelper.createAuditHeader(appId);
    auditServiceTestHelper.createAuditHeader(appId);
    auditServiceTestHelper.createAuditHeader(appId);

    PageResponse<AuditHeader> res =
        auditService.list(aPageRequest().withOffset("1").withLimit("2").addFilter("appId", Operator.EQ, appId).build());

    assertThat(res).isNotNull();
    assertThat(res.size()).isEqualTo(2);
    assertThat(res.getTotal()).isEqualTo(4);
    assertThat(res.getPageSize()).isEqualTo(2);
    assertThat(res.getStart()).isEqualTo(1);
    assertThat(res.getResponse()).isNotNull();
    assertThat(res.getResponse().size()).isEqualTo(2);

    for (String restrictedAccountType : auditTrailFeature.getRestrictedAccountTypes()) {
      LicenseInfo newLicenseInfo = getLicenseInfo();
      newLicenseInfo.setAccountType(restrictedAccountType);
      licenseService.updateAccountLicense(accountId, newLicenseInfo);
      try {
        auditService.list(new PageRequest<>());
        fail("Audits accessible to an unauthorized user");
      } catch (WingsException e) {
        assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
      }
    }
  }

  /**
   * Should not list if unavailable for account.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void shouldNotListIfUnavailable() {
    for (String restrictedAccountType : auditTrailFeature.getRestrictedAccountTypes()) {
      LicenseInfo newLicenseInfo = getLicenseInfo();
      newLicenseInfo.setAccountType(restrictedAccountType);
      licenseService.updateAccountLicense(accountId, newLicenseInfo);
      try {
        auditService.list(new PageRequest<>());
        fail("Audits accessible to an unauthorized user");
      } catch (WingsException e) {
        assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
      }

      try {
        auditService.listUsingFilter(
            accountId, RandomStringUtils.randomAlphabetic(5), RandomStringUtils.randomNumeric(2), "0");
        fail("Audits accessible to an unauthorized user");
      } catch (WingsException e) {
        assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
      }
    }
  }

  /**
   * Should update user.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldUpdateUser() throws Exception {
    AuditHeader header = auditServiceTestHelper.createAuditHeader(appId);
    assertThat(header).isNotNull();
    assertThat(header.getRemoteUser()).isNull();
    User user = User.Builder.anUser().uuid(generateUuid()).name("abc").build();
    auditService.updateUser(header, user);
    AuditHeader header2 = auditService.read(header.getAppId(), header.getUuid());
    assertThat(header2).isNotNull();
    assertThat(header2.getRemoteUser()).isEqualTo(user);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDeleteAuditRecords() throws Exception {
    auditServiceTestHelper.createAuditHeader(appId);
    auditServiceTestHelper.createAuditHeader(appId);
    auditServiceTestHelper.createAuditHeader(appId);
    auditServiceTestHelper.createAuditHeader(appId);

    auditService.deleteAuditRecords(LocalDateTime.now().plusDays(2).toInstant(ZoneOffset.UTC).toEpochMilli());
    assertThat(auditService.list(aPageRequest().addFilter(ArtifactStreamKeys.appId, EQ, appId).build())).hasSize(0);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotDeleteAuditRecordsWithInRetentionTime() throws Exception {
    auditServiceTestHelper.createAuditHeader(appId);
    auditServiceTestHelper.createAuditHeader(appId);
    auditServiceTestHelper.createAuditHeader(appId);
    auditServiceTestHelper.createAuditHeader(appId);

    auditService.deleteAuditRecords(1 * 24 * 60 * 60 * 1000);
    assertThat(auditService.list(aPageRequest().addFilter(ArtifactStreamKeys.appId, EQ, appId).build())).hasSize(4);
  }
}
