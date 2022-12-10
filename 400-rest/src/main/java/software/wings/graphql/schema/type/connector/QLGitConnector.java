/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type.connector;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.GitConfig.ProviderType;
import software.wings.beans.GitConfig.UrlType;
import software.wings.graphql.schema.type.QLCustomCommitDetails;
import software.wings.graphql.schema.type.QLUser;
import software.wings.graphql.schema.type.secrets.QLUsageScope;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.Scope;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Scope(ResourceType.SETTING)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLGitConnector implements QLConnector {
  private String id;
  private String name;
  private Long createdAt;
  private QLUser createdBy;

  private String userName;
  private String URL;
  private UrlType urlType;
  private String branch;
  private String passwordSecretId;
  private String sshSettingId;
  private String webhookUrl;
  private Boolean generateWebhookUrl;
  private QLCustomCommitDetails customCommitDetails;
  private QLUsageScope usageScope;
  private List<String> delegateSelectors;
  private Boolean disableUserGitConfig;
  private ProviderType providerType;

  public static class QLGitConnectorBuilder implements QLConnectorBuilder {}
}
