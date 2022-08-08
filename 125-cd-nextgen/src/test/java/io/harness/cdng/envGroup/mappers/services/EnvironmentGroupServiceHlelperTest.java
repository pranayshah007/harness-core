/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envGroup.mappers.services;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupFilterPropertiesDTO;
import io.harness.cdng.envGroup.services.EnvironmentGroupServiceHelper;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;

public class EnvironmentGroupServiceHlelperTest extends CategoryTest {
  private String ACC_ID = "accId";
  private String ORG_ID = "orgId";
  private String PRO_ID = "proId";

  private String envGroupName = "envGroupName";
  private String description = "description";
  private List<String> envIdentifier = Arrays.asList("env");

  @Mock private FilterService filterService;

  @InjectMocks private EnvironmentGroupServiceHelper environmentGroupServiceHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testPopulateEnvGroupFilter() {
    Criteria criteria = new Criteria();
    EnvironmentGroupFilterPropertiesDTO filterPropertiesDTO = EnvironmentGroupFilterPropertiesDTO.builder()
                                                                  .envGroupName(envGroupName)
                                                                  .description(description)
                                                                  .envIdentifiers(envIdentifier)
                                                                  .build();
    environmentGroupServiceHelper.populateEnvGroupFilter(criteria, filterPropertiesDTO);
    Document criteriaObject = criteria.getCriteriaObject();
    // description, name and identifier
    assertThat(criteriaObject.get(EnvironmentGroupEntity.EnvironmentGroupKeys.description)).isEqualTo(description);
    assertThat(((List<Map<?, ?>>) criteriaObject.get("$or")).size()).isEqualTo(2);
    assertThat(((List<Map<?, ?>>) criteriaObject.get("$or"))
                   .get(0)
                   .get(EnvironmentGroupEntity.EnvironmentGroupKeys.name)
                   .toString())
        .isEqualTo(envGroupName);
    assertThat(((List<Map<?, ?>>) criteriaObject.get("$or"))
                   .get(1)
                   .get(EnvironmentGroupEntity.EnvironmentGroupKeys.identifier)
                   .toString())
        .isEqualTo(envGroupName);

    // envIdentifiers
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get("envIdentifiers")).get("$all")).size()).isEqualTo(1);
    assertThat(
        ((List<?>) ((Map<?, ?>) criteriaObject.get("envIdentifiers")).get("$all")).contains(envIdentifier.get(0)))
        .isTrue();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testPopulateEnvGroupFilterUsingIdentifier() {
    Criteria criteria = new Criteria();
    FilterDTO filterPropertiesDTO = FilterDTO.builder()
                                        .filterProperties(EnvironmentGroupFilterPropertiesDTO.builder()
                                                              .envGroupName(envGroupName)
                                                              .description(description)
                                                              .envIdentifiers(envIdentifier)
                                                              .build())
                                        .build();
    doReturn(filterPropertiesDTO)
        .when(filterService)
        .get(ACC_ID, ORG_ID, PRO_ID, "filterID", FilterType.ENVIRONMENTGROUP);
    environmentGroupServiceHelper.populateEnvGroupFilterUsingIdentifier(criteria, ACC_ID, ORG_ID, PRO_ID, "filterID");
    Document criteriaObject = criteria.getCriteriaObject();

    // description, name and identifier
    assertThat(criteriaObject.get(EnvironmentGroupEntity.EnvironmentGroupKeys.description)).isEqualTo(description);
    assertThat(((List<Map<?, ?>>) criteriaObject.get("$or")).size()).isEqualTo(2);
    assertThat(((List<Map<?, ?>>) criteriaObject.get("$or"))
                   .get(0)
                   .get(EnvironmentGroupEntity.EnvironmentGroupKeys.name)
                   .toString())
        .isEqualTo(envGroupName);
    assertThat(((List<Map<?, ?>>) criteriaObject.get("$or"))
                   .get(1)
                   .get(EnvironmentGroupEntity.EnvironmentGroupKeys.identifier)
                   .toString())
        .isEqualTo(envGroupName);

    // envIdentifiers
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get("envIdentifiers")).get("$all")).size()).isEqualTo(1);
    assertThat(
        ((List<?>) ((Map<?, ?>) criteriaObject.get("envIdentifiers")).get("$all")).contains(envIdentifier.get(0)))
        .isTrue();
  }
}
