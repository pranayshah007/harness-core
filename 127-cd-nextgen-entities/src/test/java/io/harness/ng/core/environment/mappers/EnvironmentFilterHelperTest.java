/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.mappers;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentFilterHelperTest extends CategoryTest {
  EnvironmentFilterHelper environmentFilterHelper;
  @Mock FilterService filterService;

  String accountIdentifier = "accountIdentifier";
  String orgIdentifier = "orgIdentifier";
  String projectIdentifier = "projectIdentifier";
  String environmentIdentifier = "environmentIdentifier";
  String serviceIdentifier = "serviceIdentifier";
  EnvironmentType environmentType = EnvironmentType.PreProduction;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    environmentFilterHelper = new EnvironmentFilterHelper(filterService);
  }

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
    assertThat(criteriaObject.get(EnvironmentKeys.accountId)).isEqualTo(accountId);
    assertThat(criteriaObject.get(EnvironmentKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    assertThat(criteriaObject.get(EnvironmentKeys.projectIdentifier)).isEqualTo(projectIdentifier);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetUpdateOperations() {
    Environment environment = Environment.builder().build();
    Update updateOperations = EnvironmentFilterHelper.getUpdateOperations(environment);
    Set<String> stringSet = ((Document) updateOperations.getUpdateObject().get("$set")).keySet();
    PropertyDescriptor[] propertyDescriptors = BeanUtils.getPropertyDescriptors(Environment.class);
    Set<String> excludedFields =
        new HashSet<>(Arrays.asList(EnvironmentKeys.id, EnvironmentKeys.createdAt, EnvironmentKeys.version,
            EnvironmentKeys.yaml, EnvironmentKeys.branch, EnvironmentKeys.filePath, EnvironmentKeys.isFromDefaultBranch,
            EnvironmentKeys.objectIdOfYaml, EnvironmentKeys.yamlGitConfigRef, EnvironmentKeys.rootFolder, "class"));

    for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
      boolean shouldExist =
          stringSet.contains(propertyDescriptor.getName()) || excludedFields.contains(propertyDescriptor.getName());
      assertThat(shouldExist).isTrue();
    }

    Set<String> setOnInsert = ((Document) updateOperations.getUpdateObject().get("$setOnInsert")).keySet();
    assertThat(setOnInsert).hasSize(1);
    assertThat(setOnInsert).contains(EnvironmentKeys.createdAt);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetUpdateOperationsForDelete() {
    Update updateOperations = EnvironmentFilterHelper.getUpdateOperationsForDelete();
    Set<String> stringSet = ((Document) updateOperations.getUpdateObject().get("$set")).keySet();
    PropertyDescriptor[] propertyDescriptors = BeanUtils.getPropertyDescriptors(Environment.class);

    for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
      if (propertyDescriptor.getName().equals("deleted")) {
        assertThat(stringSet.contains(propertyDescriptor.getName())).isTrue();
      } else {
        assertThat(stringSet.contains(propertyDescriptor.getName())).isFalse();
      }
    }
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListWithNamesFilter() {
    EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO =
        EnvironmentFilterPropertiesDTO.builder().environmentNames(Arrays.asList("qa", "dev")).build();
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, false, null, null, environmentFilterPropertiesDTO, false);
    Document criteriaObj = criteria.getCriteriaObject();
    assertThat(criteriaObj.get(EnvironmentKeys.accountId)).isEqualTo(accountIdentifier);
    assertThat(criteriaObj.get(EnvironmentKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObj.get(EnvironmentKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    Object p =
        ((Document) ((List<?>) ((Document) ((List<?>) criteriaObj.get("$and")).get(0)).get("$or")).get(0)).get("name");
    assertThat(((Pattern) p).pattern()).isEqualTo("qa");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListWithEnvTypeFilter() {
    EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO =
        EnvironmentFilterPropertiesDTO.builder().environmentTypes(Collections.singletonList(environmentType)).build();
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, false, null, null, environmentFilterPropertiesDTO, false);
    Document criteriaObj = criteria.getCriteriaObject();
    assertThat(criteriaObj.get(EnvironmentKeys.accountId)).isEqualTo(accountIdentifier);
    assertThat(criteriaObj.get(EnvironmentKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObj.get(EnvironmentKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    Object p = ((List<?>) ((Document) criteriaObj.get("type")).get("$in")).get(0);
    assertThat((EnvironmentType) p).isEqualTo(EnvironmentType.PreProduction);
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListWithDescriptionFilter() {
    EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO =
        EnvironmentFilterPropertiesDTO.builder().description("deploying to production").build();
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, false, null, null, environmentFilterPropertiesDTO, false);
    Document criteriaObj = criteria.getCriteriaObject();
    assertThat(criteriaObj.get(EnvironmentKeys.accountId)).isEqualTo(accountIdentifier);
    assertThat(criteriaObj.get(EnvironmentKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObj.get(EnvironmentKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    Object p = ((Document) ((List<?>) criteriaObj.get("$and")).get(0)).get("description");
    // pattern
    assertThat(((Pattern) p).pattern()).isEqualTo("deploying|to|production");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListWithSearchTermFilter() {
    EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO = EnvironmentFilterPropertiesDTO.builder().build();
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, false, "gcp", null, environmentFilterPropertiesDTO, false);
    Document criteriaObj = criteria.getCriteriaObject();
    assertThat(criteriaObj.get(EnvironmentKeys.accountId)).isEqualTo(accountIdentifier);
    assertThat(criteriaObj.get(EnvironmentKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObj.get(EnvironmentKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    List<?> p = (List<?>) ((Document) ((List<?>) criteriaObj.get("$and")).get(0)).get("$or");
    assertThat(p.size()).isEqualTo(4); // name, description, identifier, tags criteria

    assertThat(((Pattern) ((Document) p.get(0)).get("name")).pattern()).isEqualTo("gcp");
    assertThat(((Pattern) ((Document) p.get(1)).get("identifier")).pattern()).isEqualTo("gcp");
    assertThat(((Pattern) ((Document) p.get(2)).get("description")).pattern()).isEqualTo("gcp");
    assertThat(((NGTag) ((Document) p.get(3)).get("tags")).getKey()).isEqualTo("gcp");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListServiceOverridesCriteria() {
    Criteria criteria = environmentFilterHelper.createCriteriaForGetServiceOverrides(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier);
    Document criteriaObj = criteria.getCriteriaObject();
    assertThat(criteriaObj.get(NGServiceOverridesEntityKeys.accountId)).isEqualTo(accountIdentifier);
    assertThat(criteriaObj.get(NGServiceOverridesEntityKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObj.get(NGServiceOverridesEntityKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    assertThat(criteriaObj.get(NGServiceOverridesEntityKeys.environmentRef)).isEqualTo(environmentIdentifier);
    assertThat(criteriaObj.get(NGServiceOverridesEntityKeys.serviceRef)).isEqualTo(serviceIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListWithSavedFilter() {
    EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO =
        EnvironmentFilterPropertiesDTO.builder().description("deploying to production").build();
    FilterDTO filterDTO = FilterDTO.builder().filterProperties(environmentFilterPropertiesDTO).build();

    doReturn(filterDTO)
        .when(filterService)
        .get(accountIdentifier, orgIdentifier, projectIdentifier, "filterIdentifier", FilterType.ENVIRONMENT);

    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, false, null, "filterIdentifier", null, false);

    Document criteriaObj = criteria.getCriteriaObject();
    assertThat(criteriaObj.get(EnvironmentKeys.accountId)).isEqualTo(accountIdentifier);
    assertThat(criteriaObj.get(EnvironmentKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObj.get(EnvironmentKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    Object p = ((Document) ((List<?>) criteriaObj.get("$and")).get(0)).get("description");
    // pattern
    assertThat(((Pattern) p).pattern()).isEqualTo("deploying|to|production");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListIncludeAllScopesEnvAtProjectLevel() {
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, false, null, null, null, true);
    Document criteriaObj = criteria.getCriteriaObject();
    // 3 criteria for org/project
    assertThat(criteriaObj.toJson())
        .isEqualTo(
            "{\"accountId\": \"accountIdentifier\", \"deleted\": false, \"$and\": [{\"$or\": [{\"orgIdentifier\": \"orgIdentifier\", \"projectIdentifier\": \"projectIdentifier\"}, {\"orgIdentifier\": \"orgIdentifier\", \"projectIdentifier\": null}, {\"orgIdentifier\": null, \"projectIdentifier\": null}]}]}");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListIncludeAllScopesEnvAtOrgLevel() {
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, null, false, null, null, null, true);
    Document criteriaObj = criteria.getCriteriaObject();

    // 2 criteria for org/project
    assertThat(criteriaObj.toJson())
        .isEqualTo(
            "{\"accountId\": \"accountIdentifier\", \"deleted\": false, \"$and\": [{\"$or\": [{\"orgIdentifier\": \"orgIdentifier\", \"projectIdentifier\": null}, {\"orgIdentifier\": null, \"projectIdentifier\": null}]}]}");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListIncludeAllScopesEnvAtAccountLevel() {
    Criteria criteria =
        environmentFilterHelper.createCriteriaForGetList(accountIdentifier, null, null, false, null, null, null, true);
    Document criteriaObj = criteria.getCriteriaObject();
    assertThat(criteriaObj.get(EnvironmentKeys.accountId)).isEqualTo(accountIdentifier);
    // 1 criteria for org/project
    assertThat(criteriaObj.toJson())
        .isEqualTo(
            "{\"accountId\": \"accountIdentifier\", \"deleted\": false, \"$and\": [{\"$or\": [{\"orgIdentifier\": null, \"projectIdentifier\": null}]}]}");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListIncludeAllScopesEnvAtAccountLevelWithEnvNames() {
    EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO =
        EnvironmentFilterPropertiesDTO.builder().environmentNames(List.of("qa")).build();
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, null, null, false, null, null, environmentFilterPropertiesDTO, true);
    Document criteriaObj = criteria.getCriteriaObject();
    assertThat(criteriaObj.get(EnvironmentKeys.accountId)).isEqualTo(accountIdentifier);
    // 1 criteria for org/project
    assertThat(criteriaObj.toJson())
        .isEqualTo(
            "{\"accountId\": \"accountIdentifier\", \"deleted\": false, \"$and\": [{\"$or\": [{\"orgIdentifier\": null, \"projectIdentifier\": null}]}, {\"$or\": [{\"name\": {\"$regularExpression\": {\"pattern\": \"qa\", \"options\": \"i\"}}}]}]}");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListFilteringWithRefsAtProjectLevel() {
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(accountIdentifier, orgIdentifier,
        projectIdentifier, Arrays.asList("account.accEnv", "org.orgEnv", "projEnv"), false);
    Document criteriaObj = criteria.getCriteriaObject();
    // 3 criteria for org/project
    assertThat(criteriaObj.toJson())
        .isEqualTo(
            "{\"accountId\": \"accountIdentifier\", \"$and\": [{\"$or\": [{\"orgIdentifier\": null, \"projectIdentifier\": null, \"identifier\": {\"$in\": [\"accEnv\"]}}, {\"orgIdentifier\": \"orgIdentifier\", \"projectIdentifier\": null, \"identifier\": {\"$in\": [\"orgEnv\"]}}, {\"orgIdentifier\": \"orgIdentifier\", \"projectIdentifier\": \"projectIdentifier\", \"identifier\": {\"$in\": [\"projEnv\"]}}]}], \"deleted\": false}");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListFilteringWithoutRefsAtProjectLevel() {
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, null, false);
    Document criteriaObj = criteria.getCriteriaObject();
    assertThat(criteriaObj.toJson())
        .isEqualTo(
            "{\"accountId\": \"accountIdentifier\", \"orgIdentifier\": \"orgIdentifier\", \"projectIdentifier\": \"projectIdentifier\", \"deleted\": false}");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListFilteringWithNullRefsAtProjectLevel() {
    List<String> envRefs = new ArrayList<>();
    envRefs.add(null);
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, envRefs, false);
    Document criteriaObj = criteria.getCriteriaObject();
    assertThat(criteriaObj.toJson())
        .isEqualTo(
            "{\"accountId\": \"accountIdentifier\", \"orgIdentifier\": \"orgIdentifier\", \"projectIdentifier\": \"projectIdentifier\", \"deleted\": false}");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testListFilteringIncludingNullRefsAtProjectLevel() {
    List<String> envRefs = new ArrayList<>();
    envRefs.add(null);
    envRefs.add("env");

    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, envRefs, false);
    Document criteriaObj = criteria.getCriteriaObject();
    assertThat(criteriaObj.toJson())
        .isEqualTo(
            "{\"accountId\": \"accountIdentifier\", \"$and\": [{\"$or\": [{\"orgIdentifier\": \"orgIdentifier\", \"projectIdentifier\": \"projectIdentifier\", \"identifier\": {\"$in\": [\"env\"]}}]}], \"deleted\": false}");
  }
}
