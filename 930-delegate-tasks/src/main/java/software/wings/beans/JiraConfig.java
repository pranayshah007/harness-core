/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static software.wings.audit.ResourceType.COLLABORATION_PROVIDER;
import static software.wings.beans.CGConstants.ENCRYPTED_VALUE_STR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;

import software.wings.annotation.EncryptableSetting;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.setting.CollaborationProviderYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.CDC)
@JsonTypeName("JIRA")
@Data
@Builder
@ToString(exclude = {"password"})
@EqualsAndHashCode(callSuper = false)
public class JiraConfig extends SettingValue implements EncryptableSetting, ExecutionCapabilityDemander {
  public enum JiraSetupType { JIRA_CLOUD, JIRA_SERVER }

  private static final CharSequence JIRA_CLOUD_DOMAINNAME = ".atlassian.net";

  @Attributes(title = "Base URL", required = true) @NotEmpty private String baseUrl;

  @Attributes(title = "Username", required = true) @NotEmpty private String username;

  /**
   * Handles both password & OAuth(1.0) token.
   */
  @Attributes(title = "Password/API Token", required = true)
  @Encrypted(fieldName = "password/api_token")
  private char[] password;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;
  @SchemaIgnore @NotEmpty private String accountId;

  private List<String> delegateSelectors;

  public JiraConfig() {
    super(SettingVariableTypes.JIRA.name());
  }

  public JiraConfig(String baseUrl, String username, char[] password, String encryptedPassword, String accountId,
      List<String> delegateSelectors) {
    this();
    this.baseUrl = baseUrl;
    this.username = username;
    this.password = Arrays.copyOf(password, password.length);
    this.encryptedPassword = encryptedPassword;
    this.accountId = accountId;
    this.delegateSelectors = delegateSelectors;
  }

  private JiraSetupType getSetupType() {
    JiraSetupType setupType = null;
    if (StringUtils.isNotEmpty(baseUrl)) {
      setupType = baseUrl.contains(JIRA_CLOUD_DOMAINNAME) ? JiraSetupType.JIRA_CLOUD : JiraSetupType.JIRA_SERVER;
    }

    return setupType;
  }

  @Override
  public String fetchResourceCategory() {
    return COLLABORATION_PROVIDER.name();
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends CollaborationProviderYaml {
    private String baseUrl;
    private String username;
    private String password = ENCRYPTED_VALUE_STR;
    private List<String> delegateSelectors;

    @Builder
    public Yaml(String type, String harnessApiVersion, String baseUrl, String username, String password,
        List<String> delegateSelectors, UsageRestrictions.Yaml usageRestrictions) {
      super(type, harnessApiVersion, usageRestrictions);
      this.baseUrl = baseUrl;
      this.username = username;
      this.password = password;
      this.delegateSelectors = delegateSelectors;
    }
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.singletonList(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(baseUrl, maskingEvaluator));
  }
}
