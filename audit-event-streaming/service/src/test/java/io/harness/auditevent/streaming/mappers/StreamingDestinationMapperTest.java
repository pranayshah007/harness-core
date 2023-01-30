/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.mappers;

import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationDTO.StatusEnum.ACTIVE;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO.TypeEnum.AWS_S3;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.audit.v1.model.AwsS3StreamingDestinationSpecDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StreamingDestinationMapperTest extends CategoryTest {
  private StreamingDestinationMapper streamingDestinationMapper;

  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  private final long MINUTES_15_IN_MILLS = 15 * 60 * 1000L;
  private final long MINUTES_30_IN_MILLS = 30 * 60 * 1000L;

  @Before
  public void setup() {
    this.streamingDestinationMapper = new StreamingDestinationMapper();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testToStreamingDestinationDTOAwsS3() {
    StreamingDestination streamingDestination = getAwsS3StreamingDestination();
    StreamingDestinationDTO streamingDestinationDTO =
        streamingDestinationMapper.toStreamingDestinationDTO(streamingDestination);
    StreamingDestinationDTO streamingDestinationDTOExpected =
        new StreamingDestinationDTO()
            .identifier(streamingDestination.getIdentifier())
            .name(streamingDestination.getName())
            .status(streamingDestination.getStatus())
            .connectorRef(streamingDestination.getConnectorRef())
            .spec(new AwsS3StreamingDestinationSpecDTO()
                      .bucket(((AwsS3StreamingDestination) streamingDestination).getBucket())
                      .type(AWS_S3));
    assertThat(streamingDestinationDTO).isEqualToComparingFieldByField(streamingDestinationDTOExpected);
  }

  private StreamingDestination getAwsS3StreamingDestination() {
    String bucket = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String name = randomAlphabetic(10);
    String connectorRef = randomAlphabetic(10);
    long lastStatusChangedAt = System.currentTimeMillis();
    long createdAt = lastStatusChangedAt - MINUTES_30_IN_MILLS;
    long lastModifiedAt = lastStatusChangedAt - MINUTES_15_IN_MILLS;

    StreamingDestination streamingDestination = AwsS3StreamingDestination.builder().bucket(bucket).build();
    streamingDestination.setIdentifier(identifier);
    streamingDestination.setName(name);
    streamingDestination.setStatus(ACTIVE);
    streamingDestination.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    streamingDestination.setCreatedAt(createdAt);
    streamingDestination.setLastModifiedDate(lastModifiedAt);
    streamingDestination.setLastStatusChangedAt(lastStatusChangedAt);
    streamingDestination.setConnectorRef(connectorRef);
    streamingDestination.setType(AWS_S3);
    return streamingDestination;
  }
}
