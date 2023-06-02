/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package software.wings.graphql.datafetcher.userGroup;

import static io.harness.rule.OwnerRule.RAFAEL;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_KEY;
import static software.wings.utils.WingsTestConstants.USER_ID;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.beans.UserInvite.UserInviteBuilder;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.AccountThreadLocal;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.usergroup.QLUserGroupFilter;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UserGroupQueryHelperTest extends WingsBaseTest {
  @Inject private UserGroupQueryHelper userGroupQueryHelper;

  @Inject WingsPersistence wingsPersistence;

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void setQueryWithUserCreatedInvite() {
    QLIdFilter qlIdFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {USER_ID}).build();
    List<QLUserGroupFilter> filters = List.of(QLUserGroupFilter.builder().user(qlIdFilter).build());
    Query query;
    AccountThreadLocal.set(ACCOUNT_ID);
    query = wingsPersistence.createAuthorizedQuery(UserGroup.class);
    query.filter(SettingAttributeKeys.accountId, ACCOUNT_ID);
    userGroupQueryHelper.setQuery(filters, query);
    assertThat(query.toString())
        .isEqualToIgnoringCase(
            "{ {\"accountId\": \"ACCOUNT_ID\", \"$or\": [{\"memberIds\": \"USER_ID\"}, {\"_id\": {\"$in\": []}}]}  }");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void setQueryWithUserInvitedWithoutPendingAccounts() {
    UserGroup userGroup = UserGroup.builder()
                              .uuid(UUIDGenerator.generateUuid())
                              .memberIds(List.of(USER_ID))
                              .accountId(ACCOUNT_ID)
                              .uuid("usergroup-1")
                              .build();
    UserGroup userGroup2 =
        UserGroup.builder().uuid(UUIDGenerator.generateUuid()).accountId(ACCOUNT_ID).uuid("usergroup-2").build();
    Account account = Account.Builder.anAccount()
                          .withAccountName("account-name")
                          .withCompanyName("company-name")
                          .withUuid(ACCOUNT_ID)
                          .withAccountKey(ACCOUNT_KEY)
                          .build();
    User user = User.Builder.anUser()
                    .uuid(USER_ID)
                    .email("email@harness.io")
                    .accounts(List.of(account))
                    .userGroups(List.of(userGroup))
                    .build();
    UserInvite userInvite = UserInviteBuilder.anUserInvite()
                                .withUserGroups(List.of(userGroup2))
                                .withUserId(user.getUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withCompleted(false)
                                .withEmail("email@harness.io")
                                .withCompanyName("company-name")
                                .build();
    QLIdFilter qlIdFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {USER_ID}).build();
    List<QLUserGroupFilter> filters = List.of(QLUserGroupFilter.builder().user(qlIdFilter).build());
    wingsPersistence.save(account);
    wingsPersistence.save(userGroup);
    wingsPersistence.save(userGroup2);
    wingsPersistence.save(userInvite);
    wingsPersistence.save(user);

    Query query;
    AccountThreadLocal.set(ACCOUNT_ID);
    query = wingsPersistence.createAuthorizedQuery(UserGroup.class);
    query.filter(SettingAttributeKeys.accountId, ACCOUNT_ID);
    userGroupQueryHelper.setQuery(filters, query);
    assertThat(query.toString())
        .isEqualToIgnoringCase(
            "{ {\"accountId\": \"ACCOUNT_ID\", \"$or\": [{\"memberIds\": \"USER_ID\"}, {\"_id\": {\"$in\": []}}]}  }");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void setQueryWithUserWithPendingAccounts() {
    UserGroup userGroup =
        UserGroup.builder().uuid(UUIDGenerator.generateUuid()).accountId(ACCOUNT_ID).uuid("usergroup-1").build();
    UserGroup userGroup2 =
        UserGroup.builder().uuid(UUIDGenerator.generateUuid()).accountId(ACCOUNT_ID).uuid("usergroup-2").build();
    Account account = Account.Builder.anAccount()
                          .withAccountName("account-name")
                          .withCompanyName("company-name")
                          .withUuid(ACCOUNT_ID)
                          .withAccountKey(ACCOUNT_KEY)
                          .build();
    User user = User.Builder.anUser()
                    .uuid(USER_ID)
                    .email("email@harness.io")
                    .pendingAccounts(List.of(account))
                    .userGroups(List.of(userGroup, userGroup2))
                    .build();
    UserInvite userInvite = UserInviteBuilder.anUserInvite()
                                .withUserGroups(List.of(userGroup, userGroup2))
                                .withUserId(user.getUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withCompleted(false)
                                .withEmail("email@harness.io")
                                .withCompanyName("company-name")
                                .build();
    QLIdFilter qlIdFilter = QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {USER_ID}).build();
    List<QLUserGroupFilter> filters = List.of(QLUserGroupFilter.builder().user(qlIdFilter).build());
    wingsPersistence.save(account);
    wingsPersistence.save(userGroup);
    wingsPersistence.save(userGroup2);
    wingsPersistence.save(userInvite);
    wingsPersistence.save(user);

    Query query;
    AccountThreadLocal.set(ACCOUNT_ID);
    query = wingsPersistence.createAuthorizedQuery(UserGroup.class);
    query.filter(SettingAttributeKeys.accountId, ACCOUNT_ID);
    userGroupQueryHelper.setQuery(filters, query);
    assertThat(query.toString())
        .isEqualToIgnoringCase(
            "{ {\"accountId\": \"ACCOUNT_ID\", \"$or\": [{\"memberIds\": \"USER_ID\"}, {\"_id\": {\"$in\": [\"usergroup-1\", \"usergroup-2\"]}}]}  }");
  }
}
