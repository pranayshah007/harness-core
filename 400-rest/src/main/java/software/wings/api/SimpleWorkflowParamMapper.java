/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import software.wings.sm.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(CDC)
public class SimpleWorkflowParamMapper implements ContextElementParamMapper {
  private static final String SIMPLE_WORKFLOW_COMMAND_NAME = "SIMPLE_WORKFLOW_COMMAND_NAME";
  private static final String SIMPLE_WORKFLOW_REPEAT_STRATEGY = "SIMPLE_WORKFLOW_REPEAT_STRATEGY";

  private final SimpleWorkflowParam element;

  public SimpleWorkflowParamMapper(SimpleWorkflowParam element) {
    this.element = element;
  }

  @Override
  public Map<String, Object> paramMap(ExecutionContext context) {
    HashMap<String, Object> map = new HashMap<>();
    map.put(SIMPLE_WORKFLOW_COMMAND_NAME, this.element.getCommandName());
    map.put(SIMPLE_WORKFLOW_REPEAT_STRATEGY, this.element.getExecutionStrategy());

    return map;
  }
}