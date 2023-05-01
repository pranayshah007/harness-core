/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import io.harness.pms.pipeline.PipelineMetadataV2;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

public interface PipelineMetadataService {
  int incrementRunSequence(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  int incrementExecutionCounter(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  PipelineMetadataV2 save(PipelineMetadataV2 metadata);

  PipelineMetadataV2 update(Criteria criteria, Update update);

  Optional<PipelineMetadataV2> getMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier);

  Map<String, PipelineMetadataV2> getMetadataForGivenPipelineIds(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> identifiers);

  /**
   * Hard Deletes the pipeline metadata for given params for both PipelineMetadata and PipelineMetadataV2
   * Ignores throwing the exception and only log it
   * Uses - account_org_project_pipeline idx
   * @param accountId
   * @param orgIdentifier
   * @param projectIdentifier
   * @param identifier
   * @return
   */
  boolean deletePipelineMetadata(String accountId, String orgIdentifier, String projectIdentifier, String identifier);
}
