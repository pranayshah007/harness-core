/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineMetadataV2;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PipelineMetadataService {
  int incrementRunSequence(PipelineEntity entity);

  int incrementExecutionCounter(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  PipelineMetadataV2 save(PipelineMetadataV2 metadata);

  Optional<PipelineMetadataV2> getMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier);

  Map<String, PipelineMetadataV2> getMetadataForGivenPipelineIds(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> identifiers);
}
