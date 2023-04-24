/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.event.muutation;

import static io.harness.rule.OwnerRule.MOUNIK;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CgEventConfig;
import io.harness.beans.CgEventRule;
import io.harness.beans.WebHookEventConfig;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.schema.mutation.event.EventsConfigValidationHelper;

import dev.morphia.query.Query;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EventsConfigValidationHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks EventsConfigValidationHelper eventsConfigValidationHelper;
  @Mock WingsPersistence wingsPersistence;
  @Mock Query<Pipeline> pipelineQuery;
  @Mock Query<Workflow> workflowQuery;

  public static final String GLOBAL_APP_ID = "__GLOBAL_APP_ID__";
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  private CgEventConfig getEventConfigSample() {
    CgEventRule eventRule = new CgEventRule();
    eventRule.setType(CgEventRule.CgRuleType.ALL);
    CgEventConfig cgEventConfig = CgEventConfig.builder()
                                      .name("config1")
                                      .accountId(GLOBAL_ACCOUNT_ID)
                                      .appId(GLOBAL_APP_ID)
                                      .enabled(true)
                                      .rule(eventRule)
                                      .build();
    WebHookEventConfig config = new WebHookEventConfig();
    cgEventConfig.setConfig(config);
    cgEventConfig.getConfig().setUrl("url1");
    return cgEventConfig;
  }

  @Before
  public void setUp() throws Exception {
    when(wingsPersistence.createQuery(Pipeline.class)).thenReturn(pipelineQuery);
    when(pipelineQuery.filter(any(), any())).thenReturn(pipelineQuery);
    when(pipelineQuery.project(Mockito.eq(PipelineKeys.uuid), eq(true))).thenReturn(pipelineQuery);
    when(wingsPersistence.createQuery(Workflow.class)).thenReturn(workflowQuery);
    when(workflowQuery.filter(any(), any())).thenReturn(workflowQuery);
    when(workflowQuery.project(Mockito.eq(WorkflowKeys.uuid), eq(true))).thenReturn(workflowQuery);
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testValidEventConfigs() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    eventsConfigValidationHelper.validateWorkflowIds(cgEventConfig, GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID);
    eventsConfigValidationHelper.validatePipelineIds(cgEventConfig, GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID);
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testValidPipelineEventConfigs() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    CgEventRule eventRule = cgEventConfig.getRule();
    eventRule.setType(CgEventRule.CgRuleType.PIPELINE);
    CgEventRule.PipelineRule pipelineRule = new CgEventRule.PipelineRule();
    pipelineRule.setAllEvents(true);
    pipelineRule.setAllPipelines(false);
    pipelineRule.setPipelineIds(Arrays.asList("id1"));
    eventRule.setPipelineRule(pipelineRule);
    when(pipelineQuery.asList()).thenReturn(Arrays.asList(Pipeline.builder().uuid("id1").build()));
    eventsConfigValidationHelper.validatePipelineIds(cgEventConfig, GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID);
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testInvalidPipelineEventConfigs() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    CgEventRule eventRule = cgEventConfig.getRule();
    eventRule.setType(CgEventRule.CgRuleType.PIPELINE);
    CgEventRule.PipelineRule pipelineRule = new CgEventRule.PipelineRule();
    pipelineRule.setAllEvents(true);
    pipelineRule.setAllPipelines(false);
    pipelineRule.setPipelineIds(Arrays.asList("id1", "id2"));
    eventRule.setPipelineRule(pipelineRule);
    when(pipelineQuery.asList()).thenReturn(Arrays.asList(Pipeline.builder().uuid("id1").build()));
    Assertions
        .assertThatThrownBy(
            () -> eventsConfigValidationHelper.validatePipelineIds(cgEventConfig, GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The following pipeline ids are invalid :[id2]");
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testValidWorkflowEventConfigs() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    CgEventRule eventRule = cgEventConfig.getRule();
    eventRule.setType(CgEventRule.CgRuleType.WORKFLOW);
    CgEventRule.WorkflowRule workflowRule = new CgEventRule.WorkflowRule();
    workflowRule.setAllEvents(true);
    workflowRule.setAllWorkflows(false);
    workflowRule.setWorkflowIds(Arrays.asList("id1"));
    eventRule.setWorkflowRule(workflowRule);
    Workflow workflow = new Workflow();
    workflow.setUuid("id1");
    when(workflowQuery.asList()).thenReturn(asList(workflow));
    eventsConfigValidationHelper.validateWorkflowIds(cgEventConfig, GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID);
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testInvalidWorkflowEventConfigs() {
    CgEventConfig cgEventConfig = getEventConfigSample();
    CgEventRule eventRule = cgEventConfig.getRule();
    eventRule.setType(CgEventRule.CgRuleType.WORKFLOW);
    CgEventRule.WorkflowRule workflowRule = new CgEventRule.WorkflowRule();
    workflowRule.setAllEvents(true);
    workflowRule.setAllWorkflows(false);
    workflowRule.setWorkflowIds(Arrays.asList("id1", "id2"));
    eventRule.setWorkflowRule(workflowRule);
    Workflow workflow = new Workflow();
    workflow.setUuid("id2");
    when(workflowQuery.asList()).thenReturn(Arrays.asList(workflow));
    Assertions
        .assertThatThrownBy(
            () -> eventsConfigValidationHelper.validateWorkflowIds(cgEventConfig, GLOBAL_ACCOUNT_ID, GLOBAL_APP_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The following workflow ids are invalid :[id1]");
  }
}
