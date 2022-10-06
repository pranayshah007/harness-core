/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.handler.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.event.handler.impl.Constants.ACCOUNT_ID;
import static io.harness.event.handler.impl.Constants.EMAIL_ID;
import static io.harness.event.handler.impl.Constants.USER_NAME;
import static io.harness.event.handler.impl.MarketoHandler.Constants.UTM_CAMPAIGN;
import static io.harness.event.handler.impl.MarketoHandler.Constants.UTM_CONTENT;
import static io.harness.event.handler.impl.MarketoHandler.Constants.UTM_MEDIUM;
import static io.harness.event.handler.impl.MarketoHandler.Constants.UTM_SOURCE;
import static io.harness.event.handler.impl.MarketoHandler.Constants.UTM_TERM;
import static io.harness.event.model.EventType.COMMUNITY_TO_ESSENTIALS;
import static io.harness.event.model.EventType.COMMUNITY_TO_PAID;
import static io.harness.event.model.EventType.COMPLETE_USER_REGISTRATION;
import static io.harness.event.model.EventType.ESSENTIALS_TO_PAID;
import static io.harness.event.model.EventType.FIRST_DELEGATE_REGISTERED;
import static io.harness.event.model.EventType.FIRST_DEPLOYMENT_EXECUTED;
import static io.harness.event.model.EventType.FIRST_ROLLED_BACK_DEPLOYMENT;
import static io.harness.event.model.EventType.FIRST_VERIFIED_DEPLOYMENT;
import static io.harness.event.model.EventType.FIRST_WORKFLOW_CREATED;
import static io.harness.event.model.EventType.LICENSE_UPDATE;
import static io.harness.event.model.EventType.NEW_TRIAL_SIGNUP;
import static io.harness.event.model.EventType.PAID_TO_ESSENTIALS;
import static io.harness.event.model.EventType.SETUP_2FA;
import static io.harness.event.model.EventType.SETUP_CV_24X7;
import static io.harness.event.model.EventType.SETUP_IP_WHITELISTING;
import static io.harness.event.model.EventType.SETUP_RBAC;
import static io.harness.event.model.EventType.SETUP_SSO;
import static io.harness.event.model.EventType.TRIAL_TO_COMMUNITY;
import static io.harness.event.model.EventType.TRIAL_TO_ESSENTIALS;
import static io.harness.event.model.EventType.TRIAL_TO_PAID;
import static io.harness.event.model.EventType.USER_INVITED_FROM_EXISTING_ACCOUNT;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.handler.EventHandler;
import io.harness.event.handler.marketo.MarketoConfig;
import io.harness.event.handler.marketo.MarketoRestClient;
import io.harness.event.listener.EventListener;
import io.harness.event.model.Event;
import io.harness.event.model.EventData;
import io.harness.event.model.EventType;
import io.harness.event.model.marketo.Campaign;
import io.harness.event.model.marketo.Id;
import io.harness.event.model.marketo.LoginResponse;
import io.harness.event.model.marketo.Response;
import io.harness.exception.WingsException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.network.Http;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.utm.UtmInfo;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * @author rktummala on 11/20/18
 */
@OwnedBy(PL)
@Singleton
@Slf4j
public class MarketoHandler implements EventHandler {
  @Inject private UserService userService;
  @Inject private AccountService accountService;
  @Inject private MarketoHelper marketoHelper;
  @Inject private PersistentLocker persistentLocker;
  @Inject private Utils utils;

  public interface Constants {
    String UTM_SOURCE = "utm_source";
    String UTM_CONTENT = "utm_content";
    String UTM_MEDIUM = "utm_medium";
    String UTM_TERM = "utm_term";
    String UTM_CAMPAIGN = "utm_campaign";
  }

  private MarketoConfig marketoConfig;

  // key = event type, value = campaign id
  private Map<EventType, Long> campaignRegistry;
  private Retrofit retrofit;

  @Inject
  public MarketoHandler(MarketoConfig marketoConfig, EventListener eventListener) {
    this.marketoConfig = marketoConfig;
    if (isMarketoEnabled()) {
      campaignRegistry = new HashMap<>();
      registerCampaignRegistry();
      registerEventHandlers(eventListener);
      retrofit = new Retrofit.Builder()
                     .baseUrl(marketoConfig.getUrl())
                     .addConverterFactory(JacksonConverterFactory.create())
                     .client(Http.getOkHttpClient(marketoConfig.getUrl(), true))
                     .build();
    }
  }

  private boolean isMarketoEnabled() {
    return marketoConfig.isEnabled();
  }

  private void registerCampaignRegistry() {
    //    1898 user invited from existing account USER_INVITED_FROM_EXISTING_ACCOUNT
    //    1800 completed account signup           COMPLETE_USER_REGISTRATION
    //    1802 created first workflow             FIRST_WORKFLOW_CREATED
    //    1803 first deployment                   FIRST_DEPLOYMENT_EXECUTED
    //    1804 verified deployment                FIRST_VERIFIED_DEPLOYMENT
    //    1805 initiated rollback                 FIRST_ROLLED_BACK_DEPLOYMENT
    //    1806 CV 24X7                            SETUP_CV_24X7
    //    1807 2 FA                               SETUP_2FA
    //    1808 SSO setup                          SETUP_SSO
    //    1809 IP Whitelisting                    SETUP_IP_WHITELISTING
    //    1810 RBAC                               SETUP_RBAC

    //    1801 installed delegate                 FIRST_DELEGATE_REGISTERED
    //    1811 Trial to Paid                      TRIAL_TO_PAID
    //    1812 Trial to Community                 TRIAL_TO_COMMUNITY
    //    1813 Community to Paid                  COMMUNITY_TO_PAID

    //    2015 User invited for trial signup

    campaignRegistry.put(USER_INVITED_FROM_EXISTING_ACCOUNT, 1898L);
    campaignRegistry.put(COMPLETE_USER_REGISTRATION, 1800L);
    campaignRegistry.put(FIRST_DELEGATE_REGISTERED, 1801L);
    campaignRegistry.put(FIRST_WORKFLOW_CREATED, 1802L);
    campaignRegistry.put(FIRST_DEPLOYMENT_EXECUTED, 1803L);
    campaignRegistry.put(FIRST_VERIFIED_DEPLOYMENT, 1804L);
    campaignRegistry.put(FIRST_ROLLED_BACK_DEPLOYMENT, 1805L);
    campaignRegistry.put(SETUP_CV_24X7, 1806L);
    campaignRegistry.put(SETUP_2FA, 1807L);
    campaignRegistry.put(SETUP_SSO, 1808L);
    campaignRegistry.put(SETUP_IP_WHITELISTING, 1809L);
    campaignRegistry.put(SETUP_RBAC, 1810L);
    campaignRegistry.put(TRIAL_TO_PAID, 1811L);
    campaignRegistry.put(TRIAL_TO_COMMUNITY, 1812L);
    campaignRegistry.put(COMMUNITY_TO_PAID, 1813L);
    campaignRegistry.put(COMMUNITY_TO_ESSENTIALS, 1814L);
    campaignRegistry.put(ESSENTIALS_TO_PAID, 1815L);
    campaignRegistry.put(PAID_TO_ESSENTIALS, 1816L);
    campaignRegistry.put(TRIAL_TO_ESSENTIALS, 1817L);
    campaignRegistry.put(NEW_TRIAL_SIGNUP, 2015L);
  }

  private void registerEventHandlers(EventListener eventListener) {
    eventListener.registerEventHandler(this,
        Sets.newHashSet(USER_INVITED_FROM_EXISTING_ACCOUNT, COMPLETE_USER_REGISTRATION, FIRST_DELEGATE_REGISTERED,
            FIRST_WORKFLOW_CREATED, FIRST_DEPLOYMENT_EXECUTED, FIRST_VERIFIED_DEPLOYMENT, FIRST_ROLLED_BACK_DEPLOYMENT,
            SETUP_CV_24X7, SETUP_2FA, SETUP_SSO, SETUP_IP_WHITELISTING, SETUP_RBAC, TRIAL_TO_PAID, TRIAL_TO_COMMUNITY,
            COMMUNITY_TO_PAID, COMMUNITY_TO_ESSENTIALS, ESSENTIALS_TO_PAID, PAID_TO_ESSENTIALS, TRIAL_TO_ESSENTIALS,
            NEW_TRIAL_SIGNUP, LICENSE_UPDATE));
  }

  @Override
  public void handleEvent(Event event) {
    if (event == null) {
      log.error("Event is null");
      return;
    }

    EventType eventType = event.getEventType();
    if (eventType == null) {
      log.error("Event type is null");
      return;
    }

    try {
      EventData eventData = event.getEventData();
      if (eventData == null) {
        log.error("Event data is null");
        return;
      }

      Map<String, String> properties = eventData.getProperties();

      if (isEmpty(properties)) {
        log.error("Event data properties are null");
        return;
      }

      String accessToken = getAccessToken(marketoConfig.getClientId(), marketoConfig.getClientSecret());

      if (NEW_TRIAL_SIGNUP == eventType) {
        String email = properties.get(EMAIL_ID);
        if (isEmpty(email)) {
          log.error("User email is empty");
          return;
        }

        UtmInfo utmInfo = UtmInfo.builder()
                              .utmCampaign(properties.get(UTM_CAMPAIGN))
                              .utmContent(properties.get(UTM_CONTENT))
                              .utmMedium(properties.get(UTM_MEDIUM))
                              .utmSource(properties.get(UTM_SOURCE))
                              .utmTerm(properties.get(UTM_TERM))
                              .build();
        long marketoLeadId = reportLead(properties.get(USER_NAME), email, accessToken, true, utmInfo);
        if (marketoLeadId > 0) {
          reportCampaignEvent(eventType, accessToken, Arrays.asList(Id.builder().id(marketoLeadId).build()));
        }
        return;
      }

      String accountId = properties.get(ACCOUNT_ID);
      if (isEmpty(accountId)) {
        log.error("Account is empty");
        return;
      }

      Account account = accountService.get(accountId);
      notNullCheck("Account is null for accountId:" + accountId, account);

      switch (eventType) {
        case COMMUNITY_TO_PAID:
        case COMMUNITY_TO_ESSENTIALS:
        case TRIAL_TO_COMMUNITY:
        case TRIAL_TO_PAID:
        case ESSENTIALS_TO_PAID:
        case PAID_TO_ESSENTIALS:
        case TRIAL_TO_ESSENTIALS:
        case FIRST_DELEGATE_REGISTERED:
          reportToAllUsers(account, accessToken, eventType);
          return;
        case LICENSE_UPDATE:
          updateAllUsersInMarketo(account, accessToken);
          return;
        default:
          break;
      }

      User user = utils.getUser(properties);
      if (user == null) {
        return;
      }

      switch (eventType) {
        case USER_INVITED_FROM_EXISTING_ACCOUNT:
        case COMPLETE_USER_REGISTRATION:
          registerLeadAndReportCampaign(account, user, accessToken, event, user.getUtmInfo());
          break;
        default:
          reportCampaignEvent(account, eventType, accessToken, user, user.getUtmInfo());
          break;
      }

    } catch (Exception ex) {
      log.error("Error while sending event to marketo for event {}", eventType, ex);
    }
  }

  private void reportToAllUsers(Account account, String accessToken, EventType eventType) throws IOException {
    List<User> usersOfAccount = userService.getUsersOfAccount(account.getUuid());
    if (isEmpty(usersOfAccount)) {
      return;
    }

    List<Id> leadIdList = usersOfAccount.stream()
                              .filter(user -> user.getMarketoLeadId() != 0L)
                              .map(user -> Id.builder().id(user.getMarketoLeadId()).build())
                              .collect(Collectors.toList());
    reportCampaignEvent(eventType, accessToken, leadIdList);
  }

  private void updateAllUsersInMarketo(Account account, String accessToken) {
    List<User> usersOfAccount = userService.getUsersOfAccount(account.getUuid());
    if (isEmpty(usersOfAccount)) {
      return;
    }

    usersOfAccount.stream().filter(user -> user.getMarketoLeadId() != 0L).forEach(user -> {
      try {
        reportLead(account, user, accessToken, false, user.getUtmInfo());
      } catch (IOException | URISyntaxException e) {
        log.error("Error while updating license to all users in marketo", e);
      }
    });
  }

  private void registerLeadAndReportCampaign(Account account, User user, String accessToken, Event event,
      UtmInfo utmInfo) throws IOException, URISyntaxException {
    long marketoLeadId = user.getMarketoLeadId();
    if (marketoLeadId == 0L) {
      reportLead(account, user, accessToken, true, utmInfo);
    }
    // Getting the latest copy since we had a sleep of 10 seconds.
    user = userService.getUserFromCacheOrDB(user.getUuid());
    reportCampaignEvent(account, event.getEventType(), accessToken, user, utmInfo);
  }

  public long reportLead(Account account, User user, String accessToken, boolean wait, UtmInfo utmInfo)
      throws IOException, URISyntaxException {
    long marketoLeadId = marketoHelper.createOrUpdateLead(
        account, user.getName(), user.getEmail(), accessToken, user.getOauthProvider(), retrofit, utmInfo);
    if (marketoLeadId > 0) {
      if (marketoLeadId != user.getMarketoLeadId()) {
        updateUser(user, marketoLeadId);
      }

      if (wait) {
        // Sleeping for 10 secs as a work around for marketo issue.
        // Marketo can't process trigger campaign with a lead just created.
        try {
          Thread.sleep(10000);
        } catch (InterruptedException ex) {
          log.warn("Exception while waiting 10 seconds for marketo to catchup");
        }
      }
    }
    return marketoLeadId;
  }

  public long reportLead(String userName, String email, String accessToken, boolean wait, UtmInfo utmInfo)
      throws IOException, URISyntaxException {
    long marketoLeadId = marketoHelper.createOrUpdateLead(null, userName, email, accessToken, null, retrofit, utmInfo);

    // Sleeping for 10 secs as a work around for marketo issue.
    // Marketo can't process trigger campaign with a lead just created.
    if (marketoLeadId > 0 && wait) {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException ex) {
        log.warn("Exception while waiting 10 seconds for marketo to catchup");
      }
    }
    return marketoLeadId;
  }

  private User updateUser(User user, EventType eventType) {
    try (AcquiredLock lock =
             persistentLocker.waitToAcquireLock(user.getEmail(), Duration.ofMinutes(2), Duration.ofMinutes(4))) {
      return userService.addEventToUserMarketoCampaigns(user.getUuid(), eventType);
    }
  }

  private User updateUser(User user, long marketoLeadId) {
    if (marketoLeadId == 0L) {
      return user;
    }

    try (AcquiredLock lock =
             persistentLocker.waitToAcquireLock(user.getEmail(), Duration.ofMinutes(2), Duration.ofMinutes(4))) {
      User latestUser = userService.getUserFromCacheOrDB(user.getUuid());
      if (latestUser != null) {
        latestUser.setMarketoLeadId(marketoLeadId);
        return userService.update(latestUser);
      } else {
        throw new WingsException("Invalid user for the given id:" + user.getUuid(), USER);
      }
    }
  }

  public boolean reportCampaignEvent(EventType eventType, String accessToken, List<Id> leadIdList) throws IOException {
    log.info("Reporting campaign for event {} with leads {}", eventType, leadIdList);
    if (isEmpty(leadIdList)) {
      log.error("No Leads reported for event {}", eventType);
      return false;
    }

    long campaignId = campaignRegistry.get(eventType);
    if (campaignId == 0) {
      log.warn("No Campaign found for event type {}", eventType);
      return false;
    }

    Campaign campaign = Campaign.builder().input(Campaign.Input.builder().leads(leadIdList).build()).build();

    retrofit2.Response<Response> response =
        retrofit.create(MarketoRestClient.class).triggerCampaign(campaignId, accessToken, campaign).execute();

    if (!response.isSuccessful()) {
      log.error("Error while triggering campaign to Marketo for eventType {}. Response code is {}", eventType,
          response.code());
      return false;
    }

    Response campaignResponse = response.body();

    if (campaignResponse == null) {
      log.error("Marketo trigger campaign response was null for eventType {}", eventType);
      return false;
    }

    if (!campaignResponse.isSuccess()) {
      log.error("Marketo http response reported failure for eventType {}, {}", eventType,
          utils.getErrorMsg(campaignResponse.getErrors()));
      return false;
    }

    log.info("Reported campaign for event {} with leads {}", eventType, leadIdList);
    return true;
  }

  private void reportCampaignEvent(Account account, EventType eventType, String accessToken, User user, UtmInfo utmInfo)
      throws IOException, URISyntaxException {
    String userId = user.getUuid();
    long marketoLeadId = user.getMarketoLeadId();
    if (marketoLeadId == 0L) {
      marketoLeadId = reportLead(account, user, accessToken, true, utmInfo);
      if (marketoLeadId == 0L) {
        log.error("Invalid lead id reported for user {}", userId);
        return;
      }

      // Getting the latest copy since we had a sleep of 10 seconds.
      user = userService.getUserFromCacheOrDB(userId);
    }

    boolean reported =
        reportCampaignEvent(eventType, accessToken, Arrays.asList(Id.builder().id(marketoLeadId).build()));
    if (reported) {
      updateUser(user, eventType);
    }
  }

  public String getAccessToken(String clientId, String clientSecret) throws IOException {
    retrofit2.Response<LoginResponse> response =
        retrofit.create(MarketoRestClient.class).login(clientId, clientSecret).execute();

    if (!response.isSuccessful()) {
      throw new IOException(response.message());
    }

    LoginResponse loginResponse = response.body();

    if (loginResponse == null) {
      throw new IOException("Login response is null");
    }

    return loginResponse.getAccess_token();
  }
}
