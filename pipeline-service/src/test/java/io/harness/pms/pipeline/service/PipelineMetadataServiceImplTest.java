/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.repositories.pipeline.PipelineMetadataV2Repository;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineMetadataServiceImplTest extends CategoryTest {
  PipelineMetadataServiceImpl pipelineMetadataService;
  @Mock PipelineMetadataV2Repository pipelineMetadataRepository;

  String ACCOUNT_ID = "account_id";
  String ORG_IDENTIFIER = "orgId";
  String PROJ_IDENTIFIER = "projId";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    pipelineMetadataService = new PipelineMetadataServiceImpl(pipelineMetadataRepository, null);
  }

  private PipelineMetadataV2 getPipelineMetadata(String pipelineId) {
    return PipelineMetadataV2.builder()
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJ_IDENTIFIER)
        .identifier(pipelineId)
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
}
