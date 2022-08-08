/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import org.apache.commons.codec.binary.Base64;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by rsingh on 3/15/18.
 */
@Data
@JsonTypeName("PROMETHEUS")
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrometheusConfig extends SettingValue implements EncryptableSetting, ExecutionCapabilityDemander {
  public static final String VALIDATION_URL = "api/v1/query?query=up";
  @Attributes(title = "URL", required = true) private String url;
  @Attributes(title = "Username") private String username;
  @Attributes(title = "Password") @Encrypted(fieldName = "password") private char[] password;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;
  @SchemaIgnore @NotEmpty private String accountId;

  public PrometheusConfig() {
    super(SettingVariableTypes.PROMETHEUS.name());
  }

  public PrometheusConfig(String url, String accountId, char[] password, String username, String encryptedPassword) {
    this();
    this.url = url;
    this.accountId = accountId;
    this.username = username;
    this.encryptedPassword = encryptedPassword;
    this.password = password;
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
        .headers(generateHeaders())
        .options(new HashMap<>())
        .build();
  }

  public Map<String, String> generateHeaders() {
    HashMap<String, String> headersMap = new HashMap();

    if (isEmpty(username)) {
      return headersMap;
    }

    if (isNotEmpty(password)) {
      headersMap.put("Authorization",
          String.format("Basic %s",
              Base64.encodeBase64String(String.format("%s:%s", username, new String(password)).getBytes())));
    } else if (isNotEmpty(encryptedPassword)) {
      headersMap.put("Authorization", String.format("Basic encodeWithBase64(%s:${password})", username));
    }

    return headersMap;
  }

  public boolean usesBasicAuth() {
    return isNotEmpty(username) && (isNotEmpty(password) || isNotEmpty(encryptedPassword));
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class PrometheusYaml extends VerificationProviderYaml {
    private String prometheusUrl;
    private String username;
    private String password;

    @Builder
    public PrometheusYaml(String type, String harnessApiVersion, String prometheusUrl,
        UsageRestrictions.Yaml usageRestrictions, String username, String password) {
      super(type, harnessApiVersion, usageRestrictions);
      this.prometheusUrl = prometheusUrl;
      this.username = username;
      this.password = password;
    }
  }
}
