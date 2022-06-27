/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.rule.OwnerRule.NIKOLA;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_KEY;
import static software.wings.utils.WingsTestConstants.USER_GROUP_ID;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.security.SimpleEncryption;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.Event.Type;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserRestrictionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuditServiceHelper;

import com.google.inject.Inject;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@Slf4j
public class ApiKeyServiceTest extends WingsBaseTest {
  @Mock private AccountService accountService;
  @Mock private UserGroupService userGroupService;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private Cache<String, ApiKeyEntry> apiKeyCache;
  @Mock private Cache<String, UserPermissionInfo> apiKeyPermissionInfoCache;
  @Mock private Cache<String, UserRestrictionInfo> apiKeyRestrictionInfoCache;
  @Inject @InjectMocks private ApiKeyService apiKeyService;

  @Before
  public void init() {
    setUserRequestContext();
  }

  private void setUserRequestContext() {
    User user = User.Builder.anUser().name(USER_NAME).uuid(USER_ID).build();
    user.setUserRequestContext(UserRequestContext.builder().accountId(ACCOUNT_ID).build());
    UserThreadLocal.set(user);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testGenerate() {
    ApiKeyEntry savedApiKeyEntry = generateKey("name");

    assertThat(savedApiKeyEntry).isNotNull();
    assertThat(savedApiKeyEntry.getDecryptedKey()).isNotEmpty();
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(savedApiKeyEntry.getAccountId()), eq(null), any(ApiKeyEntry.class), eq(Type.CREATE));
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void testGenerateWithAlreadyExistingName() {
    ApiKeyEntry savedApiKeyEntry = generateKey("name");

    assertThat(savedApiKeyEntry).isNotNull();
    assertThat(savedApiKeyEntry.getDecryptedKey()).isNotEmpty();
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(savedApiKeyEntry.getAccountId()), eq(null), any(ApiKeyEntry.class), eq(Type.CREATE));

    ApiKeyEntry newApiKeyEntry = generateKey("name");

    assertThat(newApiKeyEntry).isNotNull();
    assertThat(newApiKeyEntry.getName()).isEqualTo("name1");
    assertThat(newApiKeyEntry.getDecryptedKey()).isNotEmpty();
    verify(auditServiceHelper, times(2))
        .reportForAuditingUsingAccountId(
            eq(newApiKeyEntry.getAccountId()), eq(null), any(ApiKeyEntry.class), eq(Type.CREATE));
  }

  private ApiKeyEntry generateKey(String name) {
    Account account = anAccount().withUuid(ACCOUNT_ID).withAccountKey(ACCOUNT_KEY).build();
    doReturn(account).when(accountService).get(ACCOUNT_ID);
    UserGroup userGroup = UserGroup.builder().uuid(USER_GROUP_ID).name(name).build();
    PageResponse pageResponse = aPageResponse().withResponse(asList(userGroup)).build();
    doReturn(pageResponse).when(userGroupService).list(anyString(), any(PageRequest.class), anyBoolean(), any(), any());
    ApiKeyEntry apiKeyEntry =
        ApiKeyEntry.builder().name("name1").accountId(ACCOUNT_ID).userGroupIds(asList(USER_GROUP_ID)).build();
    return apiKeyService.generate(ACCOUNT_ID, apiKeyEntry);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testUpdate() {
    ApiKeyEntry apiKeyEntry = generateKey("name");
    ApiKeyEntry apiKeyEntryForUpdate = ApiKeyEntry.builder().name("newName").build();
    ApiKeyEntry updatedApiKeyEntry =
        apiKeyService.update(apiKeyEntry.getUuid(), apiKeyEntry.getAccountId(), apiKeyEntryForUpdate);
    assertThat(updatedApiKeyEntry).isNotNull();
    assertThat(updatedApiKeyEntry.getUuid()).isEqualTo(apiKeyEntry.getUuid());
    assertThat(updatedApiKeyEntry.getDecryptedKey()).isEqualTo(apiKeyEntry.getDecryptedKey());
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(apiKeyEntry.getAccountId()), eq(null), any(ApiKeyEntry.class), eq(Type.UPDATE));
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateWithCache() {
    ApiKeyEntry apiKeyEntry = generateKey("name");

    ApiKeyEntry apiKeyEntryForUpdate =
        ApiKeyEntry.builder().name("newName").userGroupIds(asList(USER_GROUP_ID + "2")).build();
    ApiKeyEntry updatedApiKeyEntry =
        apiKeyService.update(apiKeyEntry.getUuid(), apiKeyEntry.getAccountId(), apiKeyEntryForUpdate);
    assertThat(updatedApiKeyEntry).isNotNull();
    assertThat(updatedApiKeyEntry.getUuid()).isEqualTo(apiKeyEntry.getUuid());
    assertThat(updatedApiKeyEntry.getUserGroupIds()).isNotEmpty();
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(apiKeyEntry.getAccountId()), eq(null), any(ApiKeyEntry.class), eq(Type.CREATE));
    verify(apiKeyCache, times(1)).remove(updatedApiKeyEntry.getAccountId() + updatedApiKeyEntry.getDecryptedKey());
    verify(apiKeyPermissionInfoCache, times(1))
        .remove(updatedApiKeyEntry.getAccountId() + updatedApiKeyEntry.getDecryptedKey());
    verify(apiKeyRestrictionInfoCache, times(1))
        .remove(updatedApiKeyEntry.getAccountId() + updatedApiKeyEntry.getDecryptedKey());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUpdateForUserGroup() {
    ApiKeyEntry apiKeyEntry = generateKey("name");

    ApiKeyEntry apiKeyEntryForUpdate =
        ApiKeyEntry.builder().name("newName").userGroupIds(asList(USER_GROUP_ID)).build();
    ApiKeyEntry updatedApiKeyEntry =
        apiKeyService.update(apiKeyEntry.getUuid(), apiKeyEntry.getAccountId(), apiKeyEntryForUpdate);
    assertThat(updatedApiKeyEntry).isNotNull();
    assertThat(updatedApiKeyEntry.getUuid()).isEqualTo(apiKeyEntry.getUuid());
    assertThat(updatedApiKeyEntry.getUserGroupIds()).isEqualTo(apiKeyEntry.getUserGroupIds());
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(apiKeyEntry.getAccountId()), eq(null), any(ApiKeyEntry.class), eq(Type.CREATE));
  }

  @Test(expected = WingsException.class)
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testDelete() {
    ApiKeyEntry apiKeyEntry = generateKey("name");
    apiKeyService.delete(ACCOUNT_ID, apiKeyEntry.getUuid());
    apiKeyService.get(apiKeyEntry.getUuid(), apiKeyEntry.getAccountId());
    verify(auditServiceHelper, times(1))
        .reportDeleteForAuditingUsingAccountId(eq(apiKeyEntry.getAccountId()), any(ApiKeyEntry.class));
  }

  private SimpleEncryption getSimpleEncryption(String accountId) {
    Account account = accountService.get(accountId);
    notNullCheck("Account", account);
    return new SimpleEncryption(account.getAccountKey().toCharArray());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testGet() {
    ApiKeyEntry apiKeyEntry = generateKey("name");
    ApiKeyEntry apiKeyEntryFromGet = apiKeyService.get(apiKeyEntry.getUuid(), ACCOUNT_ID);
    assertThat(apiKeyEntryFromGet).isNotNull();
    String key = apiKeyEntryFromGet.getDecryptedKey();
    assertThat(key).isEqualTo(apiKeyEntry.getDecryptedKey());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testList() {
    generateKey("name");
    PageRequest request = PageRequestBuilder.aPageRequest().addFilter("accountId", Operator.EQ, ACCOUNT_ID).build();
    PageResponse pageResponse = apiKeyService.list(request, ACCOUNT_ID, false, false);
    assertThat(pageResponse.getResponse()).isNotEmpty();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testValidate() {
    ApiKeyEntry apiKeyEntry = generateKey("name");
    try {
      apiKeyService.validate(apiKeyEntry.getDecryptedKey(), ACCOUNT_ID);
    } catch (UnauthorizedException ex) {
      fail("Validation failed: " + ex.getMessage());
    }
  }
}
