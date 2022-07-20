/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.handler.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import static org.apache.commons.lang3.StringUtils.join;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.handler.marketo.MarketoRestClient;
import io.harness.event.model.marketo.Error;
import io.harness.event.model.marketo.GetLeadResponse;
import io.harness.event.model.marketo.GetLeadResponse.Result;
import io.harness.event.model.marketo.Lead;
import io.harness.event.model.marketo.LeadRequestWithEmail;
import io.harness.event.model.marketo.LeadRequestWithId;
import io.harness.event.model.marketo.LoginResponse;
import io.harness.event.model.marketo.Response;

import software.wings.beans.Account;
import software.wings.beans.LicenseInfo;
import software.wings.beans.UserInvite;
import software.wings.beans.utm.UtmInfo;
import software.wings.service.intfc.SignupService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Retrofit;

/**
 * @author rktummala on 11/20/18
 */
@OwnedBy(PL)
@Singleton
@Slf4j
public class MarketoHelper {
  @Inject private UserService userService;
  @Inject private SignupService signupService;
  @Inject private Utils utils;

  public long createOrUpdateLead(Account account, String userName, String email, String accessToken,
      String oauthProvider, Retrofit retrofit, UtmInfo utmInfo) throws IOException, URISyntaxException {
    notNullCheck("Email is null while registering the lead", email);

    int existingLeadId = 0;
    retrofit2.Response<GetLeadResponse> response =
        retrofit.create(MarketoRestClient.class).getLead(accessToken, "email", email).execute();
    GetLeadResponse existingLeadResponse = response.body();

    if (existingLeadResponse != null) {
      if (existingLeadResponse.isSuccess()) {
        List<Result> resultList = existingLeadResponse.getResult();
        if (isNotEmpty(resultList)) {
          Result result = resultList.get(0);
          existingLeadId = result.getId();
        }

      } else {
        log.error("Marketo http response reported failure while looking up lead. {}",
            utils.getErrorMsg(existingLeadResponse.getErrors()));
      }
    } else {
      log.error("Marketo http response reported null while looking up lead");
    }

    UserInvite userInvite;
    if (account != null) {
      userInvite = userService.getUserInviteByEmailAndAccount(email, account.getUuid());
    } else {
      userInvite = signupService.getUserInviteByEmail(email);
    }

    String userInviteUrl = utils.getUserInviteUrl(userInvite, account);

    retrofit2.Response<Response> createOrUpdateResponse;
    if (existingLeadId > 0) {
      try {
        createOrUpdateResponse = updateLead(retrofit, existingLeadId, email, userName, account, userInviteUrl,
            accessToken, oauthProvider, userInvite.getUtmInfo(), userInvite);
      } catch (Exception ex) {
        // Retrying with email if id was invalid, we have cases where the lead was manually deleted from marketo
        createOrUpdateResponse = createLead(
            retrofit, email, userName, account, userInviteUrl, accessToken, oauthProvider, utmInfo, userInvite);
      }
    } else {
      createOrUpdateResponse = createLead(
          retrofit, email, userName, account, userInviteUrl, accessToken, oauthProvider, utmInfo, userInvite);
    }

    return processLeadResponse(createOrUpdateResponse);
  }

  private retrofit2.Response<Response> createLead(Retrofit retrofit, String email, String userName, Account account,
      String userInviteUrl, String accessToken, String oauthProvider, UtmInfo utmInfo, UserInvite userInvite)
      throws IOException {
    log.info("Creating lead with email: {} in marketo with oauth provider {}", email, oauthProvider);

    Lead lead = buildLead(email, userName, account, userInviteUrl, oauthProvider, utmInfo, userInvite, null);

    LeadRequestWithEmail leadRequestWithEmail =
        LeadRequestWithEmail.builder().action("createOrUpdate").lookupField("email").input(Arrays.asList(lead)).build();

    retrofit2.Response<Response> response =
        retrofit.create(MarketoRestClient.class).createLead(accessToken, leadRequestWithEmail).execute();
    log.info("Created lead with email: {} in marketo with oauth provider {}", email, oauthProvider);
    return response;
  }

  private retrofit2.Response<Response> updateLead(Retrofit retrofit, int existingLeadId, String email, String userName,
      Account account, String userInviteUrl, String accessToken, String oauthProvider, UtmInfo utmInfo,
      UserInvite userInvite) throws IOException {
    log.info("Updating lead {} to marketo", existingLeadId);

    LeadRequestWithId.LeadWithId leadWithId = LeadRequestWithId.LeadWithId.builder().build();
    buildLead(email, userName, account, userInviteUrl, oauthProvider, utmInfo, userInvite, leadWithId);
    leadWithId.setId(existingLeadId);
    LeadRequestWithId leadRequestWithId =
        LeadRequestWithId.builder().action("createOrUpdate").lookupField("id").input(Arrays.asList(leadWithId)).build();
    retrofit2.Response<Response> response =
        retrofit.create(MarketoRestClient.class).updateLead(accessToken, leadRequestWithId).execute();
    log.info("Updated lead {} to marketo", existingLeadId);
    return response;
  }

  private Lead buildLead(String email, String userName, Account account, String userInviteUrl, String oauthProvider,
      UtmInfo utmInfo, UserInvite userInvite, Lead lead) {
    if (lead == null) {
      lead = new Lead();
    }

    lead.setEmail(email);
    lead.setFirstName(utils.getFirstName(userName, email));
    lead.setLastName(utils.getLastName(userName, email));

    if (account != null) {
      if (isNotEmpty(oauthProvider)) {
        // In case of sso, make the company name null because it's populated by harness using email.
        lead.setCompany(null);
      } else {
        lead.setCompany(account.getCompanyName());
      }
      lead.setHarness_Account_ID__c_lead(account.getUuid());
      LicenseInfo licenseInfo = account.getLicenseInfo();
      if (licenseInfo != null) {
        lead.setFree_Trial_Status__c(licenseInfo.getAccountStatus());
        lead.setDays_Left_in_Trial__c(utils.getDaysLeft(licenseInfo.getExpiryTime()));
      }
    } else if (userInvite != null) {
      lead.setCompany(userInvite.getCompanyName());
    }

    if (isNotEmpty(userInviteUrl)) {
      lead.setFreemium_Invite_URL__c(userInviteUrl);
    }

    if (isNotEmpty(oauthProvider)) {
      lead.setSSO_Freemium_Type__c(oauthProvider);
    }

    if (utmInfo != null) {
      lead.setUTM_Source__c(utmInfo.getUtmSource());
      lead.setUTM_Content__c(utmInfo.getUtmContent());
      lead.setUTM_Medium__c(utmInfo.getUtmMedium());
      lead.setUTM_Term__c(utmInfo.getUtmTerm());
      lead.setUTM__c(utmInfo.getUtmCampaign());
    }

    if (userInvite != null && isNotEmpty(userInvite.getFreemiumProducts())) {
      lead.setFreemium_Products__c(join(userInvite.getFreemiumProducts(), ";"));
    }

    if (userInvite != null && userInvite.getFreemiumAssistedOption() != null) {
      lead.setFreemiumassistedoption(userInvite.getFreemiumAssistedOption());
    }

    if (userInvite != null && userInvite.getCountry() != null) {
      lead.setCountry(userInvite.getCountry());
    }

    if (userInvite != null && userInvite.getState() != null) {
      lead.setState(userInvite.getState());
    }

    if (userInvite != null && userInvite.getPhone() != null) {
      lead.setPhone(userInvite.getPhone());
    }
    return lead;
  }

  private long processLeadResponse(retrofit2.Response<Response> response) {
    long marketoLeadId = 0L;
    if (!response.isSuccessful()) {
      log.error(
          "Error while creating lead in marketo. Error code is {}, message is {}", response.code(), response.message());
      return marketoLeadId;
    }

    Response leadResponse = response.body();
    if (!leadResponse.isSuccess()) {
      log.error("Marketo http response reported failure while creating lead. {}",
          utils.getErrorMsg(leadResponse.getErrors()));
      return marketoLeadId;
    }

    List<Response.Result> results = leadResponse.getResult();
    if (isEmpty(results)) {
      log.error("Marketo http response reported empty result while creating lead");
      return marketoLeadId;
    }

    Response.Result result = results.get(0);

    String status = result.getStatus();
    if (!("updated".equalsIgnoreCase(status) || "created".equalsIgnoreCase(status))) {
      List<Error> reasons = result.getReasons();
      if (isEmpty(reasons)) {
        log.error("Marketo reported status {} for lead creation. No error reported in response", status);
      } else {
        Error error = reasons.get(0);
        log.error("Marketo reported status {} for lead creation. Error code is: {}, message is: {}", status,
            error.getCode(), error.getMessage());
      }
    }

    log.info("Marketo returned lead id {}", result.getId());
    return result.getId();
  }

  public String getAccessToken(String clientId, String clientSecret, Retrofit retrofit) throws IOException {
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
