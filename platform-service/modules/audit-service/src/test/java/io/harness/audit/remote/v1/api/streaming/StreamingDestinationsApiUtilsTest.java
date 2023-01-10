/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.remote.v1.api.streaming;

import static io.harness.beans.SortOrder.OrderType.DESC;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO.TypeEnum.AWS_S3;
import static io.harness.utils.PageUtils.SortFields.CREATED;
import static io.harness.utils.PageUtils.SortFields.NAME;
import static io.harness.utils.PageUtils.SortFields.SLUG;
import static io.harness.utils.PageUtils.SortFields.UPDATED;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.rule.Owner;
import io.harness.spec.server.audit.v1.model.AwsS3StreamingDestinationSpecDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO.StatusEnum;
import io.harness.spec.server.audit.v1.model.StreamingDestinationResponse;

import java.util.Map;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class StreamingDestinationsApiUtilsTest extends CategoryTest {
  public static final int RANDOM_STRING_CHAR_COUNT_10 = 10;
  public static final int RANDOM_STRING_CHAR_COUNT_15 = 15;
  public static final int TIME_DIFFERENCE_IN_MILLS = 10000;
  public static final int MAX_PAGE_NUMBER = 100;
  public static final int MAX_PAGE_SIZE = 100;
  private StreamingDestinationsApiUtils streamingDestinationsApiUtils;
  private String[] statusStrings;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setup() {
    this.streamingDestinationsApiUtils = new StreamingDestinationsApiUtils();
    this.statusStrings = new String[] {"ACTIVE", "INACTIVE"};
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetFilterProperties() {
    String searchTerm = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    String statusString = statusStrings[RandomUtils.nextInt(0, statusStrings.length - 1)];
    StreamingDestinationFilterProperties filterProperties =
        streamingDestinationsApiUtils.getFilterProperties(searchTerm, statusString);
    assertThat(filterProperties).isNotNull();
    assertThat(filterProperties.getStatus().value()).isEqualTo(statusString);
    assertThat(filterProperties.getSearchTerm()).isEqualTo(searchTerm);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetFilterProperties_nullStatus() {
    String searchTerm = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    StreamingDestinationFilterProperties filterProperties =
        streamingDestinationsApiUtils.getFilterProperties(searchTerm, "");
    assertThat(filterProperties).isNotNull();
    assertThat(filterProperties.getStatus()).isNull();
    assertThat(filterProperties.getSearchTerm()).isEqualTo(searchTerm);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetFilterProperties_unknownStatus() {
    String searchTerm = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    String statusString = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    expectedException.expect(UnknownEnumTypeException.class);
    streamingDestinationsApiUtils.getFilterProperties(searchTerm, statusString);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetStreamingDestinationResponseTest_AwsS3() {
    String bucket = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    String accountIdentifier = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    String identifier = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    String name = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    String connectorRef = "account." + randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    StatusEnum statusEnum = StatusEnum.values()[RandomUtils.nextInt(0, StatusEnum.values().length - 1)];
    Long createdAt = System.currentTimeMillis() - TIME_DIFFERENCE_IN_MILLS;
    Long lastModifiedAt = System.currentTimeMillis();
    StreamingDestination streamingDestination = AwsS3StreamingDestination.builder().bucket(bucket).build();
    streamingDestination.setAccountIdentifier(accountIdentifier);
    streamingDestination.setIdentifier(identifier);
    streamingDestination.setName(name);
    streamingDestination.setStatus(statusEnum);
    streamingDestination.setType(AWS_S3);
    streamingDestination.setConnectorRef(connectorRef);
    streamingDestination.setCreatedAt(createdAt);
    streamingDestination.setLastModifiedDate(lastModifiedAt);

    StreamingDestinationResponse response =
        streamingDestinationsApiUtils.getStreamingDestinationResponse(streamingDestination);

    StreamingDestinationResponse expectedResponse =
        new StreamingDestinationResponse()
            .streamingDestination(new StreamingDestinationDTO()
                                      .slug(identifier)
                                      .name(name)
                                      .status(statusEnum)
                                      .connectorRef(connectorRef)
                                      .spec(new AwsS3StreamingDestinationSpecDTO().bucket(bucket).type(AWS_S3)))
            .created(createdAt)
            .updated(lastModifiedAt);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
    assertThat(response.getStreamingDestination().getSpec())
        .isEqualToComparingFieldByField(expectedResponse.getStreamingDestination().getSpec());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetPageRequest() {
    int page = RandomUtils.nextInt(0, MAX_PAGE_NUMBER);
    int limit = RandomUtils.nextInt(1, MAX_PAGE_SIZE);
    Map<String, String> sortToField = Map.ofEntries(Map.entry(SLUG.value(), StreamingDestinationKeys.identifier),
        Map.entry(NAME.value(), StreamingDestinationKeys.name),
        Map.entry(CREATED.value(), StreamingDestinationKeys.createdAt),
        Map.entry(UPDATED.value(), StreamingDestinationKeys.lastModifiedDate));
    String order = SortOrder.OrderType.values()[RandomUtils.nextInt(0, SortOrder.OrderType.values().length - 1)].name();
    sortToField.forEach((String sort, String field) -> {
      Pageable pageable = streamingDestinationsApiUtils.getPageRequest(page, limit, sort, order);
      assertThat(pageable.getPageNumber()).isEqualTo(page);
      assertThat(pageable.getPageSize()).isEqualTo(limit);
      assertThat(pageable.getSort()).isNotEmpty();
      assertThat(pageable.getSort()).containsExactly(new Sort.Order(Sort.Direction.fromString(order), field));
    });
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetPageRequest_DefaultOrder() {
    int page = RandomUtils.nextInt(0, MAX_PAGE_NUMBER);
    int limit = RandomUtils.nextInt(1, MAX_PAGE_SIZE);

    Pageable pageable = streamingDestinationsApiUtils.getPageRequest(page, limit, null, null);
    assertThat(pageable.getPageNumber()).isEqualTo(page);
    assertThat(pageable.getPageSize()).isEqualTo(limit);
    assertThat(pageable.getSort()).isNotEmpty();
    assertThat(pageable.getSort())
        .containsExactly(
            new Sort.Order(Sort.Direction.fromString(DESC.name()), StreamingDestinationKeys.lastModifiedDate));
  }
}
