/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.deploymentsummary;

import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.entities.DeploymentSummary;
import io.harness.entities.DeploymentSummary.DeploymentSummaryKeys;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.TransactionTimedOutException;

public class DeploymentSummaryCustomImplTest extends InstancesTestBase {
  private static final String INFRA_MAPPING_ID = "TEST_INFRA_MAPPING_ID";
  private static final String ACCOUNT_ID = "TEST_ACCOUNT_ID";
  private static final String ORG_ID = "TEST_ORG_ID";
  private static final String PROJECT_ID = "TEST_PROJECT_ID";
  private static final String ENV_ID = "ENV_ID";
  private static final String SERVICE_ID = "TEST_SERVICE_ID";
  private static final String INFRA_KEY = "TEST_INFRA_KEY";
  private static final String INSTANCE_SYNC_KEY = "instanceSyncKey";
  private static final String PIPELINE_EXECUTION_ID = "PIPELINE_EXECUTION_ID";
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks DeploymentSummaryCustomImpl deploymentSummaryCustom;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void fetchNthRecordFromNowTest() {
    int N = 5;
    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO();
    Criteria criteria = Criteria.where(DeploymentSummaryKeys.instanceSyncKey)
                            .is(INSTANCE_SYNC_KEY)
                            .and(DeploymentSummaryKeys.accountIdentifier)
                            .is(infrastructureMappingDTO.getAccountIdentifier());

    if (EmptyPredicate.isNotEmpty(infrastructureMappingDTO.getOrgIdentifier())) {
      criteria.and(DeploymentSummaryKeys.orgIdentifier).is(infrastructureMappingDTO.getOrgIdentifier());
    }
    if (EmptyPredicate.isNotEmpty(infrastructureMappingDTO.getProjectIdentifier())) {
      criteria.and(DeploymentSummaryKeys.projectIdentifier).is(infrastructureMappingDTO.getProjectIdentifier());
    }

    criteria.and(DeploymentSummaryKeys.infrastructureMappingId).is(infrastructureMappingDTO.getId());

    Query query = new Query().addCriteria(criteria);
    query.with(Sort.by(Sort.Direction.DESC, DeploymentSummaryKeys.createdAt));
    query.skip((long) N - 1);
    query.limit(1);
    DeploymentSummary deploymentSummary = DeploymentSummary.builder().build();
    List<DeploymentSummary> deploymentSummaryList = Arrays.asList(deploymentSummary);
    when(mongoTemplate.find(query, DeploymentSummary.class)).thenReturn(deploymentSummaryList);
    assertThat(deploymentSummaryCustom.fetchNthRecordFromNow(N, INSTANCE_SYNC_KEY, infrastructureMappingDTO).get())
        .isEqualTo(deploymentSummary);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void fetchLatestByInstanceKeyAndPipelineExecutionIdNotTest() {
    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO();
    Criteria criteria = Criteria.where(DeploymentSummaryKeys.instanceSyncKey)
                            .is(INSTANCE_SYNC_KEY)
                            .and(DeploymentSummaryKeys.accountIdentifier)
                            .is(infrastructureMappingDTO.getAccountIdentifier())
                            .and(DeploymentSummaryKeys.orgIdentifier)
                            .is(infrastructureMappingDTO.getOrgIdentifier())
                            .and(DeploymentSummaryKeys.projectIdentifier)
                            .is(infrastructureMappingDTO.getProjectIdentifier())
                            .and(DeploymentSummaryKeys.infrastructureMappingId)
                            .is(infrastructureMappingDTO.getId())
                            .and(DeploymentSummaryKeys.pipelineExecutionId)
                            .ne(PIPELINE_EXECUTION_ID);
    Query query = new Query().addCriteria(criteria);
    query.with(Sort.by(Sort.Direction.DESC, DeploymentSummaryKeys.createdAt));
    query.limit(1);

    DeploymentSummary deploymentSummary = DeploymentSummary.builder().build();
    List<DeploymentSummary> deploymentSummaryList = Arrays.asList(deploymentSummary);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    when(mongoTemplate.find(any(), eq(DeploymentSummary.class))).thenReturn(deploymentSummaryList);
    Optional<DeploymentSummary> deploymentSummaryResult =
        deploymentSummaryCustom.fetchLatestByInstanceKeyAndPipelineExecutionIdNot(
            INSTANCE_SYNC_KEY, infrastructureMappingDTO, PIPELINE_EXECUTION_ID);

    verify(mongoTemplate).find(queryArgumentCaptor.capture(), eq(DeploymentSummary.class));
    assertThat(queryArgumentCaptor.getValue().getQueryObject()).isEqualTo(query.getQueryObject());
    assertThat(deploymentSummaryResult.get()).isEqualTo(deploymentSummary);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFetchNthRecordFromNowWhenDocumentsAreNotPresent() {
    int n = 5;
    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO();
    Criteria criteria = Criteria.where(DeploymentSummaryKeys.instanceSyncKey).is(INSTANCE_SYNC_KEY);
    Query query = new Query().addCriteria(criteria);
    query.with(Sort.by(Sort.Direction.DESC, DeploymentSummaryKeys.createdAt));
    query.skip((long) n - 1);
    query.limit(1);
    when(mongoTemplate.find(query, DeploymentSummary.class)).thenReturn(Collections.emptyList());
    Optional<DeploymentSummary> record =
        deploymentSummaryCustom.fetchNthRecordFromNow(n, INSTANCE_SYNC_KEY, infrastructureMappingDTO);
    assertFalse(record.isPresent());
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFetchNthRecordFromNowWhenInfraMappingDTOIsNull() {
    deploymentSummaryCustom.fetchNthRecordFromNow(5, INSTANCE_SYNC_KEY, null);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testDeleteWithAccountOrgAndProject() {
    Criteria criteria = new Criteria();
    criteria.and(DeploymentSummaryKeys.accountIdentifier)
        .is(ACCOUNT_ID)
        .and(DeploymentSummaryKeys.orgIdentifier)
        .is(ORG_ID)
        .and(DeploymentSummaryKeys.projectIdentifier)
        .is(PROJECT_ID);
    Query query = new Query(criteria);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);

    deploymentSummaryCustom.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    verify(mongoTemplate).remove(queryArgumentCaptor.capture(), eq(DeploymentSummary.class));
    assertThat(queryArgumentCaptor.getValue().getQueryObject()).isEqualTo(query.getQueryObject());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testDeleteWithOnlyAccountId() {
    Criteria criteria = new Criteria();
    criteria.and(DeploymentSummaryKeys.accountIdentifier).is(ACCOUNT_ID);
    Query query = new Query(criteria);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);

    deploymentSummaryCustom.delete(ACCOUNT_ID, null, null);

    verify(mongoTemplate).remove(queryArgumentCaptor.capture(), eq(DeploymentSummary.class));
    assertThat(queryArgumentCaptor.getValue().getQueryObject()).isEqualTo(query.getQueryObject());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testDeleteWithoutAccountId() {
    boolean result = deploymentSummaryCustom.delete(null, null, null);

    verifyNoInteractions(mongoTemplate);
    assertFalse(result);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testDeleteWithAccountOrgAndProjectRetries() {
    when(mongoTemplate.remove(any(), eq(DeploymentSummary.class)))
        .thenThrow(new TransactionTimedOutException("Failed"));

    deploymentSummaryCustom.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    verify(mongoTemplate, times(3)).remove(any(), eq(DeploymentSummary.class));
  }
  private InfrastructureMappingDTO mockInfraMappingDTO() {
    return InfrastructureMappingDTO.builder()
        .id(INFRA_MAPPING_ID)
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
        .envIdentifier(ENV_ID)
        .serviceIdentifier(SERVICE_ID)
        .infrastructureKey(INFRA_KEY)
        .build();
  }
}
