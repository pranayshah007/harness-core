/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.filter;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.rule.Owner;
import io.harness.utils.PipelineGitXHelper;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.PIPELINE)
@PrepareForTest({PipelineGitXHelper.class})
public class AsyncFilterCreationDispatcherTest extends CategoryTest {
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFilterCreation() throws Exception {
    MockedStatic<PipelineGitXHelper> mockSettings = Mockito.mockStatic(PipelineGitXHelper.class);
    AsyncFilterCreationDispatcher dispatcher = AsyncFilterCreationDispatcher.builder()
                                                   .pmsPipelineService(pmsPipelineService)
                                                   .pmsPipelineServiceHelper(pmsPipelineServiceHelper)
                                                   .yamlHash(101)
                                                   .uuid("uuid")
                                                   .messageId("messageId")
                                                   .build();
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId("ACCOUNT_ID")
                                        .orgIdentifier("ORG_IDENTIFIER")
                                        .projectIdentifier("PROJ_IDENTIFIER")
                                        .identifier("PIPELINE_IDENTIFIER")
                                        .name("PIPELINE_IDENTIFIER")
                                        .yaml("yaml")
                                        .stageCount(0)
                                        .stageName("STAGE")
                                        .version(1L)
                                        .allowStageExecutions(false)
                                        .connectorRef("connectorRef")
                                        .repo("repo")
                                        .yamlHash(101)
                                        .build();
    when(pmsPipelineService.getPipelineByUUID(any())).thenReturn(Optional.of(pipelineEntity));
    when(pmsPipelineServiceHelper.updatePipelineInfo(any(), any())).thenReturn(pipelineEntity);
    dispatcher.run();
    mockSettings.verify(()
                            -> PipelineGitXHelper.setupGitParentEntityDetails(eq("ACCOUNT_ID"), eq("ORG_IDENTIFIER"),
                                eq("PROJ_IDENTIFIER"), eq("connectorRef"), eq("repo")));
    mockSettings.close();
  }
}