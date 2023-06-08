/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.USER_DISABLED;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.saml.SamlClientService.SAML_TRIGGER_TYPE;

import static com.github.scribejava.core.model.OAuthConstants.ACCESS_TOKEN;
import static com.github.scribejava.core.model.OAuthConstants.CLIENT_CREDENTIALS;
import static com.github.scribejava.core.model.OAuthConstants.CLIENT_ID;
import static com.github.scribejava.core.model.OAuthConstants.CLIENT_SECRET;
import static com.github.scribejava.core.model.OAuthConstants.GRANT_TYPE;
import static com.github.scribejava.core.model.OAuthConstants.SCOPE;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.persistence.HPersistence;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.sso.SamlSettings;
import software.wings.logcontext.UserLogContext;
import software.wings.security.saml.SamlClientService;
import software.wings.security.saml.SamlClientService.HostType;
import software.wings.security.saml.SamlUserGroupSync;
import software.wings.service.impl.UserServiceHelper;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;

import com.coveo.saml.SamlClient;
import com.coveo.saml.SamlException;
import com.coveo.saml.SamlResponse;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.core.xml.schema.impl.XSAnyImpl;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;

@OwnedBy(PL)
@Singleton
@Slf4j
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class SamlBasedAuthHandler implements AuthHandler {
  @Inject private SamlClientService samlClientService;
  @Inject private AuthenticationUtils authenticationUtils;
  @Inject private SamlUserGroupSync samlUserGroupSync;
  @Inject private SSOSettingService ssoSettingService;
  @Inject private UserService userService;
  @Inject private UserServiceHelper userServiceHelper;
  @Inject private DomainWhitelistCheckerService domainWhitelistCheckerService;
  @Inject private NgSamlAuthorizationEventPublisher ngSamlAuthorizationEventPublisher;
  @Inject private SecretManager secretManager;
  @Inject private EncryptionService encryptionService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private HPersistence hPersistence;

  private static final String USER_ID_ATTR = "uid";
  static final String AZURE_GET_MEMBER_OBJECTS_URL_FORMAT =
      "https://graph.microsoft.com/v1.0/%s/users/%s/getMemberObjects";
  static final String AZURE_OAUTH_LOGIN_URL_FORMAT = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
  private static final String HTTPS_REGEX = "^\\w+://.*";

  @Override
  public AuthenticationResponse authenticate(String... credentials) throws IOException {
    try {
      if (credentials == null || !ImmutableList.of(2, 3, 4).contains(credentials.length)) {
        log.error("SAML: Wrong number of arguments to saml authentication - "
            + (credentials == null ? 0 : credentials.length));
        throw new WingsException("Invalid arguments while authenticating using SAML");
      }
      String idpUrl = credentials[0];
      String samlResponseString = credentials[1];
      String accountId = credentials.length >= 3 ? credentials[2] : null;
      idpUrl = populateIdPUrlIfEmpty(idpUrl, samlResponseString, accountId);
      log.info("SAML: Credentials got from SAML provider is {}, for accountId {}", credentials, accountId);
      String relayState = credentials.length >= 4 ? credentials[3] : "";
      Map<String, String> relayStateData = getRelayStateData(relayState);
      log.info("SAML: IdpURL is {}\n samlResponseString is {}\n accountId is {}\n relayStateData is {}", idpUrl,
          samlResponseString, accountId, relayStateData);
      User user = null;
      try {
        user = decodeResponseAndReturnUserByEmailId(idpUrl, samlResponseString, accountId);
        if (user != null) {
          log.info("SAML: The user was found with email Id {} for account {}", user.getEmail(), accountId);
        }
      } catch (Exception e) {
        log.info("SAML: The user was not found with email Id for account {}", accountId);
      }

      if (featureFlagService.isEnabled(FeatureName.EXTERNAL_USERID_BASED_LOGIN, accountId)) {
        User userByUserId = decodeResponseAndReturnUserByUserId(idpUrl, samlResponseString, accountId);
        if (user != null && userByUserId != null && !user.getEmail().equals(userByUserId.getEmail())) {
          log.info(
              "SAMLFeature: fetched user with externalUserId for accountId {} and difference in userEmail in user object {}, new userID is {}, old user object {}, old user id {}",
              accountId, userByUserId.getEmail(), userByUserId.getUuid(), user.getEmail(), user.getUuid());
          userByUserId.setEmail(user.getEmail());
          userByUserId.setAccounts(Stream.concat(user.getAccounts().stream(), userByUserId.getAccounts().stream())
                                       .distinct()
                                       .collect(Collectors.toList()));
          hPersistence.delete(user);
          hPersistence.save(userByUserId);
          user = userByUserId;
          log.info(
              "SAMLFeature: final user with externalUserId for accountId {} saved in db {}", accountId, userByUserId);
        }
        if (user == null && userByUserId != null) {
          accountId = StringUtils.isEmpty(accountId) ? userByUserId.getDefaultAccountId() : accountId;
          SamlSettings samlSettings = ssoSettingService.getSamlSettingsByAccountId(accountId);
          String email = getEmailIdFromSamlResponseString(samlResponseString, samlSettings);
          if (isEmpty(email)) {
            throw new InvalidRequestException("Email is not present in SAML assertion");
          }
          log.info("SAMLFeature: email fetched from response string is {} in accountId {}", email, accountId);
          userByUserId.setEmail(email.trim().toLowerCase());
          hPersistence.save(userByUserId);
          user = userByUserId;
          log.info(
              "SAMLFeature: final user with externalUserId for accountId {} saved in db {}", accountId, userByUserId);
        }
      }

      if (user == null) {
        throw new InvalidRequestException("User does not exist");
      }
      log.info("SAML: User {} is trying to login in accountId {} ", user.getEmail(), accountId);

      accountId = StringUtils.isEmpty(accountId) ? user.getDefaultAccountId() : accountId;
      String uuid = user.getUuid();
      try (AutoLogContext ignore = new UserLogContext(accountId, uuid, OVERRIDE_ERROR)) {
        Account account = authenticationUtils.getAccount(accountId);
        if (account == null) {
          account = authenticationUtils.getDefaultAccount(user);
        }
        if (!domainWhitelistCheckerService.isDomainWhitelisted(user, account)) {
          domainWhitelistCheckerService.throwDomainWhitelistFilterException();
        }
        log.info("SAML: Authenticating user with id {}, email {} via SAML in account {}", uuid, user.getEmail(),
            account.getUuid());
        // Occurs when SAML settings are being tested before being enabled
        if (!relayStateData.getOrDefault(SAML_TRIGGER_TYPE, "").equals("login")
            && account.getAuthenticationMechanism() != io.harness.ng.core.account.AuthenticationMechanism.SAML) {
          log.info("SAML test login successful for user: [{}], for account {}", user.getEmail(), accountId);
          throw new WingsException(ErrorCode.SAML_TEST_SUCCESS_MECHANISM_NOT_ENABLED);
        }

        List<SamlSettings> samlSettingsList = new ArrayList<>();
        boolean withMultipleIdpSupport = false;
        if (featureFlagService.isNotEnabled(FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId)) {
          samlSettingsList.add(ssoSettingService.getSamlSettingsByAccountId(account.getUuid()));
        } else {
          withMultipleIdpSupport = true;
          samlSettingsList.addAll(ssoSettingService.getSamlSettingsListByAccountId(account.getUuid()));
        }

        SamlSettings toSyncSamlSetting =
            getMatchingSamlSettingFromResponseAndIssuer(samlResponseString, samlSettingsList, withMultipleIdpSupport);

        if (null != toSyncSamlSetting && toSyncSamlSetting.isAuthorizationEnabled()) {
          List<String> userGroups = getUserGroupsForIdpUrl(idpUrl, samlResponseString, accountId);
          log.info("SAML: UserGroups synced for the user are {}, for account {}", userGroups.toString(), accountId);
          SamlUserAuthorization samlUserAuthorization =
              SamlUserAuthorization.builder().email(user.getEmail()).userGroups(userGroups).build();

          // Event Publisher
          samlUserGroupSync.syncUserGroup(samlUserAuthorization, account.getUuid(), toSyncSamlSetting.getUuid());
          synchronizeSamlUserGroups(
              samlUserAuthorization, userGroups, account.getUuid(), toSyncSamlSetting.getUuid(), user.getEmail());
        } else {
          log.warn(
              "SAML: No SamlSettings matched the saml response for account {}. UserGroup sync for saml would not have been triggered",
              account.getUuid());
        }

        if (withMultipleIdpSupport) {
          log.info(
              "SAML: MULTIPLE_IDP Syncing user groups for user {} on non signed-in saml settings linked to harness user groups in account {}",
              user.getEmail(), account.getUuid());
          processNGSamlGroupSyncForNotSignedInSamlSettings(
              samlSettingsList, toSyncSamlSetting, user.getEmail(), account.getUuid());
        }

        return new AuthenticationResponse(user);
      }
    } catch (URISyntaxException e) {
      throw new WingsException("Saml Authentication Failed", e);
    } catch (UnsupportedEncodingException e) {
      throw new WingsException("Saml Authentication Failed while parsing RelayState", e);
    } catch (SamlException e) {
      throw new InvalidRequestException("SAML: Could not authenticate with User Id for saml", e);
    }
  }

  @VisibleForTesting
  void processNGSamlGroupSyncForNotSignedInSamlSettings(
      List<SamlSettings> samlSettingsList, SamlSettings toSyncSamlSetting, String userEmail, String userAccountId) {
    samlSettingsList.stream()
        .filter(Objects::nonNull)
        .filter(SamlSettings::isAuthorizationEnabled)
        .filter(setting -> toSyncSamlSetting != null && !setting.getUuid().equals(toSyncSamlSetting.getUuid()))
        .forEach(setting -> {
          SamlUserAuthorization samlUserAuthorization = SamlUserAuthorization.builder()
                                                            .email(userEmail)
                                                            .userGroups(new ArrayList<>())
                                                            .build(); // new ArrayList<>() for empty groups
          synchronizeSamlUserGroups(
              samlUserAuthorization, new ArrayList<>(), userAccountId, setting.getUuid(), userEmail);
        });
  }

  private void synchronizeSamlUserGroups(SamlUserAuthorization samlUserAuthorization, List<String> userGroups,
      String accountUuid, String samlSettingUuid, String userEmail) {
    try {
      ngSamlAuthorizationEventPublisher.publishSamlAuthorizationAssertion(
          samlUserAuthorization, accountUuid, samlSettingUuid);
    } catch (Exception e) {
      log.error(
          "SAML: Exception in publishing event for SAML Assertion for user {} with account {} userGroups {} and SSO id {} ",
          userEmail, accountUuid, userGroups, samlSettingUuid);
    }
  }

  @VisibleForTesting
  String populateIdPUrlIfEmpty(String idpUrl, String samlResponseString, String accountId) throws URISyntaxException {
    if (isEmpty(idpUrl) && !isEmpty(accountId)) {
      if (featureFlagService.isNotEnabled(FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId)) {
        SamlSettings samlSettings = ssoSettingService.getSamlSettingsByAccountId(accountId);
        idpUrl = getIdpUrlFromSamlSettingsOrigin(samlSettings);
      } else {
        List<SamlSettings> settingsList = ssoSettingService.getSamlSettingsListByAccountId(accountId);
        SamlSettings resultSettings =
            settingsList.stream()
                .filter(setting -> entityIdForSamlResponseMatchesWithSamlSettings(samlResponseString, setting))
                .findFirst()
                .orElse(null);
        if (null != resultSettings) {
          idpUrl = getIdpUrlFromSamlSettingsOrigin(resultSettings);
        }
      }
    }
    return idpUrl;
  }

  private String getIdpUrlFromSamlSettingsOrigin(SamlSettings setting) throws URISyntaxException {
    String resultIdpUrl = setting.getOrigin();
    if (isNotEmpty(resultIdpUrl) && !resultIdpUrl.toLowerCase().matches(HTTPS_REGEX)) {
      resultIdpUrl = (new URIBuilder()).setScheme("https").setHost(resultIdpUrl).build().toString();
    }
    return resultIdpUrl;
  }

  @VisibleForTesting
  SamlSettings getMatchingSamlSettingFromResponseAndIssuer(
      String samlResponseString, List<SamlSettings> samlSettingsList, boolean withMultipleIdPSupport) {
    if (!withMultipleIdPSupport) {
      return samlSettingsList.get(0);
    }
    SamlSettings toSyncSamlSetting = null;
    for (SamlSettings settingsValue : samlSettingsList) {
      if (Objects.nonNull(settingsValue) && settingsValue.isAuthorizationEnabled()) {
        try {
          SamlClient samlClient = samlClientService.getSamlClient(settingsValue);
          SamlResponse samlResponse = samlClient.decodeAndValidateSamlResponse(samlResponseString);
          Assertion samlAssertionValue = samlResponse.getAssertion();
          if (settingsValue.getMetaDataFile() != null && samlAssertionValue.getIssuer() != null
              && settingsValue.getMetaDataFile().contains(samlAssertionValue.getIssuer().getValue())) {
            if (isNotEmpty(settingsValue.getEntityIdentifier())) {
              if (samlAssertionValue.getConditions() != null
                  && samlAssertionValue.getConditions().getAudienceRestrictions() != null
                  && samlAssertionValue.getConditions()
                         .getAudienceRestrictions()
                         .stream()
                         .filter(Objects::nonNull)
                         .anyMatch(ar
                             -> ar.getAudiences()
                                    .stream()
                                    .filter(Objects::nonNull)
                                    .anyMatch(audience
                                        -> audience.getAudienceURI() != null
                                            && audience.getAudienceURI().equalsIgnoreCase(
                                                settingsValue.getEntityIdentifier())))) {
                toSyncSamlSetting = settingsValue;
                break;
              }
            } else {
              toSyncSamlSetting = settingsValue;
              break;
            }
          }
        } catch (SamlException samlExc) {
          // continue with next samlSetting in iterator
        }
      }
    }
    return toSyncSamlSetting;
  }

  private Map<String, String> getRelayStateData(String relayState) throws UnsupportedEncodingException {
    Map<String, String> relayStateData = new HashMap<>();
    if (StringUtils.isEmpty(relayState)) {
      return relayStateData;
    }

    String[] pairs = relayState.split("&");
    for (String pair : pairs) {
      String[] items = pair.split("=");
      relayStateData.put(URLDecoder.decode(items[0], "UTF-8"), URLDecoder.decode(items[1], "UTF-8"));
    }
    return relayStateData;
  }

  private User decodeResponseAndReturnUserByUserId(String idpUrl, String samlResponseString, String accountId)
      throws URISyntaxException {
    String userIdFromSamlResponse = getUserIdForIdpUrl(idpUrl, samlResponseString, accountId);
    log.info("SAML: fetched userId {} from saml response for accountId {}", userIdFromSamlResponse, accountId);
    if (isNotEmpty(userIdFromSamlResponse)) {
      userIdFromSamlResponse = userIdFromSamlResponse.toLowerCase();
    }
    User userFromUserId = userService.getUserByUserId(accountId, userIdFromSamlResponse);
    log.info("SAML: fetched user with externalUserId {} for accountId {} and user object {}", userIdFromSamlResponse,
        accountId, userFromUserId);
    return userFromUserId;
  }

  private User decodeResponseAndReturnUserByEmailId(String idpUrl, String samlResponseString, String accountId)
      throws URISyntaxException {
    String host = samlClientService.getHost(idpUrl);
    log.info("SAML: host is {}, for accountId {}", host, accountId);
    HostType hostType = samlClientService.getHostType(idpUrl);
    switch (hostType) {
      case GOOGLE: {
        Iterator<SamlSettings> samlSettingsIterator = samlClientService.getSamlSettingsFromOrigin(host, accountId);
        if (samlSettingsIterator != null) {
          while (samlSettingsIterator.hasNext()) {
            final SamlSettings samlSettings = samlSettingsIterator.next();
            try {
              Optional<NameValuePair> nameValuePair = URLEncodedUtils.parse(new URI(samlSettings.getUrl()), "UTF-8")
                                                          .stream()
                                                          .filter(pair -> pair.getName().equals("idpid"))
                                                          .findFirst();
              if (nameValuePair.isPresent() && idpUrl.contains(nameValuePair.get().getValue())) {
                return getUser(samlResponseString, samlSettings);
              }
            } catch (SamlException e) {
              log.warn("SAML: Could not validate SAML Response idpUrl:[{}], samlSettings url:[{}], for accountId {}",
                  idpUrl, samlSettings.getUrl(), accountId, e);
            }
          }

          // if you have reached here, it means none of the SAML IDP metadata matched, try a brute force approach the
          // 2nd time
          User user = getUserForIdpUrl(idpUrl, samlResponseString, accountId);
          if (user != null) {
            return user;
          }
        }
      } break;
      case AZURE: {
        Iterator<SamlSettings> samlSettingsIterator = samlClientService.getSamlSettingsFromOrigin(host, accountId);
        if (samlSettingsIterator != null) {
          while (samlSettingsIterator.hasNext()) {
            SamlSettings samlSettings = samlSettingsIterator.next();
            if (idpUrl.startsWith(samlSettings.getUrl())) {
              try {
                return getUser(samlResponseString, samlSettings);
              } catch (SamlException e) {
                log.warn("SAML: Could not validate SAML Response idpUrl:[{}], samlSettings url:[{}], for accountId {}",
                    idpUrl, samlSettings.getUrl(), accountId, e);
              }
            }
          }
          // if you have reached here, it means none of the SAML IDP metadata matched, try a brute force approach the
          // 2nd time
          User user = getUserForIdpUrl(idpUrl, samlResponseString, accountId);
          if (user != null) {
            return user;
          }
        }
      }

      break;
      default: {
        User user = getUserForIdpUrl(idpUrl, samlResponseString, accountId);
        if (user != null) {
          return user;
        }
      }
    }

    log.info("SAML: No IDP metadata could be found for URL [{}], for account {}", idpUrl, accountId);
    throw new WingsException("Saml Authentication Failed");
  }

  private String getEmailIdFromSamlResponseString(String samlResponseString, SamlSettings samlSettings)
      throws SamlException {
    SamlClient samlClient = samlClientService.getSamlClient(samlSettings);
    SamlResponse samlResponse = samlClient.decodeAndValidateSamlResponse(samlResponseString);
    return samlResponse.getNameID();
  }

  private User getUserForIdpUrl(String idpUrl, String samlResponseString, String accountId) throws URISyntaxException {
    String host = new URI(idpUrl).getHost();
    Iterator<SamlSettings> samlSettingsIterator =
        featureFlagService.isNotEnabled(FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId)
        ? samlClientService.getSamlSettingsFromOrigin(host, accountId)
        : ssoSettingService.getSamlSettingsIteratorByAccountId(accountId);
    if (samlSettingsIterator != null) {
      while (samlSettingsIterator.hasNext()) {
        SamlSettings samlSettings = samlSettingsIterator.next();
        try {
          User user = getUser(samlResponseString, samlSettings);
          if (null != user) {
            return user;
          }
        } catch (SamlException e) {
          log.warn("SAML: Could not validate SAML Response idpUrl:[{}], samlSettings url:[{}] for account {}", idpUrl,
              samlSettings.getUrl(), accountId, e);
        }
      }
    }
    return null;
  }

  private List<String> getUserGroupsForIdpUrl(String idpUrl, String samlResponseString, String accountId)
      throws URISyntaxException {
    String host = new URI(idpUrl).getHost();
    final HostType hostType = samlClientService.getHostType(idpUrl);
    Iterator<SamlSettings> samlSettingsIterator =
        featureFlagService.isNotEnabled(FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId)
        ? samlClientService.getSamlSettingsFromOrigin(host, accountId)
        : ssoSettingService.getSamlSettingsIteratorByAccountId(accountId);
    if (samlSettingsIterator != null) {
      while (samlSettingsIterator.hasNext()) {
        SamlSettings samlSettings = samlSettingsIterator.next();
        try {
          SamlClient samlClient = samlClientService.getSamlClient(samlSettings);
          SamlResponse samlResponse = samlClient.decodeAndValidateSamlResponse(samlResponseString);
          Assertion samlAssertionValue = samlResponse.getAssertion();
          List<AttributeStatement> attributeStatements = samlAssertionValue.getAttributeStatements();
          final String groupMembershipAttr = samlSettings.getGroupMembershipAttr();

          switch (hostType) {
            case AZURE:
              return getUserGroupsForAzure(attributeStatements, samlSettings, groupMembershipAttr,
                  samlAssertionValue.getIssuer().getValue(), accountId);
            case GOOGLE:
            case OTHER:
            default:
              return getUserGroups(attributeStatements, groupMembershipAttr, accountId);
          }
        } catch (Exception e) {
          log.warn("SAML: Could not fetch userGroups for Account: {}", accountId, e);
        }
      }
    }
    log.info("SAML: Authorization failed for Saml with idp URL [{}], for accountID {}", idpUrl, accountId);
    throw new WingsException("Saml Authorization Failed");
  }

  public String getUserIdForIdpUrl(String idpUrl, String samlResponseString, String accountId)
      throws URISyntaxException {
    String host = new URI(idpUrl).getHost();
    final HostType hostType = samlClientService.getHostType(idpUrl);
    Iterator<SamlSettings> samlSettingsIterator = samlClientService.getSamlSettingsFromOrigin(host, accountId);
    if (samlSettingsIterator != null) {
      while (samlSettingsIterator.hasNext()) {
        SamlSettings samlSettings = samlSettingsIterator.next();
        try {
          SamlClient samlClient = samlClientService.getSamlClient(samlSettings);
          SamlResponse samlResponse = samlClient.decodeAndValidateSamlResponse(samlResponseString);
          Assertion samlAssertionValue = samlResponse.getAssertion();
          List<AttributeStatement> attributeStatements = samlAssertionValue.getAttributeStatements();
          final String userIdAttr = samlSettings.getUserIdAttr() != null ? samlSettings.getUserIdAttr() : USER_ID_ATTR;
          log.info("SAMLFeature: userIdAttr {} for accountId {} ", userIdAttr, accountId);

          switch (hostType) {
            case AZURE:
              return getUserIdForAzure(
                  attributeStatements, samlSettings, userIdAttr, samlAssertionValue.getIssuer().getValue(), accountId);
            case GOOGLE:
            case OTHER:
            default:
              return getUserId(attributeStatements, userIdAttr, accountId);
          }
        } catch (Exception e) {
          log.error("SAML: Could not fetch the userId for Account {}", accountId, e);
        }
      }
    }
    log.info("SAML: Authorization failed for Saml with idp URL [{}], for account {}", idpUrl, accountId);
    throw new WingsException("Saml Authorization Failed");
  }

  private List<String> getUserGroups(
      List<AttributeStatement> attributeStatements, final String groupMembershipAttr, String accountId) {
    List<String> userGroups = new ArrayList<>();
    for (AttributeStatement attributeStatement : attributeStatements) {
      for (Attribute attribute : attributeStatement.getAttributes()) {
        if (attribute.getName().equals(groupMembershipAttr)) {
          for (XMLObject xmlObject : attribute.getAttributeValues()) {
            final String userGroup = getAttributeValue(xmlObject);
            if (userGroup != null) {
              userGroups.add(userGroup);
            }
          }
        }
      }
    }
    log.info("SAML: groupMembershipAttr {} and fetched {}, for account {}", groupMembershipAttr, userGroups, accountId);
    return userGroups;
  }

  private String getUserId(List<AttributeStatement> attributeStatements, final String userIdAttr, String accountId) {
    List<String> userIds = new ArrayList<>();
    for (AttributeStatement attributeStatement : attributeStatements) {
      for (Attribute attribute : attributeStatement.getAttributes()) {
        if (attribute.getName().equals(userIdAttr)) {
          for (XMLObject xmlObject : attribute.getAttributeValues()) {
            final String userId = getAttributeValue(xmlObject);
            if (userId != null) {
              userIds.add(userId);
            }
          }
        }
      }
    }
    log.info("SAMLFeature: userIdAttr {} and fetched {}, for account {}", userIdAttr, userIds, accountId);
    return isNotEmpty(userIds) ? userIds.get(0).toLowerCase() : "";
  }

  @VisibleForTesting
  List<String> getUserGroupsForAzure(List<AttributeStatement> attributeStatements, SamlSettings samlSettings,
      final String groupMembershipAttr, final String issuerURIString, final String accountId) {
    log.info("SAML: IssuerURI from Azure is {}, for accountId {}", issuerURIString, accountId);
    List<String> userGroups = new ArrayList<>();

    boolean hasAzureSAMLGroupsLink = false;
    String azureUserId = null;
    final String azureMicrosoftString = "microsoft";
    for (AttributeStatement attributeStatement : attributeStatements) {
      for (Attribute attribute : attributeStatement.getAttributes()) {
        if (attribute.getName().equals(groupMembershipAttr)) {
          for (XMLObject xmlObject : attribute.getAttributeValues()) {
            final String userGroup = getAttributeValue(xmlObject);
            if (userGroup != null) {
              log.info("SAML: userGroup fetched from Azure is {}, for accountId {}", userGroup, accountId);
              userGroups.add(userGroup);
            }
          }
        } else {
          log.info("SAML: attribute from Azure is {}, for accountId {}", attribute, accountId);
          if (attribute.getName().endsWith("groups.link")
              && attribute.getName().contains(
                  azureMicrosoftString)) { // occurs for Azure SAML case when no of groups > 150
            hasAzureSAMLGroupsLink = true;
          }

          if (attribute.getName().endsWith("objectidentifier")
              && attribute.getName().contains(
                  azureMicrosoftString)) { // occurs for Azure SAML case when no of groups > 150
            azureUserId = getAttributeValue(attribute.getAttributeValues().get(0));
          }
        }
      }
    }

    if (hasAzureSAMLGroupsLink && isNotEmpty(azureUserId)) {
      URI issuerUri = URI.create(issuerURIString);
      final String tenantId = isNotEmpty(issuerUri.getPath()) ? issuerUri.getPath().replace("/", "") : "";
      log.info("SAML: Tenant Id received from Azure is {}, for accountId {}", tenantId, accountId);
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) samlSettings, null, null);
      encryptionService.decrypt(samlSettings, encryptionDetails, false);

      if (isNotEmpty(samlSettings.getClientId()) && isNotEmpty(samlSettings.getClientSecret())) {
        final String accessToken = this.getAccessTokenForAzure(
            tenantId, samlSettings.getClientId(), String.valueOf(samlSettings.getClientSecret()), accountId);

        if (isNotEmpty(accessToken)) {
          try {
            userGroups.addAll(this.getUsersGroupResource(accessToken, tenantId, azureUserId, accountId));
          } catch (IOException | JSONException exc) {
            log.error("SAML: Getting azure user groups failed in case of more than 150 groups for Account: {}",
                accountId, exc);
          }
        }
      }
    }
    log.info("SAML: User groups fetched for Azure SAML are {}, for accountId {}", userGroups, accountId);
    return userGroups;
  }

  @VisibleForTesting
  String getUserIdForAzure(List<AttributeStatement> attributeStatements, SamlSettings samlSettings,
      final String userIdAttr, final String issuerURIString, final String accountId) {
    log.info("SAML: IssuerURI from Azure is {}, for accountId {}", issuerURIString, accountId);
    List<String> userIds = new ArrayList<>();

    boolean hasAzureSAMLGroupsLink = false;
    String azureUserId = null;
    final String azureMicrosoftString = "microsoft";
    for (AttributeStatement attributeStatement : attributeStatements) {
      for (Attribute attribute : attributeStatement.getAttributes()) {
        if (attribute.getName().equals(userIdAttr)) {
          for (XMLObject xmlObject : attribute.getAttributeValues()) {
            final String userId = getAttributeValue(xmlObject);
            if (userId != null) {
              log.info("SAML: user fetched from Azure is {}, for accountId {}", userId, accountId);
              userIds.add(userId);
            }
          }
        } else {
          log.info("SAML: attribute from Azure is {}, for accountId {}", attribute, accountId);
          if (attribute.getName().endsWith("groups.link")
              && attribute.getName().contains(
                  azureMicrosoftString)) { // occurs for Azure SAML case when no of groups > 150
            hasAzureSAMLGroupsLink = true;
          }

          if (attribute.getName().endsWith("objectidentifier")
              && attribute.getName().contains(
                  azureMicrosoftString)) { // occurs for Azure SAML case when no of groups > 150
            azureUserId = getAttributeValue(attribute.getAttributeValues().get(0));
          }
        }
      }
    }

    if (hasAzureSAMLGroupsLink && isNotEmpty(azureUserId)) {
      URI issuerUri = URI.create(issuerURIString);
      final String tenantId = isNotEmpty(issuerUri.getPath()) ? issuerUri.getPath().replace("/", "") : "";
      log.info("SAML: Tenant Id of Azure AD is {}, for accountId {}", tenantId, accountId);
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) samlSettings, null, null);
      encryptionService.decrypt(samlSettings, encryptionDetails, false);

      if (isNotEmpty(samlSettings.getClientId()) && isNotEmpty(samlSettings.getClientSecret())) {
        log.info("SAML: Client Id for Azure AD is {}, for accountId {}", samlSettings.getClientId(), accountId);
        final String accessToken = this.getAccessTokenForAzure(
            tenantId, samlSettings.getClientId(), String.valueOf(samlSettings.getClientSecret()), accountId);

        if (isNotEmpty(accessToken)) {
          try {
            userIds.addAll(this.getUsersGroupResource(accessToken, tenantId, azureUserId, accountId));
          } catch (IOException | JSONException exc) {
            log.error("SAML: Getting azure user groups failed in case of more than 150 groups for Account: {}",
                accountId, exc);
          }
        }
      }
    }
    log.info("SAMLFeature: userIdAttr {} and fetched {}, for accountId {}", userIdAttr, userIds, accountId);
    return isNotEmpty(userIds) ? userIds.get(0).toLowerCase() : "";
  }

  private String getAttributeValue(XMLObject attributeValue) {
    if (attributeValue == null) {
      return null;
    } else {
      if (attributeValue instanceof XSString) {
        return getStringAttributeValue((XSString) attributeValue);
      } else if (attributeValue instanceof XSAnyImpl) {
        return getAnyAttributeValue((XSAnyImpl) attributeValue);
      } else {
        return attributeValue.toString();
      }
    }
  }

  private String getStringAttributeValue(XSString attributeValue) {
    return attributeValue.getValue();
  }

  private String getAnyAttributeValue(XSAnyImpl attributeValue) {
    return attributeValue.getTextContent();
  }

  private boolean matchAssertionsForJit(
      Assertion samlAssertionValue, String jitValidationKey, String jitValidationValue) {
    List<AttributeStatement> attributeStatements = samlAssertionValue.getAttributeStatements();
    for (AttributeStatement attributeStatement : attributeStatements) {
      for (Attribute attribute : attributeStatement.getAttributes()) {
        if (attribute.getName().equals(jitValidationKey)) {
          for (XMLObject xmlObject : attribute.getAttributeValues()) {
            final String value = getAttributeValue(xmlObject);
            if (Objects.equals(value, jitValidationValue)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private boolean entityIdForSamlResponseMatchesWithSamlSettings(String samlResponseStr, SamlSettings settings) {
    try {
      SamlClient samlClient = samlClientService.getSamlClient(settings);
      SamlResponse samlResponse = samlClient.decodeAndValidateSamlResponse(samlResponseStr);
      Assertion samlAssertionValue = samlResponse.getAssertion();
      if (settings.getMetaDataFile() != null && samlAssertionValue.getIssuer() != null
          && settings.getMetaDataFile().contains(samlAssertionValue.getIssuer().getValue())) {
        if (isNotEmpty(settings.getEntityIdentifier()) && samlAssertionValue.getConditions() != null
            && samlAssertionValue.getConditions().getAudienceRestrictions() != null) {
          return samlAssertionValue.getConditions()
              .getAudienceRestrictions()
              .stream()
              .filter(Objects::nonNull)
              .anyMatch(ar
                  -> ar.getAudiences()
                         .stream()
                         .filter(Objects::nonNull)
                         .anyMatch(audience
                             -> audience.getAudienceURI() != null
                                 && audience.getAudienceURI().equalsIgnoreCase(settings.getEntityIdentifier())));
        } else {
          return true;
        }
      }
    } catch (SamlException e) {
      // do nothing
    }
    return false;
  }

  private User getUser(String samlResponseString, SamlSettings samlSettings) throws SamlException {
    try {
      SamlClient samlClient = samlClientService.getSamlClient(samlSettings);
      SamlResponse samlResponse = samlClient.decodeAndValidateSamlResponse(samlResponseString);
      String email = samlResponse.getNameID();
      User user = userService.getUserByEmail(email, samlSettings.getAccountId());
      if (user == null) {
        if (isUserApplicableToJustInTimeUserProvision(samlResponse, samlSettings)) {
          return createNewUserOrAddUserToAccountViaJIT(email, samlSettings.getAccountId());
        }
        log.info("User {} does not exists.", email);
        throw new WingsException(ErrorCode.USER_DOES_NOT_EXIST);
      } else if (user.isDisabled()) {
        log.info("User {} is disabled.", email);
        throw new WingsException(USER_DISABLED, USER);
      } else if (isUserApplicableToJustInTimeUserProvision(samlResponse, samlSettings)
          && !userServiceHelper.isUserActiveInNG(user, samlSettings.getAccountId())) {
        // this is to handle the case where the user is applicable to just in time user provision and is only part of cg
        // and not part of ng
        return createNewUserOrAddUserToAccountViaJIT(email, samlSettings.getAccountId());
      }
      return user;
    } catch (SamlException e) {
      log.warn("SAML: SamlResponse cannot be validated for saml settings id=[{}], url=[{}], accountId=[{}]",
          samlSettings.getUuid(), samlSettings.getUrl(), samlSettings.getAccountId());
    }
    return null;
  }

  private boolean isUserApplicableToJustInTimeUserProvision(SamlResponse samlResponse, SamlSettings samlSettings)
      throws SamlException {
    return samlSettings.isJitEnabled() && checkForKeyValuePairAndMatchWithAssertion(samlResponse, samlSettings);
  }

  private boolean checkForKeyValuePairAndMatchWithAssertion(SamlResponse samlResponse, SamlSettings samlSettings)
      throws SamlException {
    if (samlResponse == null) {
      return false;
    }
    Assertion samlAssertionValue = samlResponse.getAssertion();
    // if samlSettings does not have key-value pair set, this mean all users are to be JIT provisioned
    return isEmpty(samlSettings.getJitValidationKey()) || isEmpty(samlSettings.getJitValidationValue())
        || matchAssertionsForJit(
            samlAssertionValue, samlSettings.getJitValidationKey(), samlSettings.getJitValidationValue());
  }

  private User createNewUserOrAddUserToAccountViaJIT(String email, String accountId) {
    return userService.completeUserCreationOrAdditionViaJitAndSignIn(email, accountId);
  }

  private String getAccessTokenForAzure(
      final String tenantId, final String clientId, final String clientSecretKey, String accountId) {
    log.info("SAML: clientId for Azure Ad is {}, for accountId {}", clientId, accountId);
    RequestBody authenticationPayload = new MultipartBody.Builder()
                                            .setType(MultipartBody.FORM)
                                            .addFormDataPart(GRANT_TYPE, CLIENT_CREDENTIALS)
                                            .addFormDataPart(CLIENT_ID, clientId)
                                            .addFormDataPart(CLIENT_SECRET, clientSecretKey)
                                            .addFormDataPart(SCOPE, "https://graph.microsoft.com/.default")
                                            .build();

    final String authUrl = String.format(AZURE_OAUTH_LOGIN_URL_FORMAT, tenantId);
    Request request = new Request.Builder()
                          .url(authUrl)
                          .post(authenticationPayload)
                          .addHeader(CONTENT_TYPE, APPLICATION_FORM_URLENCODED)
                          .build();
    OkHttpClient azureClient = new OkHttpClient();
    try (Response response = azureClient.newCall(request).execute()) {
      JSONObject jsonObject = new JSONObject(response.body().string());
      return jsonObject.getString(ACCESS_TOKEN);
    } catch (Exception ex) {
      log.error("SAML: Getting azure access token failed, for accountId {}", accountId, ex);
      return null;
    }
  }

  private List<String> getUsersGroupResource(
      final String accessToken, final String tenantId, final String userId, String accountId) throws IOException {
    final String membersGroupString = "{\"securityEnabledOnly\": false}";
    MediaType applicationJsonType = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(applicationJsonType, membersGroupString);
    String resourceUrl = String.format(AZURE_GET_MEMBER_OBJECTS_URL_FORMAT, tenantId, userId);
    Request request = new Request.Builder()
                          .url(resourceUrl)
                          .addHeader("Authorization", "Bearer " + accessToken)
                          .method("POST", body)
                          .build();

    OkHttpClient client = new OkHttpClient();
    Response response = client.newCall(request).execute();
    JSONObject jsonObject = new JSONObject(response.body().string());
    JSONArray groupIdValues = jsonObject.getJSONArray("value");
    List<String> userGroups = new ArrayList<>();
    for (int i = 0; i < groupIdValues.length(); i++) {
      userGroups.add(groupIdValues.getString(i));
    }
    log.info("SAML: User groups fetched are {}, for accountId {}", userGroups, accountId);
    return userGroups;
  }

  // TODO : revisit this method when we are doing SAML authorization
  protected void validateUser(User user, String accountId) {
    if (user.getAccounts().parallelStream().noneMatch(account -> account.getUuid().equals(accountId))) {
      log.warn("SAML: User : [{}] not part of accountId : [{}]", user.getEmail(), accountId);
      throw new WingsException(ErrorCode.ACCESS_DENIED);
    }
  }

  @Override
  public io.harness.ng.core.account.AuthenticationMechanism getAuthenticationMechanism() {
    return AuthenticationMechanism.SAML;
  }
}