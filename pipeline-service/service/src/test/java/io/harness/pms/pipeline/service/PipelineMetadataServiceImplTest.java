/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.PipelineMetadataV2.PipelineMetadataV2Keys;
import io.harness.repositories.pipeline.PipelineMetadataV2Repository;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public class PipelineMetadataServiceImplTest extends CategoryTest {
  PipelineMetadataServiceImpl pipelineMetadataService;
  @Mock PipelineMetadataV2Repository pipelineMetadataRepository;
  @Mock PersistentLocker persistentLocker;

  static final String ACCOUNT_ID = "account_id";
  static final String ORG_IDENTIFIER = "orgId";
  static final String PROJ_IDENTIFIER = "projId";
  static final String PIPE_IDENTIFIER = "pipelineIdentifier";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    pipelineMetadataService = new PipelineMetadataServiceImpl(pipelineMetadataRepository, persistentLocker);
  }

  private PipelineMetadataV2 getPipelineMetadata(String pipelineId) {
    return getPipelineMetadata(pipelineId, 0);
  }

  private PipelineMetadataV2 getPipelineMetadata(String pipelineId, int runSequence) {
    return PipelineMetadataV2.builder()
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJ_IDENTIFIER)
        .identifier(pipelineId)
        .runSequence(runSequence)
        .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetMetadataForGivenPipelineIds() {
    List<String> pipelineIds = Arrays.asList("p1", "p2", "p3");
    doReturn(Arrays.asList(getPipelineMetadata("p1"), getPipelineMetadata("p2"), getPipelineMetadata("p3")))
        .when(pipelineMetadataRepository)
        .getMetadataForGivenPipelineIds(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, pipelineIds);
    Map<String, PipelineMetadataV2> pipelineMetadataMap = pipelineMetadataService.getMetadataForGivenPipelineIds(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, pipelineIds);
    assertThat(pipelineMetadataMap).hasSize(3);
    assertThat(pipelineMetadataMap.get("p1")).isEqualTo(getPipelineMetadata("p1"));
    assertThat(pipelineMetadataMap.get("p2")).isEqualTo(getPipelineMetadata("p2"));
    assertThat(pipelineMetadataMap.get("p3")).isEqualTo(getPipelineMetadata("p3"));
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSave() {
    PipelineMetadataV2 metadata = getPipelineMetadata("pipelineIdentifierA");
    pipelineMetadataService.save(metadata);
    verify(pipelineMetadataRepository).save(metadata);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldUpdate() {
    Criteria criteria = mock(Criteria.class);
    Update update = mock(Update.class);
    pipelineMetadataService.update(criteria, update);
    verify(pipelineMetadataRepository).update(criteria, update);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldReturnEntityRunSequenceFromRepository() {
    when(pipelineMetadataRepository.incCounter(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPE_IDENTIFIER))
        .thenReturn(getPipelineMetadata(PIPE_IDENTIFIER, 4));
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .identifier(PIPE_IDENTIFIER)
                                .build();
    int result =
        pipelineMetadataService.incrementRunSequence(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPE_IDENTIFIER);
    assertThat(result).isEqualTo(4);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldIncrementRunSequenceValueFromEntityRepository() {
    when(pipelineMetadataRepository.incCounter(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPE_IDENTIFIER))
        .thenReturn(null);
    when(persistentLocker.waitToAcquireLock(anyString(), notNull(), notNull())).thenReturn(mock(AcquiredLock.class));
    when(pipelineMetadataRepository.cloneFromPipelineMetadata(
             ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPE_IDENTIFIER))
        .thenReturn(Optional.empty());
    doAnswer(AdditionalAnswers.returnsLastArg()).when(pipelineMetadataRepository).save(any());

    PipelineEntity entity = PipelineEntity.builder()
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .identifier(PIPE_IDENTIFIER)
                                .runSequence(2)
                                .build();
    int result =
        pipelineMetadataService.incrementRunSequence(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPE_IDENTIFIER);
    assertThat(result).isEqualTo(1);

    ArgumentCaptor<PipelineMetadataV2> arg = ArgumentCaptor.forClass(PipelineMetadataV2.class);
    verify(pipelineMetadataRepository).save(arg.capture());

    final PipelineMetadataV2 metadata = arg.getValue();
    assertThat(metadata.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(metadata.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(metadata.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(metadata.getIdentifier()).isEqualTo(PIPE_IDENTIFIER);
    assertThat(metadata.getRunSequence()).isEqualTo(1);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldUnableToLock() {
    when(pipelineMetadataRepository.incCounter(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPE_IDENTIFIER))
        .thenReturn(null);
    when(persistentLocker.waitToAcquireLock(anyString(), notNull(), notNull())).thenReturn(null);
    PipelineEntity entity = PipelineEntity.builder()
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .identifier(PIPE_IDENTIFIER)
                                .build();
    Assertions
        .assertThatCode(()
                            -> pipelineMetadataService.incrementRunSequence(
                                ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPE_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unable to update build sequence, please retry the execution");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldEnforceLockNameWhenIncrementExecutionCounter() {
    when(pipelineMetadataRepository.incCounter("A", "B", "C", "D")).thenReturn(null);
    when(persistentLocker.waitToAcquireLock(eq("pipelineMetadata/A/B/C/D"), notNull(), notNull()))
        .thenReturn(mock(AcquiredLock.class));
    when(pipelineMetadataRepository.cloneFromPipelineMetadata("A", "B", "C", "D")).thenReturn(Optional.empty());

    int result = pipelineMetadataService.incrementExecutionCounter("A", "B", "C", "D");
    assertThat(result).isEqualTo(-1);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeletePipelineMetadata() {
    Criteria metadataFindCriteria = Criteria.where(PipelineMetadataV2Keys.accountIdentifier)
                                        .is(ACCOUNT_ID)
                                        .and(PipelineMetadataV2Keys.orgIdentifier)
                                        .is(ORG_IDENTIFIER)
                                        .and(PipelineMetadataV2Keys.projectIdentifier)
                                        .is(PROJ_IDENTIFIER)
                                        .and(PipelineMetadataV2Keys.identifier)
                                        .is(PIPE_IDENTIFIER);
    doReturn(true).when(pipelineMetadataRepository).delete(metadataFindCriteria);
    boolean pipelineMetadataDelete =
        pipelineMetadataService.deletePipelineMetadata(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPE_IDENTIFIER);
    assertThat(pipelineMetadataDelete).isTrue();
  }
}
