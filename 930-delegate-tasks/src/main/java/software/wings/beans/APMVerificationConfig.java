/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.HttpCapabilityDetailsLevel.QUERY;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.beans.apm.Method;
import software.wings.security.UsageRestrictions;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.CVUtils;
import software.wings.yaml.setting.VerificationProviderYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Transient;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@JsonTypeName("APM_VERIFICATION")
@Data
@ToString(exclude = {"headersList", "optionsList"})
@EqualsAndHashCode(callSuper = false)
@Slf4j
public class APMVerificationConfig extends SettingValue implements EncryptableSetting, ExecutionCapabilityDemander {
  @Transient @SchemaIgnore public static final String MASKED_STRING = "*****";
  @Transient @SchemaIgnore private static final String SECRET_REGEX = "\\$\\{secretRef:([^,]*),([^}]*)}";

  @Attributes(title = "Base Url") private String url;

  @NotEmpty @Attributes(title = "Validation Url", required = true) private String validationUrl;

  private String validationBody;

  private Method validationMethod;

  @SchemaIgnore private boolean logVerification;

  @SchemaIgnore @NotEmpty private String accountId;

  private List<KeyValues> headersList;
  private List<KeyValues> optionsList;
  private List<KeyValues> additionalEncryptedFields;
  @SchemaIgnore private Map<String, String> secretIdsToFieldNameMap = new HashMap<>();

  /**LogMLAnalysisRecord.java
   * Instantiates a new config.
   */
  public APMVerificationConfig() {
    super(SettingVariableTypes.APM_VERIFICATION.name());
  }

  public APMVerificationConfig(SettingVariableTypes type) {
    super(type.name());
  }

  public String getValidationUrl() {
    if (isEmpty(validationUrl)) {
      return validationUrl;
    }
    try {
      return validationUrl.replaceAll("`", URLEncoder.encode("`", "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new WingsException("Unsupported encoding exception while encoding backticks in " + validationUrl);
    }
  }

  public Map<String, String> collectionHeaders() {
    Map<String, String> headers = new HashMap<>();
    if (!isEmpty(headersList)) {
      for (KeyValues keyValue : headersList) {
        if (keyValue.encrypted) {
          headers.put(keyValue.getKey(), "${" + keyValue.getKey() + "}");
        } else {
          headers.put(keyValue.getKey(), keyValue.getValue());
        }
      }
    }
    return headers;
  }

  public Map<String, String> collectionParams() {
    Map<String, String> params = new HashMap<>();
    if (!isEmpty(optionsList)) {
      for (KeyValues keyValue : optionsList) {
        if (keyValue.encrypted) {
          params.put(keyValue.getKey(), "${" + keyValue.getKey() + "}");
        } else {
          params.put(keyValue.getKey(), keyValue.getValue());
        }
      }
    }
    return params;
  }

  public APMValidateCollectorConfig createAPMValidateCollectorConfig(
      SecretManager secretManager, EncryptionService encryptionService) {
    try {
      Map<String, String> headers = new HashMap<>();
      if (!isEmpty(headersList)) {
        for (KeyValues keyValue : headersList) {
          if (keyValue.encrypted) {
            final Optional<EncryptedDataDetail> encryptedDataDetail =
                secretManager.encryptedDataDetails(accountId, keyValue.key, keyValue.value, null);
            if (!encryptedDataDetail.isPresent()) {
              throw new IllegalStateException("could not find record " + keyValue.value + " for " + keyValue.key);
            }
            headers.put(
                keyValue.getKey(), new String(encryptionService.getDecryptedValue(encryptedDataDetail.get(), false)));
          } else {
            headers.put(keyValue.getKey(), keyValue.getValue());
          }
        }
      }

      Map<String, String> options = new HashMap<>();
      if (!isEmpty(optionsList)) {
        for (KeyValues keyValue : optionsList) {
          if (keyValue.encrypted) {
            final Optional<EncryptedDataDetail> encryptedDataDetail =
                secretManager.encryptedDataDetails(accountId, keyValue.key, keyValue.value, null);
            if (!encryptedDataDetail.isPresent()) {
              throw new IllegalStateException("could not find record " + keyValue.value + " for " + keyValue.key);
            }
            options.put(
                keyValue.getKey(), new String(encryptionService.getDecryptedValue(encryptedDataDetail.get(), false)));
          } else {
            options.put(keyValue.getKey(), keyValue.getValue());
          }
        }
      }

      List<KeyValues> bodySecrets = getSecretNameIdKeyValueList(validationBody);
      List<KeyValues> urlSecrets = getSecretNameIdKeyValueList(validationUrl);

      return APMValidateCollectorConfig.builder()
          .baseUrl(url)
          .url(resolveSecretNameInUrlOrBody(validationUrl))
          .body(resolveSecretNameInUrlOrBody(validationBody))
          .collectionMethod(validationMethod)
          .headers(headers)
          .options(options)
          .encryptedDataDetails(encryptedDataDetails(secretManager, Arrays.asList(bodySecrets, urlSecrets)))
          .build();
    } catch (Exception ex) {
      throw new VerificationOperationException(ErrorCode.APM_CONFIGURATION_ERROR, "Unable to validate connector ", ex);
    }
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.VERIFICATION_PROVIDER.name();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Arrays.asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        CVUtils.appendPathToBaseUrl(getUrl(), getValidationUrl()), QUERY, maskingEvaluator));
  }

  @Override
  public List<String> fetchRelevantEncryptedSecrets() {
    List<String> rv = new ArrayList<>();
    if (isNotEmpty(headersList)) {
      headersList.forEach(keyValues -> rv.add(keyValues.value));
    }

    if (isNotEmpty(optionsList)) {
      optionsList.forEach(keyValues -> rv.add(keyValues.value));
    }

    List<KeyValues> bodySecrets = getSecretNameIdKeyValueList(validationBody);
    bodySecrets.forEach(keyValues -> rv.add(keyValues.encryptedValue));
    List<KeyValues> urlSecrets = getSecretNameIdKeyValueList(validationUrl);
    urlSecrets.forEach(keyValues -> rv.add(keyValues.encryptedValue));
    return rv;
  }

  @Data
  @Builder
  public static class KeyValues {
    private String key;
    private String value;
    private boolean encrypted;
    private String encryptedValue;
  }

  public List<EncryptedDataDetail> encryptedDataDetails(
      SecretManager secretManager, List<List<KeyValues>> keyValueList) {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    if (isNotEmpty(keyValueList)) {
      keyValueList.forEach(keyValue -> {
        if (isNotEmpty(keyValue)) {
          encryptedDataDetails.addAll(
              keyValue.stream()
                  .filter(entry -> entry.encrypted)
                  .map(entry
                      -> secretManager.encryptedDataDetails(accountId, entry.key, entry.encryptedValue, null)
                             .<WingsException>orElseThrow(
                                 ()
                                     -> new VerificationOperationException(
                                         ErrorCode.APM_CONFIGURATION_ERROR, "Unable to decrypt field " + entry.key)))
                  .collect(Collectors.toList()));
        }
      });
    }
    return encryptedDataDetails.size() > 0 ? encryptedDataDetails : null;
  }
  public List<EncryptedDataDetail> encryptedDataDetails(SecretManager secretManager) {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    if (headersList != null) {
      encryptedDataDetails.addAll(
          headersList.stream()
              .filter(entry -> entry.encrypted)
              .map(entry
                  -> secretManager.encryptedDataDetails(accountId, entry.key, entry.encryptedValue, null)
                         .<WingsException>orElseThrow(
                             ()
                                 -> new VerificationOperationException(
                                     ErrorCode.APM_CONFIGURATION_ERROR, "Unable to decrypt field " + entry.key)))
              .collect(Collectors.toList()));
    }
    if (optionsList != null) {
      encryptedDataDetails.addAll(
          optionsList.stream()
              .filter(entry -> entry.encrypted)
              .map(entry
                  -> secretManager.encryptedDataDetails(accountId, entry.key, entry.encryptedValue, null)
                         .<WingsException>orElseThrow(
                             ()
                                 -> new VerificationOperationException(
                                     ErrorCode.APM_CONFIGURATION_ERROR, "Unable to decrypt field " + entry.key)))
              .collect(Collectors.toList()));
    }
    return encryptedDataDetails.size() > 0 ? encryptedDataDetails : null;
  }

  public void encryptFields() {
    secretIdsToFieldNameMap.clear();
    if (headersList != null) {
      headersList.stream()
          .filter(header -> header.encrypted)
          .filter(header -> !header.value.equals(MASKED_STRING))
          .forEach(header -> {
            header.encryptedValue = header.value;
            secretIdsToFieldNameMap.put(header.value, "header." + header.key);
          });
    }

    if (optionsList != null) {
      optionsList.stream()
          .filter(option -> option.encrypted)
          .filter(option -> !option.value.equals(MASKED_STRING))
          .forEach(option -> {
            option.encryptedValue = option.value;
            secretIdsToFieldNameMap.put(option.value, "option." + option.key);
          });
    }

    List<KeyValues> bodySecrets = getSecretNameIdKeyValueList(validationBody);
    bodySecrets.forEach(keyValues -> secretIdsToFieldNameMap.put(keyValues.encryptedValue, keyValues.key));
    List<KeyValues> urlSecrets = getSecretNameIdKeyValueList(validationUrl);
    urlSecrets.forEach(keyValues -> secretIdsToFieldNameMap.put(keyValues.encryptedValue, keyValues.key));
  }

  public String resolveSecretNameInUrlOrBody(String url) {
    if (isEmpty(url)) {
      return url;
    }
    Pattern batchPattern = Pattern.compile(SECRET_REGEX);
    Matcher matcher = batchPattern.matcher(url);
    while (matcher.find()) {
      String fullMatch = matcher.group();
      String name = matcher.group(1);
      url = url.replace(fullMatch, "${" + name + "}");
    }
    return url;
  }

  public static List<KeyValues> getSecretNameIdKeyValueList(String url) {
    List<KeyValues> keyValuesList = new ArrayList<>();
    if (!isEmpty(url)) {
      Pattern secretPattern = Pattern.compile(SECRET_REGEX);
      Matcher matcher = secretPattern.matcher(url);
      while (matcher.find()) {
        String name = matcher.group(1);
        String id = matcher.group(2);
        keyValuesList.add(
            KeyValues.builder().encrypted(true).encryptedValue(id).key(name).value(MASKED_STRING).build());
      }
    }
    return keyValuesList;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends VerificationProviderYaml {
    private String url;
    private String validationUrl;
    private String validationBody;
    private Method validationMethod;
    private boolean logVerification;
    private List<KeyValues> headersList;
    private List<KeyValues> optionsList;
    private List<KeyValues> additionalEncryptedFields;

    @Builder
    public Yaml(String type, String harnessApiVersion, UsageRestrictions.Yaml usageRestrictions, String url,
        String validationUrl, String validationBody, Method validationMethod, boolean logVerification,
        List<KeyValues> headersList, List<KeyValues> optionsList, List<KeyValues> additionalEncryptedFields) {
      super(type, harnessApiVersion, usageRestrictions);
      this.url = url;
      this.validationUrl = validationUrl;
      this.validationBody = validationBody;
      this.validationMethod = validationMethod;
      this.logVerification = logVerification;
      this.headersList = headersList;
      this.optionsList = optionsList;
      this.additionalEncryptedFields = additionalEncryptedFields;
    }
  }
}
