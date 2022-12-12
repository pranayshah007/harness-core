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
  public static final int RANDOM_STRING_CHAR_COUNT_10 = 10;
  public static final int RANDOM_STRING_CHAR_COUNT_15 = 15;
  private StreamingDestinationMapper streamingDestinationMapper;

  @Before
  public void setup() {
    this.streamingDestinationMapper = new StreamingDestinationMapper();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testToStreamingDestinationEntity_AwsS3() {
    String accountIdentifier = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    String slug = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    String name = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    StatusEnum statusEnum = StatusEnum.values()[RandomUtils.nextInt(0, StatusEnum.values().length - 1)];
    String bucket = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    String connectorRef = "account." + randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    StreamingDestinationSpecDTO streamingDestinationSpecDTO =
        new AwsS3StreamingDestinationSpecDTO().bucket(bucket).type(TypeEnum.AWS_S3);
    StreamingDestinationDTO streamingDestinationDTO = new StreamingDestinationDTO()
                                                          .slug(slug)
                                                          .name(name)
                                                          .status(statusEnum)
                                                          .connectorRef(connectorRef)
                                                          .spec(streamingDestinationSpecDTO);

    StreamingDestination streamingDestination =
        streamingDestinationMapper.toStreamingDestinationEntity(accountIdentifier, streamingDestinationDTO);

    StreamingDestination expectedStreamingDestination = AwsS3StreamingDestination.builder().bucket(bucket).build();
    expectedStreamingDestination.setAccountIdentifier(accountIdentifier);
    expectedStreamingDestination.setIdentifier(slug);
    expectedStreamingDestination.setName(name);
    expectedStreamingDestination.setStatus(statusEnum);
    expectedStreamingDestination.setConnectorRef(connectorRef);
    expectedStreamingDestination.setType(TypeEnum.AWS_S3);

    assertThat(streamingDestination).isEqualToComparingFieldByField(expectedStreamingDestination);
  }
}
