/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.api.streaming.impl;

import static io.harness.beans.SortOrder.Builder.aSortOrder;
import static io.harness.beans.SortOrder.OrderType.DESC;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.REETIKA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.audit.events.StreamingDestinationCreateEvent;
import io.harness.audit.events.StreamingDestinationDeleteEvent;
import io.harness.audit.events.StreamingDestinationUpdateEvent;
import io.harness.audit.mapper.streaming.StreamingDestinationMapper;
import io.harness.audit.repositories.streaming.StreamingDestinationRepository;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NoResultFoundException;
import io.harness.ng.beans.PageRequest;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.spec.server.audit.v1.model.AwsS3StreamingDestinationSpecDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationStatus;
import io.harness.utils.PageUtils;

import com.mongodb.BasicDBList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.RandomUtils;
import org.bson.Document;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class StreamingServiceImplTest extends CategoryTest {
  private static final int RANDOM_STRING_CHAR_COUNT_10 = 10;
  private static final int RANDOM_STRING_CHAR_COUNT_15 = 15;
  private String accountIdentifier;
  private String id;
  private String identifier;
  private String name;
  private StreamingDestinationStatus statusEnum;
  private String bucket;
  private String connectorRef;

  @Mock private StreamingDestinationMapper streamingDestinationMapper;
  @Mock private StreamingDestinationRepository streamingDestinationRepository;
  private StreamingServiceImpl streamingService;
  @Mock OutboxService outboxService;
  @Mock TransactionTemplate transactionTemplate;
  @Mock private AccessControlClient accessControlClient;
  @Mock private ConnectorResourceClient connectorResourceClient;

  @Rule public ExpectedException expectedException = ExpectedException.none();
  @Captor private ArgumentCaptor<StreamingDestination> streamingDestinationArgumentCaptor;
  @Captor private ArgumentCaptor<Criteria> criteriaArgumentCaptor;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.streamingService = new StreamingServiceImpl(streamingDestinationMapper, streamingDestinationRepository,
        outboxService, transactionTemplate, connectorResourceClient, accessControlClient);
    accountIdentifier = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    id = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    identifier = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    name = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_15);
    statusEnum =
        StreamingDestinationStatus.values()[RandomUtils.nextInt(0, StreamingDestinationStatus.values().length - 1)];
    bucket = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    connectorRef = "account." + randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreate() {
    StreamingDestinationDTO streamingDestinationDTO = getStreamingDestinationDTO();
    StreamingDestination streamingDestination = getStreamingDestination();
    MockedStatic<NGRestUtils> ngRestUtilsMocked = Mockito.mockStatic(NGRestUtils.class);
    ngRestUtilsMocked.when(() -> NGRestUtils.getResponse(any()))
        .thenAnswer(invocationOnMock -> Optional.of(ConnectorDTO.builder().build()));

    when(streamingDestinationMapper.toStreamingDestinationEntity(accountIdentifier, streamingDestinationDTO))
        .thenReturn(streamingDestination);
    when(streamingDestinationRepository.save(streamingDestination)).thenReturn(streamingDestination);

    StreamingDestination savedStreamingDestination =
        streamingService.create(accountIdentifier, streamingDestinationDTO);

    verify(streamingDestinationMapper, times(1))
        .toStreamingDestinationEntity(accountIdentifier, streamingDestinationDTO);
    verify(streamingDestinationRepository, times(1)).save(streamingDestinationArgumentCaptor.capture());
    verify(outboxService, times(1)).save(any(StreamingDestinationCreateEvent.class));
    assertThat(streamingDestinationArgumentCaptor.getValue()).isEqualTo(streamingDestination);
    assertThat(savedStreamingDestination).isNotNull();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreateForDuplicateKeyException() {
    StreamingDestination streamingDestination = getStreamingDestination();
    StreamingDestinationDTO streamingDestinationDTO = getStreamingDestinationDTO();
    MockedStatic<NGRestUtils> ngRestUtilsMocked = Mockito.mockStatic(NGRestUtils.class);
    ngRestUtilsMocked.when(() -> NGRestUtils.getResponse(any()))
        .thenAnswer(invocationOnMock -> Optional.of(ConnectorDTO.builder().build()));

    when(streamingDestinationMapper.toStreamingDestinationEntity(anyString(), any())).thenReturn(streamingDestination);
    when(streamingDestinationRepository.save(any())).thenThrow(new DuplicateKeyException("duplicate key error"));

    expectedException.expect(DuplicateFieldException.class);
    expectedException.expectMessage(String.format(
        "Streaming destination with identifier [%s] already exists.", streamingDestinationDTO.getIdentifier()));

    streamingService.create(randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10), streamingDestinationDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testList() {
    String searchTerm = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    StreamingDestinationStatus statusEnum = StreamingDestinationStatus.ACTIVE;
    int page = 0;
    int limit = 10;
    Pageable pageable = PageUtils.getPageRequest(new PageRequest(
        page, limit, List.of(aSortOrder().withField(StreamingDestinationKeys.lastModifiedDate, DESC).build())));
    StreamingDestinationFilterProperties filterProperties =
        StreamingDestinationFilterProperties.builder().searchTerm(searchTerm).status(statusEnum).build();

    when(streamingDestinationRepository.findAll(any(), any()))
        .thenReturn(new PageImpl<StreamingDestination>(List.of(
            AwsS3StreamingDestination.builder().accountIdentifier(accountIdentifier).identifier("sd1").build())));
    when(accessControlClient.checkForAccessOrThrow(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Collections.singletonList(
                            AccessControlDTO.builder()
                                .resourceIdentifier("sd1")
                                .resourceScope(ResourceScope.of(accountIdentifier, null, null))
                                .permitted(true)
                                .build()))
                        .build());

    Page<StreamingDestination> streamingDestinationsPage =
        streamingService.list(accountIdentifier, pageable, filterProperties);

    verify(streamingDestinationRepository, times(1)).findAll(criteriaArgumentCaptor.capture(), eq(Pageable.unpaged()));

    assertThat(streamingDestinationsPage).isNotEmpty();
    assertCriteria(accountIdentifier, filterProperties, criteriaArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetStreamingDestination() {
    StreamingDestination streamingDestination = getStreamingDestination();

    when(streamingDestinationRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.of(streamingDestination));

    StreamingDestination savedStreamingDestination =
        streamingService.getStreamingDestination(accountIdentifier, identifier);

    verify(streamingDestinationRepository, times(1)).findByAccountIdentifierAndIdentifier(anyString(), anyString());

    assertThat(savedStreamingDestination).isEqualTo(streamingDestination);
    assertThat(savedStreamingDestination).isNotNull();
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void validateUniquenessOfValidStreamingDestination() {
    StreamingDestination streamingDestination = getStreamingDestination();

    when(streamingDestinationRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.of(streamingDestination));

    boolean valid = streamingService.validateUniqueness(accountIdentifier, identifier);
    verify(streamingDestinationRepository, times(1)).findByAccountIdentifierAndIdentifier(anyString(), anyString());
    assertThat(valid).isFalse();
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void validateUniquenessOfInvalidStreamingDestination() {
    when(streamingDestinationRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.empty());

    boolean valid = streamingService.validateUniqueness(accountIdentifier, identifier);
    verify(streamingDestinationRepository, times(1)).findByAccountIdentifierAndIdentifier(anyString(), anyString());
    assertThat(valid).isTrue();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetStreamingDestination_withNotFoundException() {
    when(streamingDestinationRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> streamingService.getStreamingDestination(accountIdentifier, identifier))
        .hasMessage(String.format("Streaming destination with identifier [%s] not found.", identifier))
        .isInstanceOf(NoResultFoundException.class);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testDeleteStreamingDestination() {
    StreamingDestination streamingDestination = getStreamingDestination();
    streamingDestination.setStatus(StreamingDestinationStatus.INACTIVE);

    when(streamingDestinationRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.of(streamingDestination));
    when(streamingDestinationRepository.deleteByCriteria(any())).thenReturn(Boolean.TRUE);

    boolean isDeleted = streamingService.delete(accountIdentifier, identifier);

    verify(streamingDestinationRepository, times(1)).findByAccountIdentifierAndIdentifier(anyString(), anyString());
    verify(streamingDestinationRepository, times(1)).deleteByCriteria(criteriaArgumentCaptor.capture());
    verify(outboxService, times(1)).save(any(StreamingDestinationDeleteEvent.class));

    assertThat(isDeleted).isTrue();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testDeleteStreamingDestination_withInvalidRequestException() {
    StreamingDestination streamingDestination = getStreamingDestination();
    streamingDestination.setStatus(StreamingDestinationStatus.ACTIVE);

    when(streamingDestinationRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.of(streamingDestination));
    when(streamingDestinationRepository.deleteByCriteria(any())).thenReturn(Boolean.TRUE);

    assertThatThrownBy(() -> streamingService.delete(accountIdentifier, identifier))
        .hasMessage(String.format(
            "Streaming destination with identifier [%s] cannot be deleted because it is active.", identifier))
        .isInstanceOf(InvalidRequestException.class);

    verify(streamingDestinationRepository, times(1)).findByAccountIdentifierAndIdentifier(anyString(), anyString());
    verify(streamingDestinationRepository, times(0)).deleteByCriteria(criteriaArgumentCaptor.capture());
    verify(outboxService, times(0)).save(any(StreamingDestinationDeleteEvent.class));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdateStreamingDestination() throws Exception {
    StreamingDestinationDTO streamingDestinationDTO = getStreamingDestinationDTO();
    streamingDestinationDTO.setName(name + " changed");
    streamingDestinationDTO.setStatus(StreamingDestinationStatus.INACTIVE);
    streamingDestinationDTO.setSpec(new AwsS3StreamingDestinationSpecDTO()
                                        .bucket(bucket + " changed")
                                        .type(StreamingDestinationSpecDTO.TypeEnum.AWS_S3));

    StreamingDestination currentStreamingDestination = getStreamingDestination();

    AwsS3StreamingDestination newStreamingDestination = (AwsS3StreamingDestination) getStreamingDestination();
    newStreamingDestination.setName(name + " changed");
    newStreamingDestination.setStatus(StreamingDestinationStatus.INACTIVE);
    newStreamingDestination.setBucket(bucket + " changed");

    when(streamingDestinationRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.of(currentStreamingDestination));
    when(streamingDestinationMapper.toStreamingDestinationEntity(anyString(), any()))
        .thenReturn(newStreamingDestination);
    when(streamingDestinationRepository.save(any())).thenReturn(newStreamingDestination);

    StreamingDestination responseStreamingDestination =
        streamingService.update(identifier, streamingDestinationDTO, accountIdentifier);

    verify(streamingDestinationRepository, times(1)).findByAccountIdentifierAndIdentifier(anyString(), anyString());
    verify(streamingDestinationRepository, times(1)).save(any());
    verify(outboxService, times(1)).save(any(StreamingDestinationUpdateEvent.class));

    assertThat(responseStreamingDestination).isEqualTo(newStreamingDestination);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdateStreamingDestination_withInvalidRequestException_forUnmatchedIdentifierInApiArgument() {
    StreamingDestinationDTO streamingDestinationDTO = getStreamingDestinationDTO();
    streamingDestinationDTO.setIdentifier(identifier + " changed");

    StreamingDestination currentStreamingDestination = getStreamingDestination();
    when(streamingDestinationRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.of(currentStreamingDestination));

    assertThatThrownBy(() -> streamingService.update(identifier, streamingDestinationDTO, accountIdentifier))
        .hasMessage(String.format(
            "Streaming destination with identifier [%s] did not match with StreamingDestinationDTO identifier [%s]",
            currentStreamingDestination.getIdentifier(), streamingDestinationDTO.getIdentifier()))
        .isInstanceOf(InvalidRequestException.class);

    verify(streamingDestinationRepository, times(1)).findByAccountIdentifierAndIdentifier(anyString(), anyString());
    verify(streamingDestinationRepository, times(0)).save(any());
    verify(outboxService, times(0)).save(any(StreamingDestinationUpdateEvent.class));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdateStreamingDestination_withInvalidRequestException_forUnmatchedConnectorRef() {
    StreamingDestinationDTO streamingDestinationDTO = getStreamingDestinationDTO();
    streamingDestinationDTO.setConnectorRef(connectorRef + " changed");

    StreamingDestination currentStreamingDestination = getStreamingDestination();
    when(streamingDestinationRepository.findByAccountIdentifierAndIdentifier(anyString(), anyString()))
        .thenReturn(Optional.of(currentStreamingDestination));

    assertThatThrownBy(() -> streamingService.update(identifier, streamingDestinationDTO, accountIdentifier))
        .hasMessage(String.format(
            "Streaming destination with connectorRef [%s] did not match with StreamingDestinationDTO connectorRef [%s]",
            currentStreamingDestination.getConnectorRef(), streamingDestinationDTO.getConnectorRef()))
        .isInstanceOf(InvalidRequestException.class);

    verify(streamingDestinationRepository, times(1)).findByAccountIdentifierAndIdentifier(anyString(), anyString());
    verify(streamingDestinationRepository, times(0)).save(any());
    verify(outboxService, times(0)).save(any(StreamingDestinationUpdateEvent.class));
  }

  private void assertCriteria(
      String accountIdentifier, StreamingDestinationFilterProperties filterProperties, Criteria criteria) {
    assertThat(criteria.getCriteriaObject())
        .contains(Map.entry(StreamingDestinationKeys.accountIdentifier, accountIdentifier),
            Map.entry(StreamingDestinationKeys.status, filterProperties.getStatus()));
    assertThat(criteria.getCriteriaObject()).containsKey("$or");
    BasicDBList orCriteriaList = (BasicDBList) criteria.getCriteriaObject().get("$or");
    assertThat(orCriteriaList).isNotEmpty().hasSize(2);
    Document nameMatcher = (Document) orCriteriaList.get(0);
    assertThat(nameMatcher.get(StreamingDestinationKeys.name)).isInstanceOf(Pattern.class);
    Pattern nameMatcherPattern = (Pattern) nameMatcher.get(StreamingDestinationKeys.name);
    assertThat(nameMatcherPattern.pattern()).isEqualTo(filterProperties.getSearchTerm());
    assertThat(nameMatcherPattern.flags()).isEqualTo(Pattern.CASE_INSENSITIVE);
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
    streamingDestination.setId(id);
    streamingDestination.setIdentifier(identifier);
    streamingDestination.setName(name);
    streamingDestination.setType(StreamingDestinationSpecDTO.TypeEnum.AWS_S3);
    streamingDestination.setStatus(statusEnum);
    streamingDestination.setAccountIdentifier(accountIdentifier);
    streamingDestination.setConnectorRef(connectorRef);

    return streamingDestination;
  }
}
