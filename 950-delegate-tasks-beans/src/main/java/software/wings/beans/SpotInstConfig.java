/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;
import io.harness.spotinst.model.SpotInstConstants;

import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.setting.CloudProviderYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

@JsonTypeName("SPOT_INST")
@Data
@Builder
@ToString(exclude = "spotInstToken")
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public class SpotInstConfig extends SettingValue implements EncryptableSetting, ExecutionCapabilityDemander {
  @NotEmpty @SchemaIgnore private String accountId;
  @Encrypted(fieldName = "spot_instance_token") private char[] spotInstToken;
  private String spotInstAccountId;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedSpotInstToken;

  public SpotInstConfig() {
    super(SettingVariableTypes.SPOT_INST.name());
  }

  public SpotInstConfig(
      String accountId, char[] spotInstToken, String spotInstAccountId, String encryptedSpotInstToken) {
    this();
    this.accountId = accountId;
    this.spotInstToken = spotInstToken == null ? null : spotInstToken.clone();
    this.spotInstAccountId = spotInstAccountId;
    this.encryptedSpotInstToken = encryptedSpotInstToken;
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.CLOUD_PROVIDER.name();
  }

  // It is expected to fail with 401, unauthorized access
  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Arrays.asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        SpotInstConstants.spotInstBaseUrl, maskingEvaluator));
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends CloudProviderYaml {
    private String spotInstToken;
    private String spotInstAccountId;

    @Builder
    public Yaml(String type, String harnessApiVersion, UsageRestrictions.Yaml usageRestrictions, String spotInstToken,
        String spotInstAccountId) {
      super(type, harnessApiVersion, usageRestrictions);
      this.spotInstToken = spotInstToken;
      this.spotInstAccountId = spotInstAccountId;
    }
  }
}
