package io.harness.ldap.scheduler;

import static io.harness.rule.OwnerRule.PRATEEK;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapUserResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PL)
@RunWith(MockitoJUnitRunner.class)
public class NGLdapSyncHelperTest extends CategoryTest {
  NgUserService ngUserService = mock(NgUserService.class);
  InviteService inviteService = mock(InviteService.class);
  UserClient userClient = mock(UserClient.class);
  UserGroupService userGroupService = mock(UserGroupService.class);

  @Spy @InjectMocks private NGLdapGroupSyncHelper ldapGroupSyncHelper;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String LDAP_SETTINGS_ID = "SSO_ID";

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testReconcileAllUserGroupsAddUser() throws IOException {
    int totalMembers = 1;

    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    final String userGrpId = "UG1";
    LdapUserResponse usrResponse = LdapUserResponse.builder().email(testUserEmail).name(testUserName).build();
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.singletonList(usrResponse))
                                     .build();
    UserGroup userGrp = UserGroup.builder()
                            .identifier(userGrpId)
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .ssoGroupId(groupDn)
                            .users(Collections.emptyList())
                            .notificationConfigs(new ArrayList<>())
                            .build();
    UserInfo userInfo = UserInfo.builder().name(testUserName).email(testUserEmail).uuid("USER_ID1").build();

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(userGrp, response);
    when(userGroupService.update(any())).thenReturn(userGrp);
    when(ngUserService.getUserInfoByEmailFromCG(anyString())).thenReturn(Optional.of(userInfo));
    when(userGroupService.addMember(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(userGrp);

    Call<RestResponse<Optional<UserInfo>>> request = mock(Call.class);
    RestResponse<Optional<UserInfo>> mockResponse = new RestResponse<>(Optional.of(userInfo));
    doReturn(Response.success(mockResponse)).when(request).execute();

    ldapGroupSyncHelper.reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(ngUserService, times(1)).getUserInfoByEmailFromCG(testUserEmail);
    verify(userGroupService, times(1)).update(any());
    verify(inviteService, times(1)).create(any(), anyBoolean(), anyBoolean());
    verify(userGroupService, times(1)).addMember(ACCOUNT_ID, ORG_ID, PROJECT_ID, userGrpId, userInfo.getUuid());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testReconcileAllUserGroupsRemoveUser() throws IOException {
    int totalMembers = 1;

    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    final String userGrpId = "UG1";
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.emptyList())
                                     .build();
    UserGroup userGrp = UserGroup.builder()
                            .identifier(userGrpId)
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .ssoGroupId(groupDn)
                            .users(Collections.singletonList(testUserEmail))
                            .notificationConfigs(new ArrayList<>())
                            .build();
    UserInfo userInfo = UserInfo.builder().name(testUserName).email(testUserEmail).uuid("User1").build();

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(userGrp, response);
    when(userGroupService.update(any())).thenReturn(userGrp);
    when(ngUserService.listCurrentGenUsers(anyString(), any())).thenReturn(Collections.singletonList(userInfo));
    when(userGroupService.removeMember(any(), anyString(), anyString())).thenReturn(userGrp);

    Call<RestResponse<Optional<UserInfo>>> request = mock(Call.class);
    RestResponse<Optional<UserInfo>> mockResponse = new RestResponse<>(Optional.of(userInfo));
    doReturn(Response.success(mockResponse)).when(request).execute();

    ldapGroupSyncHelper.reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(ngUserService, times(1))
        .listCurrentGenUsers(ACCOUNT_ID, UserFilterNG.builder().userIds(userGrp.getUsers()).build());
    verify(userGroupService, times(1)).update(any());
    verify(userGroupService, times(1))
        .removeMember(
            Scope.of(userGrp.getAccountIdentifier(), userGrp.getOrgIdentifier(), userGrp.getProjectIdentifier()),
            userGrpId, userInfo.getUuid());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testReconcileAllUserGroupsUpdateUser() throws IOException {
    int totalMembers = 1;

    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    final String userGrpId = "UG1";
    LdapUserResponse usrResponse = LdapUserResponse.builder().email(testUserEmail).name(testUserName).build();
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.singletonList(usrResponse))
                                     .build();
    UserGroup userGrp = UserGroup.builder()
                            .identifier(userGrpId)
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .ssoGroupId(groupDn)
                            .users(Collections.singletonList(testUserEmail))
                            .notificationConfigs(new ArrayList<>())
                            .build();
    UserInfo userInfo = UserInfo.builder().name(testUserName).email(testUserEmail).uuid("User1").build();

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(userGrp, response);
    when(userGroupService.update(any())).thenReturn(userGrp);
    when(ngUserService.listCurrentGenUsers(anyString(), any())).thenReturn(Collections.singletonList(userInfo));

    Call<RestResponse<Optional<UserInfo>>> request = mock(Call.class);
    RestResponse<Optional<UserInfo>> mockResponse = new RestResponse<>(Optional.of(userInfo));
    doReturn(request).when(userClient).updateUser(any());
    doReturn(Response.success(mockResponse)).when(request).execute();

    ldapGroupSyncHelper.reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(ngUserService, times(1))
        .listCurrentGenUsers(ACCOUNT_ID, UserFilterNG.builder().userIds(userGrp.getUsers()).build());
    verify(userGroupService, times(1)).update(any());
    verify(userClient, times(1)).updateUser(any());
  }
}
