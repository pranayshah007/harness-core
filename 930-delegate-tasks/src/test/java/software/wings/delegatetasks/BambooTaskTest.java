/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;

import software.wings.beans.BambooConfig;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.bamboo.Result;
import software.wings.helpers.ext.bamboo.Status;
import software.wings.sm.states.FilePathAssertionEntry;
import software.wings.sm.states.ParameterEntry;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Created by sgurubelli on 8/30/17.
 */
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class BambooTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock BambooService bambooService;

  @InjectMocks
  private BambooTask bambooTask =
      new BambooTask(DelegateTaskPackage.builder()
                         .delegateId("delid1")
                         .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                         .build(),
          null, notifyResponseData -> {}, () -> true);

  private String bambooUrl = "http://localhost:9095/";
  private String userName = "admin";
  private char[] password = "pass1".toCharArray();
  private String planKey = "TOD-TOD";
  private String buildResultKey = "TOD-TOD-85";
  private List<ParameterEntry> parameters = new ArrayList<>();
  private List<FilePathAssertionEntry> filePathAssertionEntries = new ArrayList<>();

  private BambooConfig bambooConfig =
      BambooConfig.builder().bambooUrl("http://localhost:9095/").username(userName).password(password).build();

  @Before
  public void setUp() throws Exception {
    Map<String, String> evaluatedParameters = Maps.newLinkedHashMap();
    if (isNotEmpty(parameters)) {
      parameters.forEach(
          parameterEntry -> { evaluatedParameters.put(parameterEntry.getKey(), parameterEntry.getValue()); });
    }
    when(bambooService.triggerPlan(bambooConfig, null, planKey, evaluatedParameters)).thenReturn(buildResultKey);
    when(bambooService.getBuildResultStatus(bambooConfig, null, buildResultKey))
        .thenReturn(Status.builder().finished(true).build());
  }
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldExecuteSuccessfullyWhenBuildPasses() throws Exception {
    when(bambooService.getBuildResult(bambooConfig, null, planKey))
        .thenReturn(Result.builder().buildResultKey(buildResultKey).buildState("Successful").build());
    bambooTask.run(bambooConfig, null, planKey, parameters);
    verify(bambooService).triggerPlan(bambooConfig, null, planKey, Collections.emptyMap());
    verify(bambooService).getBuildResult(bambooConfig, null, buildResultKey);
  }
}
