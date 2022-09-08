/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.instance;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.entities.Instance;
import io.harness.entities.Instance.InstanceKeys;
import io.harness.entities.Instance.InstanceKeysAdditional;
import io.harness.models.CountByServiceIdAndEnvType;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.InstancesByBuildId;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class InstanceRepositoryCustomImplTest extends InstancesTestBase {
  private final String ACCOUNT_ID = "acc";
  private final String ORGANIZATION_ID = "org";
  private final String PROJECT_ID = "project";
  private final String SERVICE_ID = "service";
  private final String INFRASTRUCTURE_MAPPING_ID = "infra";
  private final String INSTANCE_INFO_PODNAME = "podname";
  private final String INSTANCE_INFO_POD_NAMESPACE = "podnamespace";
  private final String ENVIRONMENT_ID = "envID";
  private final String ENVIRONMENT_NAME = "envName";
  private final String TAG = "tag";
  private final int COUNT = 3;
  private final long TIMESTAMP = 123L;
  private final long START_TIMESTAMP = 124L;
  private final long END_TIMESTAMP = 125L;
  @Mock MongoTemplate mongoTemplate;
  @InjectMocks InstanceRepositoryCustomImpl instanceRepositoryCustom;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void findAndReplaceTest() {
    Criteria criteria = Criteria.where("key");
    Instance instance = Instance.builder().build();
    Query query = new Query(criteria);
    when(mongoTemplate.findAndReplace(eq(query), eq(instance), any(FindAndReplaceOptions.class))).thenReturn(instance);
    assertThat(instanceRepositoryCustom.findAndReplace(criteria, instance)).isEqualTo(instance);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void findAndModifyTest() {
    Criteria criteria = Criteria.where("key");
    Update update = new Update();
    Instance instance = Instance.builder().build();
    Query query = new Query(criteria);
    when(mongoTemplate.findAndModify(query, update, Instance.class)).thenReturn(instance);
    assertThat(instanceRepositoryCustom.findAndModify(criteria, update)).isEqualTo(instance);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByAccountTest() {
    Instance instance1 = Instance.builder().instanceKey("abc").build();
    Criteria criteria1 = Criteria.where(InstanceKeys.accountIdentifier).is(ACCOUNT_ID);
    criteria1.andOperator(
        Criteria.where(InstanceKeys.isDeleted).is(false), Criteria.where(InstanceKeys.createdAt).lte(TIMESTAMP));
    Query query1 = new Query().addCriteria(criteria1);
    when(mongoTemplate.find(query1, Instance.class)).thenReturn(Collections.singletonList(instance1));

    Instance instance2 = Instance.builder().instanceKey("def").build();
    Criteria criteria2 = Criteria.where(InstanceKeys.accountIdentifier).is(ACCOUNT_ID);
    criteria2.andOperator(
        Criteria.where(InstanceKeys.deletedAt).gte(TIMESTAMP), Criteria.where(InstanceKeys.createdAt).lte(TIMESTAMP));
    Query query2 = new Query().addCriteria(criteria2);
    when(mongoTemplate.find(query2, Instance.class)).thenReturn(Collections.singletonList(instance2));

    assertThat(instanceRepositoryCustom.getActiveInstancesByAccount(ACCOUNT_ID, TIMESTAMP))
        .containsExactlyInAnyOrderElementsOf(Arrays.asList(instance1, instance2));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInstancesDeployedInIntervalTest() {
    Criteria criteria = Criteria.where(InstanceKeys.accountIdentifier)
                            .is(ACCOUNT_ID)
                            .and(InstanceKeys.lastDeployedAt)
                            .gte(START_TIMESTAMP)
                            .lte(END_TIMESTAMP);
    Instance instance = Instance.builder().build();
    Query query = new Query().addCriteria(criteria);
    when(mongoTemplate.find(query, Instance.class)).thenReturn(Arrays.asList(instance));
    assertThat(instanceRepositoryCustom.getInstancesDeployedInInterval(ACCOUNT_ID, START_TIMESTAMP, END_TIMESTAMP))
        .isEqualTo(Arrays.asList(instance));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInstancesDeployedInIntervalWithOtherParameterTest() {
    Criteria criteria = Criteria.where(InstanceKeys.accountIdentifier)
                            .is(ACCOUNT_ID)
                            .and(InstanceKeys.orgIdentifier)
                            .is(ORGANIZATION_ID)
                            .and(InstanceKeys.projectIdentifier)
                            .is(PROJECT_ID)
                            .and(InstanceKeys.lastDeployedAt)
                            .gte(START_TIMESTAMP)
                            .lte(END_TIMESTAMP);
    Instance instance = Instance.builder().build();
    Query query = new Query().addCriteria(criteria);
    when(mongoTemplate.find(query, Instance.class)).thenReturn(Arrays.asList(instance));
    assertThat(instanceRepositoryCustom.getInstancesDeployedInInterval(
                   ACCOUNT_ID, ORGANIZATION_ID, PROJECT_ID, START_TIMESTAMP, END_TIMESTAMP))
        .isEqualTo(Arrays.asList(instance));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getInstancesTest() {
    assertThat(
        instanceRepositoryCustom.getInstances(ACCOUNT_ID, ORGANIZATION_ID, PROJECT_ID, INFRASTRUCTURE_MAPPING_ID))
        .isNull();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesTest() {
    Criteria baseCriteria = Criteria.where(InstanceKeys.accountIdentifier)
                                .is(ACCOUNT_ID)
                                .and(InstanceKeys.orgIdentifier)
                                .is(ORGANIZATION_ID)
                                .and(InstanceKeys.projectIdentifier)
                                .is(PROJECT_ID);

    Criteria filterCreatedAt = Criteria.where(InstanceKeys.createdAt).lte(TIMESTAMP);
    Criteria filterDeletedAt = Criteria.where(InstanceKeys.deletedAt).gte(TIMESTAMP);
    Criteria filterNotDeleted = Criteria.where(InstanceKeys.isDeleted).is(false);

    Criteria criteria = baseCriteria.andOperator(filterCreatedAt.orOperator(filterNotDeleted, filterDeletedAt));
    Instance instance = Instance.builder().build();
    Query query = new Query().addCriteria(criteria);
    when(mongoTemplate.find(query, Instance.class)).thenReturn(Arrays.asList(instance));
    assertThat(instanceRepositoryCustom.getActiveInstances(ACCOUNT_ID, ORGANIZATION_ID, PROJECT_ID, TIMESTAMP))
        .isEqualTo(Arrays.asList(instance));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByInstanceInfoTest() {
    Criteria criteria = Criteria.where(InstanceKeys.accountIdentifier)
                            .is(ACCOUNT_ID)
                            .and(InstanceKeysAdditional.instanceInfoPodName)
                            .is(INSTANCE_INFO_PODNAME)
                            .and(InstanceKeysAdditional.instanceInfoNamespace)
                            .is(INSTANCE_INFO_POD_NAMESPACE);
    Query query = new Query().addCriteria(criteria).with(Sort.by(Sort.Direction.DESC, InstanceKeys.createdAt));
    Instance instance = Instance.builder().build();
    when(mongoTemplate.find(query, Instance.class)).thenReturn(Arrays.asList(instance));
    assertThat(instanceRepositoryCustom.getActiveInstancesByInstanceInfo(
                   ACCOUNT_ID, INSTANCE_INFO_POD_NAMESPACE, INSTANCE_INFO_PODNAME))
        .isEqualTo(Arrays.asList(instance));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByServiceIdTest() {
    Criteria baseCriteria = Criteria.where(InstanceKeys.accountIdentifier)
                                .is(ACCOUNT_ID)
                                .and(InstanceKeys.orgIdentifier)
                                .is(ORGANIZATION_ID)
                                .and(InstanceKeys.projectIdentifier)
                                .is(PROJECT_ID);

    Criteria filterCreatedAt = Criteria.where(InstanceKeys.createdAt).lte(TIMESTAMP);
    Criteria filterDeletedAt = Criteria.where(InstanceKeys.deletedAt).gte(TIMESTAMP);
    Criteria filterNotDeleted = Criteria.where(InstanceKeys.isDeleted).is(false);

    Criteria criteria = baseCriteria.andOperator(filterCreatedAt.orOperator(filterNotDeleted, filterDeletedAt))
                            .and(InstanceKeys.serviceIdentifier)
                            .is(SERVICE_ID);
    Query query = new Query().addCriteria(criteria);
    Instance instance = Instance.builder().build();
    when(mongoTemplate.find(query, Instance.class)).thenReturn(Arrays.asList(instance));
    assertThat(instanceRepositoryCustom.getActiveInstancesByServiceId(
                   ACCOUNT_ID, ORGANIZATION_ID, PROJECT_ID, SERVICE_ID, TIMESTAMP))
        .isEqualTo(Arrays.asList(instance));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByInfrastructureMappingIdTest() {
    Criteria criteria = Criteria.where(InstanceKeys.accountIdentifier)
                            .is(ACCOUNT_ID)
                            .and(InstanceKeys.orgIdentifier)
                            .is(ORGANIZATION_ID)
                            .and(InstanceKeys.projectIdentifier)
                            .is(PROJECT_ID)
                            .and(InstanceKeys.infrastructureMappingId)
                            .is(INFRASTRUCTURE_MAPPING_ID)
                            .and(InstanceKeys.isDeleted)
                            .is(false);
    Query query = new Query().addCriteria(criteria);
    Instance instance = Instance.builder().build();
    when(mongoTemplate.find(query, Instance.class)).thenReturn(Arrays.asList(instance));
    assertThat(instanceRepositoryCustom.getActiveInstancesByInfrastructureMappingId(
                   ACCOUNT_ID, ORGANIZATION_ID, PROJECT_ID, INFRASTRUCTURE_MAPPING_ID))
        .isEqualTo(Arrays.asList(instance));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getEnvBuildInstanceCountByServiceIdTest() {
    EnvBuildInstanceCount envBuildInstanceCount =
        new EnvBuildInstanceCount(ENVIRONMENT_ID, ENVIRONMENT_NAME, TAG, COUNT);
    AggregationResults<EnvBuildInstanceCount> aggregationResults =
        new AggregationResults<>(Arrays.asList(envBuildInstanceCount), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(Instance.class), eq(EnvBuildInstanceCount.class)))
        .thenReturn(aggregationResults);
    assertThat(instanceRepositoryCustom.getEnvBuildInstanceCountByServiceId(
                   ACCOUNT_ID, ORGANIZATION_ID, PROJECT_ID, SERVICE_ID, TIMESTAMP))
        .isEqualTo(aggregationResults);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByServiceIdEnvIdAndBuildIdsTest() {
    List<String> buildIds = Arrays.asList("build1", "build2");
    int limit = 5;
    Instance instance = Instance.builder().build();
    InstancesByBuildId instancesByBuildId = new InstancesByBuildId("buildId", Arrays.asList(instance));
    AggregationResults<InstancesByBuildId> aggregationResults =
        new AggregationResults<>(Arrays.asList(instancesByBuildId), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(Instance.class), eq(InstancesByBuildId.class)))
        .thenReturn(aggregationResults);
    assertThat(instanceRepositoryCustom.getActiveInstancesByServiceIdEnvIdAndBuildIds(
                   ACCOUNT_ID, ORGANIZATION_ID, PROJECT_ID, SERVICE_ID, ENVIRONMENT_ID, buildIds, TIMESTAMP, limit))
        .isEqualTo(aggregationResults);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceCountBreakdownTest() {
    CountByServiceIdAndEnvType countByServiceIdAndEnvType =
        new CountByServiceIdAndEnvType(SERVICE_ID, EnvironmentType.Production, COUNT);
    AggregationResults<CountByServiceIdAndEnvType> aggregationResults =
        new AggregationResults<>(Arrays.asList(countByServiceIdAndEnvType), new Document());
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(Instance.class), eq(CountByServiceIdAndEnvType.class)))
        .thenReturn(aggregationResults);
    assertThat(instanceRepositoryCustom.getActiveServiceInstanceCountBreakdown(
                   ACCOUNT_ID, ORGANIZATION_ID, PROJECT_ID, Arrays.asList(SERVICE_ID), TIMESTAMP))
        .isEqualTo(aggregationResults);
  }
}
