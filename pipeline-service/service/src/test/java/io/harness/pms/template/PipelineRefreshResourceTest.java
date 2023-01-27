/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.template;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.harness.PipelineServiceTestBase;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.template.service.PipelineRefreshService;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PipelineRefreshResourceTest extends PipelineServiceTestBase {
  @Mock private PipelineRefreshService pipelineRefreshService;
  @Mock private AccessControlClient accessControlClient;
  @InjectMocks PipelineRefreshResource pipelineRefreshResource;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "basichttpFail";
  private final String STAGE = "qaStage";
  private String yaml;
  private String simplifiedYaml;

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testRefreshAndUpdateTemplate() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    ResponseDTO<Boolean> responseDTO = ResponseDTO
                                           .newResponse(pipelineRefreshResource.refreshAndUpdateTemplate(
                                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null))
                                           .getData();
    verify(pipelineRefreshService, times(1))
        .refreshTemplateInputsInPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER);
    verifyNoMoreInteractions(pipelineRefreshService);
    assertThat(responseDTO.getData()).isEqualTo(false);
  }
}
