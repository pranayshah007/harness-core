/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.dashboard;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.service.PipelineDashboardServiceImpl;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class QueryBuilderPipelineTest extends CategoryTest {
  String tableName_default = "pipeline_execution_summary";
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testSelectTableFromModuleInfo() {
    String ci_table_name = "pipeline_execution_summary_ci";
    String cd_table_name = "pipeline_execution_summary_cd";
    assertThat(new PipelineDashboardServiceImpl().selectTableFromModuleInfo("ci")).isEqualTo(ci_table_name);
    assertThat(new PipelineDashboardServiceImpl().selectTableFromModuleInfo("CI")).isEqualTo(ci_table_name);
    assertThat(new PipelineDashboardServiceImpl().selectTableFromModuleInfo("Ci")).isEqualTo(ci_table_name);
    assertThat(new PipelineDashboardServiceImpl().selectTableFromModuleInfo("cI")).isEqualTo(ci_table_name);

    assertThat(new PipelineDashboardServiceImpl().selectTableFromModuleInfo("cd")).isEqualTo(cd_table_name);
    assertThat(new PipelineDashboardServiceImpl().selectTableFromModuleInfo("CD")).isEqualTo(cd_table_name);
    assertThat(new PipelineDashboardServiceImpl().selectTableFromModuleInfo("Cd")).isEqualTo(cd_table_name);
    assertThat(new PipelineDashboardServiceImpl().selectTableFromModuleInfo("cD")).isEqualTo(cd_table_name);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testSelectTableFromModuleInfoForNullModuleInfo() {
    assertThat(new PipelineDashboardServiceImpl().selectTableFromModuleInfo(null)).isEqualTo(tableName_default);
  }
}
