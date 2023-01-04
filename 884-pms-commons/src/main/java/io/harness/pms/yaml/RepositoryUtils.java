/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.yaml.repository.Reference;
import io.harness.yaml.repository.Repository;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
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

  public Optional<Reference> getReferenceFromInputPayload(String inputPayload) {
    Reference reference = null;
    if (EmptyPredicate.isNotEmpty(inputPayload)) {
      JsonNode inputPayloadNode = JsonPipelineUtils.readTree(inputPayload);
      if (inputPayloadNode != null && inputPayloadNode.has(YAMLFieldNameConstants.REPOSITORY)) {
        JsonNode inputRepositoryNode = inputPayloadNode.get(YAMLFieldNameConstants.REPOSITORY);
        if (inputRepositoryNode != null && inputRepositoryNode.isObject()
            && inputRepositoryNode.has(YAMLFieldNameConstants.REFERENCE)) {
          JsonNode inputReferenceNode = inputRepositoryNode.get(YAMLFieldNameConstants.REFERENCE);
          try {
            reference = JsonPipelineUtils.read(inputReferenceNode.toString(), Reference.class);
          } catch (IOException e) {
            log.warn(String.format("Invalid input payload provided for repository reference: %s", inputPayload));
          }
        }
      }
    }
    return Optional.ofNullable(reference);
  }
}
