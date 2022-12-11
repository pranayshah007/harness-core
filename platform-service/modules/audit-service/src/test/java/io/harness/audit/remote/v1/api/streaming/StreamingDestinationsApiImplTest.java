/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.remote.v1.api.streaming;

import static io.harness.rule.OwnerRule.NISHANT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.audit.api.streaming.StreamingService;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.audit.v1.model.AwsS3StreamingDestinationSpecDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO.StatusEnum;
import io.harness.spec.server.audit.v1.model.StreamingDestinationResponse;
import io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO;
import io.harness.utils.PageUtils.SortFields;

import java.util.List;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class StreamingDestinationsApiImplTest extends CategoryTest {
  public static final int RANDOM_STRING_CHAR_COUNT_10 = 10;
  public static final int RANDOM_STRING_CHAR_COUNT_15 = 15;
  public static final int MAX_PAGE_NUMBER = 100;
  public static final int MAX_PAGE_SIZE = 100;
  @Mock private StreamingService streamingService;
  @Mock private StreamingDestinationsApiUtils streamingDestinationsApiUtils;
  private StreamingDestinationsApiImpl streamingDestinationsApi;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    this.streamingDestinationsApi = new StreamingDestinationsApiImpl(streamingService, streamingDestinationsApiUtils);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateStreamingDestinations() {
    String harnessAccount = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    String slug = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    String name = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    String bucket = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    StatusEnum statusEnum = StatusEnum.values()[RandomUtils.nextInt(0, StatusEnum.values().length - 1)];

    StreamingDestinationSpecDTO s3StreamingDestinationSpecDTO =
        new AwsS3StreamingDestinationSpecDTO().bucket(bucket).type(StreamingDestinationSpecDTO.TypeEnum.AWS_S3);
    StreamingDestinationDTO streamingDestinationDTO =
        new StreamingDestinationDTO().slug(slug).name(name).status(statusEnum).spec(s3StreamingDestinationSpecDTO);

    StreamingDestination streamingDestination = AwsS3StreamingDestination.builder().build();

    when(streamingService.create(harnessAccount, streamingDestinationDTO)).thenReturn(streamingDestination);
    when(streamingDestinationsApiUtils.getStreamingDestinationResponse(streamingDestination))
        .thenReturn(new StreamingDestinationResponse());

    Response response = streamingDestinationsApi.createStreamingDestinations(streamingDestinationDTO, harnessAccount);

    verify(streamingService, times(1)).create(harnessAccount, streamingDestinationDTO);
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
    assertThat(response.getEntity()).isInstanceOf(StreamingDestinationResponse.class);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetStreamingDestinations() {
    String harnessAccount = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    String searchTerm = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    int page = RandomUtils.nextInt(0, MAX_PAGE_NUMBER);
    int limit = RandomUtils.nextInt(1, MAX_PAGE_SIZE);
    String sort = SortFields.UPDATED.value();
    String order = SortOrder.OrderType.values()[RandomUtils.nextInt(0, SortOrder.OrderType.values().length - 1)].name();
    StatusEnum statusEnum = StatusEnum.values()[RandomUtils.nextInt(0, StatusEnum.values().length - 1)];

    StreamingDestinationFilterProperties filterProperties =
        StreamingDestinationFilterProperties.builder().searchTerm(searchTerm).status(statusEnum).build();
    Pageable pageable =
        PageRequest.of(page, limit, Sort.Direction.fromString(order), StreamingDestinationKeys.lastModifiedDate);
    Page<StreamingDestination> streamingDestinationPage =
        new PageImpl<>(List.of(AwsS3StreamingDestination.builder().build()));
    StreamingDestinationResponse streamingDestinationResponse = new StreamingDestinationResponse();
    StreamingDestination streamingDestination = AwsS3StreamingDestination.builder().build();

    when(streamingDestinationsApiUtils.getFilterProperties(searchTerm, statusEnum.name())).thenReturn(filterProperties);
    when(streamingDestinationsApiUtils.getPageRequest(page, limit, sort, order)).thenReturn(pageable);
    when(streamingService.list(harnessAccount, pageable, filterProperties)).thenReturn(streamingDestinationPage);
    when(streamingDestinationsApiUtils.getStreamingDestinationResponse(streamingDestination))
        .thenReturn(streamingDestinationResponse);

    Response response = streamingDestinationsApi.getStreamingDestinations(
        harnessAccount, page, limit, sort, order, searchTerm, statusEnum.name());

    verify(streamingDestinationsApiUtils, times(1)).getFilterProperties(searchTerm, statusEnum.name());
    verify(streamingDestinationsApiUtils, times(1)).getPageRequest(page, limit, sort, order);
    verify(streamingService, times(1)).list(harnessAccount, pageable, filterProperties);
    verify(streamingDestinationsApiUtils, times(1)).getStreamingDestinationResponse(streamingDestination);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getHeaders()).containsKey("Link");
  }
}
