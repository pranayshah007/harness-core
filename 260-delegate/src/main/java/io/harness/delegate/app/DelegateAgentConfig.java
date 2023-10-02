/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Singleton;
import io.dropwizard.Configuration;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Singleton
public class DelegateAgentConfig extends Configuration {
  @JsonProperty private final String accountId;
  @JsonProperty private final String delegateToken;
  @JsonProperty private final String managerUrl;
  @JsonProperty private final String verificationServiceUrl;
  @JsonProperty private final String cvNextGenUrl;
  @JsonProperty private final long heartbeatIntervalMs;
  @JsonProperty private final String localDiskPath;
  @JsonProperty private final boolean doUpgrade;
  @JsonProperty private final boolean pollForTasks;
  @JsonProperty private final String description;
  @JsonProperty private final String managerTarget;
  @JsonProperty private final String managerAuthority;
  @JsonProperty private final String queueFilePath;
  @JsonProperty private final boolean grpcServiceEnabled;
  @JsonProperty private final int grpcServiceConnectorPort;
  @JsonProperty private final String logStreamingServiceBaseUrl;
  @JsonProperty private final boolean clientToolsDownloadDisabled;
  @JsonProperty private final boolean installClientToolsInBackground;
  @JsonProperty private final int maxCachedArtifacts;
  @JsonProperty private final String clientCertificateFilePath;
  @JsonProperty private final String clientCertificateKeyFilePath;
  @JsonProperty private final boolean trustAllCertificates;
  @JsonProperty private final boolean grpcAuthorityModificationDisabled;
  @JsonProperty private final String delegateName;
}
