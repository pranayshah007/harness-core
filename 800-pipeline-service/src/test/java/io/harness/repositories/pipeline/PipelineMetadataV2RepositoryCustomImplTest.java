/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.pipeline;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.PipelineMetadataV2.PipelineMetadataV2Keys;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class PipelineMetadataV2RepositoryCustomImplTest extends CategoryTest {
  PipelineMetadataV2RepositoryCustomImpl pipelineMetadataRepository;
  @Mock MongoTemplate mongoTemplate;

  String ACCOUNT_ID = "account_id";
  String ORG_IDENTIFIER = "orgId";
  String PROJ_IDENTIFIER = "projId";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    pipelineMetadataRepository = new PipelineMetadataV2RepositoryCustomImpl(mongoTemplate, null);
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
    Criteria criteria = Criteria.where(PipelineMetadataV2Keys.accountIdentifier)
                            .is(ACCOUNT_ID)
                            .and(PipelineMetadataV2Keys.orgIdentifier)
                            .is(ORG_IDENTIFIER)
                            .and(PipelineMetadataV2Keys.projectIdentifier)
                            .is(PROJ_IDENTIFIER)
                            .and(PipelineMetadataV2Keys.identifier)
                            .in(pipelineIds);
    Query query = query(criteria);
    doReturn(Arrays.asList(getPipelineMetadata("p1"), getPipelineMetadata("p2"), getPipelineMetadata("p3")))
        .when(mongoTemplate)
        .find(query, PipelineMetadataV2.class);
    List<PipelineMetadataV2> metadataForGivenPipelineIds = pipelineMetadataRepository.getMetadataForGivenPipelineIds(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, pipelineIds);
    assertThat(metadataForGivenPipelineIds).isNotNull();
    assertThat(metadataForGivenPipelineIds).hasSize(3);
    assertThat(metadataForGivenPipelineIds).contains(getPipelineMetadata("p1"));
    assertThat(metadataForGivenPipelineIds).contains(getPipelineMetadata("p2"));
    assertThat(metadataForGivenPipelineIds).contains(getPipelineMetadata("p3"));
  }
}
