/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.url;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.configuration.DeployMode;
import io.harness.ff.FeatureFlagService;

import software.wings.app.PortalConfig;
import software.wings.app.UrlConfiguration;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.graphql.datafetcher.AccountThreadLocal;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URL;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by mehulkasliwal on 2020-01-21.
 */
@Singleton
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@OwnedBy(PL)
public class SubdomainUrlHelper implements SubdomainUrlHelperIntfc {
  @Inject private AccountService accountService;
  @Inject private UrlConfiguration urlConfiguration;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private PortalConfig portalConfig;

  /**
   * Returns Portal URL
   * @param accountId
   * @return Base URl
   */
  @Override
  public String getPortalBaseUrl(String accountId) {
    // Set baseUrl = subDomainUrl only if subDomainUrl is not null, otherwise
    // set baseUrl equal to URL of portal
    String portalUrl = appendSeparatorToUrl(getPortalUrl(accountId));
    log.info("Returning {} from getPortalBaseUrl for account {}", portalUrl, accountId);
    return portalUrl;
  }

  /**
   * Returns Gateway public URL
   * @param accountId
   * @return Base URL for gateway
   */
  @Override
  public String getGatewayBaseUrl(String accountId) {
    log.info("Generating Gateway base URL for account {}", accountId);
    String portalUrl = appendSeparatorToUrl(getPortalUrl(accountId));
    String gatewayUrl = portalUrl + portalConfig.getGatewayPathPrefix();
    log.info("Returning {} from getGatewayBaseUrl for account {}", gatewayUrl, accountId);
    return gatewayUrl;
  }

  @Override
  public String getPortalBaseUrl(String accountId, String defaultBaseUrl) {
    Optional<String> subdomainUrl = getCustomSubdomainUrl(accountId);
    if (!subdomainUrl.isPresent()) {
      return defaultBaseUrl;
    }
    return appendSeparatorToUrl(subdomainUrl.get());
  }

  private String getPortalUrl(String accountId) {
    Optional<String> subdomainUrl = getCustomSubdomainUrl(accountId);
    return subdomainUrl.isPresent() ? subdomainUrl.get() : urlConfiguration.getPortalUrl().trim();
  }

  /**
   * Returns API URL
   * @param accountId
   * @return Base URL
   */
  @Override
  public String getApiBaseUrl(String accountId) {
    // Set baseUrl = subDomainUrl only if subDomainUrl is not null, otherwise
    // set baseUrl equal to API URL
    log.info("Generating API URL for account {}", accountId);
    String apiUrl = getApiUrlForAccount(accountId);
    log.info("Returning {} from getApiBaseUrl", apiUrl);
    return appendSeparatorToUrl(apiUrl);
  }

  /**
   * Returns an optional object containing subdomain URL of the account
   * @param accountId
   * @return
   */
  private Optional<String> getCustomSubdomainUrl(String accountId) {
    try {
      if (null == accountId) {
        accountId = getAccountIdFromThreadLocal();
      }
      if (null != accountId) {
        Account account = accountService.get(accountId);
        log.info("Returning subdomain URL {} for account {}", account.getSubdomainUrl(), accountId);
        return Optional.ofNullable(account.getSubdomainUrl());
      }
    } catch (Exception e) {
      log.info("Exception occurred at getCustomSubdomainUrl for account {}", accountId, e);
      return Optional.empty();
    }
    return Optional.empty();
  }

  private String getAccountIdFromThreadLocal() {
    String accountId = AccountThreadLocal.get();
    log.info("Got account id {} from AccountThreadLocal", accountId);

    if (null == accountId) {
      User user = UserThreadLocal.get();
      if (null != user && null != user.getUserRequestContext()) {
        accountId = user.getUserRequestContext().getAccountId();
        log.info("Got account id {} from UserThreadLocal", accountId);
      }
    }
    return accountId;
  }

  /**
   * Appends / at the end of URL
   * @param url
   * @return
   */
  private String appendSeparatorToUrl(String url) {
    if (url != null && !url.endsWith("/")) {
      url += "/";
    }
    return url;
  }

  /**
   * Removes / at the end of URL
   * @param url
   * @return
   */
  private String removeSeparatorFromUrl(String url) {
    if (url != null && url.endsWith("/")) {
      url = StringUtils.chop(url);
    }
    return url;
  }

  /**
   * Returns manager Url used for Delegate
   * @param request
   * @return
   */
  public String getManagerUrl(HttpServletRequest request, String accountId) {
    log.info("Generating manager URL for account {}", accountId);
    String apiUrl = getApiUrlForAccount(accountId);
    apiUrl = removeSeparatorFromUrl(apiUrl);
    log.info("Returning manager URL {} for account {}", apiUrl, accountId);
    return !StringUtils.isEmpty(apiUrl)
        ? apiUrl
        : request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
  }

  public String getDelegateMetadataUrl(String accountId, String managerHost, String deployMode) {
    log.info("Generating delegate metadata URL for account {}", accountId);
    if (StringUtils.isNotBlank(managerHost) && DeployMode.isOnPrem(deployMode)) {
      return managerHost + "/storage/wingsdelegates/delegateprod.txt";
    }
    Optional<String> subdomainUrl = getCustomSubdomainUrl(accountId);
    String delegateMetadataUrl = urlConfiguration.getDelegateMetadataUrl().trim();
    delegateMetadataUrl =
        overrideMetadataUrl(subdomainUrl.isPresent() ? subdomainUrl.get() : null, delegateMetadataUrl);
    log.info("Returning delegate metadata URL {} for account {}", delegateMetadataUrl, accountId);
    return delegateMetadataUrl;
  }

  public String getWatcherMetadataUrl(String accountId, String managerHost, String deployMode) {
    log.info("Generating watcher metadata URL for account {}", accountId);
    if (StringUtils.isNotBlank(managerHost) && DeployMode.isOnPrem(deployMode)) {
      return managerHost + "/storage/wingswatchers/watcherprod.txt";
    }
    Optional<String> subdomainUrl = getCustomSubdomainUrl(accountId);
    String watcherMetadataUrl = urlConfiguration.getWatcherMetadataUrl().trim();
    watcherMetadataUrl = overrideMetadataUrl(subdomainUrl.isPresent() ? subdomainUrl.get() : null, watcherMetadataUrl);
    log.info("Returning watcher metadata URL {} for account {}", watcherMetadataUrl, accountId);
    return watcherMetadataUrl;
  }

  /**
   * Replaces metadata URL according to vanity URL
   * @param subdomainUrl
   * @param metadataUrl
   * @return
   */
  private String overrideMetadataUrl(String subdomainUrl, String metadataUrl) {
    try {
      if (StringUtils.isNotEmpty(subdomainUrl) && StringUtils.isNotEmpty(metadataUrl)) {
        URL originalMetadataUrl = new URL(metadataUrl);
        URL customSubdomainUrl = new URL(subdomainUrl);
        URL modifiedMetadataUrl =
            new URL(customSubdomainUrl.getProtocol(), customSubdomainUrl.getHost(), originalMetadataUrl.getFile());
        log.info("Returning {} from replaceMetadataUrl", modifiedMetadataUrl.toString());
        return modifiedMetadataUrl.toString();
      }
    } catch (Exception e) {
      log.info("Exception occurred at replaceMetaDataUrl with metadataUrl {}", metadataUrl, e);
      return metadataUrl;
    }
    return metadataUrl;
  }

  private String getApiUrlForAccount(String accountId) {
    Optional<String> subdomainUrl = getCustomSubdomainUrl(accountId);
    String apiUrl = urlConfiguration.getApiUrl().trim();
    if (subdomainUrl.isPresent()) {
      String subdomainUrlValue = subdomainUrl.get();
      if (apiUrl.contains("gratis")) {
        apiUrl = subdomainUrlValue + "/gratis";
      } else {
        apiUrl = subdomainUrlValue;
      }
    }
    return apiUrl;
  }

  /**
   * Returns Portal URL according to the VANITY_URL feature flag
   * @param accountId
   * @return
   */
  public String getPortalBaseUrlWithoutSeparator(String accountId) {
    log.info("Getting download URL for account {}", accountId);
    String portalUrl = removeSeparatorFromUrl(getPortalUrl(accountId));
    log.info("Returning {} as download URL for account {}", portalUrl, accountId);
    return portalUrl;
  }

  @Override
  public String getVanityUrl(String accountId) {
    Optional<String> subdomainUrl = getCustomSubdomainUrl(accountId);
    return subdomainUrl.orElse(null);
  }
}
