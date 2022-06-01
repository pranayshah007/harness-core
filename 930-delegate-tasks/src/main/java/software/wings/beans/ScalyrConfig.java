/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;

import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.beans.apm.Method;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.setting.VerificationProviderYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@JsonTypeName("SCALYR")
@Data
@Builder
@ToString(exclude = "apiToken")
@FieldNameConstants(innerTypeName = "ScalyrConfigKeys")
@EqualsAndHashCode(callSuper = false)
public class ScalyrConfig extends SettingValue implements EncryptableSetting, ExecutionCapabilityDemander {
  public static final String VALIDATION_URL = "query?queryType=log&maxCount=1";
  public static final String QUERY_URL = "query";
  @SchemaIgnore @NotEmpty private String accountId;
  @Attributes(title = "URL", required = true) private String url;
  @Attributes(title = "API Token", required = true) @Encrypted(fieldName = "api_token") private char[] apiToken;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedApiToken;

  public ScalyrConfig() {
    super(SettingVariableTypes.SCALYR.name());
  }

  private ScalyrConfig(String accountId, String url, char apiToken[], String encryptedApiToken) {
    this();
    this.accountId = accountId;
    this.url = url;
    this.apiToken = apiToken;
    this.encryptedApiToken = encryptedApiToken;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Arrays.asList(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(url, maskingEvaluator));
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.VERIFICATION_PROVIDER.name();
  }

  public APMValidateCollectorConfig createAPMValidateCollectorConfig() {
    return createAPMValidateCollectorConfig(VALIDATION_URL);
  }

  public APMValidateCollectorConfig createAPMValidateCollectorConfig(String urlToFetch) {
    return APMValidateCollectorConfig.builder()
        .baseUrl(url)
        .url(urlToFetch)
        .collectionMethod(Method.GET)
        .headers(new HashMap<>())
        .options(Collections.singletonMap("token", String.valueOf(apiToken)))
        .build();
  }

  public Map<String, Object> fetchLogBodyMap(boolean isServiceLevel) {
    Map<String, Object> body = new HashMap<>();
    body.put("queryType", "log");
    body.put("token", "${apiToken}");
    body.put("startTime", "${start_time}");
    body.put("endTime", "${end_time}");
    body.put("filter", "${query}");
    body.put("maxCount", isServiceLevel ? "10000" : "1000");
    return body;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class ScalyrYaml extends VerificationProviderYaml {
    private String scalyrUrl;
    private String apiToken;

    @Builder
    public ScalyrYaml(String type, String harnessApiVersion, String scalyrUrl, String apiToken,
        UsageRestrictions.Yaml usageRestrictions) {
      super(type, harnessApiVersion, usageRestrictions);
      this.scalyrUrl = scalyrUrl;
      this.apiToken = apiToken;
    }
  }
}
