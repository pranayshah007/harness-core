/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.servicenow.auth.refreshtoken;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.getOkHttpClientBuilder;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.INVALID_CREDENTIALS;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.NOT_FOUND;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.REFRESH_TOKEN_GRANT_TYPE;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.SERVICENOW_TOKEN_URL_SUFFIX;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenExceptionUtils.REFRESH_TOKEN_ERROR_PREFIX;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenExceptionUtils.handleRefreshTokenCacheException;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenExceptionUtils.prepareRefreshTokenException;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.GeneralException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.ServiceNowOIDCException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.network.Http;

import com.fasterxml.jackson.core.JsonParseException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Responsible for generating access token via oauth refresh token grant type protocol
 *
 * Protocol description : https://www.oauth.com/oauth2-servers/access-tokens/refreshing-access-tokens/
 *
 * Tested using ServiceNow and Okta as authentication severs.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class RefreshTokenAuthNgHelper {
  /**
   * The Size Limit For Refresh Token cache
   */
  public static final long REFRESH_TOKEN_CACHE_SIZE = 500L;
  public static final long REFRESH_TOKEN_LEEWAY = 10L;

  public static final long TIME_OUT = 60;
  public static final String SLASH = "/";

  @Value
  @Builder
  private static class RefreshTokenCacheKey {
    String tokenUrl;
    String refreshToken;
    String clientId;
    @Nullable String clientSecret;
    @Nullable String scope;
  }

  /**
   * Uses cache with maximum size, and per entry expiration period
   * which is calculated from expiry timestamp obtained with a leeway defined in the class
   *
   * Expiry duration for an entry is update on either create or update of the entity
   *
   * Expiry Docs:
   * https://www.javadoc.io/doc/com.github.ben-manes.caffeine/caffeine/2.8.4/com/github/benmanes/caffeine/cache/Expiry.html
   *       https://github.com/ben-manes/caffeine/wiki/Eviction#time-based
   *
   * In case of servicenow as token url, we need to ensure to remove the trailing slash to not redirect to login page
   * */
  private static final Cache<RefreshTokenCacheKey, AccessTokenResponse> refreshTokenCache =
      Caffeine.newBuilder()
          .maximumSize(REFRESH_TOKEN_CACHE_SIZE)
          .expireAfter(new Expiry<RefreshTokenCacheKey, AccessTokenResponse>() {
            public long expireAfterCreate(
                @NonNull RefreshTokenCacheKey key, @NonNull AccessTokenResponse graph, long currentTime) {
              return calculateExpiryWithLeewayInNanos(graph.getExpiresIn());
            }
            public long expireAfterUpdate(@NonNull RefreshTokenCacheKey key, @NonNull AccessTokenResponse graph,
                long currentTime, long currentDuration) {
              return calculateExpiryWithLeewayInNanos(graph.getExpiresIn());
            }
            public long expireAfterRead(@NonNull RefreshTokenCacheKey key, @NonNull AccessTokenResponse graph,
                long currentTime, long currentDuration) {
              return currentDuration;
            }
          })
          .build();

  public static String getAuthToken(String refreshToken, String clientId, String clientSecret, String scope,
      String tokenUrl, boolean fetchFromCache) {
    RefreshTokenCacheKey refreshTokenCacheKey = RefreshTokenCacheKey.builder()
                                                    .refreshToken(refreshToken)
                                                    .clientId(clientId)
                                                    .clientSecret(clientSecret)
                                                    .scope(scope)
                                                    .tokenUrl(tokenUrl)
                                                    .build();
    try {
      if (fetchFromCache) {
        AccessTokenResponse accessTokenResponse = refreshTokenCache.getIfPresent(refreshTokenCacheKey);
        if (!isNull(accessTokenResponse)) {
          log.info("Using cache to get access token with token url: {}", tokenUrl);
          return accessTokenResponse.getAuthToken();
        }
        log.info("Couldn't get access token from cache with token url: {}", tokenUrl);
      }
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      throw handleRefreshTokenCacheException("fetching", tokenUrl, sanitizedException);
    }

    try {
      refreshTokenCache.invalidate(refreshTokenCacheKey);
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      throw handleRefreshTokenCacheException("invalidating", tokenUrl, sanitizedException);
    }

    AccessTokenResponse accessTokenResponse = getAuthTokenInternal(refreshTokenCacheKey);

    try {
      refreshTokenCache.put(refreshTokenCacheKey, accessTokenResponse);
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      throw handleRefreshTokenCacheException("adding", tokenUrl, sanitizedException);
    }

    log.info("Adding entry in cache for access token with token url: {}", tokenUrl);
    return accessTokenResponse.getAuthToken();
  }

  private AccessTokenResponse getAuthTokenInternal(RefreshTokenCacheKey refreshTokenCacheKey) {
    try {
      String tokenUrl = refreshTokenCacheKey.getTokenUrl();
      Call<AccessTokenResponse> request;
      RefreshTokenRestClient refreshTokenRestClient;
      if (isTokenUrlOfServiceNow(tokenUrl)) {
        refreshTokenRestClient = getRefreshTokenRestClient(formatServiceNowTokenUrl(tokenUrl));

        request = refreshTokenRestClient.getAccessTokenFromServiceNow(REFRESH_TOKEN_GRANT_TYPE,
            refreshTokenCacheKey.getClientId(), refreshTokenCacheKey.getClientSecret(),
            refreshTokenCacheKey.getRefreshToken(), refreshTokenCacheKey.getScope());

      } else {
        refreshTokenRestClient = getRefreshTokenRestClient(formatTokenUrl(tokenUrl));
        request = refreshTokenRestClient.getAccessToken(REFRESH_TOKEN_GRANT_TYPE, refreshTokenCacheKey.getClientId(),
            refreshTokenCacheKey.getClientSecret(), refreshTokenCacheKey.getRefreshToken(),
            refreshTokenCacheKey.getScope(), formatTokenUrl(tokenUrl));
      }

      Response<AccessTokenResponse> response = request.execute();
      log.info("Response received from refresh_token grant: {}", response);
      RefreshTokenResponseHandler.handleResponse(response, "Failed to get access token using refresh token grant type");
      // body() is validated to be not null in above method
      return response.body();
    } catch (ServiceNowOIDCException refreshTokenEx) {
      ServiceNowOIDCException sanitizedException = ExceptionMessageSanitizer.sanitizeException(refreshTokenEx);
      log.warn("Failed to get access token using refresh token grant type: {}", sanitizedException.getMessage());
      if (String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX, INVALID_CREDENTIALS)
              .equals(sanitizedException.getMessage())) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the client credentials are correct, refresh token is valid and client have necessary permissions to access the ServiceNow resource",
            "The Refresh Token grant credentials provided are invalid or client doesn't have necessary permissions to access the ServiceNow resource",
            sanitizedException);
      } else if (String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX, NOT_FOUND)
                     .equals(sanitizedException.getMessage())) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the token url is correct and accessible from delegate", "Not able to access the given token url",
            sanitizedException);
      } else {
        throw wrapInNestedException(sanitizedException);
      }
    } catch (JsonParseException ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      log.warn(
          "Failed to parse access token response using refresh token grant type: {}", sanitizedException.getMessage());
      throw wrapInNestedException(prepareRefreshTokenException(
          "Failed to parse access token response using refresh token grant type", sanitizedException));
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      log.warn("Failed to get access token using refresh token grant type: {}", sanitizedException.getMessage());
      throw wrapInNestedException(
          prepareRefreshTokenException(ExceptionUtils.getMessage(sanitizedException), sanitizedException));
    }
  }

  private RefreshTokenRestClient getRefreshTokenRestClient(String url) {
    Retrofit retrofit = new Retrofit.Builder()
                            .client(getHttpClient(url))
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(RefreshTokenRestClient.class);
  }

  @NotNull
  private OkHttpClient getHttpClient(String url) {
    return getOkHttpClientBuilder()
        .connectTimeout(TIME_OUT, TimeUnit.SECONDS)
        .readTimeout(TIME_OUT, TimeUnit.SECONDS)
        .proxy(Http.checkAndGetNonProxyIfApplicable(url))
        .build();
  }

  @NotNull
  private static WingsException wrapInNestedException(ServiceNowOIDCException ex) {
    return NestedExceptionUtils.hintWithExplanationException(
        "Check if the token url is accessible from delegate, Refresh Token grant credentials are correct and refresh token is not expired or revoked",
        "Not able to access the given token url with the credentials or not able to get access token using refresh token grant type",
        new GeneralException(ExceptionUtils.getMessage(ex), ex, USER));
  }

  /**
   *     removes slash from serviceNow tokenUrl else it redirects to servicenow login
   *    This is done because base url in retrofit end with '/'
   *
   *    This method removes any string following last occurrence of "oauth_token.do" including it as well.
   *    Then it ensures that servicenow base url is appended with a slash for retrofit.
   *    Later it is expected to add path "oauth_token.do" (without slash)/
   *
   * */
  public static String formatServiceNowTokenUrl(String baseUrl) {
    int lastIndex = baseUrl.lastIndexOf(SERVICENOW_TOKEN_URL_SUFFIX);
    if (lastIndex != -1) {
      baseUrl = baseUrl.substring(0, lastIndex);
    }
    return formatTokenUrl(baseUrl);
  }

  public static String formatTokenUrl(String baseUrl) {
    if (!baseUrl.endsWith(SLASH)) {
      baseUrl += SLASH;
    }
    return baseUrl;
  }

  private static long calculateExpiryWithLeewayInNanos(long expiresIn) {
    long seconds = expiresIn > REFRESH_TOKEN_LEEWAY ? expiresIn - REFRESH_TOKEN_LEEWAY : expiresIn;
    return TimeUnit.SECONDS.toNanos(seconds);
  }

  private static boolean isTokenUrlOfServiceNow(String baseUrl) {
    return baseUrl.contains(SERVICENOW_TOKEN_URL_SUFFIX);
  }
}
