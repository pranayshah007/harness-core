/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;

import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.setting.CloudProviderYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

@JsonTypeName("RANCHER")
@Data
@Builder
@ToString(exclude = "bearerToken")
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
@TargetModule(HarnessModule._970_API_SERVICES_BEANS)
public class RancherConfig extends SettingValue implements EncryptableSetting {
  @Attributes(title = "Rancher URL") private String rancherUrl;
  @Attributes(title = "Bearer Token") @Encrypted(fieldName = "bearer_token") private char[] bearerToken;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedBearerToken;
  @SchemaIgnore @NotEmpty private String accountId; // internal

  public RancherConfig() {
    super(SettingVariableTypes.RANCHER.name());
  }

  public RancherConfig(String rancherUrl, char[] bearerToken, String encryptedBearerToken, String accountId) {
    this();
    this.rancherUrl = rancherUrl;
    this.bearerToken = bearerToken == null ? null : bearerToken.clone();
    this.encryptedBearerToken = encryptedBearerToken;
    this.accountId = accountId;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.singletonList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        rancherUrl, maskingEvaluator));
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.CLOUD_PROVIDER.name();
  }

  @Override
  public List<String> fetchRelevantEncryptedSecrets() {
    return Collections.singletonList(encryptedBearerToken);
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends CloudProviderYaml {
    private String rancherUrl;
    private String bearerToken;

    @Builder
    public Yaml(String type, String harnessApiVersion, String rancherUrl, String bearerToken,
        UsageRestrictions.Yaml usageRestrictions) {
      super(type, harnessApiVersion, usageRestrictions);
      this.rancherUrl = rancherUrl;
      this.bearerToken = bearerToken;
    }
  }
}
