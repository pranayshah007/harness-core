/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.NIKOLA;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_KEY;
import static software.wings.utils.WingsTestConstants.USER_GROUP_ID;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EncryptedData;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.hash.HashUtils;
import io.harness.rule.Owner;
import io.harness.secrets.SecretService;
import io.harness.security.SimpleEncryption;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.Event.Type;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserRestrictionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.CryptoUtils;

import com.google.common.base.Charsets;
import com.google.inject.Inject;
import java.util.Base64;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@Slf4j
public class ApiKeyServiceTest extends WingsBaseTest {
  @Mock private AccountService accountService;
  @Mock private UserService userService;
  @Mock private UserGroupService userGroupService;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private Cache<String, ApiKeyEntry> apiKeyCache;
  @Mock private Cache<String, UserPermissionInfo> apiKeyPermissionInfoCache;
  @Mock private Cache<String, UserRestrictionInfo> apiKeyRestrictionInfoCache;
  @Mock private SecretManager secretManager;
  @Mock private SecretService secretService;
  @Inject @InjectMocks private ApiKeyService apiKeyService;
  private final String uuid = UUIDGenerator.generateUuid();
  private final char[] decryptedValue = "decryptedValue".toCharArray();
  private final EncryptedData encryptedData = EncryptedData.builder().uuid(uuid).build();
  @Inject private WingsPersistence wingsPersistence;
  @Before
  public void init() {
    setUserRequestContext();
    when(userService.isUserAssignedToAccount(any(), any())).thenReturn(true);
    when(secretManager.encryptSecretUsingGlobalSM(any(String.class), any(SecretText.class), any(boolean.class)))
        .thenReturn(encryptedData);
    when(secretManager.getSecretById(ACCOUNT_ID, uuid)).thenReturn(encryptedData);
    when(secretService.fetchSecretValue(any(EncryptedData.class))).thenReturn(decryptedValue);
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

    ArgumentCaptor<SecretText> secretTextArgumentCaptor = ArgumentCaptor.forClass(SecretText.class);
    verify(secretManager, times(1))
        .encryptSecretUsingGlobalSM(eq(ACCOUNT_ID), secretTextArgumentCaptor.capture(), eq(false));
    SecretText secretTextAdded = secretTextArgumentCaptor.getValue();
    assertThat(secretTextAdded.getValue()).isNotNull();
    assertThat(secretTextAdded.getName()).isNotNull();

    verify(secretManager, times(1)).getSecretById(ACCOUNT_ID, uuid);
    verify(secretService, times(1)).fetchSecretValue(encryptedData);

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

    int pageSize = pageResponse.size();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testValidate() {
    String hashValue = HashUtils.calculateSha256("decryptedValue");
    mockStatic(HashUtils.class);
    when(HashUtils.calculateSha256(any())).thenReturn(hashValue);

    ApiKeyEntry apiKeyEntry = generateKey("name");
    try {
      apiKeyService.validate(apiKeyEntry.getDecryptedKey(), ACCOUNT_ID);
    } catch (UnauthorizedException ex) {
      fail("Validation failed: " + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testValidate_NonMigrated() {
    ApiKeyEntry apiKeyEntry = generateNonMigratedKey("name");
    try {
      apiKeyService.validate(apiKeyEntry.getDecryptedKey(), ACCOUNT_ID);
    } catch (UnauthorizedException ex) {
      fail("Validation failed: " + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void Validate_ApiKeyNullOrEmpty_ReturnsFalse() {
    assertThat(apiKeyService.validate(null, "abc")).isFalse();
    assertThat(apiKeyService.validate(StringUtils.EMPTY, "abc")).isFalse();
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void Validate_AccountKeyNullOrEmpty_ReturnsFalse() {
    assertThat(apiKeyService.validate("abc", null)).isFalse();
    assertThat(apiKeyService.validate("abc", StringUtils.EMPTY)).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetApiKey() {
    ApiKeyEntry apiKeyEntry = generateKey("name");
    User user = User.Builder.anUser().uuid("uid").name("username").build();
    UserThreadLocal.set(user);
    when(userService.isUserAssignedToAccount(any(), any())).thenReturn(true);
    boolean exceptionThrown = false;
    ApiKeyEntry apiKeyEntryFromGet = null;
    try {
      apiKeyEntryFromGet = apiKeyService.get(apiKeyEntry.getUuid(), ACCOUNT_ID);
    } catch (InvalidRequestException ex) {
      exceptionThrown = true;
    }
    assertThat(apiKeyEntryFromGet).isNotNull();
    String key = apiKeyEntryFromGet.getDecryptedKey();
    assertThat(key).isEqualTo(apiKeyEntry.getDecryptedKey());

    verify(secretManager, times(2)).getSecretById(ACCOUNT_ID, uuid);
    verify(secretService, times(2)).fetchSecretValue(encryptedData);

    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetApiKey2() {
    ApiKeyEntry apiKeyEntry = generateKey("name");
    User user = User.Builder.anUser().uuid("uid").name("username").build();
    UserThreadLocal.set(user);
    when(userService.isUserAssignedToAccount(any(), any())).thenReturn(false);
    ApiKeyEntry apiKeyEntryFromGet = null;
    boolean exceptionThrown = false;
    try {
      apiKeyEntryFromGet = apiKeyService.get(apiKeyEntry.getUuid(), ACCOUNT_ID);
    } catch (InvalidRequestException ex) {
      exceptionThrown = true;
    }
    assertThat(apiKeyEntryFromGet).isNotNull();
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetApiKey3() {
    ApiKeyEntry apiKeyEntry = generateKey("name");
    UserThreadLocal.set(null);
    when(userService.isUserAssignedToAccount(any(), any())).thenReturn(true);
    boolean exceptionThrown = false;
    ApiKeyEntry apiKeyEntryFromGet = null;
    try {
      apiKeyEntryFromGet = apiKeyService.get(apiKeyEntry.getUuid(), ACCOUNT_ID);
    } catch (InvalidRequestException ex) {
      exceptionThrown = true;
    }
    assertThat(apiKeyEntryFromGet).isNotNull();
    String key = apiKeyEntryFromGet.getDecryptedKey();
    assertThat(key).isEqualTo(apiKeyEntry.getDecryptedKey());
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetApiKey_NonMigrated() {
    ApiKeyEntry apiKeyEntry = generateNonMigratedKey("name");
    User user = User.Builder.anUser().uuid("uid").name("username").build();
    UserThreadLocal.set(user);

    ApiKeyEntry apiKeyEntryFromGet = apiKeyService.get(uuid, ACCOUNT_ID);

    assertThat(apiKeyEntryFromGet).isNotNull();
    String key = apiKeyEntryFromGet.getDecryptedKey();
    assertThat(key).isEqualTo(apiKeyEntry.getDecryptedKey());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldNotAddNullUserGroupsInApiKeyEntry() {
    ApiKeyEntry apiKeyEntry = ApiKeyEntry.builder()
                                  .uuid(uuid)
                                  .name("api-key-name")
                                  .accountId(ACCOUNT_ID)
                                  .userGroupIds(of("existing-group", "non-existing-group"))
                                  .build();

    when(userGroupService.filter(any(), any())).thenReturn(of(UserGroup.builder().uuid("existing-group").build()));

    apiKeyService.loadUserGroupsForApiKeys(of(apiKeyEntry), ACCOUNT_ID);

    assertThat(apiKeyEntry.getUserGroups()).doesNotContainNull();
  }

  private ApiKeyEntry generateNonMigratedKey(String name) {
    Account account = anAccount().withUuid(ACCOUNT_ID).withAccountKey(ACCOUNT_KEY).build();
    doReturn(account).when(accountService).get(ACCOUNT_ID);
    String randomKey = ACCOUNT_ID + "::" + CryptoUtils.secureRandAlphaNumString(80);
    String apiKey = Base64.getEncoder().encodeToString(randomKey.getBytes(Charsets.UTF_8));
    ApiKeyEntry apiKeyEntryToBeSaved =
        ApiKeyEntry.builder()
            .uuid(uuid)
            .name(name)
            .createdAt(currentTimeMillis())
            .encryptedKey(getSimpleEncryption(ACCOUNT_ID).encryptChars(apiKey.toCharArray()))
            .sha256Hash(HashUtils.calculateSha256(apiKey))
            .accountId(ACCOUNT_ID)
            .build();
    wingsPersistence.save(apiKeyEntryToBeSaved);
    apiKeyEntryToBeSaved.setDecryptedKey(apiKey);
    return apiKeyEntryToBeSaved;
  }
}
