/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.UserGroupService;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PipelineStageYamlHandlerTest {
  private static final String APP_ID = "APP_ID";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";

  @InjectMocks private PipelineStageYamlHandler pipelineStageYamlHandler;
  @Mock private AppService appService;
  @Mock private UserGroupService userGroupService;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGenerateYamlForApprovalStageWhenUserGroupsNull() {
    Map<String, Object> outProperties = new HashMap<>();
    Map<String, Object> inProperties = Collections.singletonMap("userGroups", null);

    PipelineStageElement stageElement = PipelineStageElement.builder().properties(inProperties).build();

    pipelineStageYamlHandler.generateYamlForApprovalStage(APP_ID, stageElement, outProperties);

    assertThat(outProperties).containsEntry("userGroups", null);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGenerateYamlForApprovalStageWhenUserGroupsEmpty() {
    Map<String, Object> outProperties = new HashMap<>();
    Map<String, Object> inProperties = Collections.singletonMap("userGroups", "");

    PipelineStageElement stageElement = PipelineStageElement.builder().properties(inProperties).build();

    pipelineStageYamlHandler.generateYamlForApprovalStage(APP_ID, stageElement, outProperties);

    assertThat(outProperties).containsEntry("userGroups", "");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGenerateYamlForApprovalStageWhenUserGroupsHasListItems() {
    List<String> asList = Lists.newArrayList("A", "B", "C");
    Map<String, Object> inProperties = Collections.singletonMap("userGroups", asList);
    Map<String, Object> outProperties = new HashMap<>();

    PipelineStageElement stageElement = PipelineStageElement.builder().properties(inProperties).build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    pipelineStageYamlHandler.generateYamlForApprovalStage(APP_ID, stageElement, outProperties);

    verify(appService).getAccountIdByAppId(APP_ID);
    verify(userGroupService, times(3)).get(eq(ACCOUNT_ID), anyString());
    assertThat(outProperties).containsEntry("userGroups", asList);
  }
}
