/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.infra;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.nio.charset.Charset.defaultCharset;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.environment.SystemEnvironment;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.common.AccessTokenBean;

import software.wings.app.MainConfiguration;
import software.wings.utils.CdnStorageUrlGenerator;
import software.wings.utils.GcsUtils;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.logging.v2.LoggingScopes;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

@Singleton
@ValidateOnExecution
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(PL)
public class InfraDownloadServiceImpl implements InfraDownloadService {
  private static final String DOWNLOAD_SERVICE_ACCOUNT_ENV_VAR = "SERVICE_ACC";
  private static final String DELEGATE_JAR = "delegate.jar";
  private static final String WATCHER_JAR = "watcher.jar";
  private static final String BUILDS_PATH = "/builds/";
  private static final String ON_PREM_ENV_STRING = "on-prem";
  private static final String ENV_ENV_VAR = "ENV";
  private static final String LOGGING_SERVICE_ACCOUNT_ENV_VAR = "LOGGING_SERVICE_ACC";
  private static final List<String> gCloudLoggingScopes =
      List.of(LoggingScopes.LOGGING_WRITE, LoggingScopes.LOGGING_READ);

  static final String DEFAULT_ERROR_STRING = "ERROR_GETTING_DATA";
  static final String LOCAL_DELEGATE = "file:///local-storage/wingsdelegates/delegate/delegate.jar";
  static final String LOCAL_WATCHER = "file:///local-storage/wingswatchers/watcher/watcher.jar";

  @Inject private GcsUtils gcsUtil;
  @Inject private SystemEnvironment sysenv;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private CdnStorageUrlGenerator cdnStorageUrlGenerator;
  @Inject private MainConfiguration mainConfiguration;

  private final Map<String, String> serviceAccountCache = new HashMap<>();

  private LoadingCache<String, AccessTokenBean> accessTokenCache =
      CacheBuilder.newBuilder()
          .maximumSize(1)
          .expireAfterWrite(50, TimeUnit.MINUTES)
          .build(new CacheLoader<String, AccessTokenBean>() {
            @Override
            public AccessTokenBean load(String key) {
              String serviceAccountJson = getServiceAccountJson(key);
              if (isEmpty(serviceAccountJson)) {
                throw new InvalidInfraException("No logging service account available");
              }

              try {
                GoogleCredential credential =
                    GoogleCredential.fromStream(IOUtils.toInputStream(serviceAccountJson, defaultCharset()))
                        .createScoped(gCloudLoggingScopes);

                if (!credential.refreshToken()) {
                  throw new InvalidInfraException("Failed to refresh token");
                }

                return new AccessTokenBean(credential.getServiceAccountProjectId(), credential.getAccessToken(),
                    credential.getExpirationTimeMilliseconds());
              } catch (Exception e) {
                throw new InvalidInfraException("Error getting logging token", e);
              }
            }
          });

  @Override
  public String getDownloadUrlForDelegate(String version, String envString, String accountId) {
    if (isEmpty(envString)) {
      envString = getEnv();
    }
    if ("local".equals(envString)) {
      return LOCAL_DELEGATE;
    }

    if (mainConfiguration.useCdnForDelegateStorage() && !ON_PREM_ENV_STRING.equals(envString)) {
      return cdnStorageUrlGenerator.getDelegateJarUrl(version);
    } else {
      String serviceAccountJson = getServiceAccountJson(DOWNLOAD_SERVICE_ACCOUNT_ENV_VAR);
      if (isNotBlank(serviceAccountJson)) {
        try {
          return getGcsUtil().getSignedUrlForServiceAccount(
              "/harness-" + envString + "-delegates" + BUILDS_PATH + version + "/" + DELEGATE_JAR, serviceAccountJson,
              3600L, accountId);
        } catch (Exception e) {
          log.warn("Failed to get downloadUrlForDelegate for version=" + version + ", env=" + envString, e);
        }
      }
    }

    return DEFAULT_ERROR_STRING;
  }

  @Override
  public String getDownloadUrlForWatcher(String version, String envString, String accountId) {
    if (isEmpty(envString)) {
      envString = getEnv();
    }
    if ("local".equals(envString)) {
      return LOCAL_WATCHER;
    }

    if (mainConfiguration.useCdnForDelegateStorage() && !ON_PREM_ENV_STRING.equals(envString)) {
      return cdnStorageUrlGenerator.getWatcherJarUrl(version);
    } else {
      String serviceAccountJson = getServiceAccountJson(DOWNLOAD_SERVICE_ACCOUNT_ENV_VAR);
      if (isNotBlank(serviceAccountJson)) {
        try {
          return getGcsUtil().getSignedUrlForServiceAccount(
              "/harness-" + envString + "-watchers" + BUILDS_PATH + version + "/" + WATCHER_JAR, serviceAccountJson,
              3600L, accountId);

        } catch (Exception e) {
          log.warn("Failed to get downloadUrlForDelegate for version=" + version + ", env=" + envString, e);
        }
      }
    }
    return DEFAULT_ERROR_STRING;
  }

  public String getDownloadUrlForWatcher(String version, String accountId) {
    String env = getEnv();
    return getDownloadUrlForWatcher(version, env, accountId);
  }

  @Override
  public String getDownloadUrlForDelegate(String version, String accountId) {
    String env = getEnv();
    return getDownloadUrlForDelegate(version, env, accountId);
  }

  @Override
  public AccessTokenBean getStackdriverLoggingToken() {
    if (isBlank(sysenv.get(LOGGING_SERVICE_ACCOUNT_ENV_VAR))) {
      return null;
    }

    try {
      return accessTokenCache.get(LOGGING_SERVICE_ACCOUNT_ENV_VAR);
    } catch (Exception e) {
      log.error("Failed to get logging token", e);
    }
    return null;
  }

  @Override
  public String getCdnWatcherMetaDataFileUrl() {
    return cdnStorageUrlGenerator.getWatcherMetaDataFileUrl(getEnv());
  }

  @Override
  public String getCdnWatcherBaseUrl() {
    return cdnStorageUrlGenerator.getWatcherJarBaseUrl();
  }

  protected String getEnv() {
    return Optional.ofNullable(sysenv.get(ENV_ENV_VAR)).orElse("local");
  }

  private String getServiceAccountJson(String serviceAccountEnvVarName) {
    if (serviceAccountCache.containsKey(serviceAccountEnvVarName)) {
      return serviceAccountCache.get(serviceAccountEnvVarName);
    }

    String serviceAccountPath = sysenv.get(serviceAccountEnvVarName);
    if (isEmpty(serviceAccountPath)) {
      String msg = String.format(
          "Environment variable [%s] containing path to the service account not found", serviceAccountEnvVarName);
      log.error(msg);
      return null;
    }

    File file = new File(serviceAccountPath);
    if (!file.exists()) {
      String msg = String.format("Service account file not found at [%s]", serviceAccountPath);
      log.error(msg);
      return null;
    }

    try {
      String serviceAccountFileContents = FileUtils.readFileToString(file, UTF_8);
      serviceAccountCache.put(serviceAccountEnvVarName, serviceAccountFileContents);
      return serviceAccountFileContents;
    } catch (Exception e) {
      String msg = String.format("Error reading service account from %s", serviceAccountPath);
      throw new InvalidInfraException(msg, e);
    }
  }

  private GcsUtils getGcsUtil() {
    return gcsUtil;
  }
}
