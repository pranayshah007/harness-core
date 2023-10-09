/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.kinds;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.StoreConfigHelper;
import io.harness.data.validator.EntityIdentifier;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "HelmRepoOverrideManifestKeys")
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestType.HelmRepoOverride)
@FieldDefaults(level = AccessLevel.PRIVATE)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("helmRepoOverrideManifest")
@RecasterAlias("io.harness.cdng.manifest.yaml.kinds.HelmRepoOverrideManifest")
public class HelmRepoOverrideManifest implements ManifestAttributes, Visitable, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @EntityIdentifier String identifier;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither @NotNull ParameterField<String> connectorRef;
  @NotNull @JsonProperty("type") String type;

  @Override
  public ManifestAttributes applyOverrides(ManifestAttributes overrideConfig) {
    // Not needed
    return overrideConfig;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    return VisitableChildren.builder().build();
  }

  @Override
  public String getKind() {
    return ManifestType.HelmRepoOverride;
  }

  @Override
  public ManifestAttributeStepParameters getManifestAttributeStepParameters() {
    return new HelmRepoOverrideManifestStepParameters(identifier);
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @Value
  public static class HelmRepoOverrideManifestStepParameters implements ManifestAttributeStepParameters {
    String identifier;
  }

  @Override
  public Set<String> validateAtRuntime() {
    Set<String> invalidParameters = new HashSet<>();
    if (StoreConfigHelper.checkStringParameterNullOrInput(connectorRef)) {
      invalidParameters.add(HelmRepoOverrideManifestKeys.connectorRef);
    }
    if (StoreConfigHelper.checkStringParameterNullOrInput(ParameterField.createValueField(type))) {
      invalidParameters.add(HelmRepoOverrideManifestKeys.type);
    }
    return invalidParameters;
  }
}
