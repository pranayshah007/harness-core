/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.settings.azureartifacts;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;

import software.wings.audit.ResourceType;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.setting.AzureArtifactsYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
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

@OwnedBy(CDC)
@JsonTypeName("AZURE_ARTIFACTS_PAT")
@Data
@Builder
@ToString(exclude = {"pat"})
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._957_CG_BEANS)
public class AzureArtifactsPATConfig extends SettingValue implements AzureArtifactsConfig {
  @SchemaIgnore @NotEmpty private String accountId;

  @NotEmpty private String azureDevopsUrl;
  @Encrypted(fieldName = "pat") private char[] pat;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPat;

  private AzureArtifactsPATConfig() {
    super(SettingVariableTypes.AZURE_ARTIFACTS_PAT.name());
  }

  public AzureArtifactsPATConfig(String accountId, String azureDevopsUrl, final char[] pat, String encryptedPat) {
    this();
    this.accountId = accountId;
    this.azureDevopsUrl = azureDevopsUrl;
    this.pat = (pat != null) ? Arrays.copyOf(pat, pat.length) : null;
    this.encryptedPat = encryptedPat;
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.ARTIFACT_SERVER.name();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.singletonList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        azureDevopsUrl, maskingEvaluator));
  }

  @Override
  public boolean shouldDeleteArtifact(SettingValue prev) {
    if (!(prev instanceof AzureArtifactsPATConfig)) {
      return true;
    }
    AzureArtifactsPATConfig prevConfig = (AzureArtifactsPATConfig) prev;
    return !StringUtils.equals(prevConfig.getAzureDevopsUrl(), azureDevopsUrl);
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends AzureArtifactsYaml {
    private String azureDevopsUrl;
    private String pat;

    @Builder
    public Yaml(String type, String harnessApiVersion, String azureDevopsUrl, String pat,
        UsageRestrictions.Yaml usageRestrictions) {
      super(type, harnessApiVersion, usageRestrictions);
      this.azureDevopsUrl = azureDevopsUrl;
      this.pat = pat;
    }
  }
}
