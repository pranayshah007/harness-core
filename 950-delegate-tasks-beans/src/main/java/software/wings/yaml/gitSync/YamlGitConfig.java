/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.yaml.gitSync;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;

import software.wings.beans.EntityType;
import software.wings.beans.GitConfig;
import software.wings.beans.dto.SettingAttribute;
import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.SchemaIgnore;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.DX)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
public class YamlGitConfig {
  public static final String ENTITY_ID_KEY = "entityId";
  public static final String ENTITY_TYPE_KEY = "entityType";
  public static final String WEBHOOK_TOKEN_KEY = "webhookToken";
  public static final String GIT_CONNECTOR_ID_KEY = "gitConnectorId";
  public static final String BRANCH_NAME_KEY = "branchName";
  public static final String REPOSITORY_NAME_KEY = "repositoryName";
  public static final String SYNC_MODE_KEY = "syncMode";

  private String uuid;
  private long createdAt;
  private EmbeddedUser createdBy;
  private long lastUpdatedAt;
  private EmbeddedUser lastUpdatedBy;
  private String appId;

  private String url;
  @NotEmpty private String branchName;
  @Nullable private String repositoryName;
  private String username;

  @JsonView(JsonViews.Internal.class) private char[] password;
  private String sshSettingId;
  private boolean keyAuth;
  @NotEmpty private String gitConnectorId;

  @JsonIgnore @SchemaIgnore private String encryptedPassword;

  private SyncMode syncMode;
  private boolean enabled;
  private String webhookToken;

  @SchemaIgnore @NotEmpty private String accountId;

  @NotEmpty private String entityId;
  @NotNull private EntityType entityType;
  private String entityName;
  private long gitPollingIterator;

  public enum SyncMode { GIT_TO_HARNESS, HARNESS_TO_GIT, BOTH, NONE }

  public enum Type {
    SETUP,
    APP,
    SERVICE,
    SERVICE_COMMAND,
    ENVIRONMENT,
    SETTING,
    WORKFLOW,
    PIPELINE,
    TRIGGER,
    FOLDER,
    ARTIFACT_STREAM
  }

  @JsonIgnore
  @SchemaIgnore
  public GitConfig getGitConfig(SettingAttribute sshSettingAttribute) {
    return GitConfig.builder()
        .accountId(this.accountId)
        .repoUrl(this.url)
        .username(this.username)
        .password(this.password)
        .sshSettingAttribute(sshSettingAttribute)
        .sshSettingId(this.sshSettingId)
        .keyAuth(this.keyAuth)
        .encryptedPassword(this.encryptedPassword)
        .branch(this.branchName.trim())
        .build();
  }
}
