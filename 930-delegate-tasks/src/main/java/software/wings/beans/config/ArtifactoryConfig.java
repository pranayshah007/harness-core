/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.config;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

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
import software.wings.yaml.setting.ArtifactServerYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by sgurubelli on 6/20/17.
 */
@OwnedBy(CDC)
@JsonTypeName("ARTIFACTORY")
@Data
@Builder
@ToString(exclude = "password")
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._957_CG_BEANS)
public class ArtifactoryConfig extends SettingValue implements EncryptableSetting, ArtifactSourceable {
  @Attributes(title = "Artifactory URL", required = true) @NotEmpty private String artifactoryUrl;

  @Attributes(title = "Username") private String username;

  @Attributes(title = "Password") @Encrypted(fieldName = "password") private char[] password;
  private List<String> delegateSelectors;

  @SchemaIgnore @NotEmpty private String accountId;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;

  private boolean skipValidation;

  public ArtifactoryConfig() {
    super(SettingVariableTypes.ARTIFACTORY.name());
  }

  public ArtifactoryConfig(String artifactoryUrl, String username, char[] password, List<String> delegateSelectors,
      String accountId, String encryptedPassword, boolean skipValidation) {
    this();
    this.artifactoryUrl = artifactoryUrl;
    this.username = username;
    this.password = password == null ? null : password.clone();
    this.accountId = accountId;
    this.encryptedPassword = encryptedPassword;
    this.delegateSelectors = delegateSelectors;
    this.skipValidation = skipValidation;
  }

  // NOTE: Do not remove this. As UI expects this field should be there..Lombok Default is not working
  public String getUsername() {
    return Objects.isNull(username) ? "" : username;
  }

  @Override
  public String fetchUserName() {
    return username;
  }

  @Override
  public String fetchRegistryUrl() {
    return artifactoryUrl;
  }

  @SchemaIgnore
  public boolean hasCredentials() {
    return isNotEmpty(username);
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.singletonList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        getArtifactoryUrl(), maskingEvaluator));
  }

  @Override
  public boolean shouldDeleteArtifact(SettingValue prev) {
    if (!(prev instanceof ArtifactoryConfig)) {
      return true;
    }
    ArtifactoryConfig prevConfig = (ArtifactoryConfig) prev;
    return !StringUtils.equals(prevConfig.getArtifactoryUrl(), artifactoryUrl);
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.ARTIFACT_SERVER.name();
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends ArtifactServerYaml {
    private List<String> delegateSelectors;
    private boolean skipValidation;

    @Builder
    public Yaml(String type, String harnessApiVersion, String url, String username, String password,
        UsageRestrictions.Yaml usageRestrictions, List<String> delegateSelectors, boolean skipValidation) {
      super(type, harnessApiVersion, url, username, password, usageRestrictions);
      this.delegateSelectors = delegateSelectors;
      this.skipValidation = skipValidation;
    }
  }
}
