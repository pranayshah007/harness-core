/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.UTKARSH;

import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.PageResponse;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.account.AccountStatus;
import software.wings.beans.alert.Alert;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.utils.Utils;

import java.util.List;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

/**
 * @author marklu on 2018-12-26
 */
@Slf4j
public class AccountResourceIntegrationTest extends IntegrationTestBase {
  @Override
  @Before
  public void setUp() {
    super.loginAdminUser();
  }

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  @Owner(developers = UTKARSH)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testAccountMigration() {
    disableAccount(accountId, true);

    String accountStatus = getAccountStatus(accountId);
    assertThat(accountStatus).isEqualTo(AccountStatus.INACTIVE);
    List<User> users = userService.getUsersOfAccount(accountId);
    for (User user : users) {
      if (userService.canEnableOrDisable(user)) {
        assertThat(user.isDisabled()).isTrue();
      }
    }

    // Once the account disabled with 'migratedToClusterUrl' attribute set. Further calls into this account
    // get 301 Moved Permanently response.
    Response alertResponse = getAccountAlerts(accountId);
    assertThat(alertResponse.getStatus()).isEqualTo(Status.MOVED_PERMANENTLY.getStatusCode());
    RestResponse<PageResponse<Alert>> restResponse =
        alertResponse.readEntity(new GenericType<RestResponse<PageResponse<Alert>>>() {});
    assertThat(restResponse.getResponseMessages().size()).isGreaterThan(0);
    assertThat(ErrorCode.ACCOUNT_MIGRATED).isEqualTo(restResponse.getResponseMessages().get(0).getCode());

    disableAccount(accountId, false);
    accountStatus = getAccountStatus(accountId);
    assertThat(accountStatus).isEqualTo(AccountStatus.ACTIVE);
    users = userService.getUsersOfAccount(accountId);
    for (User user : users) {
      if (userService.canEnableOrDisable(user)) {
        assertThat(user.isDisabled()).isFalse();
      }
    }

    alertResponse = getAccountAlerts(accountId);
    assertThat(alertResponse.getStatus()).isEqualTo(Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shallCreateAndDeleteAccount() {
    Account account = new Account();
    account.setLicenseInfo(getLicenseInfo());
    long timeMillis = System.currentTimeMillis();
    String randomString = "" + timeMillis;
    account.setCompanyName(randomString);
    account.setAccountName(randomString);
    account.setAccountKey(randomString);
    account.setCloudCostEnabled(true);

    WebTarget target = client.target(API_BASE + "/users/account");
    Response response = getRequestBuilderWithAuthHeader(target).post(entity(account, APPLICATION_JSON));
    if (response.getStatus() != Status.OK.getStatusCode()) {
      log.error("Non-ok-status. Headers: {}", response.getHeaders());
    }
    assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
    RestResponse<Account> restResponse = response.readEntity(new GenericType<RestResponse<Account>>() {});
    Account createdAccount = restResponse.getResource();
    assertThat(restResponse.getResource().getAccountName()).isEqualTo(randomString);

    target = client.target(API_BASE + "/account/delete/" + createdAccount.getUuid());
    getRequestBuilderWithAuthHeader(target).delete(new GenericType<RestResponse>() {});
    assertThat(response).isNotNull();
    if (response.getStatus() != Status.OK.getStatusCode()) {
      log.error("Non-ok-status. Headers: {}", response.getHeaders());
    }

    thrown.expect(WingsException.class);
    accountService.get(createdAccount.getUuid());
  }

  private Response getAccountAlerts(String accountId) {
    WebTarget target = client.target(API_BASE + "/alerts?accountId=" + accountId + "&status=Open");
    return getRequestBuilderWithAuthHeader(target).get();
  }

  private String getAccountStatus(String accountId) {
    WebTarget target = client.target(API_BASE + "/account/" + accountId + "/status");
    RestResponse<String> restResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<String>>() {});
    assertThat(restResponse.getResponseMessages()).isEmpty();
    String accountStatus = restResponse.getResource();
    assertThat(accountStatus).isNotNull();
    assertThat(AccountStatus.isValid(accountStatus)).isTrue();
    return accountStatus;
  }

  private void disableAccount(String accountId, boolean disable) {
    String operation = disable ? "disable" : "enable";
    String migratedToClusterUrl = "https://localhost:9090/api";
    WebTarget target = client.target(API_BASE + "/account/" + operation + "?accountId=" + accountId
        + "&migratedTo=" + Utils.urlEncode(migratedToClusterUrl));
    RestResponse<Boolean> restResponse =
        getRequestBuilderWithAuthHeader(target).post(null, new GenericType<RestResponse<Boolean>>() {});
    assertThat(restResponse.getResponseMessages()).isEmpty();
    Boolean statusUpdated = restResponse.getResource();
    assertThat(statusUpdated).isNotNull();
    assertThat(statusUpdated).isTrue();

    GovernanceConfig governanceConfig = governanceConfigService.get(accountId);
    assertThat(governanceConfig).isNotNull();
    assertThat(governanceConfig.isDeploymentFreeze()).isEqualTo(disable);

    if (disable) {
      Account account = wingsPersistence.get(Account.class, accountId);
      assertThat(account.getMigratedToClusterUrl()).isEqualTo(migratedToClusterUrl);
    }
  }
}
