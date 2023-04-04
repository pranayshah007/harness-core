/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.HAS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.validation.PersistenceValidator.duplicateCheck;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.app.ManagerCacheRegistrar.APIKEY_CACHE;
import static software.wings.app.ManagerCacheRegistrar.APIKEY_PERMISSION_CACHE;
import static software.wings.app.ManagerCacheRegistrar.APIKEY_RESTRICTION_CACHE;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.function.Function.identity;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SecretText;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.hash.HashUtils;
import io.harness.persistence.HQuery;
import io.harness.secrets.SecretService;
import io.harness.security.SimpleEncryption;

import software.wings.beans.Account;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.ApiKeyEntry.ApiKeyEntryKeys;
import software.wings.beans.Base;
import software.wings.beans.Event.Type;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.features.ApiKeysFeature;
import software.wings.features.api.RestrictedApi;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRestrictionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.CryptoUtils;

import com.google.common.base.Charsets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.cache.Cache;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

@OwnedBy(PL)
@Singleton
@ValidateOnExecution
@Slf4j
public class ApiKeyServiceImpl implements ApiKeyService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private UserGroupService userGroupService;
  @Inject @Named(APIKEY_CACHE) private Cache<String, ApiKeyEntry> apiKeyCache;
  @Inject @Named(APIKEY_PERMISSION_CACHE) private Cache<String, UserPermissionInfo> apiKeyPermissionInfoCache;
  @Inject @Named(APIKEY_RESTRICTION_CACHE) private Cache<String, UserRestrictionInfo> apiKeyRestrictionInfoCache;
  @Inject private AuthHandler authHandler;
  @Inject private AuthService authService;
  @Inject private ExecutorService executorService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private SecretManager secretManager;
  @Inject SecretService secretService;

  private static String DELIMITER = "::";

  private SimpleEncryption getSimpleEncryption(String accountId) {
    Account account = accountService.get(accountId);
    notNullCheck("Account is null for accountId: " + accountId, account);
    return new SimpleEncryption(account.getAccountKey().toCharArray());
  }

  @Override
  @RestrictedApi(ApiKeysFeature.class)
  public ApiKeyEntry generate(String accountId, ApiKeyEntry apiKeyEntry) {
    validateApiKeyName(apiKeyEntry.getName());
    int KEY_LEN = 80;
    String randomKey = accountId + DELIMITER + CryptoUtils.secureRandAlphaNumString(KEY_LEN);
    String apiKey = Base64.getEncoder().encodeToString(randomKey.getBytes(Charsets.UTF_8));

    EncryptedData encryptedKey = encryptApiKey(accountId, apiKey);

    ApiKeyEntry apiKeyEntryToBeSaved = ApiKeyEntry.builder()
                                           .uuid(generateUuid())
                                           .userGroupIds(apiKeyEntry.getUserGroupIds())
                                           .name(apiKeyEntry.getName())
                                           .createdAt(currentTimeMillis())
                                           .sha256Hash(HashUtils.calculateSha256(apiKey))
                                           .accountId(accountId)
                                           .encryptedDataId(encryptedKey.getUuid())
                                           .build();
    String id = duplicateCheck(
        () -> wingsPersistence.save(apiKeyEntryToBeSaved), ApiKeyEntryKeys.name, apiKeyEntryToBeSaved.getName());
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, apiKeyEntryToBeSaved, Type.CREATE);
    return get(id, accountId);
  }

  @Override
  @RestrictedApi(ApiKeysFeature.class)
  public ApiKeyEntry update(String uuid, String accountId, ApiKeyEntry apiKeyEntry) {
    notNullCheck("ApiKeyEntry is null", apiKeyEntry, USER);
    notNullCheck("uuid is null for the given api key entry", uuid, USER);
    notNullCheck(UserGroup.ACCOUNT_ID_KEY, accountId, USER);
    validateApiKeyName(apiKeyEntry.getName());

    ApiKeyEntry apiKeyEntryBeforeUpdate = get(uuid, accountId);

    UpdateOperations<ApiKeyEntry> operations = wingsPersistence.createUpdateOperations(ApiKeyEntry.class);
    setUnset(operations, "name", apiKeyEntry.getName());
    setUnset(operations, "userGroupIds", apiKeyEntry.getUserGroupIds());

    Query<ApiKeyEntry> query = wingsPersistence.createQuery(ApiKeyEntry.class)
                                   .filter(ID_KEY, uuid)
                                   .filter(UserGroup.ACCOUNT_ID_KEY, accountId);

    duplicateCheck(() -> wingsPersistence.update(query, operations), ApiKeyEntryKeys.name, apiKeyEntry.getName());
    ApiKeyEntry apiKeyEntryAfterUpdate = get(uuid, accountId);
    boolean same =
        ListUtils.isEqualList(apiKeyEntryBeforeUpdate.getUserGroupIds(), apiKeyEntryAfterUpdate.getUserGroupIds());
    if (!same) {
      evictApiKeyAndRebuildCache(apiKeyEntryAfterUpdate.getDecryptedKey(), accountId, true);
    }
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, apiKeyEntryAfterUpdate, Type.UPDATE);
    return apiKeyEntryAfterUpdate;
  }

  private EncryptedData encryptApiKey(String accountId, String apiKey) {
    String name = String.format("apiKey_%s_%s", accountId, UUID.randomUUID());
    SecretText secretText =
        SecretText.builder().value(apiKey).hideFromListing(true).scopedToAccount(true).name(name).build();
    return secretManager.encryptSecretUsingGlobalSM(accountId, secretText, false);
  }

  private ApiKeyEntry decryptApiKey(ApiKeyEntry apiKeyEntry) {
    if (isNull(apiKeyEntry.getEncryptedDataId())) {
      apiKeyEntry = decryptUsingAccountKey(apiKeyEntry.getUuid(), apiKeyEntry.getAccountId());
    } else {
      User user = UserThreadLocal.get();
      UserThreadLocal.set(null);
      EncryptedData encryptedData =
          secretManager.getSecretById(apiKeyEntry.getAccountId(), apiKeyEntry.getEncryptedDataId());
      UserThreadLocal.set(user);
      String decryptedKey = String.valueOf(secretService.fetchSecretValue(encryptedData));
      apiKeyEntry.setDecryptedKey(decryptedKey);
    }
    return apiKeyEntry;
  }

  private void validateApiKeyName(String name) {
    if (name == null || isBlank(name) || name.trim().length() < name.length()) {
      throw new GeneralException("Invalid API Key name", USER);
    }
  }

  private void evictApiKeyAndRebuildCache(String apiKey, String accountId, boolean rebuild) {
    log.info("Evicting cache for api key [{}], accountId: [{}]", apiKey, accountId);
    boolean apiKeyPresent = apiKeyCache.remove(getKeyForAPIKeyCache(accountId, apiKey));
    boolean apiKeyPresentInPermissions = apiKeyPermissionInfoCache.remove(getKeyForAPIKeyCache(accountId, apiKey));
    boolean apiKeyPresentInRestrictions = apiKeyRestrictionInfoCache.remove(getKeyForAPIKeyCache(accountId, apiKey));

    if (rebuild) {
      executorService.submit(() -> {
        if (apiKeyPresent) {
          // This call gets the permissions from cache, if present.
          getApiKeyFromCacheOrDB(apiKey, accountId);
        }

        if (apiKeyPresentInPermissions || apiKeyPresentInRestrictions) {
          rebuildPermissionsAndRestrictions(apiKey, accountId);
        }
      });
    }
  }

  @Override
  public void loadUserGroupsForApiKeys(List<ApiKeyEntry> apiKeyEntries, String accountId) {
    if (isEmpty(apiKeyEntries)) {
      return;
    }

    List<UserGroup> allUserGroupList = userGroupService.filter(accountId, emptyList());
    if (isEmpty(allUserGroupList)) {
      return;
    }

    Map<String, UserGroup> idUserGroupMap =
        allUserGroupList.stream().collect(Collectors.toMap(Base::getUuid, identity()));

    apiKeyEntries.forEach(apiKeyEntry -> {
      List<String> userGroupIds = apiKeyEntry.getUserGroupIds();
      if (isEmpty(userGroupIds)) {
        return;
      }

      List<UserGroup> userGroupList = new ArrayList<>();

      userGroupIds.forEach(userGroupId -> {
        UserGroup userGroup = idUserGroupMap.get(userGroupId);
        userGroupList.add(userGroup);
      });

      apiKeyEntry.setUserGroups(userGroupList);
    });
  }

  private List<UserGroup> getUserGroupsForApiKey(List<String> userGroupIds, String accountId) {
    if (isEmpty(userGroupIds)) {
      return emptyList();
    }

    return userGroupService.filter(accountId, userGroupIds);
  }

  @Override
  public PageResponse<ApiKeyEntry> list(
      PageRequest<ApiKeyEntry> pageRequest, String accountId, boolean loadUserGroups, boolean decrypt) {
    pageRequest.addFilter("accountId", EQ, accountId);
    PageResponse<ApiKeyEntry> response = wingsPersistence.query(ApiKeyEntry.class, pageRequest, HQuery.excludeValidate);
    if (response.isEmpty()) {
      return response;
    }

    List<ApiKeyEntry> apiKeyEntryList = response.getResponse();
    if (decrypt) {
      apiKeyEntryList.forEach(apiKeyEntry -> {
        ApiKeyEntry decryptedApiKey = decryptApiKey(apiKeyEntry);
        apiKeyEntry.setDecryptedKey(decryptedApiKey.getDecryptedKey());
      });
    }
    if (loadUserGroups) {
      loadUserGroupsForApiKeys(apiKeyEntryList, accountId);
    }
    return response;
  }

  private String decryptKey(ApiKeyEntry apiKeyEntry) {
    return decryptApiKey(apiKeyEntry).getDecryptedKey();
  }

  @Override
  public ApiKeyEntry get(String uuid, String accountId) {
    ApiKeyEntry entry = wingsPersistence.createQuery(ApiKeyEntry.class)
                            .filter(ApiKeyEntryKeys.accountId, accountId)
                            .filter(ID_KEY, uuid)
                            .get();
    return buildApiKeyEntry(uuid, entry);
  }

  private ApiKeyEntry decryptUsingAccountKey(String uuid, String accountId) {
    ApiKeyEntry entry = wingsPersistence.createQuery(ApiKeyEntry.class)
                            .filter(ApiKeyEntryKeys.accountId, accountId)
                            .filter(ID_KEY, uuid)
                            .get();
    notNullCheck("apiKeyEntry is null for id: " + uuid, entry);

    String decryptedKey = new String(getSimpleEncryption(entry.getAccountId()).decryptChars(entry.getEncryptedKey()));

    return ApiKeyEntry.builder()
        .uuid(entry.getUuid())
        .userGroupIds(entry.getUserGroupIds())
        .name(entry.getName())
        .accountId(entry.getAccountId())
        .decryptedKey(decryptedKey)
        .userGroups(getUserGroupsForApiKey(
            entry.getUserGroupIds() != null ? entry.getUserGroupIds() : emptyList(), entry.getAccountId()))
        .build();
  }

  private ApiKeyEntry buildApiKeyEntry(String uuid, ApiKeyEntry entry) {
    notNullCheck("apiKeyEntry is null for id: " + uuid, entry);
    String decryptedKey = decryptKey(entry);

    return ApiKeyEntry.builder()
        .uuid(entry.getUuid())
        .userGroupIds(entry.getUserGroupIds())
        .name(entry.getName())
        .accountId(entry.getAccountId())
        .decryptedKey(decryptedKey)
        .userGroups(getUserGroupsForApiKey(
            entry.getUserGroupIds() != null ? entry.getUserGroupIds() : emptyList(), entry.getAccountId()))
        .build();
  }

  @Override
  public String getAccountIdFromApiKey(String apiKey) {
    if (apiKey.contains(DELIMITER)) {
      String decodedApiKey = new String(Base64.getDecoder().decode(apiKey), Charsets.UTF_8);
      String[] split = decodedApiKey.split(DELIMITER);
      return split[0];
    } else {
      return null;
    }
  }

  @Override
  public void delete(String accountId, String uuid) {
    ApiKeyEntry apiKeyEntry = get(uuid, accountId);
    evictApiKeyAndRebuildCache(apiKeyEntry.getDecryptedKey(), accountId, false);
    auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, apiKeyEntry);
    wingsPersistence.delete(ApiKeyEntry.class, uuid);
  }

  @Override
  public Boolean validate(String apiKey, String accountId) {
    if (isEmpty(apiKey) || isEmpty(accountId)) {
      return false;
    }
    ApiKeyEntry apiKeyEntry = getApiKeyFromCacheOrDB(apiKey, accountId);
    if (apiKeyEntry == null) {
      throw new UnauthorizedException("Invalid Api Key", USER);
    }
    return true;
  }

  @Override
  public ApiKeyEntry getByKey(String key, String accountId) {
    return getApiKeyFromCacheOrDB(key, accountId);
  }

  private ApiKeyEntry getByKeyFromDB(String key, String accountId) {
    PageRequest<ApiKeyEntry> pageRequest = aPageRequest().addFilter(ApiKeyEntryKeys.accountId, EQ, accountId).build();
    String hashOfIncomingKey = HashUtils.calculateSha256(key);
    Optional<ApiKeyEntry> apiKeyEntryOptional =
        wingsPersistence.query(ApiKeyEntry.class, pageRequest)
            .getResponse()
            .stream()
            .filter(apiKeyEntry -> hashOfIncomingKey.equals(apiKeyEntry.getSha256Hash()))
            .findFirst();
    ApiKeyEntry apiKeyEntry = apiKeyEntryOptional.orElse(null);
    if (apiKeyEntry == null) {
      return null;
    }
    return buildApiKeyEntry(apiKeyEntry.getUuid(), apiKeyEntry);
  }

  private ApiKeyEntry getApiKeyFromCacheOrDB(String apiKey, String accountId) {
    if (apiKeyCache == null) {
      log.warn("apiKeyCache is null. Fetch from DB");
      return getByKeyFromDB(apiKey, accountId);
    } else {
      ApiKeyEntry apiKeyEntry;
      try {
        apiKeyEntry = apiKeyCache.get(getKeyForAPIKeyCache(accountId, apiKey));
        if (apiKeyEntry == null) {
          apiKeyEntry = getByKeyFromDB(apiKey, accountId);
          if (apiKeyEntry == null) {
            return null;
          }
          apiKeyCache.put(getKeyForAPIKeyCache(accountId, apiKeyEntry.getDecryptedKey()), apiKeyEntry);
        }
      } catch (Exception ex) {
        // If there was any exception, remove that entry from cache
        log.error("Exception while fetching the apiKeyEntry from cache", ex);
        apiKeyCache.remove(getKeyForAPIKeyCache(accountId, apiKey));
        apiKeyEntry = getByKeyFromDB(apiKey, accountId);
        if (apiKeyEntry != null) {
          apiKeyCache.put(getKeyForAPIKeyCache(accountId, apiKeyEntry.getDecryptedKey()), apiKeyEntry);
        }
      }
      return apiKeyEntry;
    }
  }

  @Override
  public UserPermissionInfo getApiKeyPermissions(ApiKeyEntry apiKeyEntry, String accountId) {
    String apiKey = apiKeyEntry.getDecryptedKey();
    if (apiKeyPermissionInfoCache == null) {
      log.warn("apiKey permissions cache is null. Fetch from DB");
      return authHandler.evaluateUserPermissionInfo(accountId, apiKeyEntry.getUserGroups(), null);
    } else {
      UserPermissionInfo apiKeyPermissionInfo;
      try {
        apiKeyPermissionInfo = apiKeyPermissionInfoCache.get(getKeyForAPIKeyCache(accountId, apiKey));
        if (apiKeyPermissionInfo == null) {
          apiKeyPermissionInfo = authHandler.evaluateUserPermissionInfo(accountId, apiKeyEntry.getUserGroups(), null);
          logApiKeyPermissions(apiKeyPermissionInfo);
          apiKeyPermissionInfoCache.put(getKeyForAPIKeyCache(accountId, apiKey), apiKeyPermissionInfo);
        }
      } catch (Exception ex) {
        // If there was any exception, remove that entry from cache
        log.info("Encountered exception while getting api key permissions for the accountId [{}]", accountId);
        apiKeyPermissionInfoCache.remove(getKeyForAPIKeyCache(accountId, apiKey));
        apiKeyPermissionInfo = authHandler.evaluateUserPermissionInfo(accountId, apiKeyEntry.getUserGroups(), null);
        logApiKeyPermissions(apiKeyPermissionInfo);
        apiKeyPermissionInfoCache.put(getKeyForAPIKeyCache(accountId, apiKey), apiKeyPermissionInfo);
      }
      return apiKeyPermissionInfo;
    }
  }

  private void logApiKeyPermissions(UserPermissionInfo apiKeyPermissionInfo) {
    try {
      if (apiKeyPermissionInfo.getAppPermissionMap().isEmpty()) {
        log.error("Api key app permission map is empty");
      }
      if (apiKeyPermissionInfo.getAccountPermissionSummary() == null
          || apiKeyPermissionInfo.getAccountPermissionSummary().getPermissions().isEmpty()) {
        log.error("Api key account permission set is empty");
      }
    } catch (Exception e) {
      log.error("Exception while getting account permission set", e);
    }
  }

  @Override
  public UserRestrictionInfo getApiKeyRestrictions(
      ApiKeyEntry apiKeyEntry, UserPermissionInfo userPermissionInfo, String accountId) {
    String apiKey = apiKeyEntry.getDecryptedKey();
    if (apiKeyRestrictionInfoCache == null) {
      log.warn("apiKey restrictions cache is null. Fetch from DB");
      return authService.getUserRestrictionInfoFromDB(accountId, userPermissionInfo, apiKeyEntry.getUserGroups());
    } else {
      UserRestrictionInfo apiKeyPermissionInfo;
      try {
        apiKeyPermissionInfo = apiKeyRestrictionInfoCache.get(getKeyForAPIKeyCache(accountId, apiKey));
        if (apiKeyPermissionInfo == null) {
          apiKeyPermissionInfo =
              authService.getUserRestrictionInfoFromDB(accountId, userPermissionInfo, apiKeyEntry.getUserGroups());
          apiKeyRestrictionInfoCache.put(getKeyForAPIKeyCache(accountId, apiKey), apiKeyPermissionInfo);
        }
      } catch (Exception ex) {
        // If there was any exception, remove that entry from cache
        log.info("Encountered exception while getting api key restrictions for the accountId [{}]", accountId);
        apiKeyRestrictionInfoCache.remove(getKeyForAPIKeyCache(accountId, apiKey));
        apiKeyPermissionInfo =
            authService.getUserRestrictionInfoFromDB(accountId, userPermissionInfo, apiKeyEntry.getUserGroups());
        apiKeyRestrictionInfoCache.put(getKeyForAPIKeyCache(accountId, apiKey), apiKeyPermissionInfo);
      }
      return apiKeyPermissionInfo;
    }
  }

  @Override
  public void deleteByAccountId(String accountId) {
    PageResponse<ApiKeyEntry> pageResponse = list(PageRequestBuilder.aPageRequest().build(), accountId, false, true);
    List<ApiKeyEntry> apiKeyEntryList = pageResponse.getResponse();
    wingsPersistence.delete(
        wingsPersistence.createQuery(ApiKeyEntry.class).filter(ApiKeyEntryKeys.accountId, accountId));

    apiKeyEntryList.forEach(apiKeyEntry -> evictApiKeyAndRebuildCache(apiKeyEntry.getDecryptedKey(), accountId, false));
  }

  @Override
  public void evictAndRebuildPermissions(String accountId, boolean rebuild) {
    PageResponse pageResponse = list(PageRequestBuilder.aPageRequest().build(), accountId, false, true);
    List<ApiKeyEntry> apiKeyEntryList = pageResponse.getResponse();
    if (isEmpty(apiKeyEntryList)) {
      return;
    }

    Set<String> keys = new HashSet<>();

    apiKeyEntryList.forEach(apiKeyEntry -> {
      String apiKey = apiKeyEntry.getDecryptedKey();
      boolean hasPermissions = apiKeyPermissionInfoCache.remove(getKeyForAPIKeyCache(accountId, apiKey));
      if (hasPermissions) {
        keys.add(apiKey);
      }
    });

    if (rebuild) {
      executorService.submit(() -> keys.forEach(key -> {
        ApiKeyEntry apiKeyEntry = get(key, accountId);
        // rebuild cache
        getApiKeyPermissions(apiKeyEntry, accountId);
      }));
    }
  }

  @Override
  public void evictAndRebuildPermissionsAndRestrictions(String accountId, boolean rebuild) {
    log.info("Evicting the permissions for the accountId [{}] with option rebuild [{}]", accountId, rebuild);
    final List<ApiKeyEntry> apiKeyEntryList = emptyIfNull(
        wingsPersistence.createQuery(ApiKeyEntry.class).filter(ApiKeyEntryKeys.accountId, accountId).asList());
    apiKeyEntryList.forEach(apiKeyEntry -> apiKeyEntry.setDecryptedKey(decryptKey(apiKeyEntry)));

    evictAndRebuildPermissionsAndRestrictions(accountId, rebuild, apiKeyEntryList);
  }

  private void evictAndRebuildPermissionsAndRestrictions(
      String accountId, boolean rebuild, List<ApiKeyEntry> apiKeyEntryList) {
    if (isEmpty(apiKeyEntryList)) {
      return;
    }

    apiKeyEntryList.forEach(
        apiKeyEntry -> evictApiKeyAndRebuildCache(apiKeyEntry.getDecryptedKey(), accountId, rebuild));
  }

  private void rebuildPermissionsAndRestrictions(String apiKey, String accountId) {
    ApiKeyEntry apiKeyEntry = getByKey(apiKey, accountId);
    if (apiKeyEntry != null) {
      rebuildPermissionsAndRestrictions(apiKeyEntry);
    }
  }

  private void rebuildPermissionsAndRestrictions(ApiKeyEntry apiKeyEntry) {
    String accountId = apiKeyEntry.getAccountId();
    // Load cache again
    UserPermissionInfo permissions = getApiKeyPermissions(apiKeyEntry, accountId);
    getApiKeyRestrictions(apiKeyEntry, permissions, accountId);
  }

  private String getKeyForAPIKeyCache(final String accountId, final String apiKey) {
    return accountId + apiKey;
  }

  @Override
  public void evictPermissionsAndRestrictionsForUserGroup(UserGroup userGroup) {
    PageRequest<ApiKeyEntry> pageRequest =
        PageRequestBuilder.aPageRequest().addFilter("userGroupIds", HAS, userGroup.getUuid()).build();
    PageResponse pageResponse = list(pageRequest, userGroup.getAccountId(), false, true);
    List<ApiKeyEntry> apiKeyEntryList = pageResponse.getResponse();
    evictAndRebuildPermissionsAndRestrictions(userGroup.getAccountId(), true, apiKeyEntryList);
  }

  @Override
  public Boolean isApiKeyValid(String apiKey, String accountId) {
    try {
      return validate(apiKey, accountId);
    } catch (UnauthorizedException | InvalidRequestException exception) {
      return false;
    }
  }
}
