/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;

import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.beans.config.ArtifactSourceable;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.setting.ArtifactServerYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 11/22/16.
 */
@OwnedBy(CDC)
@JsonTypeName("BAMBOO")
@Data
@Builder
@ToString(exclude = "password")
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._957_CG_BEANS)
public class BambooConfig extends SettingValue implements EncryptableSetting, ArtifactSourceable {
  @Attributes(title = "Bamboo URL", required = true) @NotEmpty private String bambooUrl;
  @Attributes(title = "Username", required = true) @NotEmpty private String username;
  @Attributes(title = "Password", required = true) @Encrypted(fieldName = "password") private char[] password;
  @SchemaIgnore @NotEmpty private String accountId;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;

  /**
   * Instantiates a new BambooService config.
   */
  public BambooConfig() {
    super(SettingVariableTypes.BAMBOO.name());
  }

  public BambooConfig(String bambooUrl, String username, char[] password, String accountId, String encryptedPassword) {
    this();
    this.bambooUrl = bambooUrl;
    this.username = username;
    this.password = password == null ? null : password.clone();
    this.accountId = accountId;
    this.encryptedPassword = encryptedPassword;
  }

  @Override
  public String fetchUserName() {
    return username;
  }

  @Override
  public String fetchRegistryUrl() {
    return bambooUrl;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Arrays.asList(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(bambooUrl, maskingEvaluator));
  }

  @Override
  public boolean shouldDeleteArtifact(SettingValue prev) {
    if (!(prev instanceof BambooConfig)) {
      return true;
    }
    BambooConfig prevConfig = (BambooConfig) prev;
    return !StringUtils.equals(prevConfig.getBambooUrl(), bambooUrl);
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.ARTIFACT_SERVER.name();
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends ArtifactServerYaml {
    @Builder
    public Yaml(String type, String harnessApiVersion, String url, String username, String password,
        UsageRestrictions.Yaml usageRestrictions) {
      super(type, harnessApiVersion, url, username, password, usageRestrictions);
    }
  }
}
