/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.observers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SAMARTH;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.beans.StoreType;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.events.PipelineUpdateEvent;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.repositories.inputset.PMSInputSetRepository;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class InputSetPipelineObserverTest extends PipelineServiceTestBase {
  @Mock PMSInputSetRepository inputSetRepository;
  @Mock PMSInputSetService inputSetService;
  @InjectMocks InputSetPipelineObserver inputSetPipelineObserver;

  private static final String ACCOUNT_ID = "accountId";
  private static final String PROJECT_ID = "projectId";
  private static final String ORG_ID = "orgId";
  private static final String PIPELINE_ID = "pipelineId";
  private static String pipelineYaml = "pipeline:\n"
      + "    identifier: identifier\n"
      + "    name: pipeline1\n"
      + "    accountId: accountId\n"
      + "    orgIdentifier: orgId\n"
      + "    projectIdentifier: pipelineId\n"
      + "    tags: {}\n"
      + "    variables:\n"
      + "        - name: TITLE\n"
      + "          type: String\n"
      + "          value: <+input>";
  private static String inputSetYaml = "inputSet:\n"
      + "    name: input\n"
      + "    identifier: input\n"
      + "    orgIdentifier: orgId\n"
      + "    projectIdentifier: pipelineId\n"
      + "    pipeline:\n"
      + "        identifier: identifier\n"
      + "        variables:\n"
      + "        - name: TITLE\n"
      + "          type: String\n"
      + "          value: val";
  private static String inputSetYaml1 = "inputSet:\n"
      + "    name: input\n"
      + "    identifier: input\n"
      + "    orgIdentifier: orgId\n"
      + "    projectIdentifier: pipelineId\n"
      + "    pipeline:\n"
      + "        identifier: identifier\n"
      + "        abc: abc\n";

  PipelineEntity pipelineEntity;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    pipelineEntity = PipelineEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_ID)
                         .projectIdentifier(PROJECT_ID)
                         .identifier(PIPELINE_ID)
                         .yaml(pipelineYaml)
                         .build();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnUpdate() {
    List<InputSetEntity> inputSetList = new ArrayList<>();
    inputSetList.add(InputSetEntity.builder()
                         .yaml(inputSetYaml)
                         .isInvalid(true)
                         .inputSetEntityType(InputSetEntityType.INPUT_SET)
                         .build());
    inputSetList.add(
        InputSetEntity.builder().yaml(inputSetYaml1).inputSetEntityType(InputSetEntityType.INPUT_SET).build());
    when(inputSetRepository.findAll(PMSInputSetFilterHelper.createCriteriaForGetListForBranchAndRepo(
             pipelineEntity.getAccountId(), pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(),
             pipelineEntity.getIdentifier(), InputSetListTypePMS.INPUT_SET)))
        .thenReturn(inputSetList);
    assertThatCode(()
                       -> inputSetPipelineObserver.onUpdate(
                           new PipelineUpdateEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntity, pipelineEntity)))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnUpdateForRemotePipeline() {
    PipelineEntity pipelineEntity = PipelineEntity.builder().storeType(StoreType.REMOTE).build();
    PipelineUpdateEvent updateEvent = PipelineUpdateEvent.builder().newPipeline(pipelineEntity).build();
    inputSetPipelineObserver.onUpdate(updateEvent);
    verify(inputSetRepository, times(0)).findAll(any());
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testOnDelete() {
    doNothing().when(inputSetService).deleteInputSetsOnPipelineDeletion(pipelineEntity);
    inputSetPipelineObserver.onDelete(new PipelineDeleteEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntity));
    verify(inputSetService, times(1)).deleteInputSetsOnPipelineDeletion(pipelineEntity);
  }
}
