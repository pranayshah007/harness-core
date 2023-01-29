/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.mapper.streaming;

import static io.harness.rule.OwnerRule.NISHANT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.audit.v1.model.AwsS3StreamingDestinationSpecDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO.StatusEnum;
import io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO.TypeEnum;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StreamingDestinationMapperTest extends CategoryTest {
  private static final int RANDOM_STRING_CHAR_COUNT_10 = 10;
  private static final int RANDOM_STRING_CHAR_COUNT_15 = 15;
  private String accountIdentifier;
  private String identifier;
  private String name;
  private StatusEnum statusEnum;
  private String bucket;
  private String connectorRef;

  private StreamingDestinationMapper streamingDestinationMapper;

  @Before
  public void setup() throws IllegalAccessException {
    this.streamingDestinationMapper = new StreamingDestinationMapper();

    accountIdentifier = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    identifier = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    name = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    statusEnum = StatusEnum.values()[RandomUtils.nextInt(0, StatusEnum.values().length - 1)];
    bucket = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    connectorRef = "account." + randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testToStreamingDestinationEntity_AwsS3() {
    StreamingDestinationDTO streamingDestinationDTO = getStreamingDestinationDTO();

    StreamingDestination streamingDestination =
        streamingDestinationMapper.toStreamingDestinationEntity(accountIdentifier, streamingDestinationDTO);

    StreamingDestination expectedStreamingDestination = AwsS3StreamingDestination.builder().bucket(bucket).build();
    expectedStreamingDestination.setAccountIdentifier(accountIdentifier);
    expectedStreamingDestination.setIdentifier(identifier);
    expectedStreamingDestination.setName(name);
    expectedStreamingDestination.setStatus(statusEnum);
    expectedStreamingDestination.setConnectorRef(connectorRef);
    expectedStreamingDestination.setType(TypeEnum.AWS_S3);
    expectedStreamingDestination.setLastStatusChangedAt(streamingDestination.getLastStatusChangedAt());

    assertThat(streamingDestination).isEqualToComparingFieldByField(expectedStreamingDestination);
  }

  private StreamingDestinationDTO getStreamingDestinationDTO() {
    StreamingDestinationSpecDTO streamingDestinationSpecDTO =
        new AwsS3StreamingDestinationSpecDTO().bucket(bucket).type(TypeEnum.AWS_S3);

    return new StreamingDestinationDTO()
        .identifier(identifier)
        .name(name)
        .status(statusEnum)
        .connectorRef(connectorRef)
        .spec(streamingDestinationSpecDTO);
  }
}
