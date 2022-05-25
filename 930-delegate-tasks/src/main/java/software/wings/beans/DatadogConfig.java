/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;

import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.jersey.JsonViews;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.CVUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

@JsonTypeName("DATA_DOG")
@Data
@Builder
@ToString(exclude = {"apiKey", "applicationKey"})
@EqualsAndHashCode(callSuper = false)
public class DatadogConfig extends SettingValue implements EncryptableSetting {
  public static final String validationUrl = "metrics";
  public static final String LOG_API_PATH_SUFFIX = "logs-queries/list";

  @Attributes(title = "URL", required = true) @NotEmpty private String url;

  @Attributes(title = "API Key", required = true) @Encrypted(fieldName = "api_key") private char[] apiKey;

  @Attributes(title = "Application Key", required = true)
  @Encrypted(fieldName = "application_key")
  private char[] applicationKey;

  @SchemaIgnore @NotEmpty private String accountId;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedApiKey;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedApplicationKey;

  public DatadogConfig() {
    super(SettingVariableTypes.DATA_DOG.name());
  }

  private DatadogConfig(String url, char[] apiKey, char[] applicationKey, String accountId, String encryptedApiKey,
      String encryptedApplicationKey) {
    this();
    this.url = url;
    this.apiKey = apiKey;
    this.applicationKey = applicationKey;
    this.accountId = accountId;
    this.encryptedApiKey = encryptedApiKey;
    this.encryptedApplicationKey = encryptedApplicationKey;
  }

  private Map<String, String> optionsMap() {
    Map<String, String> paramsMap = new HashMap<>();
    // check for apiKey. If not empty populate the value else populate default value.
    paramsMap.put("api_key", apiKey != null ? new String(apiKey) : "${apiKey}");
    // check for applicationKey. If not empty populate the value else populate default value.
    paramsMap.put("application_key", applicationKey != null ? new String(applicationKey) : "${applicationKey}");

    return paramsMap;
  }
  private Map<String, String> optionsMapAPM() {
    Map<String, String> paramsMap = optionsMap();
    paramsMap.put("from", String.valueOf(System.currentTimeMillis() / TimeUnit.SECONDS.toMillis(1)));
    return paramsMap;
  }

  public Map<String, String> fetchLogOptionsMap() {
    Map<String, String> paramsMap = new HashMap<>();
    // check for apiKey. If not empty populate the value else populate default value.
    paramsMap.put("api_key", apiKey != null ? new String(apiKey) : "${apiKey}");
    // check for applicationKey. If not empty populate the value else populate default value.
    paramsMap.put("application_key", applicationKey != null ? new String(applicationKey) : "${applicationKey}");
    return paramsMap;
  }

  public Map<String, Object> fetchLogBodyMap(boolean is24x7) {
    Map<String, Object> body = new HashMap<>();
    if (is24x7) {
      body.put("query", "${query}");
    } else {
      body.put("query", "${hostname_field}:(${host}) ${query}");
    }
    Map<String, String> timeMap = new HashMap<>();
    timeMap.put("from", "${start_time}");
    timeMap.put("to", "${end_time}");
    body.put("time", timeMap);
    return body;
  }

  public APMValidateCollectorConfig createAPMValidateCollectorConfig() {
    return APMValidateCollectorConfig.builder()
        .baseUrl(url)
        .url(validationUrl)
        .options(optionsMapAPM())
        .headers(new HashMap<>())
        .build();
  }

  public APMValidateCollectorConfig createLogAPMValidateCollectorConfig() {
    return APMValidateCollectorConfig.builder()
        .baseUrl(url)
        .url(validationUrl)
        .options(optionsMap())
        .headers(new HashMap<>())
        .build();
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.VERIFICATION_PROVIDER.name();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Arrays.asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        CVUtils.appendPathToBaseUrl(url, validationUrl), maskingEvaluator));
  }
}
