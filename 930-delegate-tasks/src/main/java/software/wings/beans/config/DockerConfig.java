/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

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
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.ws.rs.core.UriBuilder;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 1/5/17.
 */
@OwnedBy(CDC)
@JsonTypeName("DOCKER")
@Data
@Builder
@ToString(exclude = "password")
@EqualsAndHashCode(callSuper = false)
@TargetModule(HarnessModule._957_CG_BEANS)
public class DockerConfig extends SettingValue implements EncryptableSetting, ArtifactSourceable {
  @Attributes(title = "Docker Registry URL", required = true) @NotEmpty private String dockerRegistryUrl;
  @Attributes(title = "Username") private String username;
  @Attributes(title = "Password") @Encrypted(fieldName = "password") private char[] password;
  private List<String> delegateSelectors;
  @SchemaIgnore @NotEmpty private String accountId;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;

  private boolean skipValidation;
  /**
   * Instantiates a new Docker registry config.
   */
  public DockerConfig() {
    super(SettingVariableTypes.DOCKER.name());
  }

  @SchemaIgnore
  public boolean hasCredentials() {
    return isNotEmpty(username);
  }

  public DockerConfig(String dockerRegistryUrl, String username, char[] password, List<String> delegateSelectors,
      String accountId, String encryptedPassword, boolean skipValidation) {
    super(SettingVariableTypes.DOCKER.name());
    setDockerRegistryUrl(dockerRegistryUrl);
    this.username = username;
    this.password = password == null ? null : password.clone();
    this.accountId = accountId;
    this.encryptedPassword = encryptedPassword;
    this.delegateSelectors = delegateSelectors;
    this.skipValidation = skipValidation;
  }

  // override the setter for URL to enforce that we always put / (slash) at the end
  public void setDockerRegistryUrl(String dockerRegistryUrl) {
    URI uri = UriBuilder.fromUri(dockerRegistryUrl).build();
    this.dockerRegistryUrl =
        UriBuilder.fromUri(dockerRegistryUrl).path(uri.getPath().endsWith("/") ? "" : "/").build().toString();
  }

  // NOTE: Do not remove this. As UI expects this field should be there
  public String getUsername() {
    return Objects.isNull(username) ? "" : username;
  }

  @Override
  public String fetchUserName() {
    return username;
  }

  @Override
  public String fetchRegistryUrl() {
    return dockerRegistryUrl;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.singletonList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        dockerRegistryUrl.endsWith("/") ? dockerRegistryUrl : dockerRegistryUrl.concat("/"), maskingEvaluator));
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.ARTIFACT_SERVER.name();
  }

  @Override
  public boolean shouldDeleteArtifact(SettingValue prev) {
    if (!(prev instanceof DockerConfig)) {
      return true;
    }
    return !StringUtils.equals(((DockerConfig) prev).getDockerRegistryUrl(), dockerRegistryUrl);
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends ArtifactServerYaml {
    private List<String> delegateSelectors;
    private Boolean skipValidation;

    @Builder
    public Yaml(String type, String harnessApiVersion, String url, String username, String password,
        UsageRestrictions.Yaml usageRestrictions, List<String> delegateSelectors, Boolean skipValidation) {
      super(type, harnessApiVersion, url, username, password, usageRestrictions);
      this.delegateSelectors = delegateSelectors;
      this.skipValidation = skipValidation;
    }
  }
}
