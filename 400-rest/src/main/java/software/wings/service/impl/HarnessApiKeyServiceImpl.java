/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_ADMIN;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.app.ManagerCacheRegistrar.HARNESS_API_KEY_CACHE;

import io.harness.beans.AuthType;
import io.harness.beans.ClientType;
import io.harness.entity.HarnessApiKey;
import io.harness.entity.HarnessApiKey.HarnessApiKeyKeys;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.security.EncryptionUtils;
import io.harness.security.annotations.HarnessApiKeyAuth;

import software.wings.dl.WingsPersistence;
import software.wings.security.AuthenticationFilter;
import software.wings.security.JWT_CATEGORY;
import software.wings.security.SecretManager;
import software.wings.service.intfc.HarnessApiKeyService;
import software.wings.utils.CryptoUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;
import javax.cache.Cache;
import javax.cache.CacheException;
import javax.validation.executable.ValidateOnExecution;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@ValidateOnExecution
public class HarnessApiKeyServiceImpl implements HarnessApiKeyService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private SecretManager secretManager;
  @Inject @Named(HARNESS_API_KEY_CACHE) private Cache<String, String> harnessApiKeyCache;

  @Override
  public String generate(String clientType) {
    if (!ClientType.isValid(clientType)) {
      throw new InvalidArgumentsException("Invalid client type : " + clientType);
    }

    String apiKey;
    ClientType clientTypeObject = ClientType.valueOf(clientType);
    HarnessApiKey apiKeyObject = getApiKeyObject(clientTypeObject);
    if (apiKeyObject != null) {
      throw new WingsException("Api key already exists for the given client type.", USER);
    } else {
      apiKey = getNewKey();
      wingsPersistence.save(HarnessApiKey.builder()
                                .encryptedKey(EncryptionUtils.encrypt(apiKey.getBytes(Charset.forName("UTF-8")), null))
                                .clientType(clientTypeObject)
                                .build());
      updateHarnessApiKeyCache(clientType, apiKey);
    }
    return apiKey;
  }

  private void updateHarnessApiKeyCache(String clientType, String apiKey) {
    try {
      harnessApiKeyCache.put(clientType, apiKey);
    } catch (CacheException e) {
      log.error("Unable to set harness api key data into cache", e);
    }
  }

  private String extractToken(ContainerRequestContext requestContext, String prefix) {
    String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authorizationHeader == null || !authorizationHeader.startsWith(prefix)) {
      throw new UnauthorizedException("Invalid token", USER_ADMIN);
    }
    return authorizationHeader.substring(prefix.length()).trim();
  }

  private String getNewKey() {
    int KEY_LEN = 80;
    return CryptoUtils.secureRandAlphaNumString(KEY_LEN);
  }

  @Override
  public String get(String clientType) {
    if (!ClientType.isValid(clientType)) {
      throw new InvalidArgumentsException("Invalid client type : " + clientType);
    }

    String apiKey = harnessApiKeyCache.get(clientType);
    if (apiKey == null) {
      HarnessApiKey globalApiKey =
          wingsPersistence.createQuery(HarnessApiKey.class).filter(HarnessApiKeyKeys.clientType, clientType).get();
      notNullCheck("global api key null for client type " + clientType, globalApiKey, USER);
      apiKey = getDecryptedKey(globalApiKey.getEncryptedKey());
      updateHarnessApiKeyCache(clientType, apiKey);
    }
    return apiKey;
  }

  private String getDecryptedKey(byte[] encryptedKey) {
    return new String(EncryptionUtils.decrypt(encryptedKey, null), Charset.forName("UTF-8"));
  }

  private HarnessApiKey getApiKeyObject(ClientType clientType) {
    return wingsPersistence.createQuery(HarnessApiKey.class).filter(HarnessApiKeyKeys.clientType, clientType).get();
  }

  @Override
  public boolean delete(String clientType) {
    if (!ClientType.isValid(clientType)) {
      throw new InvalidArgumentsException("Invalid client type : " + clientType);
    }

    HarnessApiKey apiKeyObject = getApiKeyObject(ClientType.valueOf(clientType));
    if (apiKeyObject != null) {
      wingsPersistence.delete(HarnessApiKey.class, apiKeyObject.getUuid());
      harnessApiKeyCache.remove(clientType);
      return true;
    }

    return false;
  }

  @Override
  public void validateHarnessClientApiRequest(ResourceInfo resourceInfo, ContainerRequestContext requestContext) {
    ClientType[] clientTypes = getClientTypesFromHarnessClientAuth(resourceInfo);

    if (isNotEmpty(clientTypes)) {
      boolean valid = Arrays.stream(clientTypes).anyMatch(clientType -> {
        if (clientType == null) {
          return false;
        }

        AuthType authType = clientType.getAuthType();
        String apiKeyFromHeader = null;
        String apiKeyToken = null;

        if (AuthType.API_KEY_HEADER == authType) {
          apiKeyFromHeader = requestContext.getHeaderString(AuthenticationFilter.HARNESS_API_KEY_HEADER);
        } else if (AuthType.AUTH_TOKEN_HEADER == authType) {
          apiKeyToken = extractToken(requestContext, PREFIX_API_KEY_TOKEN);
        } else if (AuthType.AUTH_HEADER == authType) {
          apiKeyFromHeader = extractToken(requestContext, PREFIX_BEARER);
        }

        String apiKeyFromDB = get(clientType.name());
        if (apiKeyFromHeader != null) {
          return isNotEmpty(apiKeyFromDB) && apiKeyFromDB.equals(apiKeyFromHeader);
        } else if (apiKeyToken != null) {
          return isNotEmpty(secretManager.verifyJWTToken(apiKeyToken, apiKeyFromDB, JWT_CATEGORY.API_KEY));
        } else {
          return false;
        }
      });

      if (!valid) {
        throw new UnauthorizedException("Invalid token", USER_ADMIN);
      }
    } else {
      throw new InvalidRequestException("Invalid api annotation", USER);
    }
  }

  @Override
  public boolean validateHarnessClientApiRequest(ClientType clientType, String apiKey) {
    if (clientType == null || apiKey == null) {
      return false;
    }

    String apiKeyFromDB = get(clientType.name());
    return isNotEmpty(apiKeyFromDB) && apiKeyFromDB.equals(apiKey);
  }

  @Override
  public boolean isHarnessClientApi(ResourceInfo resourceInfo) {
    return resourceInfo.getResourceMethod().getAnnotation(HarnessApiKeyAuth.class) != null
        || resourceInfo.getResourceClass().getAnnotation(HarnessApiKeyAuth.class) != null;
  }

  private ClientType[] getClientTypesFromHarnessClientAuth(ResourceInfo resourceInfo) {
    Method resourceMethod = resourceInfo.getResourceMethod();
    HarnessApiKeyAuth methodAnnotations = resourceMethod.getAnnotation(HarnessApiKeyAuth.class);
    if (null != methodAnnotations) {
      return methodAnnotations.clientTypes();
    }

    Class<?> resourceClass = resourceInfo.getResourceClass();
    HarnessApiKeyAuth classAnnotations = resourceClass.getAnnotation(HarnessApiKeyAuth.class);
    if (null != classAnnotations) {
      return classAnnotations.clientTypes();
    }

    return null;
  }
}
