/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.repository.Repository;

import java.io.IOException;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@UtilityClass
@Slf4j
public class RepositoryUtils {
  public Optional<Repository> getRepositoryFromPipelineYaml(String pipelineYaml) {
    YamlField pipelineField;
    try {
      pipelineField = YamlUtils.readTree(pipelineYaml);
    } catch (IOException ex) {
      String message = "Invalid pipeline yaml during repository fetch";
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }

    YamlField repositoryField = pipelineField.getNode().getField(YAMLFieldNameConstants.REPOSITORY);
    Repository repository = null;
    if (repositoryField != null) {
      try {
        repository = YamlUtils.read(repositoryField.getNode().toString(), Repository.class);
      } catch (IOException ex) {
        String message = "Invalid repository yaml";
        log.error("Invalid repository yaml");
        throw new InvalidRequestException(message);
      }
    }
    return Optional.ofNullable(repository);
  }
}
