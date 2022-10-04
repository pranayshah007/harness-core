/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitpolling.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.gitpolling.GitPollingSourceConstants.GITHUB;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.gitpolling.bean.GitPollingConfig;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.task.gitpolling.GitPollingSourceType;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.With;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(GITHUB)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("gitHubPollingConfig")
@RecasterAlias("io.harness.cdng.gitpolling.bean.yaml.GitHubPollingConfig")
public class GitHubPollingConfig implements GitPollingConfig, Visitable, WithConnectorRef {
  /**
   * Connector to connect to GitHub.
   */
  @With @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> connectorRef;

  @With @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> repository;

  @EntityIdentifier @VariableExpression(skipVariableExpression = true) String identifier;

  @With @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) String webhookId;

  @With @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) int pollInterval;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public GitPollingSourceType getSourceType() {
    return GitPollingSourceType.GITHUB;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @Override
  public GitPollingConfig applyOverrides(GitPollingConfig overrideConfig) {
    return null;
  }
}
