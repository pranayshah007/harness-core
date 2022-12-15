/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.BOOPESH;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;

import io.harness.beans.PageRequest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.RequestType;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.AuditServiceTestHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuditService;
import software.wings.utils.WingsTestConstants;

import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

// this test fails intermittently
public class AuditServiceIntegrationTest extends WingsBaseTest {
  @Inject private AuditServiceTestHelper auditServiceTestHelper;
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Inject private AccountService accountService;
  @Inject protected AuditService auditService;
  private String accountId = "some-account-uuid-" + RandomStringUtils.randomAlphanumeric(5);

  private void setUserRequestContext() {
    User user = User.Builder.anUser().name(USER_NAME).uuid(USER_ID).build();
    user.setUserRequestContext(UserRequestContext.builder().accountId(accountId).build());
    UserThreadLocal.set(user);
  }
  @Before
  public void setupMocks() throws IOException {
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
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldCreateRequestPayload() throws Exception {
    AuditHeader header = auditServiceTestHelper.createAuditHeader(generateUuid());
    assertThat(header.getRequestTime()).isNull();
    assertThat(header.getRequestPayloadUuid()).isNull();
    byte[] httpBody = "TESTTESTTESTTESTTESTTESTTESTTESTTESTTEST".getBytes();
    String fileId = auditService.create(header, RequestType.REQUEST, new ByteArrayInputStream(httpBody));

    header.setResponseStatusCode(200);
    header.setResponseTime(System.currentTimeMillis());
    AuditHeader header2 = auditService.read(header.getAppId(), header.getUuid());
    assertThat(header2).isNotNull();
    assertThat(header2.getRequestPayloadUuid()).isNotNull();
    assertThat(header2.getRequestPayloadUuid()).isEqualTo(fileId);
  }

  /**
   * Should finalize.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldFinalize() throws Exception {
    AuditHeader header = auditServiceTestHelper.createAuditHeader(generateUuid());
    assertThat(header).isNotNull();
    assertThat(header.getRemoteUser()).isNull();
    byte[] httpBody = "TESTTESTTESTTESTTESTTESTTESTTESTTESTTEST".getBytes();
    header.setResponseTime(System.currentTimeMillis());
    header.setResponseStatusCode(200);
    auditService.finalize(header, httpBody);
    AuditHeader header2 = auditService.read(header.getAppId(), header.getUuid());
    assertThat(header2).isNotNull();
    assertThat(header2.getResponsePayloadUuid()).isNotNull();
    assertThat(header2.getResponseStatusCode()).isEqualTo(200);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void shouldDeleteExpiredAuditsAndAuditFilesAndAuditChunks() throws Exception {
    AuditHeader header = auditServiceTestHelper.createAuditHeader(generateUuid());
    header.setResponseStatusCode(200);
    header.setResponseTime(Instant.now().toEpochMilli());
    assertThat(auditService.list(new PageRequest<>())).hasSize(1);
    auditService.deleteAuditRecords(Instant.now().plus(Duration.ofDays(10)).toEpochMilli());
    assertThat(auditService.list(new PageRequest<>())).hasSize(0);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void shouldNotDeleteAuditsAndAuditFilesAndAuditChunks() throws Exception {
    AuditHeader header = auditServiceTestHelper.createAuditHeader(generateUuid());
    header.setResponseStatusCode(200);
    header.setResponseTime(Instant.now().toEpochMilli());
    assertThat(auditService.list(new PageRequest<>())).hasSize(1);
    auditService.deleteAuditRecords(Instant.now().minus(Duration.ofDays(10)).toEpochMilli());
    assertThat(auditService.list(new PageRequest<>())).hasSize(1);
  }
}
