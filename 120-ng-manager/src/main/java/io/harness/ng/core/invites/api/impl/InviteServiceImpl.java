/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.invites.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.accesscontrol.PlatformPermissions.INVITE_PERMISSION_IDENTIFIER;
import static io.harness.ng.core.invites.InviteType.ADMIN_INITIATED_INVITE;
import static io.harness.ng.core.invites.InviteType.USER_INITIATED_INVITE;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.FAIL;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.INVITE_EXPIRED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.INVITE_INVALID;
import static io.harness.ng.core.invites.mapper.InviteMapper.toInviteList;
import static io.harness.ng.core.invites.mapper.InviteMapper.writeDTO;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.sanitizeRoleBindings;
import static io.harness.ng.core.user.UserMembershipUpdateSource.ACCEPTED_INVITE;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.regex.Pattern.quote;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.invites.remote.InviteAcceptResponse;
import io.harness.mongo.MongoConfig;
import io.harness.ng.accesscontrol.user.ACLAggregateFilter;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.UserInviteDTO;
import io.harness.ng.core.events.UserInviteCreateEvent;
import io.harness.ng.core.events.UserInviteDeleteEvent;
import io.harness.ng.core.events.UserInviteUpdateEvent;
import io.harness.ng.core.invites.JWTGeneratorUtils;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.invites.dto.CreateInviteDTO;
import io.harness.ng.core.invites.dto.InviteDTO;
import io.harness.ng.core.invites.dto.InviteOperationResponse;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.invites.dto.RoleBinding.RoleBindingKeys;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.invites.entities.Invite.InviteKeys;
import io.harness.ng.core.invites.utils.InviteUtils;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.EmailChannel.EmailChannelBuilder;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.RestClientUtils;
import io.harness.repositories.invites.spring.InviteRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryReporter;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;
import io.harness.utils.PageUtils;
import io.harness.utils.RetryUtils;

import com.auth0.jwt.interfaces.Claim;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.MongoClientURI;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Singleton
@Slf4j
@OwnedBy(PL)
public class InviteServiceImpl implements InviteService {
  private static final int INVITATION_VALIDITY_IN_DAYS = 30;
  private static final int LINK_VALIDITY_IN_DAYS = 7;
  private static final int DEFAULT_PAGE_SIZE = 1000;
  private static final String INVITE_URL =
      "/invite?accountId=%s&account=%s&company=%s&email=%s&inviteId=%s&generation=NG";
  private static final String NG_AUTH_UI_PATH_PREFIX = "auth/";
  private static final String NG_ACCOUNT_CREATION_FRAGMENT =
      "accountIdentifier=%s&email=%s&token=%s&returnUrl=%s&generation=NG";
  private static final String ACCEPT_INVITE_PATH = "ng/api/invites/verify";
  private static final String USER_INVITE = "user_invite";
  private final String jwtPasswordSecret;
  private final JWTGeneratorUtils jwtGeneratorUtils;
  private final NgUserService ngUserService;
  private final InviteRepository inviteRepository;
  private final boolean useMongoTransactions;
  private final TransactionTemplate transactionTemplate;
  private final NotificationClient notificationClient;
  private final AccountClient accountClient;
  private final OutboxService outboxService;
  private final AccessControlClient accessControlClient;
  private final boolean isNgAuthUIEnabled;
  private final UserClient userClient;
  private final AccountOrgProjectHelper accountOrgProjectHelper;
  private final TelemetryReporter telemetryReporter;

  private final RetryPolicy<Object> transactionRetryPolicy =
      RetryUtils.getRetryPolicy("[Retrying]: Failed to mark previous invites as stale; attempt: {}",
          "[Failed]: Failed to mark previous invites as stale; attempt: {}",
          ImmutableList.of(TransactionException.class), Duration.ofSeconds(1), 3, log);

  @Inject
  public InviteServiceImpl(@Named("userVerificationSecret") String jwtPasswordSecret, MongoConfig mongoConfig,
      JWTGeneratorUtils jwtGeneratorUtils, NgUserService ngUserService, TransactionTemplate transactionTemplate,
      InviteRepository inviteRepository, NotificationClient notificationClient, AccountClient accountClient,
      OutboxService outboxService, AccessControlClient accessControlClient, UserClient userClient,
      AccountOrgProjectHelper accountOrgProjectHelper, @Named("isNgAuthUIEnabled") boolean isNgAuthUIEnabled,
      TelemetryReporter telemetryReporter) {
    this.jwtPasswordSecret = jwtPasswordSecret;
    this.jwtGeneratorUtils = jwtGeneratorUtils;
    this.ngUserService = ngUserService;
    this.inviteRepository = inviteRepository;
    this.transactionTemplate = transactionTemplate;
    this.notificationClient = notificationClient;
    this.accountClient = accountClient;
    this.userClient = userClient;
    this.outboxService = outboxService;
    this.isNgAuthUIEnabled = isNgAuthUIEnabled;
    this.accessControlClient = accessControlClient;
    this.accountOrgProjectHelper = accountOrgProjectHelper;
    this.telemetryReporter = telemetryReporter;
    MongoClientURI uri = new MongoClientURI(mongoConfig.getUri());
    useMongoTransactions = uri.getHosts().size() > 2;
  }

  @Override
  public InviteOperationResponse create(Invite invite, boolean isScimInvite) {
    if (invite == null) {
      return FAIL;
    }
    checkPermissions(invite.getAccountIdentifier(), invite.getOrgIdentifier(), invite.getProjectIdentifier(),
        INVITE_PERMISSION_IDENTIFIER);
    preCreateInvite(invite);
    if (checkIfUserAlreadyAdded(invite)) {
      return InviteOperationResponse.USER_ALREADY_ADDED;
    }

    //    if (!isInviteAcceptanceRequired) {
    //      updateJWTTokenInInvite(invite);
    //      // For SCIM user creation flow
    //      createAndInviteNonPasswordUser(invite.getAccountIdentifier(), invite.getInviteToken(), invite.getEmail());
    //      return InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED;
    //    }

    Optional<Invite> existingInviteOptional = getExistingInvite(invite);
    if (existingInviteOptional.isPresent()) {
      if (TRUE.equals(existingInviteOptional.get().getApproved())) {
        return InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED;
      }
      wrapperForTransactions(this::resendInvite, existingInviteOptional.get());
      return InviteOperationResponse.USER_ALREADY_INVITED;
    }
    try {
      return wrapperForTransactions(this::newInvite, invite, isScimInvite);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(getExceptionMessage(invite), USER_SRE, ex);
    }
  }

  @Override
  public List<InviteOperationResponse> createInvitations(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, CreateInviteDTO createInviteDTO) {
    List<InviteOperationResponse> inviteOperationResponses = new ArrayList<>();
    List<Invite> invites = toInviteList(createInviteDTO, accountIdentifier, orgIdentifier, projectIdentifier);
    for (Invite invite : invites) {
      try {
        InviteOperationResponse response = create(invite, false);
        inviteOperationResponses.add(response);
      } catch (DuplicateFieldException ex) {
        log.error("error: ", ex);
      }
    }
    return inviteOperationResponses;
  }

  private void preCreateInvite(Invite invite) {
    List<RoleBinding> roleBindings = invite.getRoleBindings();
    sanitizeRoleBindings(roleBindings, invite.getOrgIdentifier(), invite.getProjectIdentifier());
  }

  private boolean checkIfUserAlreadyAdded(Invite invite) {
    Optional<UserMetadataDTO> userOptional = ngUserService.getUserByEmail(invite.getEmail(), false);
    return userOptional
        .filter(user
            -> ngUserService.isUserAtScope(user.getUuid(),
                Scope.builder()
                    .accountIdentifier(invite.getAccountIdentifier())
                    .orgIdentifier(invite.getOrgIdentifier())
                    .projectIdentifier(invite.getProjectIdentifier())
                    .build()))
        .isPresent();
  }

  @Override
  public Optional<Invite> getInvite(String inviteId, boolean allowDeleted) {
    return allowDeleted ? inviteRepository.findById(inviteId)
                        : inviteRepository.findFirstByIdAndDeleted(inviteId, FALSE);
  }

  @Override
  public PageResponse<Invite> getInvites(Criteria criteria, PageRequest pageRequest) {
    Pageable pageable = PageUtils.getPageRequest(pageRequest);
    return PageUtils.getNGPageResponse(inviteRepository.findAll(criteria, pageable));
  }

  @Override
  public PageResponse<InviteDTO> getPendingInvites(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, PageRequest pageRequest, ACLAggregateFilter aclAggregateFilter) {
    validateRequest(searchTerm, aclAggregateFilter);
    PageResponse<Invite> invitesPage =
        getInvitePage(accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, pageRequest, aclAggregateFilter);
    List<String> userEmails = invitesPage.getContent().stream().map(Invite::getEmail).collect(toList());
    Map<String, UserMetadataDTO> userSearchDTOs = getPendingUserMap(userEmails, accountIdentifier);
    List<InviteDTO> inviteDTOS = aggregatePendingUsers(invitesPage.getContent(), userSearchDTOs);
    return PageUtils.getNGPageResponse(invitesPage, inviteDTOS);
  }

  @Override
  public Optional<Invite> deleteInvite(String inviteId) {
    Optional<Invite> inviteOptional = getInvite(inviteId, false);
    if (inviteOptional.isPresent()) {
      Invite invite = inviteOptional.get();
      checkPermissions(invite.getAccountIdentifier(), invite.getOrgIdentifier(), invite.getProjectIdentifier(),
          INVITE_PERMISSION_IDENTIFIER);
      Update update = new Update().set(InviteKeys.deleted, TRUE);
      Invite updatedInvite = inviteRepository.updateInvite(inviteId, update);
      if (updatedInvite != null) {
        outboxService.save(new UserInviteDeleteEvent(updatedInvite.getAccountIdentifier(),
            updatedInvite.getOrgIdentifier(), updatedInvite.getProjectIdentifier(), writeDTO(updatedInvite)));
      }
      return updatedInvite == null ? Optional.empty() : Optional.of(updatedInvite);
    }
    return Optional.empty();
  }

  @Override
  public boolean isUserPasswordSet(String accountIdentifier, String email) {
    return ngUserService.isUserPasswordSet(accountIdentifier, email);
  }

  @Override
  public URI getRedirectUrl(
      InviteAcceptResponse inviteAcceptResponse, String email, String decodedEmail, String jwtToken) {
    String accountIdentifier = inviteAcceptResponse.getAccountIdentifier();
    if (inviteAcceptResponse.getResponse().equals(INVITE_EXPIRED)
        || inviteAcceptResponse.getResponse().equals(INVITE_INVALID)) {
      return getLoginPageUrl(accountIdentifier, inviteAcceptResponse.getResponse());
    }

    UserInfo userInfo = inviteAcceptResponse.getUserInfo();
    AccountDTO account = RestClientUtils.getResponse(accountClient.getAccountDTO(accountIdentifier));
    if (account == null) {
      throw new IllegalStateException(String.format("Account with identifier [%s] doesn't exists", accountIdentifier));
    }

    AuthenticationMechanism authMechanism = account.getAuthenticationMechanism();
    boolean isPasswordRequired = authMechanism == null || authMechanism == AuthenticationMechanism.USER_PASSWORD;

    String baseUrl = accountOrgProjectHelper.getBaseUrl(accountIdentifier);
    URI resourceUrl = InviteUtils.getResourceUrl(baseUrl, accountIdentifier, inviteAcceptResponse.getOrgIdentifier(),
        inviteAcceptResponse.getProjectIdentifier());
    if (userInfo == null) {
      if (isPasswordRequired) {
        return getUserInfoSubmitUrl(baseUrl, resourceUrl, email, jwtToken, inviteAcceptResponse);
      } else {
        createAndInviteNonPasswordUser(accountIdentifier, jwtToken, decodedEmail.trim(), false);
        return resourceUrl;
      }
    } else {
      boolean isUserPasswordSet = isUserPasswordSet(accountIdentifier, userInfo.getEmail());
      if (isPasswordRequired && !isUserPasswordSet) {
        return getUserInfoSubmitUrl(baseUrl, resourceUrl, email, jwtToken, inviteAcceptResponse);
      } else {
        Optional<Invite> inviteOpt = getInviteFromToken(jwtToken, false);
        completeInvite(inviteOpt);
        return resourceUrl;
      }
    }
  }

  @Override
  public String getInviteLinkFromInviteId(String accountIdentifier, String inviteId) {
    Invite invite = getInvite(inviteId, false).<InvalidRequestException>orElseThrow(() -> {
      throw new InvalidRequestException("Invalid or Expired Invite Id");
    });
    try {
      return isNgAuthUIEnabled ? getAcceptInviteUrl(invite) : getInvitationMailEmbedUrl(invite);
    } catch (URISyntaxException | UnsupportedEncodingException e) {
      log.error("URL format incorrect. Cannot create invite link. InviteId: " + invite.getId(), e);
      throw new UnexpectedException("Could not create invite link. Unexpectedly failed due to malformed URL.");
    }
  }

  private void createAndInviteNonPasswordUser(
      String accountIdentifier, String jwtToken, String email, boolean isScimInvite) {
    UserInviteDTO userInviteDTO =
        UserInviteDTO.builder().accountId(accountIdentifier).email(email).name(email).token(jwtToken).build();
    RestClientUtils.getResponse(userClient.createUserAndCompleteNGInvite(userInviteDTO, isScimInvite));
  }

  private URI getUserInfoSubmitUrl(
      String baseUrl, URI resourceUrl, String email, String jwtToken, InviteAcceptResponse inviteAcceptResponse) {
    String accountIdentifier = inviteAcceptResponse.getAccountIdentifier();
    try {
      String accountCreationFragment =
          String.format(NG_ACCOUNT_CREATION_FRAGMENT, accountIdentifier, email, jwtToken, resourceUrl);
      URIBuilder uriBuilder = new URIBuilder(baseUrl);
      uriBuilder.setPath(NG_AUTH_UI_PATH_PREFIX);
      uriBuilder.setFragment("/accept-invite?" + accountCreationFragment);
      return uriBuilder.build();
    } catch (URISyntaxException e) {
      throw new WingsException(e);
    }
  }

  private URI getLoginPageUrl(String accountIdentifier, InviteOperationResponse inviteOperationResponse) {
    try {
      String baseUrl = accountOrgProjectHelper.getBaseUrl(accountIdentifier);
      URIBuilder uriBuilder = new URIBuilder(baseUrl);
      uriBuilder.setPath(NG_AUTH_UI_PATH_PREFIX);
      uriBuilder.setFragment("/signin?errorCode=" + inviteOperationResponse.getType());
      return uriBuilder.build();
    } catch (URISyntaxException e) {
      throw new WingsException(e);
    }
  }

  private Invite resendInvite(Invite newInvite) {
    checkPermissions(newInvite.getAccountIdentifier(), newInvite.getOrgIdentifier(), newInvite.getProjectIdentifier(),
        INVITE_PERMISSION_IDENTIFIER);
    Update update = new Update()
                        .set(InviteKeys.createdAt, new Date())
                        .set(InviteKeys.validUntil,
                            Date.from(OffsetDateTime.now().plusDays(INVITATION_VALIDITY_IN_DAYS).toInstant()))
                        .set(InviteKeys.roleBindings, newInvite.getRoleBindings())
                        .set(InviteKeys.userGroups, newInvite.getUserGroups());
    inviteRepository.updateInvite(newInvite.getId(), update);
    outboxService.save(new UserInviteUpdateEvent(newInvite.getAccountIdentifier(), newInvite.getOrgIdentifier(),
        newInvite.getProjectIdentifier(), writeDTO(newInvite), writeDTO(newInvite)));
    try {
      sendInvitationMail(newInvite);
    } catch (URISyntaxException e) {
      log.error("Mail embed url incorrect. can't sent email. InviteId: " + newInvite.getId(), e);
    } catch (UnsupportedEncodingException e) {
      log.error("Invite Email sending failed due to encoding exception. InviteId: " + newInvite.getId(), e);
    }
    return newInvite;
  }

  public InviteAcceptResponse acceptInvite(String jwtToken) {
    Optional<Invite> inviteOptional = getInviteFromToken(jwtToken, true);
    if (!inviteOptional.isPresent() || !inviteOptional.get().getInviteToken().equals(jwtToken)) {
      log.warn("Invite token {} is invalid", jwtToken);
      return InviteAcceptResponse.builder().response(INVITE_INVALID).build();
    }
    Invite invite = inviteOptional.get();
    Date today = Date.from(OffsetDateTime.now().toInstant());
    Date validUntil = invite.getValidUntil();
    if (validUntil.compareTo(today) < 0) {
      log.warn("Invite expired");
      return InviteAcceptResponse.builder().response(INVITE_EXPIRED).build();
    }

    Optional<UserMetadataDTO> ngUserOpt = ngUserService.getUserByEmail(invite.getEmail(), true);
    UserInfo userInfo = ngUserOpt
                            .map(user
                                -> UserInfo.builder()
                                       .uuid(user.getUuid())
                                       .name(user.getName())
                                       .email(user.getEmail())
                                       .locked(user.isLocked())
                                       .disabled(user.isDisabled())
                                       .externallyManaged(user.isExternallyManaged())
                                       .build())
                            .orElse(null);

    markInviteApproved(invite);
    return InviteAcceptResponse.builder()
        .response(InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED)
        .userInfo(userInfo)
        .accountIdentifier(invite.getAccountIdentifier())
        .orgIdentifier(invite.getOrgIdentifier())
        .projectIdentifier(invite.getProjectIdentifier())
        .inviteId(invite.getId())
        .email(invite.getEmail())
        .build();
  }

  @Override
  public Optional<Invite> getInviteFromToken(String jwtToken, boolean allowDeleted) {
    if (isBlank(jwtToken)) {
      return Optional.empty();
    }
    Optional<String> inviteIdOptional = Optional.empty();
    try {
      inviteIdOptional = getInviteIdFromToken(jwtToken);
    } catch (InvalidRequestException e) {
      log.error("Invalid invite JWT token", e);
    }
    if (!inviteIdOptional.isPresent()) {
      log.warn("Invalid token. verification failed");
      return Optional.empty();
    }
    return getInvite(inviteIdOptional.get(), allowDeleted);
  }

  @Override
  public Optional<Invite> updateInvite(Invite updatedInvite) {
    if (updatedInvite == null) {
      return Optional.empty();
    }
    preCreateInvite(updatedInvite);
    Optional<Invite> inviteOptional = getInvite(updatedInvite.getId(), false);
    if (!inviteOptional.isPresent() || TRUE.equals(inviteOptional.get().getApproved())) {
      return Optional.empty();
    }

    Invite existingInvite = inviteOptional.get();
    updatedInvite.setAccountIdentifier(existingInvite.getAccountIdentifier());
    updatedInvite.setOrgIdentifier(existingInvite.getOrgIdentifier());
    updatedInvite.setProjectIdentifier(existingInvite.getProjectIdentifier());

    Preconditions.checkState(updatedInvite.getEmail().equals(existingInvite.getEmail()),
        "Can't update recipient of an Invite after creation");

    if (existingInvite.getInviteType() == ADMIN_INITIATED_INVITE) {
      wrapperForTransactions(this::resendInvite, updatedInvite);
      return Optional.of(updatedInvite);
    } else if (existingInvite.getInviteType() == USER_INITIATED_INVITE) {
      throw new UnsupportedOperationException("User initiated requests are not supported yet");
    }
    return Optional.empty();
  }

  private String getExceptionMessage(Invite invite) {
    String message = String.format("Invite [%s] under account [%s] ", invite.getId(), invite.getAccountIdentifier());
    if (!isBlank(invite.getProjectIdentifier())) {
      message = message
          + String.format(",organization [%s] and project [%s] already exists", invite.getOrgIdentifier(),
              invite.getProjectIdentifier());
    } else if (!isBlank(invite.getOrgIdentifier())) {
      message = message + String.format("and organization [%s] already exists", invite.getOrgIdentifier());
    } else {
      message = message + "already exists";
    }
    return message;
  }

  private Optional<Invite> getExistingInvite(Invite invite) {
    return inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
        invite.getAccountIdentifier(), invite.getOrgIdentifier(), invite.getProjectIdentifier(), invite.getEmail());
  }

  private InviteOperationResponse newInvite(Invite invite, boolean isScimInvite) {
    Invite savedInvite = inviteRepository.save(invite);
    outboxService.save(new UserInviteCreateEvent(
        invite.getAccountIdentifier(), invite.getOrgIdentifier(), invite.getProjectIdentifier(), writeDTO(invite)));
    try {
      sendInvitationMail(savedInvite);
    } catch (URISyntaxException e) {
      log.error("Mail embed url incorrect. can't sent email. InviteId: " + savedInvite.getId(), e);
    } catch (UnsupportedEncodingException e) {
      log.error("Invite Email sending failed due to encoding exception. InviteId: " + savedInvite.getId(), e);
    }
    String accountId = invite.getAccountIdentifier();
    String email = invite.getEmail().trim();
    if (isScimInvite) {
      createAndInviteNonPasswordUser(accountId, invite.getInviteToken(), email, true);
    } else if (RestClientUtils.getResponse(accountClient.checkAutoInviteAcceptanceEnabledForAccount(accountId))) {
      createAndInviteNonPasswordUser(accountId, invite.getInviteToken(), email, false);
    }
    return InviteOperationResponse.USER_INVITED_SUCCESSFULLY;
  }

  private void updateJWTTokenInInvite(Invite invite) {
    String jwtToken = jwtGeneratorUtils.generateJWTToken(ImmutableMap.of(InviteKeys.id, invite.getId()),
        TimeUnit.MILLISECONDS.convert(LINK_VALIDITY_IN_DAYS, TimeUnit.DAYS), jwtPasswordSecret);
    invite.setInviteToken(jwtToken);
    Update update = new Update().set(InviteKeys.inviteToken, invite.getInviteToken());
    inviteRepository.updateInvite(invite.getId(), update);
  }

  private void sendInvitationMail(Invite invite) throws URISyntaxException, UnsupportedEncodingException {
    updateJWTTokenInInvite(invite);
    String url = isNgAuthUIEnabled ? getAcceptInviteUrl(invite) : getInvitationMailEmbedUrl(invite);
    EmailChannelBuilder emailChannelBuilder = EmailChannel.builder()
                                                  .accountId(invite.getAccountIdentifier())
                                                  .recipients(Collections.singletonList(invite.getEmail()))
                                                  .team(Team.PL)
                                                  .templateId("email_invite")
                                                  .userGroups(Collections.emptyList());
    Map<String, String> templateData = new HashMap<>();
    templateData.put("url", url);
    if (!isBlank(invite.getProjectIdentifier())) {
      templateData.put("projectname",
          accountOrgProjectHelper.getProjectName(
              invite.getAccountIdentifier(), invite.getOrgIdentifier(), invite.getProjectIdentifier()));
    } else if (!isBlank(invite.getOrgIdentifier())) {
      templateData.put("organizationname",
          accountOrgProjectHelper.getOrgName(invite.getAccountIdentifier(), invite.getOrgIdentifier()));
    } else {
      templateData.put("accountname", accountOrgProjectHelper.getAccountName(invite.getAccountIdentifier()));
    }
    emailChannelBuilder.templateData(templateData);
    notificationClient.sendNotificationAsync(emailChannelBuilder.build());
  }

  private String getInvitationMailEmbedUrl(Invite invite) throws URISyntaxException {
    AccountDTO account = RestClientUtils.getResponse(accountClient.getAccountDTO(invite.getAccountIdentifier()));
    String fragment = String.format(INVITE_URL, invite.getAccountIdentifier(), account.getName(),
        account.getCompanyName(), invite.getEmail(), invite.getInviteToken());

    String baseUrl = accountOrgProjectHelper.getBaseUrl(invite.getAccountIdentifier());
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setFragment(fragment);
    return uriBuilder.toString();
  }

  private String getAcceptInviteUrl(Invite invite) throws URISyntaxException, UnsupportedEncodingException {
    String baseUrl = accountOrgProjectHelper.getGatewayBaseUrl(invite.getAccountIdentifier()) + ACCEPT_INVITE_PATH;
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setParameters(getParameterList(invite));
    uriBuilder.setFragment(null);
    return uriBuilder.toString();
  }

  private List<NameValuePair> getParameterList(Invite invite) throws UnsupportedEncodingException {
    return Arrays.asList(new BasicNameValuePair("accountIdentifier", invite.getAccountIdentifier()),
        new BasicNameValuePair("email", URLEncoder.encode(invite.getEmail(), "UTF-8")),
        new BasicNameValuePair("token", invite.getInviteToken()));
  }

  @Override
  public boolean completeInvite(Optional<Invite> inviteOpt) {
    if (!inviteOpt.isPresent()) {
      return false;
    }
    Invite invite = inviteOpt.get();
    String email = invite.getEmail();
    Optional<UserMetadataDTO> userOpt = ngUserService.getUserByEmail(email, true);
    Preconditions.checkState(userOpt.isPresent(), "Illegal state: user doesn't exists");
    UserMetadataDTO user = userOpt.get();
    Scope scope = Scope.builder()
                      .accountIdentifier(invite.getAccountIdentifier())
                      .orgIdentifier(invite.getOrgIdentifier())
                      .projectIdentifier(invite.getProjectIdentifier())
                      .build();

    ngUserService.addUserToScope(
        user.getUuid(), scope, invite.getRoleBindings(), invite.getUserGroups(), ACCEPTED_INVITE);
    // Adding user to the account for sign in flow to work
    ngUserService.addUserToCG(user.getUuid(), scope);
    markInviteApprovedAndDeleted(invite);
    // telemetry for adding user to an account
    sendInviteAcceptTelemetryEvents(user, invite);
    return true;
  }

  private void sendInviteAcceptTelemetryEvents(UserMetadataDTO user, Invite invite) {
    String userEmail = user.getEmail();
    String accountId = invite.getAccountIdentifier();

    String accountName = "";
    // get the name of the account
    AccountDTO account = RestClientUtils.getResponse(accountClient.getAccountDTO(accountId));
    if (account != null) {
      accountName = account.getName();
    }

    HashMap<String, Object> properties = new HashMap<>();
    properties.put("email", userEmail);
    properties.put("name", user.getName());
    properties.put("id", user.getUuid());
    properties.put("startTime", String.valueOf(Instant.now().toEpochMilli()));
    properties.put("accountId", accountId);
    properties.put("accountName", accountName);
    properties.put("source", USER_INVITE);

    // identify event to register new user
    telemetryReporter.sendIdentifyEvent(userEmail, properties,
        ImmutableMap.<Destination, Boolean>builder()
            .put(Destination.MARKETO, true)
            .put(Destination.AMPLITUDE, true)
            .build());

    HashMap<String, Object> groupProperties = new HashMap<>();
    groupProperties.put("group_id", accountId);
    groupProperties.put("group_type", "Account");
    groupProperties.put("group_name", accountName);

    // group event to register new signed-up user with new account
    telemetryReporter.sendGroupEvent(
        accountId, userEmail, groupProperties, ImmutableMap.<Destination, Boolean>builder().build());

    // flush all events so that event queue is empty
    telemetryReporter.flush();

    properties.put("platform", "NG");
    // Wait 20 seconds, to ensure identify is sent before track
    ScheduledExecutorService tempExecutor = Executors.newSingleThreadScheduledExecutor();
    tempExecutor.schedule(()
                              -> telemetryReporter.sendTrackEvent("Invite  Accepted", userEmail, accountId, properties,
                                  ImmutableMap.<Destination, Boolean>builder()
                                      .put(Destination.MARKETO, true)
                                      .put(Destination.AMPLITUDE, true)
                                      .build(),
                                  null),
        20, TimeUnit.SECONDS);
    log.info("User Invite telemetry sent");
  }

  private void markInviteApproved(Invite invite) {
    invite.setApproved(TRUE);
    Update update = new Update().set(InviteKeys.approved, TRUE);
    inviteRepository.updateInvite(invite.getId(), update);
  }

  private void markInviteApprovedAndDeleted(Invite invite) {
    invite.setApproved(TRUE);
    invite.setDeleted(TRUE);
    Update update = new Update().set(InviteKeys.approved, TRUE).set(InviteKeys.deleted, TRUE);
    inviteRepository.updateInvite(invite.getId(), update);
  }

  private Optional<String> getInviteIdFromToken(String token) {
    Map<String, Claim> claims = jwtGeneratorUtils.verifyJWTToken(token, jwtPasswordSecret);
    if (!claims.containsKey("exp")) {
      log.warn(this.getClass().getName() + " verifies JWT Token without Expiry Date");
      Principal principal = SecurityContextBuilder.getPrincipalFromClaims(claims);
      if (principal != null) {
        log.info(String.format(
            "Principal type is %s and its name is %s", principal.getType().toString(), principal.getName()));
      }
    }
    if (!claims.containsKey(InviteKeys.id)) {
      return Optional.empty();
    }
    return Optional.of(claims.get(InviteKeys.id).asString());
  }

  private void validateRequest(String searchTerm, ACLAggregateFilter aclAggregateFilter) {
    if (!isBlank(searchTerm) && ACLAggregateFilter.isFilterApplied(aclAggregateFilter)) {
      log.error("Search term and filter on role/resourcegroup identifiers can't be applied at the same time");
      throw new InvalidRequestException(
          "Search term and filter on role/resourcegroup identifiers can't be applied at the same time");
    }
  }

  private PageResponse<Invite> getInvitePage(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String searchTerm, PageRequest pageRequest, ACLAggregateFilter aclAggregateFilter) {
    Criteria criteria = Criteria.where(InviteKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(InviteKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(InviteKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(InviteKeys.deleted)
                            .is(FALSE);
    if (ACLAggregateFilter.isFilterApplied(aclAggregateFilter)) {
      if (isNotEmpty(aclAggregateFilter.getRoleIdentifiers())) {
        criteria.and(InviteKeys.roleBindings + "." + RoleBindingKeys.roleIdentifier)
            .in(aclAggregateFilter.getRoleIdentifiers());
      }
      if (isNotEmpty(aclAggregateFilter.getResourceGroupIdentifiers())) {
        criteria.and(InviteKeys.roleBindings + "." + RoleBindingKeys.resourceGroupIdentifier)
            .in(aclAggregateFilter.getResourceGroupIdentifiers());
      }
    }
    if (!isBlank(searchTerm)) {
      Page<UserInfo> userInfos = ngUserService.listCurrentGenUsers(
          accountIdentifier, searchTerm, org.springframework.data.domain.PageRequest.of(0, DEFAULT_PAGE_SIZE));
      List<String> emailIds = userInfos.stream().map(UserInfo::getEmail).collect(toList());
      Criteria searchTermCriteria = new Criteria();
      searchTermCriteria.orOperator(
          Criteria.where(InviteKeys.email).regex(quote(searchTerm)), Criteria.where(InviteKeys.email).in(emailIds));
      criteria = new Criteria().andOperator(criteria, searchTermCriteria);
    }
    return getInvites(criteria, pageRequest);
  }

  private Map<String, UserMetadataDTO> getPendingUserMap(List<String> userEmails, String accountIdentifier) {
    List<UserInfo> users =
        ngUserService.listCurrentGenUsers(accountIdentifier, UserFilterNG.builder().emailIds(userEmails).build());
    Map<String, UserMetadataDTO> userMetadataMap = new HashMap<>();
    users.forEach(user
        -> userMetadataMap.put(user.getEmail(),
            UserMetadataDTO.builder()
                .email(user.getEmail())
                .name(user.getName())
                .uuid(user.getUuid())
                .locked(user.isLocked())
                .disabled(user.isDisabled())
                .externallyManaged(user.isExternallyManaged())
                .build()));
    for (String email : userEmails) {
      userMetadataMap.computeIfAbsent(email, email1 -> UserMetadataDTO.builder().email(email1).build());
    }
    return userMetadataMap;
  }

  private List<InviteDTO> aggregatePendingUsers(List<Invite> invites, Map<String, UserMetadataDTO> userMap) {
    Preconditions.checkState(invites.stream().map(Invite::getEmail).distinct().count() == userMap.size(),
        "Number of invites should be same as number of invites. Invariant violated");
    List<InviteDTO> inviteDTOs = new ArrayList<>();
    for (Invite invite : invites) {
      UserMetadataDTO user = userMap.get(invite.getEmail());
      inviteDTOs.add(InviteDTO.builder()
                         .id(invite.getId())
                         .name(user.getName())
                         .email(user.getEmail())
                         .roleBindings(invite.getRoleBindings())
                         .inviteType(invite.getInviteType())
                         .build());
    }
    return inviteDTOs;
  }

  private void checkPermissions(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String permissionIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("USER", null), permissionIdentifier);
  }

  private <T, S> S wrapperForTransactions(Function<T, S> function, T arg) {
    if (!useMongoTransactions) {
      return function.apply(arg);
    } else {
      return Failsafe.with(transactionRetryPolicy)
          .get(() -> transactionTemplate.execute(status -> function.apply(arg)));
    }
  }

  private <T, U, R> R wrapperForTransactions(BiFunction<T, U, R> function, T arg1, U arg2) {
    if (!useMongoTransactions) {
      return function.apply(arg1, arg2);
    } else {
      return Failsafe.with(transactionRetryPolicy)
          .get(() -> transactionTemplate.execute(status -> function.apply(arg1, arg2)));
    }
  }
}
