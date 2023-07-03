/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.configuration;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.client.impl.EventPublisherConstants;

import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@ToString
@OwnedBy(DEL)
public class DelegateConfiguration {
  private String accountId;
  private String delegateName;
  @ToString.Exclude private String accountSecret;
  @ToString.Exclude private String delegateToken;
  private String managerUrl;
  private String verificationServiceUrl;
  private String cvNextGenUrl;
  private String watcherCheckLocation;
  private long heartbeatIntervalMs;
  private String localDiskPath;
  private boolean doUpgrade;
  private Integer maxCachedArtifacts;
  private boolean pollForTasks;
  private String description;

  private String managerTarget;
  private String managerAuthority;
  private String queueFilePath;

  private boolean useCdn;
  private String cdnUrl;

  private String kubectlPath;
  private String ocPath;
  private String kustomizePath;
  private String helmPath;
  private String helm3Path;
  private String cfCli6Path;
  private String cfCli7Path;

  private boolean grpcServiceEnabled;
  private Integer grpcServiceConnectorPort;

  private String logStreamingServiceBaseUrl;
  private boolean clientToolsDownloadDisabled;
  private boolean installClientToolsInBackground;
  private boolean dynamicHandlingOfRequestEnabled;

  private String clientCertificateFilePath;
  private String clientCertificateKeyFilePath;

  private boolean isImmutable;
  private boolean isLocalNgDelegate;

  /*
   * If true, the delegate will send the unmodified authority in grpc calls instead of a service specific authority.
   *
   * Note: This setting is used for delegates connecting via the delegate-gateway and can be removed after migration.
   */
  private boolean grpcAuthorityModificationDisabled;

  /*
   * If true, the delegate doesn't verify the certificate of the SAAS endpoint.
   *
   * Note: This setting is meant for development only.
   */
  private boolean trustAllCertificates;

  // TODO: This method will get removed once we rolled out new delegate.
  public String getDelegateToken() {
    if (StringUtils.isEmpty(delegateToken)) {
      // Return account secret only if delegate token is not available.
      return accountSecret;
    }
    return delegateToken;
  }

  public String getQueueFilePath() {
    return Optional.ofNullable(queueFilePath).orElse(EventPublisherConstants.DEFAULT_QUEUE_FILE_PATH);
  }
}
