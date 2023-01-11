/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.remote.v1.api.streaming;

import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.NISHANT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NoResultFoundException;
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
  private static final int RANDOM_STRING_CHAR_COUNT_10 = 10;
  private static final int RANDOM_STRING_CHAR_COUNT_15 = 15;
  private static final int MAX_PAGE_NUMBER = 100;
  private static final int MAX_PAGE_SIZE = 100;
  private String harnessAccount;
  private String identifier;
  private String name;
  private String bucket;
  private StatusEnum statusEnum;
  private String connectorRef;

  @Mock private StreamingService streamingService;
  @Mock private StreamingDestinationsApiUtils streamingDestinationsApiUtils;
  private StreamingDestinationsApiImpl streamingDestinationsApi;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    this.streamingDestinationsApi = new StreamingDestinationsApiImpl(streamingService, streamingDestinationsApiUtils);

    harnessAccount = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    identifier = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    name = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    bucket = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    statusEnum = StatusEnum.values()[RandomUtils.nextInt(0, StatusEnum.values().length - 1)];
    connectorRef = "account." + randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateStreamingDestinations() {
    StreamingDestinationDTO streamingDestinationDTO = getStreamingDestinationDTO();

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
  public void testGetStreamingDestinationsList() {
    String searchTerm = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    int page = RandomUtils.nextInt(0, MAX_PAGE_NUMBER);
    int limit = RandomUtils.nextInt(1, MAX_PAGE_SIZE);
    String sort = SortFields.UPDATED.value();
    String order = SortOrder.OrderType.values()[RandomUtils.nextInt(0, SortOrder.OrderType.values().length - 1)].name();

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

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetStreamingDestinations() {
    StreamingDestination streamingDestination = getStreamingDestination();

    when(streamingService.getStreamingDestination(harnessAccount, identifier)).thenReturn(streamingDestination);
    when(streamingDestinationsApiUtils.getStreamingDestinationResponse(streamingDestination))
        .thenReturn(new StreamingDestinationResponse());

    Response response = streamingDestinationsApi.getStreamingDestination(identifier, harnessAccount);

    verify(streamingService, times(1)).getStreamingDestination(harnessAccount, identifier);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
    assertThat(response.getEntity()).isInstanceOf(StreamingDestinationResponse.class);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetStreamingDestinations_withNotFoundException() {
    doThrow(NoResultFoundException.newBuilder()
                .code(ErrorCode.RESOURCE_NOT_FOUND)
                .message(String.format("StreamingDestination: not found with identifier [%s]", identifier))
                .level(Level.ERROR)
                .reportTargets(USER)
                .build())
        .when(streamingService)
        .getStreamingDestination(harnessAccount, identifier);

    assertThatThrownBy(() -> streamingDestinationsApi.getStreamingDestination(identifier, harnessAccount))
        .hasMessage(String.format("StreamingDestination: not found with identifier [%s]", identifier))
        .isInstanceOf(NoResultFoundException.class);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testDeleteStreamingDestinations() {
    when(streamingService.delete(harnessAccount, identifier)).thenReturn(Boolean.TRUE);

    Response response = streamingDestinationsApi.deleteDisabledStreamingDestination(identifier, harnessAccount);

    verify(streamingService, times(1)).delete(harnessAccount, identifier);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testDeleteStreamingDestinations_withInvalidRequestException() {
    doThrow(new InvalidRequestException(String.format(
                "StreamingDestination: cannot delete an active StreamingDestination with identifier [%s]", identifier)))
        .when(streamingService)
        .delete(harnessAccount, identifier);

    assertThatThrownBy(() -> streamingDestinationsApi.deleteDisabledStreamingDestination(identifier, harnessAccount))
        .hasMessage(String.format(
            "StreamingDestination: cannot delete an active StreamingDestination with identifier [%s]", identifier))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdateStreamingDestinations() throws Exception {
    StreamingDestination streamingDestination = getStreamingDestination();
    StreamingDestinationDTO streamingDestinationDTO = getStreamingDestinationDTO();

    when(streamingService.update(any(), any(), any())).thenReturn(streamingDestination);
    when(streamingDestinationsApiUtils.getStreamingDestinationResponse(streamingDestination))
        .thenReturn(new StreamingDestinationResponse());

    Response response =
        streamingDestinationsApi.updateStreamingDestination(identifier, streamingDestinationDTO, harnessAccount);

    verify(streamingService, times(1)).update(identifier, streamingDestinationDTO, harnessAccount);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isNotNull();
    assertThat(response.getEntity()).isInstanceOf(StreamingDestinationResponse.class);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdateStreamingDestinations_withExceptions() throws Exception {
    StreamingDestinationDTO streamingDestinationDTO = getStreamingDestinationDTO();

    doThrow(new InvalidRequestException(String.format(
                "StreamingDestination: identifier [%s] did not match with StreamingDestinationDTO identifier [%s]",
                identifier, identifier)))
        .when(streamingService)
        .update(identifier, streamingDestinationDTO, harnessAccount);
    assertThatThrownBy(
        () -> streamingDestinationsApi.updateStreamingDestination(identifier, streamingDestinationDTO, harnessAccount))
        .hasMessage(String.format(
            "StreamingDestination: identifier [%s] did not match with StreamingDestinationDTO identifier [%s]",
            identifier, identifier));

    doThrow(new InvalidRequestException(String.format(
                "StreamingDestination: conectorRef [%s] did not match with StreamingDestinationDTO conectorRef [%s]",
                connectorRef, connectorRef)))
        .when(streamingService)
        .update(identifier, streamingDestinationDTO, harnessAccount);
    assertThatThrownBy(
        () -> streamingDestinationsApi.updateStreamingDestination(identifier, streamingDestinationDTO, harnessAccount))
        .hasMessage(String.format(
            "StreamingDestination: conectorRef [%s] did not match with StreamingDestinationDTO conectorRef [%s]",
            connectorRef, connectorRef));

    doThrow(new InvalidRequestException(
                String.format("StreamingDestination: type [%s] did not match with StreamingDestinationDTO type [%s]",
                    statusEnum.name(), statusEnum.name())))
        .when(streamingService)
        .update(identifier, streamingDestinationDTO, harnessAccount);
    assertThatThrownBy(
        () -> streamingDestinationsApi.updateStreamingDestination(identifier, streamingDestinationDTO, harnessAccount))
        .hasMessage(
            String.format("StreamingDestination: type [%s] did not match with StreamingDestinationDTO type [%s]",
                statusEnum.name(), statusEnum.name()));
  }

  private StreamingDestinationDTO getStreamingDestinationDTO() {
    StreamingDestinationSpecDTO streamingDestinationSpecDTO =
        new AwsS3StreamingDestinationSpecDTO().bucket(bucket).type(StreamingDestinationSpecDTO.TypeEnum.AWS_S3);

    return new StreamingDestinationDTO()
        .identifier(identifier)
        .name(name)
        .status(statusEnum)
        .connectorRef(connectorRef)
        .spec(streamingDestinationSpecDTO);
  }

  private StreamingDestination getStreamingDestination() {
    StreamingDestination streamingDestination = AwsS3StreamingDestination.builder().bucket(bucket).build();
    streamingDestination.setIdentifier(identifier);
    streamingDestination.setName(name);
    streamingDestination.setType(StreamingDestinationSpecDTO.TypeEnum.AWS_S3);
    streamingDestination.setStatus(statusEnum);
    streamingDestination.setAccountIdentifier(harnessAccount);
    streamingDestination.setConnectorRef(connectorRef);

    return streamingDestination;
  }
}
