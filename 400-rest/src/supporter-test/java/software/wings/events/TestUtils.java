/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.beans.User.Builder.anUser;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.COMPANY_NAME;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.event.handler.marketo.MarketoConfig;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;

import software.wings.beans.Account;
import software.wings.beans.Account.Builder;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.User;
import software.wings.beans.account.AccountStatus;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * @author rktummala on 12/05/18
 */
@OwnedBy(PL)
@Singleton
@TargetModule(HarnessModule._990_COMMONS_TEST)
public class TestUtils {
  @Inject private ScmSecret scmSecret;

  public Account createAccount() {
    return Builder.anAccount()
        .withAccountName(ACCOUNT_NAME)
        .withCompanyName(COMPANY_NAME)
        .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
        .withUuid(ACCOUNT_ID)
        .withLicenseInfo(LicenseInfo.builder()
                             .accountType(AccountType.TRIAL)
                             .accountStatus(AccountStatus.ACTIVE)
                             .expiryTime(System.currentTimeMillis() + 10 * 24 * 60 * 60 * 1000)
                             .licenseUnits(100)
                             .build())
        .build();
  }

  public MarketoConfig initializeMarketoConfig() {
    MarketoConfig marketoConfig = new MarketoConfig();
    marketoConfig.setEnabled(true);
    marketoConfig.setUrl(scmSecret.decryptToString(new SecretName("marketo_url")));
    marketoConfig.setClientId(scmSecret.decryptToString(new SecretName("marketo_client_id")));
    marketoConfig.setClientSecret(scmSecret.decryptToString(new SecretName("marketo_client_secret")));
    return marketoConfig;
  }

  public User createUser(Account account) {
    return anUser()
        .uuid(generateUuid())
        .appId(APP_ID)
        .email(USER_EMAIL)
        .name(USER_NAME)
        .password(PASSWORD)
        .accountName(ACCOUNT_NAME)
        .companyName(COMPANY_NAME)
        .accounts(Lists.newArrayList(account))
        .build();
  }
}
