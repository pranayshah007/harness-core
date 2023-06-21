/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.secret.ConfigSecret;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import java.util.List;
import lombok.Data;

/**
 * The Class PortalConfig.
 */
@Data
@OwnedBy(PL)
public class PortalConfig {
  @JsonProperty(defaultValue = "https://localhost:8000") private String url = "https://localhost:8000";
  private List<String> allowedOrigins = Lists.newArrayList();
  @JsonProperty(defaultValue = "") private String companyName = "";
  @JsonProperty private String zendeskBaseUrl;
  @JsonProperty private String cannyBaseUrl;
  @JsonProperty private String gatewayPathPrefix;
  @JsonProperty(defaultValue = "/register/verify") private String verificationUrl = "/register/verify";
  @JsonProperty(defaultValue = "/app/%s/overview") private String applicationOverviewUrlPattern = "/app/%s/overview";
  @JsonProperty(defaultValue = "/app/%s/env/%s/execution/%s/detail")
  private String executionUrlPattern = "/app/%s/env/%s/execution/%s/detail";
  @ConfigSecret private String jwtPasswordSecret;
  @ConfigSecret private String jwtExternalServiceSecret;
  @ConfigSecret private String jwtZendeskSecret;
  @ConfigSecret private String jwtCannySecret;
  @ConfigSecret private String jwtMultiAuthSecret;
  @ConfigSecret private String jwtSsoRedirectSecret;
  @ConfigSecret private String jwtAuthSecret;
  private String jwtMarketPlaceSecret;
  @ConfigSecret private String jwtIdentityServiceSecret;
  @ConfigSecret private String jwtDataHandlerSecret;
  @ConfigSecret private String jwtNextGenManagerSecret;
  private String delegateDockerImage;
  private int externalGraphQLRateLimitPerMinute = 500;
  private int customDashGraphQLRateLimitPerMinute = 1000;
  private Long authTokenExpiryInMillis = 24 * 60 * 60 * 1000L;
  private long optionalDelegateTaskRejectAtLimit;
  private long importantDelegateTaskRejectAtLimit;
  private long criticalDelegateTaskRejectAtLimit;

  /**
   * Gets allowed origins.
   * @return the allowed origins
   */
  @JsonProperty(defaultValue = "")
  public String getAllowedOrigins() {
    return Joiner.on(",").join(allowedOrigins);
  }

  /**
   * Sets allowed orgins.
   *
   * @param allowedOrigins
   */
  public void setAllowedOrigins(String allowedOrigins) {
    this.allowedOrigins = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(allowedOrigins);
  }

  public String getJwtMultiAuthSecret() {
    return jwtMultiAuthSecret;
  }

  public void setJwtMultiAuthSecret(String jwtMultiAuthSecret) {
    this.jwtMultiAuthSecret = jwtMultiAuthSecret;
  }
}
