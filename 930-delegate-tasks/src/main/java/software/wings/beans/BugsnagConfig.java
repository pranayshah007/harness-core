/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.data.structure.EmptyPredicate;
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
import software.wings.utils.CVUtils;
import software.wings.yaml.setting.VerificationProviderYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

@JsonTypeName("BUG_SNAG")
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
public class BugsnagConfig extends SettingValue implements EncryptableSetting {
  public static final String validationUrl = "user/organizations";

  @Attributes(title = "URL", required = true) @NotEmpty private String url;

  @Attributes(title = "Auth Token", required = true)
  @Encrypted(fieldName = "auth_token")
  @ToString.Exclude
  private char[] authToken;

  @SchemaIgnore @NotEmpty private String accountId;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore @ToString.Exclude private String encryptedAuthToken;

  public BugsnagConfig() {
    super(SettingVariableTypes.BUG_SNAG.name());
  }

  private BugsnagConfig(String url, char[] authToken, String accountId, String encryptedAuthToken) {
    this();
    this.url = url;
    this.authToken = authToken;
    this.accountId = accountId;
    this.encryptedAuthToken = encryptedAuthToken;
  }

  public Map<String, String> optionsMap() {
    return new HashMap<>();
  }

  public Map<String, String> headersMap() {
    Map<String, String> headerMap = new HashMap<>();
    if (!EmptyPredicate.isEmpty(authToken)) {
      headerMap.put("Authorization", "token " + new String(authToken));
    } else {
      headerMap.put("Authorization", "token ${authToken}");
    }
    return headerMap;
  }

  public APMValidateCollectorConfig createAPMValidateCollectorConfig() {
    return APMValidateCollectorConfig.builder()
        .baseUrl(url)
        .url(validationUrl)
        .options(optionsMap())
        .headers(headersMap())
        .build();
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.VERIFICATION_PROVIDER.name();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Arrays.asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        CVUtils.appendPathToBaseUrl(getUrl(), validationUrl), maskingEvaluator));
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends VerificationProviderYaml {
    private String url;
    private String authToken;

    @Builder
    public Yaml(
        String type, String harnessApiVersion, UsageRestrictions.Yaml usageRestrictions, String url, String authToken) {
      super(type, harnessApiVersion, usageRestrictions);
      this.url = url;
      this.authToken = authToken;
    }
  }
}
