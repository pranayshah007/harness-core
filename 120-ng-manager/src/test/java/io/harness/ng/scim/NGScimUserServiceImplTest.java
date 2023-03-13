/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim;

import static io.harness.NGConstants.CREATED;
import static io.harness.NGConstants.DISPLAY_NAME;
import static io.harness.NGConstants.FAMILY_NAME;
import static io.harness.NGConstants.FORMATTED_NAME;
import static io.harness.NGConstants.GIVEN_NAME;
import static io.harness.NGConstants.LAST_MODIFIED;
import static io.harness.NGConstants.LOCATION;
import static io.harness.NGConstants.RESOURCE_TYPE;
import static io.harness.NGConstants.VERSION;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.PL_JPMC_SCIM_REQUIREMENTS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimUser;
import io.harness.serializer.JsonUtils;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import software.wings.beans.Account;
import software.wings.beans.UserInvite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;

@OwnedBy(PL)
@Slf4j
public class NGScimUserServiceImplTest extends NgManagerTestBase {
  private NgUserService ngUserService;

  private UserGroupService userGroupService;
  private InviteService inviteService;
  private NGScimUserServiceImpl scimUserService;
  @Mock private AccountClient accountClient;

  private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  ObjectMapper mapper = new ObjectMapper();
  private static final String USERNAME = "userName";

  @Before
  public void setup() throws IOException {
    inviteService = mock(InviteService.class);
    ngUserService = mock(NgUserService.class);
    userGroupService = mock(UserGroupService.class);
    ngFeatureFlagHelperService = mock(NGFeatureFlagHelperService.class);

    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(retrofit2.Response.success(new RestResponse<>(true)));

    scimUserService = new NGScimUserServiceImpl(
        ngUserService, inviteService, userGroupService, accountClient, ngFeatureFlagHelperService);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testScim_IfUserIsPartOfAccountAlready_ItShouldUpdate() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");

    UserInfo userInfo = UserInfo.builder().admin(true).email("username@harness.io").name("display_name").build();

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(ngUserService.getUserInfoByEmailFromCG(any())).thenReturn(Optional.ofNullable(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(any())).thenReturn(Optional.ofNullable(userInfo));
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    assertThat(((ScimUser) response.getEntity()).getUserName()).isEqualTo(userInfo.getEmail());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testScim_doesNotReturnsFamilyNameAndGivenName_whenFFTurnedOff() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");
    setNameForScimUser(scimUser);

    UserInfo userInfo = UserInfo.builder()
                            .admin(true)
                            .email("username@harness.io")
                            .name("display_name")
                            .givenName("given_name")
                            .familyName("family_name")
                            .build();

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(ngUserService.getUserInfoByEmailFromCG(any())).thenReturn(Optional.empty());
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(any())).thenReturn(Optional.ofNullable(userInfo));
    when(ngFeatureFlagHelperService.isEnabled(account.getUuid(), PL_JPMC_SCIM_REQUIREMENTS)).thenReturn(false);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    ScimUser result = (ScimUser) response.getEntity();
    assertNotNull(result);
    JsonNode jsonNode = result.getName();
    assertNotNull(jsonNode);
    assertNotNull(jsonNode.get(DISPLAY_NAME));
    assertNull(jsonNode.get(GIVEN_NAME));
    assertNull(jsonNode.get(FAMILY_NAME));
    assertNull(jsonNode.get(FORMATTED_NAME));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testScim_doesReturnsFamilyNameAndGivenName_whenFFTurnedOn() throws IOException {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    String testMail = "username@harness.io";
    scimUser.setUserName(testMail);
    scimUser.setDisplayName("display_name");
    setNameForScimUser(scimUser);
    setEmailsForScimUser(scimUser, testMail);

    UserInfo userInfo = UserInfo.builder()
                            .admin(true)
                            .email("username@harness.io")
                            .name("display_name")
                            .givenName("given_name")
                            .familyName("family_name")
                            .build();

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(ngUserService.getUserInfoByEmailFromCG(any())).thenReturn(Optional.empty());
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(any())).thenReturn(Optional.ofNullable(userInfo));
    when(ngFeatureFlagHelperService.isEnabled(account.getUuid(), PL_JPMC_SCIM_REQUIREMENTS)).thenReturn(true);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    ScimUser result = (ScimUser) response.getEntity();
    assertNotNull(result);
    JsonNode jsonNode = result.getName();
    assertNotNull(jsonNode);
    assertNotNull(jsonNode.get(DISPLAY_NAME));
    assertNotNull(jsonNode.get(GIVEN_NAME));
    assertNotNull(jsonNode.get(FAMILY_NAME));
    assertNotNull(jsonNode.get(FORMATTED_NAME));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testScim_returnsExternalIdAlsoAsInRequest() throws IOException {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    String userName = "test_user_name_01@test.co";
    scimUser.setUserName(userName);
    scimUser.setDisplayName("display_name");
    String testMail = "valid_email@test.corp";
    setEmailsForScimUser(scimUser, testMail);

    UserInfo userInfo = UserInfo.builder().admin(true).email(userName).name("display_name").build();

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail(userName);

    when(ngUserService.getUserInfoByEmailFromCG(any())).thenReturn(Optional.empty());
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(any())).thenReturn(Optional.of(userInfo));
    when(ngFeatureFlagHelperService.isEnabled(account.getUuid(), PL_JPMC_SCIM_REQUIREMENTS)).thenReturn(false);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    ScimUser result = (ScimUser) response.getEntity();
    assertNotNull(result);
    JsonNode jsonNode = result.getName();
    assertNotNull(jsonNode);
    assertNotNull(jsonNode.get(DISPLAY_NAME));
    assertNull(jsonNode.get(FORMATTED_NAME));
    jsonNode = result.getEmails();
    assertNotNull(jsonNode);
    assertNotNull(jsonNode.get(0));
    assertNull(result.getExternalId());
    assertThat(result.getUserName()).isEqualTo(userName);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testScim_doesNotReturnMeta_whenFFTurnedOff() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");
    setNameForScimUser(scimUser);

    UserInfo userInfo = UserInfo.builder()
                            .admin(true)
                            .email("username@harness.io")
                            .name("display_name")
                            .givenName("given_name")
                            .familyName("family_name")
                            .createdAt(Long.parseLong(randomNumeric(10)))
                            .lastUpdatedAt(Long.parseLong(randomNumeric(10)))
                            .build();

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(ngUserService.getUserInfoByEmailFromCG(any())).thenReturn(Optional.empty());
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(any())).thenReturn(Optional.ofNullable(userInfo));
    when(ngFeatureFlagHelperService.isEnabled(account.getUuid(), PL_JPMC_SCIM_REQUIREMENTS)).thenReturn(false);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    ScimUser result = (ScimUser) response.getEntity();
    assertNotNull(result);
    JsonNode jsonNode = result.getMeta();
    assertNull(jsonNode);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testScim_doesReturnMeta_whenFFTurnedOn() throws IOException {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    String testMail = "username@harness.io";
    scimUser.setUserName(testMail);
    scimUser.setDisplayName("display_name");
    setNameForScimUser(scimUser);
    setEmailsForScimUser(scimUser, testMail);

    UserInfo userInfo = UserInfo.builder()
                            .admin(true)
                            .email("username@harness.io")
                            .name("display_name")
                            .givenName("given_name")
                            .familyName("family_name")
                            .createdAt(Long.parseLong(randomNumeric(10)))
                            .lastUpdatedAt(Long.parseLong(randomNumeric(10)))
                            .build();

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(ngUserService.getUserInfoByEmailFromCG(any())).thenReturn(Optional.empty());
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(any())).thenReturn(Optional.ofNullable(userInfo));
    when(ngFeatureFlagHelperService.isEnabled(account.getUuid(), PL_JPMC_SCIM_REQUIREMENTS)).thenReturn(true);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    ScimUser result = (ScimUser) response.getEntity();
    assertNotNull(result);
    JsonNode meta = result.getMeta();
    assertNotNull(meta);
    assertNotNull(meta.get(RESOURCE_TYPE));
    assertNotNull(meta.get(CREATED));
    assertNotNull(meta.get(LAST_MODIFIED));
    assertNotNull(meta.get(VERSION));
    assertNotNull(meta.get(LOCATION));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testScimGetUser_doesNotReturnGroups_whenFFTurnedOff() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");
    setNameForScimUser(scimUser);

    UserInfo userInfo = UserInfo.builder()
                            .admin(true)
                            .email("username@harness.io")
                            .name("display_name")
                            .givenName("given_name")
                            .familyName("family_name")
                            .createdAt(Long.parseLong(randomNumeric(10)))
                            .lastUpdatedAt(Long.parseLong(randomNumeric(10)))
                            .build();

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(ngUserService.getUserInfoByEmailFromCG(any())).thenReturn(Optional.empty());
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(any())).thenReturn(Optional.ofNullable(userInfo));
    when(ngFeatureFlagHelperService.isEnabled(account.getUuid(), PL_JPMC_SCIM_REQUIREMENTS)).thenReturn(false);
    when(userGroupService.getUserGroupsForUser(any(), any())).thenReturn(null);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    ScimUser result = (ScimUser) response.getEntity();
    assertNotNull(result);
    JsonNode jsonNode = result.getGroups();
    assertNull(jsonNode);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testScimGetUser_doesReturnGroups_whenFFTurnedOn() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");
    setNameForScimUser(scimUser);

    UserInfo userInfo = UserInfo.builder()
                            .admin(true)
                            .email("username@harness.io")
                            .name("display_name")
                            .givenName("given_name")
                            .familyName("family_name")
                            .createdAt(Long.parseLong(randomNumeric(10)))
                            .lastUpdatedAt(Long.parseLong(randomNumeric(10)))
                            .build();

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");
    List<UserGroup> userGroups = new ArrayList<>();
    userGroups.add(
        UserGroup.builder().name("name1").accountIdentifier(account.getUuid()).identifier("randomId1").build());
    userGroups.add(
        UserGroup.builder().name("name2").accountIdentifier(account.getUuid()).identifier("randomId2").build());

    when(ngUserService.getUserInfoByEmailFromCG(any())).thenReturn(Optional.empty());
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(any())).thenReturn(Optional.ofNullable(userInfo));
    when(ngFeatureFlagHelperService.isEnabled(account.getUuid(), PL_JPMC_SCIM_REQUIREMENTS)).thenReturn(true);
    when(userGroupService.getUserGroupsForUser(any(), any())).thenReturn(userGroups);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    ScimUser result = (ScimUser) response.getEntity();
    assertNotNull(result);
    JsonNode jsonNode = result.getGroups();
    assertNotNull(jsonNode);
    assertThat(jsonNode.size()).isEqualTo(2);
    JsonNode group1 = jsonNode.get(0);
    assertThat(group1.size()).isEqualTo(3);
    assertNotNull(group1.get("value"));
    assertNotNull(group1.get("ref"));
    assertNotNull(group1.get("display"));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testScim_IfUserIsNotPartOfAccountAlready_ItShouldAddToAccount_AlreadyPresent() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");

    UserInfo userInfo = UserInfo.builder().admin(true).email("username@harness.io").name("display_name").build();

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(ngUserService.getUserInfoByEmailFromCG(anyString())).thenReturn(Optional.ofNullable(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(null));
    when(ngUserService.getUserById(anyString())).thenReturn(Optional.ofNullable(userInfo));
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getEntity()).isNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testScim_IfUserIsNotPartOfAccountAlready_ItShouldAddToAccount() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    final String accountId = generateUuid();
    account.setUuid(accountId);
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");

    List<GatewayAccountRequestDTO> accounts = new ArrayList<>();
    accounts.add(GatewayAccountRequestDTO.builder().uuid(account.getUuid()).build());
    UserInfo userInfo =
        UserInfo.builder().admin(true).email("username@harness.io").accounts(accounts).name("display_name").build();

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    when(ngUserService.getUserInfoByEmailFromCG(any())).thenReturn(Optional.ofNullable(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(any())).thenReturn(Optional.ofNullable(userInfo));
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    assertThat(((ScimUser) response.getEntity()).getUserName()).isEqualTo(userInfo.getEmail());
    verify(ngUserService, times(1))
        .addUserToScope(
            userMetadataDTO.getUuid(), Scope.of(accountId, null, null), null, null, UserMembershipUpdateSource.SYSTEM);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testNGScimSearchForCGUser() {
    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("randomEmail.com");
    scimUser.setDisplayName("randomDisplayname");
    ScimListResponse<ScimUser> scimUserScimListResponse = new ScimListResponse<>();
    List<ScimUser> resources = new ArrayList<>();
    resources.add(scimUser);
    scimUserScimListResponse.setResources(resources);
    scimUserScimListResponse.setTotalResults(1);
    when(ngUserService.searchScimUsersByEmailQuery(anyString(), anyString(), any(), any()))
        .thenReturn(scimUserScimListResponse);
    when(ngUserService.getUserByEmail(anyString(), anyBoolean())).thenReturn(Optional.empty());
    ScimListResponse<ScimUser> result = scimUserService.searchUser("accountId", "filter", 1, 0);
    assertThat(result.getTotalResults()).isEqualTo(0);
    assertThat(result.getResources().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testNGScimSearchForNGUser() {
    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("randomEmail.com");
    scimUser.setDisplayName("randomDisplayname");
    ScimListResponse<ScimUser> scimUserScimListResponse = new ScimListResponse<>();
    List<ScimUser> resources = new ArrayList<>();
    resources.add(scimUser);
    scimUserScimListResponse.setResources(resources);
    scimUserScimListResponse.setTotalResults(1);
    UserMetadataDTO userMetadataDTO = new UserMetadataDTO();
    userMetadataDTO.setEmail("randomEmail.com");
    userMetadataDTO.setUuid("random");
    when(ngUserService.searchScimUsersByEmailQuery(anyString(), anyString(), any(), any()))
        .thenReturn(scimUserScimListResponse);
    when(ngUserService.getUserByEmail(anyString(), anyBoolean())).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(true);
    ScimListResponse<ScimUser> result = scimUserService.searchUser("accountId", "filter", 1, 0);
    assertThat(result.getTotalResults()).isEqualTo(1);
    assertThat(result.getResources().size()).isEqualTo(1);
    assertThat(result.getResources().get(0).getDisplayName()).isEqualTo("randomDisplayname");
    assertThat(result.getResources().get(0).getUserName()).isEqualTo("randomEmail.com");
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testNGScimSearchForNGUserButInDifferentAccount() {
    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("randomEmail.com");
    scimUser.setDisplayName("randomDisplayname");
    ScimListResponse<ScimUser> scimUserScimListResponse = new ScimListResponse<>();
    List<ScimUser> resources = new ArrayList<>();
    resources.add(scimUser);
    scimUserScimListResponse.setResources(resources);
    scimUserScimListResponse.setTotalResults(1);
    UserMetadataDTO userMetadataDTO = new UserMetadataDTO();
    userMetadataDTO.setEmail("randomEmail.com");
    userMetadataDTO.setUuid("random");
    when(ngUserService.searchScimUsersByEmailQuery(anyString(), anyString(), any(), any()))
        .thenReturn(scimUserScimListResponse);
    when(ngUserService.getUserByEmail(anyString(), anyBoolean())).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(false);
    ScimListResponse<ScimUser> result = scimUserService.searchUser("accountId", "filter", 1, 0);
    assertThat(result.getTotalResults()).isEqualTo(0);
    assertThat(result.getResources().size()).isEqualTo(0);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testGetUserInCGNotInNG() {
    UserInfo userInfo =
        UserInfo.builder().admin(true).email("username@harness.io").name("display_name").uuid("someRandom").build();
    when(ngUserService.getUserById(userInfo.getUuid())).thenReturn(Optional.of(userInfo));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(true);
    scimUserService.getUser(userInfo.getUuid(), "someRandom");
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testGetUserInBothCGAndNG() {
    UserInfo userInfo =
        UserInfo.builder().admin(true).email("username@harness.io").name("display_name").uuid("someRandom").build();
    UserMetadataDTO userMetadataDTO = new UserMetadataDTO();
    userMetadataDTO.setEmail("username@harness.io");
    userMetadataDTO.setUuid("someRandom");
    when(ngUserService.getUserById(userInfo.getUuid())).thenReturn(Optional.of(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), false)).thenReturn(Optional.of(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(true);
    ScimUser scimUser = scimUserService.getUser(userInfo.getUuid(), "someRandom");
    assertThat(scimUser).isNotNull();
    assertThat(scimUser.getUserName().equals("someRandom"));
    assertThat(scimUser.getDisplayName().equals("display_name"));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testGetUserInBothCGAndNGButDifferentAccountInNg() {
    UserInfo userInfo =
        UserInfo.builder().admin(true).email("username@harness.io").name("display_name").uuid("someRandom").build();
    UserMetadataDTO userMetadataDTO = new UserMetadataDTO();
    userMetadataDTO.setEmail("username@harness.io");
    userMetadataDTO.setUuid("someRandom");
    when(ngUserService.getUserById(userInfo.getUuid())).thenReturn(Optional.of(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), false)).thenReturn(Optional.of(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(false);
    ScimUser scimUser = scimUserService.getUser(userInfo.getUuid(), "someRandom");
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetUserInBothCGAndNGButDifferentAccountInNg2() {
    UserInfo userInfo =
        UserInfo.builder().admin(true).email("username@harness.io").name("display_name").uuid("userId").build();
    UserMetadataDTO userMetadataDTO = new UserMetadataDTO();
    userMetadataDTO.setEmail("username@harness.io");
    userMetadataDTO.setUuid("userId");
    when(ngUserService.getUserById(userInfo.getUuid())).thenReturn(Optional.of(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), false)).thenReturn(Optional.of(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(true);
    ScimUser scimUser = scimUserService.getUser(userInfo.getUuid(), "accountId");
    assertThat(scimUser).isNotNull();
    assertThat(scimUser.getName()).isNotNull();
    assertThat(scimUser.getDisplayName()).isNotNull();
    assertThat(scimUser.getDisplayName()).isEqualTo(userInfo.getName());
    assertThat(scimUser.getUserName()).isNotNull();
    assertThat(scimUser.getUserName()).isEqualTo(userInfo.getEmail());
    assertThat(scimUser.getId()).isNotNull();
    assertThat(scimUser.getId()).isEqualTo(userInfo.getUuid());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testUpdateUserNameAndEmail() {
    String name = randomAlphabetic(10);
    String updatedName = randomAlphabetic(10);
    String email = "username@harness.io";
    String updatedEmail = "username123@harness.io";
    String VALUE = "value";
    String PRIMARY = "primary";
    String userId = randomAlphabetic(10);
    String accountId = randomAlphabetic(10);

    UserInfo userInfo = UserInfo.builder().admin(true).email(email).name(name).uuid(userId).build();

    UserMetadataDTO userMetadataDTO = UserMetadataDTO.builder().name(name).email(email).build();
    Map<String, Object> emailMap = new HashMap<>() {
      {
        put(VALUE, updatedEmail);
        put(PRIMARY, true);
      }
    };
    ScimUser scimUser = new ScimUser();
    scimUser.setUserName(updatedName);
    scimUser.setEmails(JsonUtils.asTree(Collections.singletonList(emailMap)));

    when(ngUserService.getUserById(userId)).thenReturn(Optional.of(userInfo));
    when(ngUserService.getUserMetadata(userId)).thenReturn(Optional.of(userMetadataDTO));
    when(ngUserService.updateScimUser(accountId, userId, scimUser)).thenReturn(true);

    scimUserService.updateUser(userId, accountId, scimUser);

    userMetadataDTO.setName(updatedName);
    userMetadataDTO.setExternallyManaged(true);
    userMetadataDTO.setEmail(updatedEmail);
    verify(ngUserService, times(1)).updateUserMetadata(userMetadataDTO);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testEmailUpdateShouldConvertToLowerCase() {
    String email = "username@harness.io";
    String updatedEmail = "USERNAME123@harness.io";
    String userId = randomAlphabetic(10);
    String accountId = randomAlphabetic(10);

    UserInfo userInfo = UserInfo.builder().admin(true).email(email).uuid(userId).build();

    UserMetadataDTO userMetadataDTO = UserMetadataDTO.builder().email(email).build();
    ScimUser scimUser = new ScimUser();
    scimUser.setUserName(updatedEmail);

    when(ngUserService.getUserById(userId)).thenReturn(Optional.of(userInfo));
    when(ngUserService.getUserMetadata(userId)).thenReturn(Optional.of(userMetadataDTO));
    when(ngUserService.updateScimUser(accountId, userId, scimUser)).thenReturn(true);

    scimUserService.updateUser(userId, accountId, scimUser);

    userMetadataDTO.setExternallyManaged(true);
    userMetadataDTO.setEmail(updatedEmail.toLowerCase());
    verify(ngUserService, times(1)).updateUserMetadata(userMetadataDTO);
  }

  private void setNameForScimUser(ScimUser scimUser) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("givenName", "given_name");
    jsonObject.addProperty("familyName", "family_name");

    JsonNode jsonNode;

    try {
      jsonNode = mapper.readTree(jsonObject.toString());
      scimUser.setName(jsonNode);
    } catch (IOException ioe) {
      log.error("IO Exception while creating okta replace operation in SCIM", ioe);
    }
  }

  private void setEmailsForScimUser(ScimUser scimUser, String testMail) throws IOException {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("value", testMail);
    jsonObject.addProperty("primary", "true");

    JsonNode jsonNode = mapper.readTree(jsonObject.toString());

    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.addAll(List.of(jsonNode));
    scimUser.setEmails(arrayNode);
  }
}
