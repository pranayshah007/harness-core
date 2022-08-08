/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.settings.helm;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.delegate.beans.executioncapability.SocketConnectivityExecutionCapability;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.HelmVersion;

import software.wings.audit.ResourceType;
import software.wings.jersey.JsonViews;
import software.wings.security.UsageRestrictions;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.setting.HelmRepoYaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.SchemaIgnore;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@JsonTypeName("HTTP_HELM_REPO")
@Data
@Builder
@ToString(exclude = {"password"})
@EqualsAndHashCode(callSuper = false)
@Slf4j
@TargetModule(HarnessModule._957_CG_BEANS)
public class HttpHelmRepoConfig extends SettingValue implements HelmRepoConfig {
  @SchemaIgnore @NotEmpty private String accountId;

  @NotEmpty private String chartRepoUrl;
  private String username;
  @Encrypted(fieldName = "password") private char[] password;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;

  public HttpHelmRepoConfig() {
    super(SettingVariableTypes.HTTP_HELM_REPO.name());
  }

  public HttpHelmRepoConfig(
      String accountId, String chartRepoUrl, String username, final char[] password, String encryptedPassword) {
    super(SettingVariableTypes.HTTP_HELM_REPO.name());
    this.accountId = accountId;
    this.chartRepoUrl = chartRepoUrl;
    this.username = username;
    this.password = (password != null) ? Arrays.copyOf(password, password.length) : null;
    this.encryptedPassword = encryptedPassword;
  }

  @Override
  public String getConnectorId() {
    return null;
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.ARTIFACT_SERVER.name();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilityList = new ArrayList<>();
    executionCapabilityList.add(HelmInstallationCapability.builder()
                                    .version(HelmVersion.V3)
                                    .criteria("HTTP_HELM_REPO: " + getChartRepoUrl())
                                    .build());
    /*
      We are henceforth using SocketConnectivityExecutionCapability instead of HttpConnectionExecutionCapability
      this is to ensure that we don't fail Helm Repo Connector Validation in case the url returns 400
      ref: https://harness.atlassian.net/browse/CDS-36189
     */
    try {
      URI url = new URI(chartRepoUrl);
      int port = url.getPort();
      if (port <= -1) {
        if (url.getScheme() != null) {
          port = url.getScheme().equals("https") ? 443 : 80;
        } else {
          port = 443;
        }
      }
      executionCapabilityList.add(SocketConnectivityExecutionCapability.builder()
                                      .url(chartRepoUrl)
                                      .scheme(url.getScheme())
                                      .hostName(url.getHost())
                                      .port(String.valueOf(port))
                                      .build());
    } catch (URISyntaxException e) {
      log.error("Unable to process URL: " + e.getMessage());
    }
    return executionCapabilityList;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends HelmRepoYaml {
    private String url;
    private String username;
    private String password;

    @Builder
    public Yaml(String type, String harnessApiVersion, String url, String username, String password,
        UsageRestrictions.Yaml usageRestrictions) {
      super(type, harnessApiVersion, usageRestrictions);
      this.url = url;
      this.username = username;
      this.password = password;
    }
  }
}
