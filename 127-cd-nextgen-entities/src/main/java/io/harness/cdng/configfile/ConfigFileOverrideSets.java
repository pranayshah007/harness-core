/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFile.ConfigFileStepParameters;
import io.harness.cdng.visitor.helpers.manifest.ManifestOverridesVisitorHelper;
import io.harness.data.validator.EntityIdentifier;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("overrideSet")
@SimpleVisitorHelper(helperClass = ManifestOverridesVisitorHelper.class)
@TypeAlias("configFileOverrideSets")
public class ConfigFileOverrideSets implements Visitable {
  @EntityIdentifier String identifier;
  List<ConfigFileWrapper> configFiles;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    configFiles.forEach(manifest -> children.add("configFiles", manifest));
    return children;
  }

  @Value
  public static class ConfigFileOverrideSetsStepParametersWrapper {
    ConfigFileOverrideSetsStepParameters configFiles;

    public static ConfigFileOverrideSetsStepParametersWrapper fromConfigFileOverrideSets(
        ConfigFileOverrideSets configFileOverrideSets) {
      return new ConfigFileOverrideSetsStepParametersWrapper(
          ConfigFileOverrideSetsStepParameters.fromConfigFileOverrideSets(configFileOverrideSets));
    }
  }

  public static class ConfigFileOverrideSetsStepParameters extends HashMap<String, ConfigFileStepParameters> {
    public static ConfigFileOverrideSetsStepParameters fromConfigFileOverrideSets(
        ConfigFileOverrideSets configFileOverrideSets) {
      if (configFileOverrideSets == null || configFileOverrideSets.getConfigFiles() == null) {
        return null;
      }

      ConfigFileOverrideSetsStepParameters stepParameters = new ConfigFileOverrideSetsStepParameters();
      configFileOverrideSets.getConfigFiles().forEach(configFileWrapper
          -> stepParameters.put(configFileWrapper.getConfigFile().getIdentifier(),
              ConfigFileStepParameters.fromConfigFile(configFileWrapper.getConfigFile())));
      return stepParameters;
    }
  }
}
