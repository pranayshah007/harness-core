/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.mapper.streaming;

import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.spec.server.audit.v1.model.AwsS3StreamingDestinationSpecDTO;

public class AwsS3StreamingDestinationSpecDTOToEntityMapper {
  private AwsS3StreamingDestinationSpecDTOToEntityMapper() {}

  public static AwsS3StreamingDestination toStreamingDestination(
      AwsS3StreamingDestinationSpecDTO awsS3StreamingDestinationSpecDTO) {
    return AwsS3StreamingDestination.builder().bucket(awsS3StreamingDestinationSpecDTO.getBucket()).build();
  }
}
