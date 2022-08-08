/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static software.wings.settings.SettingVariableTypes.GCP;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.config.CCMConfig;
import io.harness.ccm.config.CloudCostAware;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;

import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.yaml.setting.CloudProviderYaml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by bzane on 2/28/17
 */
@JsonTypeName("GCP")
@FieldNameConstants(innerTypeName = "GcpConfigKeys")
@Data
@Builder
@ToString(exclude = {"serviceAccountKeyFileContent", "encryptedServiceAccountKeyFileContent"})
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._957_CG_BEANS)
public class GcpConfig extends SettingValue implements EncryptableSetting, CloudCostAware {
  @Encrypted(fieldName = "service_account_key_file") private char[] serviceAccountKeyFileContent;

  @SchemaIgnore @NotEmpty private String accountId;
  @JsonInclude(Include.NON_NULL) @SchemaIgnore private CCMConfig ccmConfig;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedServiceAccountKeyFileContent;

  private boolean useDelegate;
  private String delegateSelector;
  private boolean useDelegateSelectors;
  private List<String> delegateSelectors;
  private boolean skipValidation;

  public GcpConfig() {
    super(GCP.name());
  }

  public GcpConfig(char[] serviceAccountKeyFileContent, String accountId, CCMConfig ccmConfig,
      String encryptedServiceAccountKeyFileContent, boolean useDelegate, String delegateSelector,
      boolean useDelegateSelectors, List<String> delegateSelectors, boolean skipValidation) {
    this();
    this.serviceAccountKeyFileContent =
        serviceAccountKeyFileContent == null ? null : serviceAccountKeyFileContent.clone();
    this.accountId = accountId;
    this.ccmConfig = ccmConfig;
    this.encryptedServiceAccountKeyFileContent = encryptedServiceAccountKeyFileContent;
    this.delegateSelectors = delegateSelectors;
    this.useDelegateSelectors = useDelegateSelectors;
    this.skipValidation = skipValidation;
    this.delegateSelector = delegateSelector;
    this.useDelegate = useDelegate;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return emptyList();
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.CLOUD_PROVIDER.name();
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends CloudProviderYaml {
    private String serviceAccountKeyFileContent;
    private boolean useDelegate;
    private String delegateSelector;
    private boolean useDelegateSelectors;
    private List<String> delegateSelectors;
    private boolean skipValidation;

    @Builder
    public Yaml(String type, String harnessApiVersion, String serviceAccountKeyFileContent,
        UsageRestrictions.Yaml usageRestrictions, boolean useDelegate, String delegateSelector,
        boolean useDelegateSelectors, List<String> delegateSelectors, boolean skipValidation) {
      super(type, harnessApiVersion, usageRestrictions);
      this.serviceAccountKeyFileContent = serviceAccountKeyFileContent;
      this.delegateSelectors = delegateSelectors;
      this.useDelegateSelectors = useDelegateSelectors;
      this.skipValidation = skipValidation;
      this.delegateSelector = delegateSelector;
      this.useDelegate = useDelegate;
    }
  }
}
