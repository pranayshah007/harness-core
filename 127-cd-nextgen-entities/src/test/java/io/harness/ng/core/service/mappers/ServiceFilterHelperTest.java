/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mappers;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.rule.Owner;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CDC)
public class ServiceFilterHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetCriteria() {
    String accountId = "ACCOUNT_ID";
    String orgIdentifier = "ORG_ID";
    String projectIdentifier = "PROJECT_ID";
    Criteria criteriaFromServiceFilter =
        CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false);
    assertThat(criteriaFromServiceFilter).isNotNull();
    Document criteriaObject = criteriaFromServiceFilter.getCriteriaObject();
    assertThat(criteriaObject.get(ServiceEntityKeys.accountId)).isEqualTo(accountId);
    assertThat(criteriaObject.get(ServiceEntityKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    assertThat(criteriaObject.get(ServiceEntityKeys.projectIdentifier)).isEqualTo(projectIdentifier);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetUpdateOperations() {
    ServiceEntity serviceEntity = ServiceEntity.builder().build();
    Update updateOperations = ServiceFilterHelper.getUpdateOperations(serviceEntity);
    Set<String> stringSet = ((Document) updateOperations.getUpdateObject().get("$set")).keySet();
    PropertyDescriptor[] propertyDescriptors = BeanUtils.getPropertyDescriptors(ServiceEntity.class);
    Set<String> excludedFields =
        new HashSet<>(Arrays.asList(ServiceEntityKeys.id, ServiceEntityKeys.createdAt, ServiceEntityKeys.deletedAt,
            ServiceEntityKeys.version, ServiceEntityKeys.objectIdOfYaml, ServiceEntityKeys.isFromDefaultBranch,
            ServiceEntityKeys.branch, ServiceEntityKeys.yamlGitConfigRef, ServiceEntityKeys.filePath,
            ServiceEntityKeys.rootFolder, "class", "templateReference", "data", ServiceEntityKeys.storeType,
            ServiceEntityKeys.repo, ServiceEntityKeys.connectorRef, ServiceEntityKeys.repoURL));

    for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
      boolean shouldExist =
          stringSet.contains(propertyDescriptor.getName()) || excludedFields.contains(propertyDescriptor.getName());
      assertThat(shouldExist).isTrue();
    }
    Set<String> setOnInsert = ((Document) updateOperations.getUpdateObject().get("$setOnInsert")).keySet();
    assertThat(setOnInsert).hasSize(1);
    assertThat(setOnInsert).contains(ServiceEntityKeys.createdAt);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetUpdateOperationsForDelete() {
    Update updateOperations = ServiceFilterHelper.getUpdateOperationsForDelete();
    Set<String> stringSet = ((Document) updateOperations.getUpdateObject().get("$set")).keySet();
    PropertyDescriptor[] propertyDescriptors = BeanUtils.getPropertyDescriptors(ServiceEntity.class);

    for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
      if (propertyDescriptor.getName().equals("deleted")) {
        assertThat(stringSet.contains(propertyDescriptor.getName())).isTrue();
      } else if (propertyDescriptor.getName().equals("deletedAt")) {
        assertThat(stringSet.contains(propertyDescriptor.getName())).isTrue();
      } else {
        assertThat(stringSet.contains(propertyDescriptor.getName())).isFalse();
      }
    }
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCreateCriteriaForGetList1() {
    Criteria criteria =
        ServiceFilterHelper.createCriteriaForGetList("accId", "orgId", "projId", false, "foo", null, null, false);

    assertThat(criteria.getCriteriaObject().toJson())
        .isEqualTo(
            "{\"accountId\": \"accId\", \"orgIdentifier\": \"orgId\", \"projectIdentifier\": \"projId\", \"deleted\": false, \"$and\": [{\"$or\": [{\"name\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}, {\"identifier\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}, {\"tags.key\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}, {\"tags.value\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}]}]}");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCreateCriteriaForGetList2() {
    Criteria criteria = ServiceFilterHelper.createCriteriaForGetList(
        "accId", "orgId", "projId", false, "foo", ServiceDefinitionType.KUBERNETES, Boolean.TRUE, false);

    assertThat(criteria.getCriteriaObject().toJson())
        .isEqualTo(
            "{\"accountId\": \"accId\", \"orgIdentifier\": \"orgId\", \"projectIdentifier\": \"projId\", \"deleted\": false, \"type\": \"KUBERNETES\", \"$and\": [{\"$or\": [{\"name\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}, {\"identifier\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}, {\"tags.key\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}, {\"tags.value\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}]}], \"gitOpsEnabled\": true}");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCreateCriteriaForGetList3() {
    Criteria criteria = ServiceFilterHelper.createCriteriaForGetList(
        "accId", "orgId", "projId", false, "foo", ServiceDefinitionType.NATIVE_HELM, null, false);

    assertThat(criteria.getCriteriaObject().toJson())
        .isEqualTo(
            "{\"accountId\": \"accId\", \"orgIdentifier\": \"orgId\", \"projectIdentifier\": \"projId\", \"deleted\": false, \"type\": \"NATIVE_HELM\", \"$and\": [{\"$or\": [{\"name\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}, {\"identifier\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}, {\"tags.key\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}, {\"tags.value\": {\"$regularExpression\": {\"pattern\": \"foo\", \"options\": \"i\"}}}]}]}");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateCriteriaForGetListScopedRef1() {
    Criteria criteria = ServiceFilterHelper.createCriteriaForGetList(
        "accId", "orgId", "projId", Collections.singletonList("account.accServ1"), false, null, null, null, false);

    assertThat(criteria.getCriteriaObject().toJson())
        .isEqualTo(
            "{\"accountId\": \"accId\", \"$and\": [{\"$or\": [{\"orgIdentifier\": null, \"projectIdentifier\": null, \"identifier\": {\"$in\": [\"accServ1\"]}}]}], \"deleted\": false}");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateCriteriaForGetListScopedRef2() {
    Criteria criteria = ServiceFilterHelper.createCriteriaForGetList("accId", "orgId", "projId",
        Arrays.asList("account.accServ1", "org.orgServ1", "projServ1"), false, null, null, null, false);

    assertThat(criteria.getCriteriaObject().toJson())
        .isEqualTo(
            "{\"accountId\": \"accId\", \"$and\": [{\"$or\": [{\"orgIdentifier\": null, \"projectIdentifier\": null, \"identifier\": {\"$in\": [\"accServ1\"]}}, {\"orgIdentifier\": \"orgId\", \"projectIdentifier\": null, \"identifier\": {\"$in\": [\"orgServ1\"]}}, {\"orgIdentifier\": \"orgId\", \"projectIdentifier\": \"projId\", \"identifier\": {\"$in\": [\"projServ1\"]}}]}], \"deleted\": false}");
  }
}
