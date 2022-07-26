/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.rule.Owner;

import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public class PMSInputSetFilterHelperTest extends CategoryTest {
  String accountId = "ACCOUNT_ID";
  String orgIdentifier = "ORG_ID";
  String projectIdentifier = "PROJECT_ID";
  String pipelineIdentifier = "PIPELINE_ID";

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetCriteria() {
    Criteria criteriaFromFilter = PMSInputSetFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, InputSetListTypePMS.ALL, null, false);
    assertThat(criteriaFromFilter).isNotNull();

    Document criteriaObject = criteriaFromFilter.getCriteriaObject();
    assertThat(criteriaObject.get(InputSetEntityKeys.accountId)).isEqualTo(accountId);
    assertThat(criteriaObject.get(InputSetEntityKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    assertThat(criteriaObject.get(InputSetEntityKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObject.get(InputSetEntityKeys.pipelineIdentifier)).isEqualTo(pipelineIdentifier);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetCriteriaWithSearchTerm() {
    String searchTerm = "overlay.*";
    Criteria criteriaFromFilter = PMSInputSetFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, InputSetListTypePMS.ALL, searchTerm, false);
    assertThat(criteriaFromFilter).isNotNull();

    Document criteriaObject = criteriaFromFilter.getCriteriaObject();
    assertThat(criteriaObject.get(InputSetEntityKeys.accountId)).isEqualTo(accountId);
    assertThat(criteriaObject.get(InputSetEntityKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    assertThat(criteriaObject.get(InputSetEntityKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObject.get(InputSetEntityKeys.pipelineIdentifier)).isEqualTo(pipelineIdentifier);
    assertThat(criteriaObject.get("$and")).isNotNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetCriteriaForOnlyOneKind() {
    Criteria criteriaFromFilter = PMSInputSetFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, InputSetListTypePMS.INPUT_SET, "", false);
    assertThat(criteriaFromFilter).isNotNull();

    Document criteriaObject = criteriaFromFilter.getCriteriaObject();
    assertThat(criteriaObject.get(InputSetEntityKeys.accountId)).isEqualTo(accountId);
    assertThat(criteriaObject.get(InputSetEntityKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    assertThat(criteriaObject.get(InputSetEntityKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObject.get(InputSetEntityKeys.pipelineIdentifier)).isEqualTo(pipelineIdentifier);
    assertThat(criteriaObject.get(InputSetEntityKeys.inputSetEntityType)).isEqualTo(InputSetEntityType.INPUT_SET);

    criteriaFromFilter = PMSInputSetFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, InputSetListTypePMS.OVERLAY_INPUT_SET, "", false);
    assertThat(criteriaFromFilter).isNotNull();

    criteriaObject = criteriaFromFilter.getCriteriaObject();
    assertThat(criteriaObject.get(InputSetEntityKeys.inputSetEntityType))
        .isEqualTo(InputSetEntityType.OVERLAY_INPUT_SET);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateCriteriaForGetListForBranchAndRepo() {
    Criteria criteria = PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, InputSetListTypePMS.INPUT_SET);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(InputSetEntityKeys.accountId)).isEqualTo(accountId);
    assertThat(criteriaObject.get(InputSetEntityKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    assertThat(criteriaObject.get(InputSetEntityKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObject.get(InputSetEntityKeys.pipelineIdentifier)).isEqualTo(pipelineIdentifier);
    assertThat(criteriaObject.get(InputSetEntityKeys.inputSetEntityType)).isEqualTo(InputSetEntityType.INPUT_SET);
  }
}
