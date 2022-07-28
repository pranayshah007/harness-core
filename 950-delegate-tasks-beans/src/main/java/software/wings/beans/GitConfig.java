/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.shell.AuthenticationScheme.HTTP_PASSWORD;

import static software.wings.audit.ResourceType.SOURCE_REPO_PROVIDER;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.validator.Trimmed;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;
import io.harness.git.model.GitRepositoryType;
import io.harness.shell.AuthenticationScheme;

import software.wings.annotation.EncryptableSetting;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.setting.SourceRepoProviderYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Transient;

@JsonTypeName("GIT")
@Data
@ToString(exclude = {"password", "sshSettingAttribute", "encryptedPassword"})
@EqualsAndHashCode(callSuper = false, exclude = {"sshSettingAttribute"})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
@TargetModule(_957_CG_BEANS)
public class GitConfig extends SettingValue implements EncryptableSetting {
  public static final String HARNESS_IO_KEY_ = "Harness.io";
  public static final String HARNESS_SUPPORT_EMAIL_KEY = "support@harness.io";
  public static final String GIT_USER = "git";

  @Attributes(title = "Username", required = true) private String username;
  @Attributes(title = "Password", required = true) @Encrypted(fieldName = "password") private char[] password;
  @NotEmpty @Trimmed @Attributes(title = "Git Repo Url", required = true) private String repoUrl;
  @Default private UrlType urlType = UrlType.REPO;
  @Attributes(title = "Git Branch", required = true) private String branch;
  @SchemaIgnore private String reference;

  @SchemaIgnore @NotEmpty private String accountId;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;
  private String sshSettingId;
  @SchemaIgnore @Transient private SettingAttribute sshSettingAttribute;
  @SchemaIgnore @Transient @Trimmed @Nullable private String repoName;
  private boolean keyAuth;
  @Default private AuthenticationScheme authenticationScheme = HTTP_PASSWORD;
  @Attributes(title = "Description") private String description;
  private String webhookToken;
  @SchemaIgnore @Transient private GitRepositoryType gitRepoType;
  @Transient private boolean generateWebhookUrl;

  @Trimmed private String authorName;
  @Trimmed @Email private String authorEmailId;
  @Trimmed private String commitMessage;
  private List<String> delegateSelectors;
  @Default private ProviderType providerType = ProviderType.GIT;

  @Builder
  public GitConfig(String username, char[] password, String repoUrl, String branch, String accountId,
      String encryptedPassword, String sshSettingId, SettingAttribute sshSettingAttribute, boolean keyAuth,
      AuthenticationScheme authenticationScheme, String description, String webhookToken, GitRepositoryType gitRepoType,
      boolean generateWebhookUrl, String authorName, String authorEmailId, String commitMessage, UrlType urlType,
      String repoName, String reference, List<String> delegateSelectors, ProviderType providerType) {
    super(SettingVariableTypes.GIT.name());
    this.username = username;
    this.password = password == null ? null : password.clone();
    this.repoUrl = repoUrl;
    this.branch = branch;
    this.accountId = accountId;
    this.encryptedPassword = encryptedPassword;
    this.sshSettingId = sshSettingId;
    this.sshSettingAttribute = sshSettingAttribute;
    this.keyAuth = keyAuth;
    this.authenticationScheme = authenticationScheme;
    this.description = description;
    this.webhookToken = webhookToken;
    this.gitRepoType = gitRepoType;
    this.generateWebhookUrl = generateWebhookUrl;
    this.authorName = authorName;
    this.authorEmailId = authorEmailId;
    this.commitMessage = commitMessage;
    this.urlType = urlType;
    this.repoName = repoName;
    this.reference = reference;
    this.delegateSelectors = delegateSelectors;
    this.providerType = providerType;
  }

  @Override
  public String fetchResourceCategory() {
    return SOURCE_REPO_PROVIDER.name();
  }

  @Override
  public List<String> fetchRelevantEncryptedSecrets() {
    if (keyAuth) {
      if (isBlank(sshSettingId)) {
        log.error("Key auth with empty ssh setting id");
      }

      // TODO(gpahal): Once ssh and winrm are moved to secrets, we can change this also.
      return Collections.emptyList();
    } else {
      return Collections.singletonList(encryptedPassword);
    }
  }

  public enum UrlType { REPO, ACCOUNT }

  public enum ProviderType { GITHUB, GITLAB, GIT, BITBUCKET }

  /**
   * Instantiates a new setting value.
   */
  public GitConfig() {
    super(SettingVariableTypes.GIT.name());
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();

    if (isNotEmpty(delegateSelectors)) {
      executionCapabilities.add(SelectorCapability.builder().selectors(new HashSet<>(delegateSelectors)).build());
    }

    return executionCapabilities;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends SourceRepoProviderYaml {
    private String branch;
    private String reference;
    private boolean keyAuth;
    private String sshKeyName;
    private String description;
    private String authorName;
    private String authorEmailId;
    private String commitMessage;
    private UrlType urlType;
    private List<String> delegateSelectors;
    private ProviderType providerType;

    @Builder
    public Yaml(String type, String harnessApiVersion, String url, String username, String password, String branch,
        String reference, UsageRestrictions.Yaml usageRestrictions, boolean keyAuth, String sshKeyName,
        String description, String authorName, String authorEmailId, String commitMessage, UrlType urlType,
        List<String> delegateSelectors, ProviderType providerType) {
      super(type, harnessApiVersion, url, username, password, usageRestrictions);
      this.branch = branch;
      this.reference = reference;
      this.keyAuth = keyAuth;
      this.sshKeyName = sshKeyName;
      this.description = description;
      this.authorName = authorName;
      this.authorEmailId = authorEmailId;
      this.commitMessage = commitMessage;
      this.urlType = urlType;
      this.delegateSelectors = delegateSelectors;
      this.providerType = providerType;
    }
  }
}
