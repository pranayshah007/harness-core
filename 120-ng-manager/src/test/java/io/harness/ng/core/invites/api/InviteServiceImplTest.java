/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.invites.api;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.invites.InviteType.ADMIN_INITIATED_INVITE;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.INVITE_INVALID;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_ALREADY_ADDED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_ALREADY_INVITED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_INVITED_SUCCESSFULLY;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_INVITE_NOT_REQUIRED;
import static io.harness.rule.OwnerRule.ANKUSH;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.invites.remote.InviteAcceptResponse;
import io.harness.mongo.MongoConfig;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.UserInviteDTO;
import io.harness.ng.core.invites.InviteType;
import io.harness.ng.core.invites.JWTGeneratorUtils;
import io.harness.ng.core.invites.api.impl.InviteServiceImpl;
import io.harness.ng.core.invites.dto.InviteOperationResponse;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.invites.entities.Invite.InviteKeys;
import io.harness.ng.core.user.TwoFactorAuthSettingsInfo;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResultWithStatus;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.invites.spring.InviteRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;
import io.harness.user.remote.UserClient;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.auth0.jwt.interfaces.Claim;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class InviteServiceImplTest extends CategoryTest {
  private static final String USER_VERIFICATION_SECRET = "abcde";
  private static final String accountIdentifier = randomAlphabetic(7);
  private static final String orgIdentifier = randomAlphabetic(7);
  private static final String projectIdentifier = randomAlphabetic(7);
  private static final String emailId = String.format("%s@%s", randomAlphabetic(7), randomAlphabetic(7));
  private static final String userId = randomAlphabetic(10);
  private static final String inviteId = randomAlphabetic(10);
  private static final String EMAIL_NOTIFY_TEMPLATE_ID = "email_notify";
  private static final String EMAIL_INVITE_TEMPLATE_ID = "email_invite";
  private static final String SHOULD_MAIL_CONTAIN_TWO_FACTOR_INFO = "shouldMailContainTwoFactorInfo";
  @Mock private JWTGeneratorUtils jwtGeneratorUtils;
  @Mock private NgUserService ngUserService;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private InviteRepository inviteRepository;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) AccountClient accountClient;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) UserClient userClient;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private AccessControlClient accessControlClient;
  @Mock private NotificationClient notificationClient;
  @Mock private OutboxService outboxService;
  @Mock private UserGroupService userGroupService;
  @Mock private AccountOrgProjectHelper accountOrgProjectHelper;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private ScheduledExecutorService executorService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Captor private ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor;

  private InviteService inviteService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    MongoConfig mongoConfig = MongoConfig.builder().uri("mongodb://localhost:27017/ng-harness").build();
    inviteService = new InviteServiceImpl(USER_VERIFICATION_SECRET, mongoConfig, jwtGeneratorUtils, ngUserService,
        transactionTemplate, inviteRepository, notificationClient, accountClient, outboxService, accessControlClient,
        userClient, accountOrgProjectHelper, false, telemetryReporter, ngFeatureFlagHelperService, executorService);

    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(Response.success(new RestResponse(AccountDTO.builder()
                                                          .identifier(accountIdentifier)
                                                          .companyName(accountIdentifier)
                                                          .name(accountIdentifier)
                                                          .build())));
    when(accountOrgProjectHelper.getBaseUrl(any())).thenReturn("qa.harness.io");
    when(notificationClient.sendNotificationAsync(any())).thenReturn(new NotificationResultWithStatus());
    when(accountOrgProjectHelper.getProjectName(any(), any(), any())).thenReturn("Project");
    when(accountOrgProjectHelper.getOrgName(any(), any())).thenReturn("Organization");
    when(accountOrgProjectHelper.getAccountName(any())).thenReturn("Account");

    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.checkUserLimit(any(), anyString())).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    when(ngFeatureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any())).thenReturn(ffCall);
    when(accountClient.checkPLNoEmailForSamlAccountInvitesEnabledForAccount(anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));
  }

  private Invite getDummyInvite() {
    return Invite.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .approved(Boolean.FALSE)
        .email(emailId)
        .name(randomAlphabetic(7))
        .id(inviteId)
        .roleBindings(getDummyRoleBinding())
        .inviteType(ADMIN_INITIATED_INVITE)
        .build();
  }

  private List<RoleBinding> getDummyRoleBinding() {
    return Collections.singletonList(RoleBinding.builder()
                                         .managedRole(false)
                                         .resourceGroupIdentifier(randomAlphabetic(7))
                                         .resourceGroupName(randomAlphabetic(7))
                                         .roleIdentifier(randomAlphabetic(7))
                                         .roleName(randomAlphabetic(7))
                                         .build());
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_NullInvite() {
    InviteOperationResponse inviteOperationResponse = inviteService.create(null, false, false);
    assertThat(inviteOperationResponse).isEqualTo(InviteOperationResponse.FAIL);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_UserAlreadyExists_UserAlreadyAdded() {
    when(ngUserService.isUserAtScope(any(), any())).thenReturn(true);
    when(ngUserService.getUserByEmail(any(), anyBoolean()))
        .thenReturn(Optional.of(UserMetadataDTO.builder().uuid(userId).build()));

    InviteOperationResponse inviteOperationResponse = inviteService.create(getDummyInvite(), false, false);
    assertThat(inviteOperationResponse).isEqualTo(USER_ALREADY_ADDED);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateUserLimit() {
    when(ngUserService.isUserAtScope(any(), any())).thenReturn(true);
    when(ngUserService.getUserByEmail(any(), anyBoolean()))
        .thenReturn(Optional.of(UserMetadataDTO.builder().uuid(userId).build()));

    InviteOperationResponse invite = inviteService.create(getDummyInvite(), false, false);
    assertThat(invite).isEqualTo(USER_ALREADY_ADDED);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_UserAlreadyExists_UserNotInvitedYet() throws IOException {
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(Optional.of(user));
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    InviteOperationResponse inviteOperationResponse = inviteService.create(getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(userClient, times(0)).updateUserTwoFactorAuthInfo(eq(emailId), any(TwoFactorAuthSettingsInfo.class));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_UserDNE_UserNotInvitedYet() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any()).execute())
        .thenReturn(Response.success(new RestResponse(false)));
    InviteOperationResponse inviteOperationResponse = inviteService.create(getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(userClient, times(0)).updateUserTwoFactorAuthInfo(eq(emailId), any(TwoFactorAuthSettingsInfo.class));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_UserInvitedBefore() {
    ArgumentCaptor<String> idArgumentCaptor = ArgumentCaptor.forClass(String.class);
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();

    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.of(user), Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.of(getDummyInvite()));

    //    when user exists
    InviteOperationResponse inviteOperationResponse = inviteService.create(getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_ALREADY_INVITED);
    verify(inviteRepository, atLeast(2)).updateInvite(idArgumentCaptor.capture(), any());
    String id = idArgumentCaptor.getValue();
    assertThat(id).isEqualTo(inviteId);
    verify(notificationClient, times(1)).sendNotificationAsync(any());

    //    when user doesn't exists
    inviteOperationResponse = inviteService.create(getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_ALREADY_INVITED);
    verify(inviteRepository, atLeast(2)).updateInvite(idArgumentCaptor.capture(), any());
    id = idArgumentCaptor.getValue();
    assertThat(id).isEqualTo(inviteId);
    verify(notificationClient, times(2)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_NewUser_InviteAccepted() {
    Invite invite = Invite.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .approved(Boolean.FALSE)
                        .email(emailId)
                        .name(randomAlphabetic(7))
                        .id(inviteId)
                        .roleBindings(getDummyInvite().getRoleBindings())
                        .inviteType(ADMIN_INITIATED_INVITE)
                        .approved(Boolean.TRUE)
                        .build();
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.of(invite));

    InviteOperationResponse inviteOperationResponse = inviteService.create(invite, false, false);

    assertThat(inviteOperationResponse).isEqualTo(ACCOUNT_INVITE_ACCEPTED);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testCreate_UserDoesNotExist_UserNotInvitedYet() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any()).execute())
        .thenReturn(Response.success(new RestResponse(false)));
    Invite dummyInvite = getDummyInvite();
    dummyInvite.setExternalId("test_external_id");
    InviteOperationResponse inviteOperationResponse = inviteService.create(dummyInvite, false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testCreate_NewUser_InviteAccepted_LdapGroup() {
    final String testExternalId = "test_external_id";
    Invite invite = Invite.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .approved(Boolean.FALSE)
                        .email(emailId)
                        .name(randomAlphabetic(7))
                        .externalId(testExternalId)
                        .id(inviteId)
                        .roleBindings(getDummyInvite().getRoleBindings())
                        .inviteType(ADMIN_INITIATED_INVITE)
                        .approved(Boolean.TRUE)
                        .build();
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.of(invite));

    InviteOperationResponse inviteOperationResponse = inviteService.create(invite, false, true);
    assertThat(inviteOperationResponse).isEqualTo(ACCOUNT_INVITE_ACCEPTED);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void deleteInvite_inviteExists() {
    ArgumentCaptor<String> idArgumentCaptor = ArgumentCaptor.forClass(String.class);
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(Optional.of(getDummyInvite()));
    when(inviteRepository.updateInvite(any(), any())).thenReturn(getDummyInvite());

    inviteService.deleteInvite(inviteId);

    verify(inviteRepository, times(1)).updateInvite(idArgumentCaptor.capture(), any());
    assertThat(idArgumentCaptor.getValue()).isEqualTo(inviteId);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void deleteInvite_InviteDNE() {
    ArgumentCaptor<String> idArgumentCaptor = ArgumentCaptor.forClass(String.class);
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(Optional.empty());

    inviteService.deleteInvite(inviteId);
    verify(inviteRepository, times(0)).updateInvite(idArgumentCaptor.capture(), any());
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void acceptInvite_InvalidJWTToken() {
    InviteAcceptResponse inviteAcceptResponse = inviteService.acceptInvite(null);
    assertThat(inviteAcceptResponse.getResponse()).isEqualTo(INVITE_INVALID);

    inviteAcceptResponse = inviteService.acceptInvite("");
    assertThat(inviteAcceptResponse.getResponse()).isEqualTo(INVITE_INVALID);

    when(jwtGeneratorUtils.verifyJWTToken(any(), any())).thenReturn(Collections.emptyMap());

    inviteAcceptResponse = inviteService.acceptInvite("sadfs");
    assertThat(inviteAcceptResponse.getResponse()).isEqualTo(INVITE_INVALID);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void acceptInvite_validToken() {
    String dummyJWTToken = "dummy invite token";
    Claim claim = mock(Claim.class);
    Invite invite = getDummyInvite();
    invite.setInviteToken(dummyJWTToken);
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();
    ArgumentCaptor<String> idCapture = ArgumentCaptor.forClass(String.class);

    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.verifyJWTToken(any(), any())).thenReturn(Collections.singletonMap(InviteKeys.id, claim));
    when(inviteRepository.findById(any())).thenReturn(Optional.of(invite));
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(Optional.of(user));

    InviteAcceptResponse inviteAcceptResponse = inviteService.acceptInvite(dummyJWTToken);

    assertThat(inviteAcceptResponse.getResponse()).isEqualTo(ACCOUNT_INVITE_ACCEPTED);
    verify(inviteRepository, times(1)).updateInvite(idCapture.capture(), any());
    assertThat(idCapture.getValue()).isEqualTo(inviteId);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void updateInvite_invalidInviteId() {
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(Optional.empty());
    Optional<Invite> returnInvite = inviteService.updateInvite(getDummyInvite());
    assertThat(returnInvite.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void updateInvite_ValidInviteId() {
    String dummyJWTToken = "Dummy jwt token";
    Claim claim = mock(Claim.class);
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(Optional.of(getDummyInvite()));
    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.generateJWTToken(any(), any(), any())).thenReturn(dummyJWTToken);
    when(notificationClient.sendNotificationAsync(any())).thenReturn(NotificationResultWithStatus.builder().build());

    Optional<Invite> returnInvite = inviteService.updateInvite(getDummyInvite());
    assertThat(returnInvite.isPresent()).isTrue();
    assertThat(returnInvite.get().getInviteToken()).isEqualTo(dummyJWTToken);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void updateInvite_ValidInviteId_UserInitiatedInvite() {
    String dummyJWTToken = "Dummy jwt token";
    Invite invite = getDummyInvite();
    invite.setInviteType(InviteType.USER_INITIATED_INVITE);
    Claim claim = mock(Claim.class);
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(Optional.of(invite));
    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.generateJWTToken(any(), any(), any())).thenReturn(dummyJWTToken);
    when(notificationClient.sendNotificationAsync(any())).thenReturn(NotificationResultWithStatus.builder().build());

    assertThatThrownBy(() -> inviteService.updateInvite(invite)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void completeInvite_InvalidJWTToken() {
    when(jwtGeneratorUtils.verifyJWTToken(any(), any())).thenReturn(Collections.emptyMap());

    boolean result = inviteService.completeInvite(Optional.empty());
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void completeInvite_ValidToken_UserNotPresent() {
    String dummyJWTTOken = "dummy jwt token";
    Claim claim = mock(Claim.class);
    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.verifyJWTToken(any(), any())).thenReturn(Collections.singletonMap(InviteKeys.id, claim));
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(Optional.of(getDummyInvite()));
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> inviteService.completeInvite(Optional.of(getDummyInvite())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void completeInvite_ValidToken() {
    String dummyJWTTOken = "dummy jwt token";
    Claim claim = mock(Claim.class);
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    ArgumentCaptor<Update> updateCapture = ArgumentCaptor.forClass(Update.class);
    ArgumentCaptor<String> idCapture = ArgumentCaptor.forClass(String.class);

    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.verifyJWTToken(any(), any())).thenReturn(Collections.singletonMap(InviteKeys.id, claim));
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(Optional.of(getDummyInvite()));
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(Optional.of(user));
    doNothing().when(ngUserService).waitForRbacSetup(any(), anyString(), anyString());
    boolean result = inviteService.completeInvite(Optional.of(getDummyInvite()));
    verify(ngUserService, times(1)).waitForRbacSetup(any(), anyString(), anyString());
    assertThat(result).isTrue();
    verify(inviteRepository, times(1)).updateInvite(idCapture.capture(), updateCapture.capture());
    assertThat(idCapture.getValue()).isEqualTo(inviteId);
    assertThat(updateCapture.getValue().modifies(InviteKeys.deleted)).isTrue();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testCreate_withSsoEnabled_withAutoInviteAcceptanceEnabled() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(any(), anyBoolean(), anyBoolean())).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));

    InviteOperationResponse inviteOperationResponse = inviteService.create(getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(notificationClient).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateId()).isEqualTo(EMAIL_NOTIFY_TEMPLATE_ID);
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateData().get(SHOULD_MAIL_CONTAIN_TWO_FACTOR_INFO))
        .isEqualTo("false");
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreate_withSsoEnabled_withAutoInviteAcceptanceEnabled_withTwoFactorEnforced() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(any(), anyBoolean(), anyBoolean())).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Call<RestResponse<Optional<UserInfo>>> userUpdateCall = mock(Call.class);
    when(userClient.updateUserTwoFactorAuthInfo(any(), any())).thenReturn(userUpdateCall);
    when(userUpdateCall.execute())
        .thenReturn(Response.success(new RestResponse<>(Optional.of(UserInfo.builder().build()))));
    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(Response.success(new RestResponse(AccountDTO.builder()
                                                          .identifier(accountIdentifier)
                                                          .companyName(accountIdentifier)
                                                          .name(accountIdentifier)
                                                          .isTwoFactorAdminEnforced(true)
                                                          .build())));

    InviteOperationResponse inviteOperationResponse = inviteService.create(getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(notificationClient).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateId()).isEqualTo(EMAIL_NOTIFY_TEMPLATE_ID);
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateData().get(SHOULD_MAIL_CONTAIN_TWO_FACTOR_INFO))
        .isEqualTo("true");
    verify(userClient, times(1)).updateUserTwoFactorAuthInfo(eq(emailId), any(TwoFactorAuthSettingsInfo.class));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testCreate_withSsoEnabled_withPLNoEmailForSamlAccountInvitesEnabled() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    Call<RestResponse<Boolean>> call = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(any(), anyBoolean(), anyBoolean())).thenReturn(call);
    when(accountClient.checkPLNoEmailForSamlAccountInvitesEnabledForAccount(anyString())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(new RestResponse<>(true)));

    InviteOperationResponse inviteOperationResponse = inviteService.create(getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITE_NOT_REQUIRED);
    verify(notificationClient, times(0)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreate_withSsoEnabled_withPLNoEmailForSamlAccountInvitesEnabled_withTwoFactorEnforced()
      throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    Call<RestResponse<Boolean>> call = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(any(), anyBoolean(), anyBoolean())).thenReturn(call);
    Call<RestResponse<Optional<UserInfo>>> userUpdateCall = mock(Call.class);
    when(userClient.updateUserTwoFactorAuthInfo(any(), any())).thenReturn(userUpdateCall);
    when(userUpdateCall.execute())
        .thenReturn(Response.success(new RestResponse<>(Optional.of(UserInfo.builder().build()))));
    when(accountClient.checkPLNoEmailForSamlAccountInvitesEnabledForAccount(anyString())).thenReturn(call);
    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(Response.success(new RestResponse(AccountDTO.builder()
                                                          .identifier(accountIdentifier)
                                                          .companyName(accountIdentifier)
                                                          .name(accountIdentifier)
                                                          .isTwoFactorAdminEnforced(true)
                                                          .build())));
    when(call.execute()).thenReturn(Response.success(new RestResponse<>(true)));

    InviteOperationResponse inviteOperationResponse = inviteService.create(getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(userClient, times(1)).updateUserTwoFactorAuthInfo(eq(emailId), any(TwoFactorAuthSettingsInfo.class));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testCreate_withInviteEmail_with2FaEnforcedAtAccountLevel() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(any(), anyBoolean(), anyBoolean())).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Call<RestResponse<Optional<UserInfo>>> userInfoCall = mock(Call.class);
    when(userClient.updateUserTwoFactorAuthInfo(any(), any())).thenReturn(userInfoCall);
    when(userInfoCall.execute())
        .thenReturn(Response.success(new RestResponse<>(Optional.of(UserInfo.builder().build()))));
    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(Response.success(new RestResponse(AccountDTO.builder()
                                                          .identifier(accountIdentifier)
                                                          .companyName(accountIdentifier)
                                                          .name(accountIdentifier)
                                                          .isTwoFactorAdminEnforced(true)
                                                          .build())));

    InviteOperationResponse inviteOperationResponse = inviteService.create(getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(notificationClient).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateId()).isEqualTo(EMAIL_INVITE_TEMPLATE_ID);
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateData().get(SHOULD_MAIL_CONTAIN_TWO_FACTOR_INFO))
        .isEqualTo("true");
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUserCreation_scimUser() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Invite invite = Invite.builder()
                        .accountIdentifier("accountId")
                        .approved(true)
                        .email("primaryEmail")
                        .name("displayName")
                        .givenName("givenName")
                        .familyName("familyName")
                        .externalId("externalId")
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .inviteType(InviteType.SCIM_INITIATED_INVITE)
                        .id(inviteId)
                        .roleBindings(getDummyRoleBinding())
                        .build();
    when(inviteRepository.save(any())).thenReturn(invite);
    ArgumentCaptor<UserInviteDTO> argumentCaptor = ArgumentCaptor.forClass(UserInviteDTO.class);
    ArgumentCaptor<Boolean> argumentCaptor1 = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<Boolean> argumentCaptor2 = ArgumentCaptor.forClass(Boolean.class);
    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(
             argumentCaptor.capture(), argumentCaptor1.capture(), argumentCaptor2.capture()))
        .thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    inviteService.create(invite, true, false);
    assertThat(argumentCaptor1.getValue()).isEqualTo(true);
    assertThat(argumentCaptor2.getValue()).isEqualTo(false);
    assertThat(argumentCaptor.getValue().getName()).isEqualTo("displayName");
    assertThat(argumentCaptor.getValue().getEmail()).isEqualTo("primaryEmail");
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUserCreation_withoutName_shouldPopulateEmailInNameField() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.findFirstByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndEmailAndDeletedFalse(
             any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Invite invite = Invite.builder()
                        .accountIdentifier("accountId")
                        .approved(true)
                        .email("primaryEmail")
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .id(inviteId)
                        .roleBindings(getDummyRoleBinding())
                        .inviteType(InviteType.ADMIN_INITIATED_INVITE)
                        .build();
    when(inviteRepository.save(any())).thenReturn(invite);
    ArgumentCaptor<UserInviteDTO> argumentCaptor = ArgumentCaptor.forClass(UserInviteDTO.class);
    ArgumentCaptor<Boolean> argumentCaptor1 = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<Boolean> argumentCaptor2 = ArgumentCaptor.forClass(Boolean.class);
    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(
             argumentCaptor.capture(), argumentCaptor1.capture(), argumentCaptor2.capture()))
        .thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    inviteService.create(invite, true, false);
    assertThat(argumentCaptor1.getValue()).isEqualTo(true);
    assertThat(argumentCaptor2.getValue()).isEqualTo(false);
    assertThat(argumentCaptor.getValue().getName()).isEqualTo("primaryEmail");
    assertThat(argumentCaptor.getValue().getEmail()).isEqualTo("primaryEmail");
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetRedirectUrl_ExistingPasswordUser_shouldSend2fa() throws IOException {
    String userId = randomAlphabetic(10);

    InviteAcceptResponse inviteAcceptResponse = InviteAcceptResponse.builder()
                                                    .response(USER_INVITED_SUCCESSFULLY)
                                                    .userInfo(UserInfo.builder().uuid(userId).email(emailId).build())
                                                    .accountIdentifier(accountIdentifier)
                                                    .build();
    AccountDTO accountDTO = AccountDTO.builder()
                                .authenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                                .isTwoFactorAdminEnforced(true)
                                .companyName(randomAlphabetic(7))
                                .build();

    when(ngUserService.isUserPasswordSet(accountIdentifier, emailId)).thenReturn(true);
    when(accountClient.getAccountDTO(accountIdentifier).execute())
        .thenReturn(Response.success(new RestResponse(accountDTO)));
    when(userClient.updateUserTwoFactorAuthInfo(eq(emailId), any(TwoFactorAuthSettingsInfo.class)).execute())
        .thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));

    Call call = mock(Call.class);
    when(userClient.sendTwoFactorAuthenticationResetEmail(userId, accountIdentifier)).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(new RestResponse(true)));

    URI uri = inviteService.getRedirectUrl(
        inviteAcceptResponse, URLEncoder.encode(emailId, "UTF-8"), emailId, randomAlphabetic(10));

    assertThat(uri).isNotNull();
    assertThat(uri).isEqualTo(URI.create(String.format("/ng/#/account/%s/home/get-started", accountIdentifier)));
    verify(userClient, times(1)).sendTwoFactorAuthenticationResetEmail(userId, accountIdentifier);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetRedirectUrl_ExistingPasswordUser_shouldNotSend2fa() throws IOException {
    String userId = randomAlphabetic(10);
    InviteAcceptResponse inviteAcceptResponse =
        InviteAcceptResponse.builder()
            .response(USER_INVITED_SUCCESSFULLY)
            .userInfo(UserInfo.builder().uuid(userId).email(emailId).twoFactorAuthenticationEnabled(true).build())
            .accountIdentifier(accountIdentifier)
            .build();
    when(ngUserService.isUserPasswordSet(accountIdentifier, emailId)).thenReturn(true);

    AccountDTO accountDTO = AccountDTO.builder()
                                .authenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                                .isTwoFactorAdminEnforced(true)
                                .companyName(randomAlphabetic(7))
                                .build();
    when(accountClient.getAccountDTO(accountIdentifier).execute())
        .thenReturn(Response.success(new RestResponse(accountDTO)));

    Call call = mock(Call.class);
    when(userClient.sendTwoFactorAuthenticationResetEmail(userId, accountIdentifier)).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(new RestResponse(true)));

    URI uri = inviteService.getRedirectUrl(
        inviteAcceptResponse, URLEncoder.encode(emailId, "UTF-8"), emailId, randomAlphabetic(10));

    assertThat(uri).isNotNull();
    assertThat(uri).isEqualTo(URI.create(String.format("/ng/#/account/%s/home/get-started", accountIdentifier)));
    verify(userClient, never()).sendTwoFactorAuthenticationResetEmail(any(String.class), any(String.class));
  }
}
