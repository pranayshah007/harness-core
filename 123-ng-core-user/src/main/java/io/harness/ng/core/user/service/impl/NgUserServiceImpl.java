/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.service.impl;

import static io.harness.NGConstants.ACCOUNT_ADMIN_ROLE;
import static io.harness.NGConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.createRoleAssignmentDTOs;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.getDefaultResourceGroupIdentifier;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.Scope.ScopeKeys;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.licensing.Edition;
import io.harness.licensing.services.LicenseService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.common.beans.UserSource;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.dto.UsersCountDTO;
import io.harness.ng.core.events.AddCollaboratorEvent;
import io.harness.ng.core.events.RemoveCollaboratorEvent;
import io.harness.ng.core.invites.InviteType;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.invites.dto.CreateInviteDTO;
import io.harness.ng.core.invites.dto.InviteOperationResponse;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.invites.utils.InviteUtils;
import io.harness.ng.core.user.AddUserResponse;
import io.harness.ng.core.user.AddUsersDTO;
import io.harness.ng.core.user.AddUsersResponse;
import io.harness.ng.core.user.NGRemoveUserFilter;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.ng.core.user.entities.UserMembership.UserMembershipKeys;
import io.harness.ng.core.user.entities.UserMetadata;
import io.harness.ng.core.user.entities.UserMetadata.UserMetadataKeys;
import io.harness.ng.core.user.exception.InvalidUserRemoveRequestException;
import io.harness.ng.core.user.remote.dto.LastAdminCheckFilter;
import io.harness.ng.core.user.remote.dto.UserFilter;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.remote.mapper.UserMetadataMapper;
import io.harness.ng.core.user.service.LastAdminCheckService;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.EmailChannel.EmailChannelBuilder;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.user.spring.UserMembershipRepository;
import io.harness.repositories.user.spring.UserMetadataRepository;
import io.harness.scim.PatchRequest;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimUser;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;
import io.harness.utils.ScopeUtils;
import io.harness.utils.TimeLogger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;
import org.springframework.transaction.support.TransactionTemplate;

@Singleton
@Slf4j
@OwnedBy(PL)
public class NgUserServiceImpl implements NgUserService {
  private static final String ACCOUNT_ADMIN = "_account_admin";
  private static final String ACCOUNT_VIEWER = "_account_viewer";
  private static final String ORGANIZATION_VIEWER = "_organization_viewer";
  private static final String ORG_ADMIN = "_organization_admin";
  private static final String PROJECT_ADMIN = "_project_admin";
  private static final String PROJECT_VIEWER = "_project_viewer";
  private static final List<String> MANAGED_RESOURCE_GROUP_IDENTIFIERS = ImmutableList.of(
      ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER, DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER,
      DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER, DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
  public static final int DEFAULT_PAGE_SIZE = 10000;
  private final UserClient userClient;
  private final AccountClient accountClient;
  private final UserMembershipRepository userMembershipRepository;
  private final AccessControlAdminClient accessControlAdminClient;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;
  private final UserGroupService userGroupService;
  private final UserMetadataRepository userMetadataRepository;
  private final InviteService inviteService;
  private final NotificationClient notificationClient;
  private final AccountOrgProjectHelper accountOrgProjectHelper;
  private final LicenseService licenseService;
  private final LastAdminCheckService lastAccountAdminCheckService;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final DefaultUserGroupService defaultUserGroupService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Inject
  public NgUserServiceImpl(UserClient userClient, AccountClient accountClient,
      UserMembershipRepository userMembershipRepository,
      @Named("PRIVILEGED") AccessControlAdminClient accessControlAdminClient,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService,
      UserGroupService userGroupService, UserMetadataRepository userMetadataRepository, InviteService inviteService,
      NotificationClient notificationClient, AccountOrgProjectHelper accountOrgProjectHelper,
      LicenseService licenseService, LastAdminCheckService lastAccountAdminCheckService,
      NGFeatureFlagHelperService ngFeatureFlagHelperService, DefaultUserGroupService defaultUserGroupService) {
    this.userClient = userClient;
    this.accountClient = accountClient;
    this.userMembershipRepository = userMembershipRepository;
    this.accessControlAdminClient = accessControlAdminClient;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.userGroupService = userGroupService;
    this.userMetadataRepository = userMetadataRepository;
    this.inviteService = inviteService;
    this.notificationClient = notificationClient;
    this.accountOrgProjectHelper = accountOrgProjectHelper;
    this.licenseService = licenseService;
    this.lastAccountAdminCheckService = lastAccountAdminCheckService;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
    this.defaultUserGroupService = defaultUserGroupService;
  }

  @Override
  public Page<UserInfo> listCurrentGenUsers(String accountIdentifier, String searchString, Pageable pageable) {
    io.harness.beans.PageResponse<UserInfo> userPageResponse =
        CGRestUtils.getResponse(userClient.list(accountIdentifier, String.valueOf(pageable.getOffset()),
            String.valueOf(pageable.getPageSize()), searchString, false));
    List<UserInfo> users = userPageResponse.getResponse();
    return new PageImpl<>(users, pageable, users.size());
  }

  @Override
  public PageResponse<UserMetadataDTO> listUsers(Scope scope, PageRequest pageRequest, UserFilter userFilter) {
    Criteria userMembershipCriteria;
    if (userFilter == null || UserFilter.ParentFilter.NO_PARENT_SCOPES.equals(userFilter.getParentFilter())) {
      userMembershipCriteria = getUserMembershipCriteria(scope, false);
    } else if (UserFilter.ParentFilter.INCLUDE_PARENT_SCOPES.equals(userFilter.getParentFilter())) {
      userMembershipCriteria = getUserMembershipCriteria(scope, true);
    } else {
      userMembershipCriteria = getUserMembershipCriteria(scope, true);
      List<String> negativeUserIds = listUserIds(scope);
      userMembershipCriteria.and(UserMembershipKeys.userId).nin(negativeUserIds);
    }
    Criteria userMetadataCriteria = new Criteria();
    if (userFilter != null) {
      if (isNotBlank(userFilter.getSearchTerm())) {
        userMetadataCriteria.orOperator(Criteria.where(UserMetadataKeys.name).regex(userFilter.getSearchTerm(), "i"),
            Criteria.where(UserMetadataKeys.email).regex(userFilter.getSearchTerm(), "i"));
      }
      if (userFilter.getIdentifiers() != null || userFilter.getEmails() != null) {
        List<String> userIds = new ArrayList<>();
        if (userFilter.getIdentifiers() != null) {
          userIds.addAll(userFilter.getIdentifiers());
        }
        if (userFilter.getEmails() != null) {
          userIds.addAll(getUserIdsByEmails(new ArrayList<>(userFilter.getEmails())));
        }
        userMembershipCriteria.and(UserMembershipKeys.userId).in(userIds);
      }
    }
    Page<String> userMembershipPage =
        userMembershipRepository.findAllUserIds(userMembershipCriteria, Pageable.unpaged());
    if (userMembershipPage.isEmpty()) {
      return PageResponse.getEmptyPageResponse(pageRequest);
    }

    userMetadataCriteria.and(UserMetadataKeys.userId).in(userMembershipPage.getContent());
    Page<UserMetadata> userMetadataPage =
        userMetadataRepository.findAll(userMetadataCriteria, getPageRequest(pageRequest));

    return PageUtils.getNGPageResponse(userMetadataPage.map(UserMetadataMapper::toDTO));
  }

  private Criteria getUserMembershipCriteria(Scope scope, boolean includeParentScopes) {
    Criteria userMembershipCriteria = new Criteria();
    if (includeParentScopes) {
      List<Criteria> scopeCriterion = new ArrayList<>();
      scopeCriterion.add(Criteria.where(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY)
                             .is(scope.getAccountIdentifier())
                             .and(UserMembershipKeys.ORG_IDENTIFIER_KEY)
                             .is(null)
                             .and(UserMembershipKeys.PROJECT_IDENTIFIER_KEY)
                             .is(null));
      if (isNotEmpty(scope.getOrgIdentifier())) {
        scopeCriterion.add(Criteria.where(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY)
                               .is(scope.getAccountIdentifier())
                               .and(UserMembershipKeys.ORG_IDENTIFIER_KEY)
                               .is(scope.getOrgIdentifier())
                               .and(UserMembershipKeys.PROJECT_IDENTIFIER_KEY)
                               .is(null));
        if (isNotEmpty(scope.getProjectIdentifier())) {
          scopeCriterion.add(Criteria.where(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY)
                                 .is(scope.getAccountIdentifier())
                                 .and(UserMembershipKeys.ORG_IDENTIFIER_KEY)
                                 .is(scope.getOrgIdentifier())
                                 .and(UserMembershipKeys.PROJECT_IDENTIFIER_KEY)
                                 .is(scope.getProjectIdentifier()));
        }
      }
      if (scopeCriterion.size() == 1) {
        userMembershipCriteria = scopeCriterion.get(0);
      } else {
        userMembershipCriteria.orOperator(scopeCriterion.toArray(new Criteria[0]));
      }
    } else {
      userMembershipCriteria.and(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY)
          .is(scope.getAccountIdentifier())
          .and(UserMembershipKeys.ORG_IDENTIFIER_KEY)
          .is(scope.getOrgIdentifier())
          .and(UserMembershipKeys.PROJECT_IDENTIFIER_KEY)
          .is(scope.getProjectIdentifier());
    }
    return userMembershipCriteria;
  }

  @Override
  public List<String> listUserIds(Scope scope) {
    Criteria criteria = Criteria.where(UserMembershipKeys.scope + "." + ScopeKeys.accountIdentifier)
                            .is(scope.getAccountIdentifier())
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier)
                            .is(scope.getOrgIdentifier())
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier)
                            .is(scope.getProjectIdentifier());
    return userMembershipRepository.findAllUserIds(criteria, Pageable.ofSize(50000)).getContent();
  }

  @Override
  public List<UserMetadataDTO> listUsers(Scope scope) {
    List<String> userIds = listUserIds(scope);
    return getUserMetadata(userIds);
  }

  public Optional<UserMetadataDTO> getUserByEmail(String email, boolean fetchFromCurrentGen) {
    if (!fetchFromCurrentGen) {
      Optional<UserMetadata> user = userMetadataRepository.findDistinctByEmail(email);
      return user.map(UserMetadataMapper::toDTO);
    } else {
      Optional<UserInfo> userInfo = CGRestUtils.getResponse(userClient.getUserByEmailId(email));
      UserMetadataDTO userMetadataDTO =
          userInfo
              .map(user
                  -> UserMetadataDTO.builder()
                         .uuid(user.getUuid())
                         .name(user.getName())
                         .email(user.getEmail())
                         .locked(user.isLocked())
                         .disabled(user.isDisabled())
                         .externallyManaged(user.isExternallyManaged())
                         .twoFactorAuthenticationEnabled(user.isTwoFactorAuthenticationEnabled())
                         .build())
              .orElse(null);
      return Optional.ofNullable(userMetadataDTO);
    }
  }

  public Optional<UserInfo> getUserInfoByEmailFromCG(String email) {
    return CGRestUtils.getResponse(userClient.getUserByEmailId(email));
  }

  @Override
  public List<UserInfo> listCurrentGenUsers(String accountId, UserFilterNG userFilter) {
    return CGRestUtils.getResponse(userClient.listUsers(
        accountId, UserFilterNG.builder().emailIds(userFilter.getEmailIds()).userIds(userFilter.getUserIds()).build()));
  }

  @Override

  public ScimListResponse<ScimUser> searchScimUsersByEmailQuery(
      String accountId, String searchQuery, Integer count, Integer startIndex) {
    return CGRestUtils.getResponse(userClient.searchScimUsers(accountId, searchQuery, count, startIndex));
  }

  @Override
  public List<UserMetadataDTO> listUsersHavingRole(Scope scope, String roleIdentifier) {
    if (Edition.COMMUNITY.equals(licenseService.calculateAccountEdition(scope.getAccountIdentifier()))) {
      return listUsers(scope);
    }
    PageResponse<RoleAssignmentResponseDTO> roleAssignmentPage =
        getResponse(accessControlAdminClient.getFilteredRoleAssignments(scope.getAccountIdentifier(),
            scope.getOrgIdentifier(), scope.getProjectIdentifier(), 0, DEFAULT_PAGE_SIZE,
            RoleAssignmentFilterDTO.builder().roleFilter(Collections.singleton(roleIdentifier)).build()));
    List<PrincipalDTO> principals = roleAssignmentPage.getContent() != null
        ? roleAssignmentPage.getContent().stream().map(dto -> dto.getRoleAssignment().getPrincipal()).collect(toList())
        : new ArrayList<>();
    Set<String> userIds = principals.stream()
                              .filter(principal -> USER.equals(principal.getType()))
                              .map(PrincipalDTO::getIdentifier)
                              .collect(Collectors.toCollection(HashSet::new));
    // multiple filters
    List<String> userGroupIds =
        principals.stream()
            .filter(principal
                -> USER_GROUP.equals(principal.getType())
                    && ScopeLevel.of(scope).toString().toLowerCase().equals(principal.getScopeLevel()))
            .map(PrincipalDTO::getIdentifier)
            .distinct()
            .collect(toList());
    if (!userGroupIds.isEmpty()) {
      UserGroupFilterDTO userGroupFilterDTO = UserGroupFilterDTO.builder()
                                                  .accountIdentifier(scope.getAccountIdentifier())
                                                  .orgIdentifier(scope.getOrgIdentifier())
                                                  .projectIdentifier(scope.getProjectIdentifier())
                                                  .identifierFilter(new HashSet<>(userGroupIds))
                                                  .build();
      List<UserGroup> userGroups = userGroupService.list(userGroupFilterDTO);
      userGroups.forEach(userGroup -> userIds.addAll(userGroup.getUsers()));
    }
    return getUserMetadata(new ArrayList<>(userIds));
  }

  @Override
  public Optional<UserMembership> getUserMembership(String userId, Scope scope) {
    Criteria criteria = Criteria.where(UserMetadataKeys.userId)
                            .is(userId)
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.accountIdentifier)
                            .is(scope.getAccountIdentifier())
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier)
                            .is(scope.getOrgIdentifier())
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier)
                            .is(scope.getProjectIdentifier());
    UserMembership userMemberships = userMembershipRepository.findOne(criteria);
    return Optional.ofNullable(userMemberships);
  }

  @Override
  public Optional<UserMetadataDTO> getUserMetadata(String userId) {
    return userMetadataRepository.findDistinctByUserId(userId).map(UserMetadataMapper::toDTO);
  }

  @Override
  public AddUsersResponse addUsers(Scope scope, AddUsersDTO addUsersDTO) {
    if (isEmpty(addUsersDTO.getEmails())) {
      return AddUsersResponse.EMPTY_RESPONSE;
    }
    Criteria criteria = Criteria.where(UserMetadataKeys.email).in(addUsersDTO.getEmails());
    List<UserMetadata> userMetadataList = userMetadataRepository.findAll(criteria, Pageable.unpaged()).getContent();

    Set<String> userIdsAlreadyPartOfAccount =
        getUsersAtScope(userMetadataList.stream().map(UserMetadata::getUserId).collect(toSet()),
            Scope.of(scope.getAccountIdentifier(), null, null));

    Set<UserMetadata> usersAlreadyPartOfAccount =
        userMetadataList.stream()
            .filter(userMetadata -> userIdsAlreadyPartOfAccount.contains(userMetadata.getUserId()))
            .collect(toSet());

    Set<String> allEmails = new HashSet<>(addUsersDTO.getEmails());
    List<String> toBeInvitedUsers = new ArrayList<>(
        Sets.difference(allEmails, usersAlreadyPartOfAccount.stream().map(UserMetadata::getEmail).collect(toSet())));

    Map<String, AddUserResponse> invitedUsersResponseMap =
        inviteUsers(scope, addUsersDTO.getRoleBindings(), addUsersDTO.getUserGroups(), toBeInvitedUsers);
    Map<String, AddUserResponse> addUserResponseMap = addUsersWhichAreAlreadyPartOfAccount(
        usersAlreadyPartOfAccount, scope, addUsersDTO.getRoleBindings(), addUsersDTO.getUserGroups());

    addUserResponseMap.putAll(invitedUsersResponseMap);
    return AddUsersResponse.builder().addUserResponseMap(addUserResponseMap).build();
  }

  private Map<String, AddUserResponse> addUsersWhichAreAlreadyPartOfAccount(Set<UserMetadata> usersAlreadyPartOfAccount,
      Scope scope, List<RoleBinding> roleBindings, List<String> userGroups) {
    if (isEmpty(usersAlreadyPartOfAccount)) {
      return new HashMap<>();
    }
    String resourceScopeName = accountOrgProjectHelper.getResourceScopeName(scope);
    String baseUrl = accountOrgProjectHelper.getBaseUrl(scope.getAccountIdentifier());

    Map<String, AddUserResponse> addUserResponseMap = new HashMap<>();
    usersAlreadyPartOfAccount.forEach(userMetadata -> {
      if (isUserAtScope(userMetadata.getUserId(), scope)) {
        addUserToScope(userMetadata.getUserId(), scope, roleBindings, userGroups, UserMembershipUpdateSource.USER);
        addUserResponseMap.put(userMetadata.getEmail(), AddUserResponse.USER_ALREADY_ADDED);
      } else {
        addUserToScope(userMetadata.getUserId(), scope, roleBindings, userGroups, UserMembershipUpdateSource.USER);
        sendSuccessfulUserAdditionNotification(userMetadata.getEmail(), scope, baseUrl, resourceScopeName);
        addUserResponseMap.put(userMetadata.getEmail(), AddUserResponse.USER_ADDED_SUCCESSFULLY);
      }
    });
    return addUserResponseMap;
  }

  @VisibleForTesting
  protected Set<String> getUsersAtScope(Set<String> userFilter, Scope scope) {
    Criteria userMembershipCriteria = Criteria.where(UserMembershipKeys.userId)
                                          .in(userFilter)
                                          .and(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY)
                                          .is(scope.getAccountIdentifier())
                                          .and(UserMembershipKeys.ORG_IDENTIFIER_KEY)
                                          .is(scope.getOrgIdentifier())
                                          .and(UserMembershipKeys.PROJECT_IDENTIFIER_KEY)
                                          .is(scope.getProjectIdentifier());
    return userMembershipRepository.findAllUserIds(userMembershipCriteria, Pageable.unpaged()).toSet();
  }

  @VisibleForTesting
  protected Map<String, AddUserResponse> inviteUsers(
      Scope scope, List<RoleBinding> roleBindings, List<String> userGroups, List<String> toBeInvitedUsers) {
    if (isEmpty(toBeInvitedUsers)) {
      return new HashMap<>();
    }
    CreateInviteDTO createInviteDTO = CreateInviteDTO.builder()
                                          .inviteType(InviteType.ADMIN_INITIATED_INVITE)
                                          .roleBindings(roleBindings)
                                          .userGroups(userGroups)
                                          .users(toBeInvitedUsers)
                                          .build();
    List<InviteOperationResponse> invitations = inviteService.createInvitations(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), createInviteDTO);
    Map<String, AddUserResponse> addUserResponseMap = new HashMap<>();
    for (int i = 0; i < invitations.size(); i++) {
      addUserResponseMap.put(toBeInvitedUsers.get(i), AddUserResponse.fromInviteOperationResponse(invitations.get(i)));
    }
    return addUserResponseMap;
  }

  private void sendSuccessfulUserAdditionNotification(
      String email, Scope scope, String baseUrl, String resourceScopeName) {
    String url = InviteUtils
                     .getResourceUrl(
                         baseUrl, scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier())
                     .toString();

    EmailChannelBuilder emailChannelBuilder = EmailChannel.builder()
                                                  .accountId(scope.getAccountIdentifier())
                                                  .recipients(Collections.singletonList(email))
                                                  .team(Team.PL)
                                                  .templateId("email_notify")
                                                  .userGroups(Collections.emptyList());
    Map<String, String> templateData = new HashMap<>();
    templateData.put("url", url);
    if (isNotEmpty(scope.getProjectIdentifier())) {
      templateData.put("projectname", resourceScopeName);
    } else if (isNotEmpty(scope.getOrgIdentifier())) {
      templateData.put("organizationname", resourceScopeName);
    } else {
      templateData.put("accountname", resourceScopeName);
    }
    emailChannelBuilder.templateData(templateData);
    notificationClient.sendNotificationAsync(emailChannelBuilder.build());
  }

  @Override
  public List<UserMetadataDTO> getUserMetadata(List<String> userIds) {
    return userMetadataRepository.findAll(Criteria.where(UserMetadataKeys.userId).in(userIds), Pageable.ofSize(50000))
        .map(UserMetadataMapper::toDTO)
        .stream()
        .collect(toList());
  }

  @Override
  public CloseableIterator<UserMetadata> streamUserMetadata(List<String> userIds) {
    return userMetadataRepository.stream(Criteria.where(UserMetadataKeys.userId).in(userIds));
  }

  @Override
  public List<String> getUserIdsByEmails(List<String> emailIds) {
    return userMetadataRepository.findAllIds(Criteria.where(UserMetadataKeys.email).in(emailIds));
  }

  @Override
  public CloseableIterator<UserMembership> streamUserMemberships(Criteria criteria) {
    return userMembershipRepository.stream(criteria);
  }

  @Override
  public void addServiceAccountToScope(
      String serviceAccountId, Scope scope, RoleBinding roleBinding, UserMembershipUpdateSource source) {
    List<RoleAssignmentDTO> roleAssignmentDTOs = new ArrayList<>(1);
    if (roleBinding != null) {
      RoleAssignmentDTO roleAssignmentDTO =
          RoleAssignmentDTO.builder()
              .roleIdentifier(roleBinding.getRoleIdentifier())
              .disabled(false)
              .principal(PrincipalDTO.builder().type(SERVICE_ACCOUNT).identifier(serviceAccountId).build())
              .resourceGroupIdentifier(isNotEmpty(roleBinding.getResourceGroupIdentifier())
                      ? roleBinding.getResourceGroupIdentifier()
                      : getDefaultResourceGroupIdentifier(scope))
              .build();
      roleAssignmentDTOs.add(roleAssignmentDTO);
    }
    createRoleAssignments(serviceAccountId, scope, getManagedRoleAssignments(roleAssignmentDTOs), true);
    createRoleAssignments(serviceAccountId, scope, getNonManagedRoleAssignments(roleAssignmentDTOs), false);
  }

  @Override
  public void addUserToScope(String userId, Scope scope, List<RoleBinding> roleBindings, List<String> userGroups,
      UserMembershipUpdateSource source) {
    ensureUserMetadata(userId);
    boolean isAccountBasicFeatureFlagEnabled =
        ngFeatureFlagHelperService.isEnabled(scope.getAccountIdentifier(), FeatureName.ACCOUNT_BASIC_ROLE);
    addUserToScopeInternal(userId, source, scope, getDefaultRoleIdentifier(scope), isAccountBasicFeatureFlagEnabled);
    addUserToParentScope(userId, scope, source, isAccountBasicFeatureFlagEnabled);
    List<RoleAssignmentDTO> roleAssignmentDTOList = createRoleAssignmentDTOs(roleBindings, userId, scope);
    if (isAccountBasicFeatureFlagEnabled) {
      createRoleAssignments(userId, scope, roleAssignmentDTOList, false);
    } else {
      createRoleAssignments(userId, scope, getManagedRoleAssignments(roleAssignmentDTOList), true);
      createRoleAssignments(userId, scope, getNonManagedRoleAssignments(roleAssignmentDTOList), false);
    }
    defaultUserGroupService.addUserToDefaultUserGroup(scope, userId);
    userGroupService.addUserToUserGroups(scope, userId, getValidUserGroups(scope, userGroups));
  }

  private List<String> getValidUserGroups(Scope scope, List<String> userGroupIdentifiers) {
    if (isEmpty(userGroupIdentifiers)) {
      return new ArrayList<>();
    }
    Set<String> userGroupIdentifiersFilter = new HashSet<>(userGroupIdentifiers);
    UserGroupFilterDTO userGroupFilterDTO = UserGroupFilterDTO.builder()
                                                .accountIdentifier(scope.getAccountIdentifier())
                                                .orgIdentifier(scope.getOrgIdentifier())
                                                .projectIdentifier(scope.getProjectIdentifier())
                                                .identifierFilter(userGroupIdentifiersFilter)
                                                .build();
    return userGroupService.list(userGroupFilterDTO).stream().map(UserGroup::getIdentifier).collect(toList());
  }

  private List<RoleAssignmentDTO> getManagedRoleAssignments(List<RoleAssignmentDTO> roleAssignmentDTOs) {
    return roleAssignmentDTOs.stream().filter(role -> isRoleAssignmentManaged(role)).collect(toList());
  }

  private List<RoleAssignmentDTO> getNonManagedRoleAssignments(List<RoleAssignmentDTO> roleAssignmentDTOs) {
    return roleAssignmentDTOs.stream()
        .filter(((Predicate<RoleAssignmentDTO>) role -> isRoleAssignmentManaged(role)).negate())
        .collect(toList());
  }

  @VisibleForTesting
  protected void createRoleAssignments(
      String userId, Scope scope, List<RoleAssignmentDTO> roleAssignmentDTOs, boolean managed) {
    if (isEmpty(roleAssignmentDTOs)) {
      return;
    }
    try {
      RoleAssignmentCreateRequestDTO createRequestDTO =
          RoleAssignmentCreateRequestDTO.builder().roleAssignments(roleAssignmentDTOs).build();
      getResponse(accessControlAdminClient.createMultiRoleAssignment(scope.getAccountIdentifier(),
          scope.getOrgIdentifier(), scope.getProjectIdentifier(), managed, createRequestDTO));
    } catch (Exception e) {
      log.error("Could not create all of the role assignments in [{}] for user [{}] at [{}]", roleAssignmentDTOs,
          userId,
          ScopeUtils.toString(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()), e);
    }
  }

  private List<String> getManagedRoleIdentifiers() {
    return ImmutableList.of(ACCOUNT_VIEWER, ORGANIZATION_VIEWER, PROJECT_VIEWER);
  }

  private boolean isRoleAssignmentManaged(RoleAssignmentDTO roleAssignmentDTO) {
    return getManagedRoleIdentifiers().stream().anyMatch(
               roleIdentifier -> roleIdentifier.equals(roleAssignmentDTO.getRoleIdentifier()))
        && MANAGED_RESOURCE_GROUP_IDENTIFIERS.stream().anyMatch(
            resourceGroupIdentifier -> resourceGroupIdentifier.equals(roleAssignmentDTO.getResourceGroupIdentifier()));
  }

  private String getDefaultRoleIdentifier(Scope scope) {
    if (!isBlank(scope.getProjectIdentifier())) {
      return PROJECT_VIEWER;
    } else if (!isBlank(scope.getOrgIdentifier())) {
      return ORGANIZATION_VIEWER;
    }
    return ACCOUNT_VIEWER;
  }

  private void ensureUserMetadata(String userId) {
    Optional<UserMetadata> userMetadataOpt = userMetadataRepository.findDistinctByUserId(userId);
    if (userMetadataOpt.isPresent()) {
      return;
    }
    Optional<UserInfo> userInfoOptional = getUserById(userId);
    UserInfo userInfo = userInfoOptional.orElseThrow(
        () -> new InvalidRequestException(String.format("User with id %s doesn't exists", userId)));

    userMetadataRepository.deleteByEmail(userInfo.getEmail());
    UserMetadata userMetadata = UserMetadata.builder()
                                    .userId(userInfo.getUuid())
                                    .name(userInfo.getName())
                                    .email(userInfo.getEmail())
                                    .locked(userInfo.isLocked())
                                    .disabled(userInfo.isDisabled())
                                    .externallyManaged(userInfo.isExternallyManaged())
                                    .twoFactorAuthenticationEnabled(userInfo.isTwoFactorAuthenticationEnabled())
                                    .build();
    try {
      userMetadataRepository.save(userMetadata);
    } catch (DuplicateKeyException e) {
      log.info(
          "DuplicateKeyException while creating usermembership for user id {}. This race condition is benign", userId);
    }
  }

  private void addUserToParentScope(
      String userId, Scope scope, UserMembershipUpdateSource source, boolean isAccountBasicFeatureFlagEnabled) {
    //  Adding user to the parent scopes as well
    if (!isBlank(scope.getProjectIdentifier())) {
      Scope orgScope = Scope.builder()
                           .accountIdentifier(scope.getAccountIdentifier())
                           .orgIdentifier(scope.getOrgIdentifier())
                           .build();
      addUserToScopeInternal(userId, source, orgScope, ORGANIZATION_VIEWER, isAccountBasicFeatureFlagEnabled);
    }

    if (!isBlank(scope.getOrgIdentifier())) {
      Scope accountScope = Scope.builder().accountIdentifier(scope.getAccountIdentifier()).build();
      addUserToScopeInternal(userId, source, accountScope, ACCOUNT_VIEWER, isAccountBasicFeatureFlagEnabled);
    }
  }

  @VisibleForTesting
  protected void addUserToScopeInternal(String userId, UserMembershipUpdateSource source, Scope scope,
      String roleIdentifier, boolean isAccountBasicFeatureFlagEnabled) {
    Optional<UserMetadata> userMetadata = userMetadataRepository.findDistinctByUserId(userId);
    String publicIdentifier = userMetadata.map(UserMetadata::getEmail).orElse(userId);

    Failsafe.with(transactionRetryPolicy).get(() -> {
      UserMembership userMembership = null;
      try {
        userMembership = userMembershipRepository.save(UserMembership.builder().userId(userId).scope(scope).build());
      } catch (DuplicateKeyException e) {
        //  This is benign. Move on.
      }
      if (userMembership != null) {
        String userName = userMetadata.map(UserMetadata::getName).orElse(null);
        outboxService.save(
            new AddCollaboratorEvent(scope.getAccountIdentifier(), scope, publicIdentifier, userId, userName, source));
      }
      return userMembership;
    });

    if (!isAccountBasicFeatureFlagEnabled) {
      try {
        RoleAssignmentDTO roleAssignmentDTO =
            RoleAssignmentDTO.builder()
                .principal(PrincipalDTO.builder().type(USER).identifier(userId).build())
                .resourceGroupIdentifier(getDefaultResourceGroupIdentifier(scope))
                .disabled(false)
                .roleIdentifier(roleIdentifier)
                .build();
        NGRestUtils.getResponse(accessControlAdminClient.createMultiRoleAssignment(scope.getAccountIdentifier(),
            scope.getOrgIdentifier(), scope.getProjectIdentifier(), true,
            RoleAssignmentCreateRequestDTO.builder().roleAssignments(singletonList(roleAssignmentDTO)).build()));
      } catch (Exception e) {
        /**
         *  It's expected that user might already have this roleassignment.
         */
      }
    }
  }

  public void addUserToCG(String userId, Scope scope) {
    try {
      CGRestUtils.getResponse(userClient.addUserToAccount(userId, scope.getAccountIdentifier()));
    } catch (Exception e) {
      log.error("Couldn't add user to the account", e);
    }
  }

  @Override
  public void updateNGUserToCGWithSource(String userId, Scope scope, UserSource userSource) {
    String accountIdentifier = scope.getAccountIdentifier();
    try {
      if (ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW)) {
        CGRestUtils.getResponse(userClient.updateNGUserToCGWithSource(userId, accountIdentifier, userSource));
      }
    } catch (Exception e) {
      log.error("Couldn't update user Account level data for user {} in account {}", userId, accountIdentifier, e);
    }
  }
  @Override
  public Optional<UserInfo> getUserById(String userId) {
    return CGRestUtils.getResponse(userClient.getUserById(userId));
  }

  @Override
  public boolean isUserAtScope(String userId, Scope scope) {
    Criteria criteria = Criteria.where(UserMembershipKeys.userId)
                            .is(userId)
                            .and(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY)
                            .is(scope.getAccountIdentifier())
                            .and(UserMembershipKeys.ORG_IDENTIFIER_KEY)
                            .is(scope.getOrgIdentifier())
                            .and(UserMembershipKeys.PROJECT_IDENTIFIER_KEY)
                            .is(scope.getProjectIdentifier());

    return null != userMembershipRepository.findOne(criteria);
  }

  @Override
  public boolean updateUserMetadata(UserMetadataDTO user) {
    Optional<UserMetadata> savedUserOpt = userMetadataRepository.findDistinctByUserId(user.getUuid());
    if (!savedUserOpt.isPresent()) {
      return true;
    }
    Update update = new Update();
    if (!isBlank(user.getName()) && !user.getName().equals(savedUserOpt.get().getName())) {
      update.set(UserMetadataKeys.name, user.getName());
      update.set(UserMetadataKeys.locked, user.isLocked());
      update.set(UserMetadataKeys.disabled, user.isDisabled());
      update.set(UserMetadataKeys.externallyManaged, user.isExternallyManaged());
      update.set(UserMetadataKeys.twoFactorAuthenticationEnabled, user.isTwoFactorAuthenticationEnabled());
    }
    if (!isBlank(user.getEmail()) && !user.getEmail().equals(savedUserOpt.get().getEmail())) {
      update.set(UserMetadataKeys.email, user.getEmail());
      update.set(UserMetadataKeys.locked, user.isLocked());
      update.set(UserMetadataKeys.disabled, user.isDisabled());
      update.set(UserMetadataKeys.externallyManaged, user.isExternallyManaged());
      update.set(UserMetadataKeys.twoFactorAuthenticationEnabled, user.isTwoFactorAuthenticationEnabled());
    }
    if (user.isTwoFactorAuthenticationEnabled() != savedUserOpt.get().isTwoFactorAuthenticationEnabled()) {
      update.set(UserMetadataKeys.locked, user.isLocked());
      update.set(UserMetadataKeys.disabled, user.isDisabled());
      update.set(UserMetadataKeys.externallyManaged, user.isExternallyManaged());
      update.set(UserMetadataKeys.twoFactorAuthenticationEnabled, user.isTwoFactorAuthenticationEnabled());
    }
    return userMetadataRepository.updateFirst(user.getUuid(), update) != null;
  }

  @Override
  public boolean removeUserFromScope(
      String userId, Scope scope, UserMembershipUpdateSource source, NGRemoveUserFilter removeUserFilter) {
    log.info("Trying to remove user {} from scope {}", userId, ScopeUtils.toString(scope));
    try (TimeLogger timeLogger = new TimeLogger(LoggerFactory.getLogger(getClass().getName()))) {
      Optional<UserMembership> currentScopeUserMembership = getUserMembership(userId, scope);
      if (!currentScopeUserMembership.isPresent()) {
        return false;
      }
      Criteria userMembershipCriteria = getCriteriaForFetchingChildScopes(userId, scope);
      validateUserMembershipsDeletion(scope, userId, removeUserFilter);

      Optional<UserMetadata> userMetadata = userMetadataRepository.findDistinctByUserId(userId);
      String publicIdentifier = userMetadata.map(UserMetadata::getEmail).orElse(userId);
      String userName = userMetadata.map(UserMetadata::getName).orElse(null);

      try (CloseableIterator<UserMembership> iterator = userMembershipRepository.stream(userMembershipCriteria)) {
        while (iterator.hasNext()) {
          UserMembership userMembership = iterator.next();
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            userMembershipRepository.delete(userMembership);
            outboxService.save(new RemoveCollaboratorEvent(
                scope.getAccountIdentifier(), userMembership.getScope(), publicIdentifier, userId, userName, source));
            return userMembership;
          }));
        }
      }
    }
    return true;
  }

  @Override
  public boolean removeUserWithCriteria(String userId, UserMembershipUpdateSource source, Criteria criteria) {
    log.info("Trying to remove user {} with criteria {}", userId, criteria.toString());
    try (TimeLogger timeLogger = new TimeLogger(LoggerFactory.getLogger(getClass().getName()))) {
      Optional<UserMetadata> userMetadata = userMetadataRepository.findDistinctByUserId(userId);
      String publicIdentifier = userMetadata.map(UserMetadata::getEmail).orElse(userId);
      String userName = userMetadata.map(UserMetadata::getName).orElse(null);

      try (CloseableIterator<UserMembership> iterator = userMembershipRepository.stream(criteria)) {
        while (iterator.hasNext()) {
          UserMembership userMembership = iterator.next();
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            userMembershipRepository.delete(userMembership);
            outboxService.save(new RemoveCollaboratorEvent(userMembership.getScope().getAccountIdentifier(),
                userMembership.getScope(), publicIdentifier, userId, userName, source));
            return userMembership;
          }));
        }
      }
    }
    return true;
  }

  private void validateUserMembershipsDeletion(Scope scope, String userId, NGRemoveUserFilter removeUserFilter) {
    if (!NGRemoveUserFilter.STRICTLY_FORCE_REMOVE_USER.equals(removeUserFilter) && isEmpty(scope.getOrgIdentifier())) {
      checkIfUserIsLastAccountAdmin(scope.getAccountIdentifier(), userId);
    }
  }

  private void checkIfUserIsLastAccountAdmin(String accountIdentifier, String userId) {
    if (!lastAccountAdminCheckService.doesAdminExistAfterRemoval(
            accountIdentifier, new LastAdminCheckFilter(userId, null))) {
      throw new InvalidUserRemoveRequestException(
          "Removing this user will remove the last Account Admin from NG. Cannot remove the user.",
          singletonList(Scope.of(accountIdentifier, null, null)));
    }
  }

  private Criteria getCriteriaForFetchingChildScopes(String userId, Scope scope) {
    Criteria criteria = Criteria.where(UserMembershipKeys.userId)
                            .is(userId)
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.accountIdentifier)
                            .is(scope.getAccountIdentifier());
    if (isNotBlank(scope.getProjectIdentifier())) {
      criteria.and(UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier).is(scope.getOrgIdentifier());
      criteria.and(UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier).is(scope.getProjectIdentifier());
    } else if (isNotBlank(scope.getOrgIdentifier())) {
      criteria.and(UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier).is(scope.getOrgIdentifier());
    }
    return criteria;
  }

  @Override
  public boolean isUserLastAdminAtScope(String userId, Scope scope) {
    String roleIdentifier;
    if (!isBlank(scope.getProjectIdentifier())) {
      roleIdentifier = PROJECT_ADMIN;
    } else if (!isBlank(scope.getOrgIdentifier())) {
      roleIdentifier = ORG_ADMIN;
    } else {
      roleIdentifier = ACCOUNT_ADMIN;
    }
    List<UserMetadataDTO> scopeAdmins = listUsersHavingRole(scope, roleIdentifier);
    return scopeAdmins.stream().allMatch(userMetadata -> userId.equals(userMetadata.getUuid()));
  }

  @Override
  public boolean isAccountAdmin(String userId, String accountIdentifier) {
    if (Edition.COMMUNITY.equals(licenseService.calculateAccountEdition(accountIdentifier))) {
      return isUserAtScope(userId, Scope.of(accountIdentifier, null, null));
    }
    PrincipalDTO principalDTO = PrincipalDTO.builder().identifier(userId).type(PrincipalType.USER).build();
    PageResponse<RoleAssignmentResponseDTO> roleAssignmentPages = getResponse(
        accessControlAdminClient.getFilteredRoleAssignments(accountIdentifier, null, null, 0, DEFAULT_PAGE_SIZE,
            RoleAssignmentFilterDTO.builder()
                .roleFilter(Collections.singleton(ACCOUNT_ADMIN_ROLE))
                .principalFilter(Collections.singleton(principalDTO))
                .build()));
    return !roleAssignmentPages.getContent().isEmpty();
  }

  @Override
  public boolean isUserPasswordSet(String accountIdentifier, String email) {
    return CGRestUtils.getResponse(userClient.isUserPasswordSet(accountIdentifier, email));
  }

  @Override
  public List<String> listUserAccountIds(String userId) {
    Optional<UserInfo> userInfoOptional = getUserById(userId);
    if (userInfoOptional.isPresent()) {
      return userInfoOptional.get()
          .getAccounts()
          .stream()
          .map(GatewayAccountRequestDTO::getUuid)
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public boolean removeUser(String userId, String accountId) {
    return CGRestUtils.getResponse(userClient.deleteUser(userId, accountId));
  }

  @Override
  public ScimUser updateScimUser(String accountId, String userId, PatchRequest patchRequest) {
    return CGRestUtils.getResponse(userClient.scimUserPatchUpdate(accountId, userId, patchRequest));
  }

  @Override
  public ScimUser updateUserDetails(String accountId, String userId, PatchRequest patchRequest) {
    return CGRestUtils.getResponse(userClient.scimUserPatchUpdateDeatils(accountId, userId, patchRequest));
  }

  @Override
  public boolean updateScimUser(String accountId, String userId, ScimUser scimUser) {
    return CGRestUtils.getResponse(userClient.scimUserUpdate(accountId, userId, scimUser));
  }

  @Override
  public boolean updateUserDisabled(String accountId, String userId, boolean disabled) {
    return CGRestUtils.getResponse(userClient.updateUserDisabled(accountId, userId, disabled));
  }

  @Override
  public boolean verifyHarnessSupportGroupUser() {
    try {
      Collection<io.harness.ng.core.user.UserMetadata> supportUsers =
          CGRestUtils.getResponse(accountClient.listAllHarnessSupportUsers(),
              "Unexpected error, could not fetch the harness support group users");
      if (supportUsers == null) {
        throw new UnexpectedException("Unexpected error, could not fetch the harness support group users");
      }
      Principal currentPrincipal = SourcePrincipalContextBuilder.getSourcePrincipal() == null
          ? SecurityContextBuilder.getPrincipal()
          : SourcePrincipalContextBuilder.getSourcePrincipal();
      return supportUsers.stream().anyMatch(userMetadata
          -> currentPrincipal != null && io.harness.security.dto.PrincipalType.USER.equals(currentPrincipal.getType())
              && userMetadata.getId().equals(currentPrincipal.getName()));
    } catch (InvalidRequestException e) {
      throw new UnexpectedException("Unexpected error, could not fetch the harness support group users");
    }
  }

  @Override
  public UsersCountDTO getUsersCount(Scope scope, long startInterval, long endInterval) {
    Criteria criteria = Criteria.where(UserMembershipKeys.scope + "." + ScopeKeys.accountIdentifier)
                            .is(scope.getAccountIdentifier())
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier)
                            .is(scope.getOrgIdentifier())
                            .and(UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier)
                            .is(scope.getProjectIdentifier())
                            .and(UserMembershipKeys.createdAt)
                            .gte(startInterval)
                            .lt(endInterval);
    long newUsersCount = userMembershipRepository.count(criteria);
    criteria = Criteria.where(UserMembershipKeys.scope + "." + ScopeKeys.accountIdentifier)
                   .is(scope.getAccountIdentifier())
                   .and(UserMembershipKeys.scope + "." + ScopeKeys.orgIdentifier)
                   .is(scope.getOrgIdentifier())
                   .and(UserMembershipKeys.scope + "." + ScopeKeys.projectIdentifier)
                   .is(scope.getProjectIdentifier());
    return UsersCountDTO.builder().totalCount(userMembershipRepository.count(criteria)).newCount(newUsersCount).build();
  }
}
