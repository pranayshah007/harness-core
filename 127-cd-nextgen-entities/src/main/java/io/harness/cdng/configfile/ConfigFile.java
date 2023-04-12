/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.configfile.ConfigFileAttributes.ConfigFileAttributeStepParameters;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WithIdentifier;
import io.harness.cdng.visitor.helpers.configfile.ConfigFileVisitorHelper;
import io.harness.data.validator.EntityIdentifier;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.intfc.OverridesApplier;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@SimpleVisitorHelper(helperClass = ConfigFileVisitorHelper.class)
@TypeAlias("configFile")
@RecasterAlias("io.harness.cdng.configfile.ConfigFile")
public class ConfigFile implements OverridesApplier<ConfigFile>, WithIdentifier, Visitable, Serializable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;

  @NotNull @JsonProperty("spec") @Valid ConfigFileAttributes spec;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder
  public ConfigFile(String uuid, String identifier, ConfigFileAttributes spec) {
    this.uuid = uuid;
    this.identifier = identifier;
    this.spec = spec;
  }

  @Override
  public ConfigFile applyOverrides(ConfigFile overrideConfig) {
    ConfigFile configFile = this;
    if (overrideConfig != null) {
      configFile.setSpec(spec.applyOverrides(overrideConfig.getSpec()));
    }

    return configFile;
  }

  @Value
  public static class ConfigFileStepParameters {
    String identifier;
    ConfigFileAttributeStepParameters spec;

    public static ConfigFileStepParameters fromConfigFile(ConfigFile configFile) {
      if (configFile == null) {
        return null;
      }

      return new ConfigFileStepParameters(
          configFile.getIdentifier(), configFile.getSpec().getConfigFileAttributeStepParameters());
    }
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add(YAMLFieldNameConstants.SPEC, spec);
    return children;
  }
}
