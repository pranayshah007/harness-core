/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eula.service.impl;

import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eula.AgreementType;
import io.harness.eula.dto.EulaDTO;
import io.harness.eula.entity.Eula;
import io.harness.eula.events.EulaSignEvent;
import io.harness.eula.mapper.EulaMapper;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.eula.spring.EulaRepository;
import io.harness.rule.Owner;

import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class EulaServiceImplTest extends CategoryTest {
  @Mock private OutboxService outboxService;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private EulaMapper eulaMapper;
  @Mock private EulaRepository eulaRepository;
  private EulaServiceImpl eulaServiceImpl;
  private static final String ACCOUNT_ID = "accountId";

  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.openMocks(this);
    eulaServiceImpl = new EulaServiceImpl(eulaRepository, eulaMapper, outboxService, transactionTemplate);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void signEula_agreementAlreadySigned_noNewEulaSignEvent() {
    when(eulaRepository.findByAccountIdentifier(ACCOUNT_ID))
        .thenReturn(Optional.of(
            Eula.builder().signedAgreements(Set.of(AgreementType.AIDA)).accountIdentifier(ACCOUNT_ID).build()));
    assertThat(
        eulaServiceImpl.sign(EulaDTO.builder().agreement(AgreementType.AIDA).accountIdentifier(ACCOUNT_ID).build()))
        .isEqualTo(false);
    verify(outboxService, times(0)).save(any(EulaSignEvent.class));
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testSignEula() {
    EulaDTO eulaDTO = EulaDTO.builder().agreement(AgreementType.AIDA).accountIdentifier(ACCOUNT_ID).build();
    Eula eula = Eula.builder().signedAgreements(Set.of(AgreementType.AIDA)).accountIdentifier(ACCOUNT_ID).build();
    when(eulaRepository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.empty());
    when(eulaMapper.toEntity(eulaDTO)).thenReturn(eula);
    when(eulaRepository.upsert(eula)).thenReturn(eula);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    assertThat(eulaServiceImpl.sign(eulaDTO)).isEqualTo(true);
    verify(outboxService, times(1)).save(any(EulaSignEvent.class));
    verify(eulaRepository, times(1)).upsert(eula);
  }
}