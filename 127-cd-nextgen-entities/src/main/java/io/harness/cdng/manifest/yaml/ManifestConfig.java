/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.cdng.manifest.yaml.ManifestAttributes.ManifestAttributeStepParameters;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.visitor.helpers.manifest.ManifestConfigVisitorHelper;
import io.harness.data.validator.EntityIdentifier;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Data
@NoArgsConstructor
@SimpleVisitorHelper(helperClass = ManifestConfigVisitorHelper.class)
@TypeAlias("manifestConfig")
@RecasterAlias("io.harness.cdng.manifest.yaml.ManifestConfig")
public class ManifestConfig implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;

  @NotNull @JsonProperty("type") ManifestConfigType type;

  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  private ManifestAttributes spec;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  public void setSpec(ManifestAttributes spec) {
    this.spec = spec;
    if (this.spec != null) {
      this.spec.setIdentifier(identifier);
    }
  }

  // Use Builder as Constructor then only external property(visible) will be filled.
  @Builder
  public ManifestConfig(String uuid, String identifier, ManifestConfigType type, ManifestAttributes spec) {
    this.uuid = uuid;
    this.identifier = identifier;
    this.type = type;
    this.spec = spec;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add(YAMLFieldNameConstants.SPEC, spec);
    return children;
  }

  @Value
  public static class ManifestConfigStepParameters {
    String identifier;
    String type;
    ManifestAttributeStepParameters spec;

    public static ManifestConfigStepParameters fromManifestConfig(ManifestConfig manifestConfig) {
      if (manifestConfig == null) {
        return null;
      }
      return new ManifestConfigStepParameters(manifestConfig.getIdentifier(),
          manifestConfig.getType() == null ? null : manifestConfig.getType().getDisplayName(),
          manifestConfig.getSpec().getManifestAttributeStepParameters());
    }
  }
}
