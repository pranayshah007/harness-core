/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.event.handler.impl.MarketoHelper;
import io.harness.event.handler.marketo.MarketoConfig;
import io.harness.migrations.Migration;
import io.harness.network.Http;
import io.harness.persistence.HIterator;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Migration script to set inviteUrl to marketo leads for all existing pending user invites.
 * @author rktummala on 02/22/19
 */
@Slf4j
@BreakDependencyOn("software.wings.app.MainConfiguration")
public class SendInviteUrlForAllUserInvites implements Migration {
  @Inject private UserService userService;
  @Inject private AccountService accountService;
  @Inject private MarketoHelper marketoHelper;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private MainConfiguration mainConfiguration;

  @Override
  public void migrate() {
    log.info("Start - Setting inviteURL for users");
    MarketoConfig marketoConfig = mainConfiguration.getMarketoConfig();

    if (!marketoConfig.isEnabled()) {
      log.info("Marketo is disabled, skipping the migration");
      return;
    }

    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(marketoConfig.getUrl())
                            .addConverterFactory(JacksonConverterFactory.create())
                            .client(Http.getUnsafeOkHttpClient(marketoConfig.getUrl()))
                            .build();

    log.info("MarketoMigration - Start - registering all users of trial accounts as leads");
    String accessToken;
    try {
      accessToken =
          marketoHelper.getAccessToken(marketoConfig.getClientId(), marketoConfig.getClientSecret(), retrofit);
      log.info("Obtained access token");
    } catch (IOException e) {
      log.error("Error while trying to get access token in migration", e);
      return;
    }

    try (HIterator<UserInvite> userInvites =
             new HIterator<>(wingsPersistence.createQuery(UserInvite.class).limit(NO_LIMIT).fetch())) {
      UserInvite userInvite;
      User user = null;
      while (userInvites.hasNext()) {
        try {
          userInvite = userInvites.next();

          if (userInvite.isCompleted()) {
            continue;
          }

          if (isEmpty(userInvite.getAccountId())) {
            marketoHelper.createOrUpdateLead(null, null, userInvite.getEmail(), accessToken, null, retrofit, null);
          } else {
            Account account = accountService.get(userInvite.getAccountId());
            if (account != null && account.getLicenseInfo() != null
                && AccountType.TRIAL.equals(account.getLicenseInfo().getAccountType())) {
              user = userService.getUserByEmail(userInvite.getEmail(), userInvite.getAccountId());
              if (user != null) {
                marketoHelper.createOrUpdateLead(
                    account, user.getName(), user.getEmail(), accessToken, user.getOauthProvider(), retrofit, null);
              }
            }
          }
        } catch (Exception ex) {
          log.error("Error while setting inviteURL for user {}", user == null ? "NA" : user.getName(), ex);
        }
      }
    }
    log.info("End - Setting inviteURL for users");
  }
}
