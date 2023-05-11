/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.delegate.beans.connector.cv.Connector;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;

import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.yaml.setting.VerificationProviderYaml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.nio.charset.StandardCharsets;
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
import org.apache.commons.codec.binary.Base64;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 8/4/16.
 */
@JsonTypeName("APP_DYNAMICS")
@Data
@Builder
@ToString(exclude = "password")
@EqualsAndHashCode(callSuper = false)
public class AppDynamicsConfig
    extends SettingValue implements EncryptableSetting, ExecutionCapabilityDemander, Connector {
  @Attributes(title = "User Name", required = true) @NotEmpty private String username;
  @Attributes(title = "Account Name", required = true) @NotEmpty private String accountname;
  @Attributes(title = "Password", required = true) @Encrypted(fieldName = "password") private char[] password;
  @Attributes(title = "Controller URL", required = true) @NotEmpty private String controllerUrl;
  @SchemaIgnore @NotEmpty private String accountId;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;

  /**
   * Instantiates a new App dynamics config.
   */
  public AppDynamicsConfig() {
    super(DelegateStateType.APP_DYNAMICS.name());
  }

  private AppDynamicsConfig(String username, String accountname, char[] password, String controllerUrl,
      String accountId, String encryptedPassword) {
    this();
    this.username = username;
    this.accountname = accountname;
    this.password = password;
    this.controllerUrl = controllerUrl;
    this.accountId = accountId;
    this.encryptedPassword = encryptedPassword;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Arrays.asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        controllerUrl, maskingEvaluator));
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.VERIFICATION_PROVIDER.name();
  }

  @Override
  @JsonIgnore
  public String getBaseUrl() {
    if (controllerUrl.endsWith("/")) {
      return controllerUrl;
    }
    return controllerUrl + "/";
  }

  @Override
  @JsonIgnore
  public Map<String, String> collectionHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", getHeaderWithCredentials());
    return headers;
  }

  @Override
  @JsonIgnore
  public Map<String, String> collectionParams() {
    return Collections.emptyMap();
  }

  private String getHeaderWithCredentials() {
    return "Basic "
        + Base64.encodeBase64String(
            String.format("%s@%s:%s", getUsername(), getAccountname(), new String(getPassword()))
                .getBytes(StandardCharsets.UTF_8));
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends VerificationProviderYaml {
    private String username;
    private String password;
    private String accountName;
    private String controllerUrl;

    @Builder
    public Yaml(String type, String harnessApiVersion, String username, String password, String accountName,
        String controllerUrl, UsageRestrictions.Yaml usageRestrictions) {
      super(type, harnessApiVersion, usageRestrictions);
      this.username = username;
      this.password = password;
      this.accountName = accountName;
      this.controllerUrl = controllerUrl;
    }
  }
}
