/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.NoResultFoundException;
import io.harness.ipallowlist.IPAllowlistResourceUtils;
import io.harness.ipallowlist.entity.IPAllowlistEntity;
import io.harness.ipallowlist.events.IPAllowlistConfigCreateEvent;
import io.harness.ipallowlist.events.IPAllowlistConfigDeleteEvent;
import io.harness.ipallowlist.events.IPAllowlistConfigUpdateEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ipallowlist.spring.IPAllowlistRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.AllowedSourceType;

import java.util.List;
import java.util.Optional;
import javax.validation.Validator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class IPAllowlistServiceImplTest extends CategoryTest {
  @Mock private IPAllowlistRepository ipAllowlistRepository;
  @Mock OutboxService outboxService;
  @Mock TransactionTemplate transactionTemplate;
  private IPAllowlistResourceUtils ipAllowlistResourceUtil;
  private IPAllowlistServiceImpl ipAllowlistService;
  private Validator validator;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();
  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);

  private static final String IDENTIFIER = randomAlphabetic(10);
  private static final String NAME = randomAlphabetic(10);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    validator = mock(Validator.class);
    this.ipAllowlistResourceUtil = new IPAllowlistResourceUtils(validator);
    this.ipAllowlistService =
        new IPAllowlistServiceImpl(ipAllowlistRepository, outboxService, transactionTemplate, ipAllowlistResourceUtil);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreate() {
    IPAllowlistEntity ipAllowlistEntity = getIPAllowlistEntity();
    when(ipAllowlistRepository.save(ipAllowlistEntity)).thenReturn(ipAllowlistEntity);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    IPAllowlistEntity result = ipAllowlistService.create(ipAllowlistEntity);
    verify(outboxService, times(1)).save(any(IPAllowlistConfigCreateEvent.class));
    assertThat(result).isNotNull();
    assertThat(result).isEqualToComparingFieldByField(ipAllowlistEntity);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateWithDuplicateException() {
    IPAllowlistEntity ipAllowlistEntity = getIPAllowlistEntity();
    when(ipAllowlistRepository.save(ipAllowlistEntity)).thenThrow(DuplicateKeyException.class);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    exceptionRule.expect(DuplicateFieldException.class);
    exceptionRule.expectMessage(String.format("IP Allowlist config with identifier [%s] already exists.", IDENTIFIER));
    ipAllowlistService.create(ipAllowlistEntity);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGet() {
    Optional<IPAllowlistEntity> ipAllowlistEntity = Optional.ofNullable(getIPAllowlistEntity());
    when(ipAllowlistRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_IDENTIFIER, IDENTIFIER))
        .thenReturn(ipAllowlistEntity);
    IPAllowlistEntity result = ipAllowlistService.get(ACCOUNT_IDENTIFIER, IDENTIFIER);
    assertThat(result).isNotNull();
    assertThat(result).isEqualToComparingFieldByField(ipAllowlistEntity.get());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGet_notFound() {
    when(ipAllowlistRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.empty());
    exceptionRule.expect(NoResultFoundException.class);
    exceptionRule.expectMessage(String.format("IP Allowlist config with identifier [%s] not found.", IDENTIFIER));
    ipAllowlistService.get(ACCOUNT_IDENTIFIER, IDENTIFIER);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testUpdate() {
    IPAllowlistEntity ipAllowlistEntity = getIPAllowlistEntity();
    Optional<IPAllowlistEntity> ipAllowlistEntityOptional = Optional.ofNullable(getIPAllowlistEntity());
    when(ipAllowlistRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_IDENTIFIER, IDENTIFIER))
        .thenReturn(ipAllowlistEntityOptional);
    when(ipAllowlistRepository.save(ipAllowlistEntity)).thenReturn(ipAllowlistEntity);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    IPAllowlistEntity result = ipAllowlistService.update(IDENTIFIER, ipAllowlistEntity);
    verify(outboxService, times(1)).save(any(IPAllowlistConfigUpdateEvent.class));
    assertThat(result).isNotNull();
    assertThat(result).isEqualToComparingFieldByField(ipAllowlistEntity);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete() {
    Optional<IPAllowlistEntity> ipAllowlistEntity = Optional.ofNullable(getIPAllowlistEntity());
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(ipAllowlistRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_IDENTIFIER, IDENTIFIER))
        .thenReturn(ipAllowlistEntity);
    boolean result = ipAllowlistService.delete(ACCOUNT_IDENTIFIER, IDENTIFIER);
    verify(outboxService, times(1)).save(any(IPAllowlistConfigDeleteEvent.class));
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_notFound() {
    when(ipAllowlistRepository.findByAccountIdentifierAndIdentifier(ACCOUNT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.empty());
    exceptionRule.expect(NoResultFoundException.class);
    exceptionRule.expectMessage(String.format("IP Allowlist config with identifier [%s] not found.", IDENTIFIER));
    ipAllowlistService.delete(ACCOUNT_IDENTIFIER, IDENTIFIER);
  }

  private IPAllowlistEntity getIPAllowlistEntity() {
    return IPAllowlistEntity.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .identifier(IDENTIFIER)
        .name(NAME)
        .description("description")
        .allowedSourceType(List.of(AllowedSourceType.UI))
        .enabled(true)
        .ipAddress("1.2.3.4")
        .build();
  }
}
