/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.registry.Registry;

import java.io.IOException;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PipelineYamlHelper {
  private static final String VERSION_FIELD_NAME = "version";

  public String getVersion(String yaml, boolean yamlSimplification) {
    return yamlSimplification ? getVersion(yaml) : PipelineVersion.V0;
  }

  public String getVersion(String yaml) {
    if (isEmpty(yaml)) {
      return PipelineVersion.V0;
    }
    String version;
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      version = yamlField.getNode().getProperty(VERSION_FIELD_NAME);
    } catch (IOException ioException) {
      throw new InvalidRequestException("Invalid yaml passed.");
    }
    return isEmpty(version) ? PipelineVersion.V0 : version;
  }

  public Optional<Registry> getRegistry(String pipelineYaml) {
    YamlField registryField = YamlUtils.tryReadTree(pipelineYaml).getNode().getField(YAMLFieldNameConstants.REGISTRY);
    Registry registry = null;
    if (registryField != null) {
      try {
        registry = YamlUtils.read(registryField.getNode().toString(), Registry.class);
      } catch (IOException ex) {
        throw new InvalidRequestException("Invalid registry yaml");
      }
    }
    return Optional.ofNullable(registry);
  }
}
