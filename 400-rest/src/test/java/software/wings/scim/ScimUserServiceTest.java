/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scim;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.SHASHANK;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.scim.OktaReplaceOperation;
import io.harness.scim.PatchRequest;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimUser;
import io.harness.scim.service.ScimUserService;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.UserInvite;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.UserServiceHelper;
import software.wings.service.intfc.UserService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PL)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class ScimUserServiceTest extends WingsBaseTest {
  private static final String USER_ID = generateUuid();
  private static final String MEMBERS = "members";
  private static final String USERNAME = "userName";
  private static final String ACCOUNT_ID = "accountId";
  private static final Integer MAX_RESULT_COUNT = 20;

  @Inject WingsPersistence realWingsPersistence;
  @Mock WingsPersistence wingsPersistence;
  @Mock UserService userService;
  @Mock private FeatureFlagService featureFlagService;

  @Inject @InjectMocks ScimUserService scimUserService;
  @Inject @InjectMocks UserServiceHelper userServiceHelper;

  UpdateOperations<User> updateOperations;
  Query<User> userQuery;

  ObjectMapper mapper = new ObjectMapper();

  @Before
  public void setup() throws IllegalAccessException {
    updateOperations = realWingsPersistence.createUpdateOperations(User.class);
    userQuery = realWingsPersistence.createQuery(User.class);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateGroupRemoveMembersShouldPass() {
    PatchRequest patchRequest = getOktaActivityReplaceOperation();
    User user = new User();
    user.setUuid(USER_ID);

    UserGroup userGroup = new UserGroup();
    userGroup.setMemberIds(Arrays.asList(USER_ID));
    userGroup.setAccountId(ACCOUNT_ID);
    userGroup.setImportedByScim(true);

    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    when(featureFlagService.isEnabled(FeatureName.PL_USER_DELETION_V2, ACCOUNT_ID)).thenReturn(false);
    when(userService.get(ACCOUNT_ID, USER_ID)).thenReturn(user);
    when(wingsPersistence.save(userGroup)).thenReturn("true");
    scimUserService.updateUser(ACCOUNT_ID, USER_ID, patchRequest);
    verify(userService, times(1)).updateUser(USER_ID, updateOperations);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateGroupRemoveMembersShouldPassV2() {
    PatchRequest patchRequest = getOktaActivityReplaceOperation();
    User user = new User();
    user.setUuid(USER_ID);

    UserGroup userGroup = new UserGroup();
    userGroup.setMemberIds(Arrays.asList(USER_ID));
    userGroup.setAccountId(ACCOUNT_ID);
    userGroup.setImportedByScim(true);

    when(featureFlagService.isEnabled(FeatureName.PL_USER_DELETION_V2, ACCOUNT_ID)).thenReturn(true);
    when(userService.get(ACCOUNT_ID, USER_ID)).thenReturn(user);
    when(wingsPersistence.save(userGroup)).thenReturn("true");
    scimUserService.updateUser(ACCOUNT_ID, USER_ID, patchRequest);
    verify(userService, times(1)).delete(ACCOUNT_ID, USER_ID);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateUser2() {
    PatchRequest patchRequest = getOktaEmailActivityReplaceOperation("admin25@harness.io");
    User user = new User();
    user.setUuid(USER_ID);
    user.setEmail("admin@harness.io");

    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    when(userService.get(ACCOUNT_ID, USER_ID)).thenReturn(user);
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);
    ScimUser userResponse = scimUserService.updateUser(ACCOUNT_ID, USER_ID, patchRequest);
    assertThat(userResponse).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateUser3() {
    PatchRequest patchRequest = getOktaEmailActivityReplaceOperation("admin25@harness.io");
    User user = new User();
    user.setUuid(USER_ID);
    user.setEmail("admin@harness.io");

    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    when(userService.get(ACCOUNT_ID, USER_ID)).thenReturn(user);
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    ScimUser userResponse = scimUserService.updateUser(ACCOUNT_ID, USER_ID, patchRequest);
    assertThat(userResponse).isNotNull();
    assertThat(userResponse.getUserName()).isEqualTo(user.getEmail());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void TC0_testCreateUserWhichIsAlreadyPresent() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");

    User user = new User();
    user.setEmail("username@harness.io");
    user.setName("display_name_old");

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(userService.getUserByEmail(anyString(), anyString())).thenReturn(user);
    when(userService.get(account.getUuid(), user.getUuid())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);

    when(featureFlagService.isEnabled(eq(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW), any())).thenReturn(false);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    verify(userService, times(1)).updateUserAccountLevelDataForThisGen(any(), any(), any(), any());
    verify(userService, times(1)).updateUser(any(), any());

    when(featureFlagService.isEnabled(eq(FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW), any())).thenReturn(true);
    response = scimUserService.createUser(scimUser, account.getUuid());
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    verify(userService, times(2)).updateUserAccountLevelDataForThisGen(any(), any(), any(), any());
    verify(userService, times(2)).updateUser(any(), any());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC0_testCreateUserWhichIsAlreadyPresent_2() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");

    User user = new User();
    user.setEmail("username@harness.io");
    user.setName("display_name");

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(userService.getUserByEmail(anyString(), anyString())).thenReturn(user);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC1_testCreateUserPositiveCase() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");
    String test_external_id = "test_external_id";
    scimUser.setExternalId(test_external_id);

    User user = new User();
    user.setEmail("username@harness.io");
    user.setDisabled(true);
    user.setUuid(generateUuid());
    user.setName("display_name");
    user.setExternalUserId(test_external_id);

    UserInvite userInvite = new UserInvite();
    userInvite.setUuid(generateUuid());

    when(userService.getUserByEmail(anyString(), anyString())).thenReturn(user);
    when(userService.get(account.getUuid(), user.getUuid())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    ScimUser result = (ScimUser) response.getEntity();
    assertThat(result.getExternalId()).isEqualTo(test_external_id);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC2_testCreateUserNegative() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");
    setNameForScimUser(scimUser);

    User user = new User();
    user.setEmail("username@harness.io");
    user.setDisabled(true);
    user.setUuid(generateUuid());
    user.setName("display_name");

    UserInvite userInvite = new UserInvite();
    userInvite.setUuid(generateUuid());

    when(userService.getUserByEmail(anyString(), anyString())).thenReturn(null);
    when(userService.get(account.getUuid(), user.getUuid())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC3_testCreateUserWithNullName() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName(null);

    User user = new User();
    user.setEmail("username@harness.io");
    user.setDisabled(true);
    user.setUuid(generateUuid());
    user.setName(null);

    UserInvite userInvite = new UserInvite();
    userInvite.setUuid(generateUuid());

    when(userService.getUserByEmail(anyString(), anyString())).thenReturn(null);
    when(userService.get(account.getUuid(), user.getUuid())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetUser() {
    User user = new User();
    user.setEmail("user_name@harness.io");
    user.setDisabled(false);
    user.setUuid(USER_ID);
    user.setName("display_name");
    user.setGivenName("givenName");
    user.setFamilyName("familyName");

    when(userService.get(ACCOUNT_ID, USER_ID)).thenReturn(user);

    ScimUser scimUser = scimUserService.getUser(USER_ID, ACCOUNT_ID);

    assertThat(scimUser).isNotNull();
    assertThat(scimUser.getId()).isEqualTo(user.getUuid());
    assertThat(scimUser.getDisplayName()).isEqualTo(user.getName());
    assertThat(scimUser.getActive()).isTrue();
    assertThat(scimUser.getName()).isNotEmpty();
    assertThat(scimUser.getEmails()).isNotEmpty();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetNullUser() {
    when(userService.get(ACCOUNT_ID, USER_ID)).thenReturn(null);
    ScimUser scimUser = scimUserService.getUser(USER_ID, ACCOUNT_ID);

    assertThat(scimUser).isNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testDeleteUser() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("user_name@harness.io");
    scimUser.setDisplayName("display_name");
    setNameForScimUser(scimUser);

    User user = new User();
    user.setEmail("user_name@harness.io");
    user.setDisabled(false);
    user.setUuid(generateUuid());
    user.setName("display_name");
    user.setAccounts(Arrays.asList(account));

    UserInvite userInvite = new UserInvite();
    userInvite.setUuid(generateUuid());

    scimUserService.deleteUser(user.getUuid(), account.getUuid());
    verify(userService, times(1)).delete(account.getUuid(), user.getUuid());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateUser() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("user_name@harness.io");
    scimUser.setDisplayName("display_name");
    scimUser.setActive(true);
    setNameForScimUser(scimUser);

    User user = new User();
    user.setEmail("user_name@harness.io");
    user.setDisabled(true);
    user.setFamilyName("family_name_diff");
    user.setGivenName("given_name_diff");
    user.setUuid(generateUuid());
    user.setName("display_name_diff");
    user.setAccounts(Arrays.asList(account));

    UserInvite userInvite = new UserInvite();
    userInvite.setUuid(generateUuid());

    when(userService.get(account.getUuid(), user.getUuid())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    Response response = scimUserService.updateUser(user.getUuid(), account.getUuid(), scimUser);
    verify(userService, times(1)).updateUser(user.getUuid(), updateOperations);
    assertThat(response.getStatus()).isNotNull();
  }

  private void setEmailsForUser(ScimUser scimUser) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("emails", "user_name_diff@harness.io");

    JsonNode jsonNode;

    try {
      jsonNode = mapper.readTree(jsonObject.toString());
      scimUser.setEmails(jsonNode);
    } catch (IOException ioe) {
      log.error("IO Exception while creating okta replace operation in SCIM", ioe);
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateUserDiff() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("user_name@harness.io");
    scimUser.setDisplayName("display_name");
    scimUser.setActive(true);
    setEmailsForUser(scimUser);
    setNameForScimUser(scimUser);

    User user = new User();
    user.setEmail("user_name_diff@harness.io");
    user.setDisabled(true);
    user.setFamilyName("family_name_diff");
    user.setGivenName("given_name_diff");
    user.setUuid(generateUuid());
    user.setName("display_name_diff");
    user.setAccounts(Arrays.asList(account));

    UserInvite userInvite = new UserInvite();
    userInvite.setUuid(generateUuid());

    when(userService.get(account.getUuid(), user.getUuid())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    Response response = scimUserService.updateUser(user.getUuid(), account.getUuid(), scimUser);
    verify(userService, times(1)).updateUser(user.getUuid(), updateOperations);
    assertThat(response.getStatus()).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateUserDiff2() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("user_name@harness.io");
    scimUser.setDisplayName("display_name");
    scimUser.setActive(true);
    setEmailsForUser(scimUser);
    setNameForScimUser(scimUser);

    User user = new User();
    user.setEmail("user_name_diff@harness.io");
    user.setDisabled(true);
    user.setFamilyName("family_name_diff");
    user.setGivenName("given_name_diff");
    user.setUuid(generateUuid());
    user.setName("display_name_diff");
    user.setAccounts(Arrays.asList(account));

    UserInvite userInvite = new UserInvite();
    userInvite.setUuid(generateUuid());

    when(userService.get(account.getUuid(), user.getUuid())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    Response response = scimUserService.updateUser(user.getUuid(), account.getUuid(), scimUser);
    verify(userService, times(1)).updateUser(user.getUuid(), updateOperations);
    assertThat(response.getStatus()).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateNullUser() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("ACCOUNT_NAME");

    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("user_name@harness.io");
    scimUser.setDisplayName("DISPLAY_NAME");
    scimUser.setActive(true);
    setNameForScimUser(scimUser);

    User user = new User();
    user.setEmail("user_name@harness.io");
    user.setDisabled(false);
    user.setUuid(generateUuid());
    user.setName("display_name");
    user.setFamilyName("family_name");
    user.setGivenName("given_name");
    user.setAccounts(Arrays.asList(account));

    UserInvite userInvite = new UserInvite();
    userInvite.setUuid(generateUuid());

    when(userService.get(account.getUuid(), user.getUuid())).thenReturn(null);
    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    Response response = scimUserService.updateUser(user.getUuid(), account.getUuid(), scimUser);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testSearchUser_WithNoUser() {
    String filter = "value eq 'user_name@harness.io'";
    int count = 1;
    int startIndex = 1;

    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("ACCOUNT_NAME");
    account.setCompanyName("COMPANY_NAME");

    when(wingsPersistence.createQuery(User.class)).thenReturn(userQuery);

    ScimListResponse<ScimUser> response = scimUserService.searchUser(account.getUuid(), filter, count, startIndex);

    assertThat(response).isNotNull();
    assertThat(response.getResources()).isNotNull();
    assertThat(response.getItemsPerPage()).isEqualTo(count);
    assertThat(response.getStartIndex()).isEqualTo(startIndex);
    assertThat(response.getTotalResults()).isEqualTo(0);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testSearchUser_WithUser() {
    String filter = "value eq 'user_name@harness.io'";
    int count = 1;
    int startIndex = 1;

    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("ACCOUNT_NAME");
    account.setCompanyName("COMPANY_NAME");

    User user = User.Builder.anUser()
                    .uuid(generateUuid())
                    .name("scim_user")
                    .familyName("family_name")
                    .givenName("given_name")
                    .accounts(Arrays.asList(account))
                    .build();

    realWingsPersistence.save(user);
    when(wingsPersistence.createQuery(User.class)).thenReturn(userQuery);
    ScimListResponse<ScimUser> response = scimUserService.searchUser(account.getUuid(), filter, count, startIndex);

    assertThat(response).isNotNull();
    assertThat(response.getResources()).isNotNull();
    assertThat(response.getItemsPerPage()).isEqualTo(count);
    assertThat(response.getStartIndex()).isEqualTo(startIndex);

    realWingsPersistence.delete(user);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSearchUser_WithUser_WithStartIndexAndCountAsNULL() {
    String filter = "value eq 'user_name@harness.io'";
    Integer count = null;
    Integer startIndex = null;

    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("ACCOUNT_NAME");
    account.setCompanyName("COMPANY_NAME");

    User user = User.Builder.anUser()
                    .uuid(generateUuid())
                    .name("scim_user")
                    .familyName("family_name")
                    .givenName("given_name")
                    .accounts(Arrays.asList(account))
                    .build();

    realWingsPersistence.save(user);
    when(wingsPersistence.createQuery(User.class)).thenReturn(userQuery);
    ScimListResponse<ScimUser> response = scimUserService.searchUser(account.getUuid(), filter, count, startIndex);

    assertThat(response).isNotNull();
    assertThat(response.getResources()).isNotNull();
    assertThat(response.getItemsPerPage()).isEqualTo(MAX_RESULT_COUNT);
    assertThat(response.getStartIndex()).isEqualTo(0);

    realWingsPersistence.delete(user);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testUpdateEmailShouldConvertToLowerCase() {
    String userId = randomAlphabetic(10);
    String accountId = randomAlphabetic(10);
    String updatedEmail = "ADMIN_MODIFIED@harness.io";

    ScimUser scimUser = new ScimUser();
    scimUser.setUserName(updatedEmail);

    Map<String, Object> emailMap = new HashMap<>() {
      {
        put("value", updatedEmail);
        put("primary", true);
      }
    };
    scimUser.setEmails(JsonUtils.asTree(Collections.singletonList(emailMap)));

    User user = new User();
    user.setUuid(userId);
    user.setEmail("admin@harness.io");

    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    when(userService.get(accountId, userId)).thenReturn(user);

    scimUserService.updateUser(userId, accountId, scimUser);

    updateOperations.set(UserKeys.email, updatedEmail.toLowerCase());
    verify(userService, times(1)).updateUser(userId, updateOperations);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateUserName_changeOnIdPUserPrincipalName() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("user_name_changed@harness.io");
    scimUser.setDisplayName("display_name");
    scimUser.setActive(true);
    setEmailsForUser(scimUser);
    setNameForScimUser(scimUser);

    User user = new User();
    user.setEmail("user_name_original@harness.io");
    user.setDisabled(false);
    user.setUuid(generateUuid());
    user.setName("display_name");
    user.setAccounts(Arrays.asList(account));

    UserInvite userInvite = new UserInvite();
    userInvite.setUuid(generateUuid());

    when(userService.get(account.getUuid(), user.getUuid())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    Response response = scimUserService.updateUser(user.getUuid(), account.getUuid(), scimUser);
    verify(userService, times(1)).updateUser(user.getUuid(), updateOperations);
    assertThat(response.getStatus()).isNotNull();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateUserName_changeOnIdPDisplayNameAndUserPrincipalNameBoth() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("user_name_changed@harness.io");
    scimUser.setDisplayName("display_name");
    scimUser.setActive(true);
    setEmailsForUser(scimUser);
    setNameForScimUser(scimUser);

    User user = new User();
    user.setEmail("user_name_original@harness.io");
    user.setDisabled(false);
    user.setUuid(generateUuid());
    user.setName("display_original_name");
    user.setAccounts(Arrays.asList(account));

    UserInvite userInvite = new UserInvite();
    userInvite.setUuid(generateUuid());

    when(userService.get(account.getUuid(), user.getUuid())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    Response response = scimUserService.updateUser(user.getUuid(), account.getUuid(), scimUser);
    verify(userService, times(1)).updateUser(user.getUuid(), updateOperations);
    assertThat(response.getStatus()).isNotNull();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateUserName_noChangeIdPEmailChange() {
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("user_name@harness.io");
    scimUser.setDisplayName("display_name");
    setEmailsForUser(scimUser);
    setNameForScimUser(scimUser);

    User user = new User();
    user.setEmail("user_name@harness.io");
    user.setFamilyName("family_name");
    user.setGivenName("given_name");
    user.setUuid(generateUuid());
    user.setName("display_name");
    user.setAccounts(Arrays.asList(account));

    UserInvite userInvite = new UserInvite();
    userInvite.setUuid(generateUuid());

    when(userService.get(account.getUuid(), user.getUuid())).thenReturn(user);
    when(wingsPersistence.createUpdateOperations(User.class)).thenReturn(updateOperations);
    Response response = scimUserService.updateUser(user.getUuid(), account.getUuid(), scimUser);
    verify(userService, times(0)).updateUser(user.getUuid(), updateOperations);
    assertThat(response.getStatus()).isNotNull();
  }

  private PatchRequest getOktaActivityReplaceOperation() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("active", false);
    JsonNode jsonNode;

    try {
      jsonNode = mapper.readTree(jsonObject.toString());
      OktaReplaceOperation replaceOperation = new OktaReplaceOperation(MEMBERS, jsonNode);
      return new PatchRequest(Collections.singletonList(replaceOperation));
    } catch (IOException ioe) {
      log.error("IO Exception while creating okta replace operation in SCIM", ioe);
    }
    return null;
  }

  private PatchRequest getOktaEmailActivityReplaceOperation(String email) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("userName", email);
    JsonNode jsonNode;

    try {
      jsonNode = mapper.readTree(jsonObject.toString());
      OktaReplaceOperation replaceOperation = new OktaReplaceOperation(USERNAME, jsonNode);
      return new PatchRequest(Collections.singletonList(replaceOperation));
    } catch (IOException ioe) {
      log.error("IO Exception while creating okta replace operation in SCIM", ioe);
    }
    return null;
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
}