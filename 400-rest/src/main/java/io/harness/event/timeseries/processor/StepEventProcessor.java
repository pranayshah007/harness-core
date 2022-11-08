/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;

import io.harness.event.model.EventInfo;

import java.util.Arrays;
import java.util.List;

public interface StepEventProcessor<T extends EventInfo> {
  int MAX_RETRY = 5;
  Integer DEFAULT_MIGRATION_QUERY_BATCH_SIZE = 100;

  String ID = "ID";
  String ACCOUNT_ID = "ACCOUNT_ID";
  String APP_ID = "APP_ID";
  String STEP_NAME = "STEP_NAME";
  String STEP_TYPE = "STEP_TYPE";
  String STATUS = "STATUS";
  String FAILURE_DETAILS = "FAILURE_DETAILS";
  String START_TIME = "START_TIME";
  String END_TIME = "END_TIME";
  String DURATION = "DURATION";
  String STAGE_NAME = "STAGE_NAME";
  String EXECUTION_ID = "EXECUTION_ID";

  String MANUAL_INTERVENTION = "MANUAL_INTERVENTION";
  String EXECUTION_INTERRUPT_ID = "MANUAL_INTERVENTION_ID";
  String EXECUTION_INTERRUPT_CREATED_AT = "MANUAL_INTERVENTION_CREATED_AT";
  String EXECUTION_INTERRUPT_CREATED_BY = "MANUAL_INTERVENTION_CREATED_BY";
  String EXECUTION_INTERRUPT_UPDATED_AT = "MANUAL_INTERVENTION_UPDATED_AT";
  String EXECUTION_INTERRUPT_UPDATED_BY = "MANUAL_INTERVENTION_UPDATED_BY";
  String EXECUTION_INTERRUPT_TYPE = "MANUAL_INTERVENTION_TYPE";

  String APPROVED_BY = "APPROVED_BY";
  String APPROVAL_TYPE = "APPROVAL_TYPE";
  String APPROVED_AT = "APPROVED_AT";
  String APPROVAL_COMMENT = "APPROVAL_COMMENT";
  String APPROVAL_EXPIRY = "APPROVAL_EXPIRY";

  List<String> STATE_TYPES = Arrays.asList("PHASE_STEP", "PHASE", "ENV_STATE");

  void processEvent(T eventInfo) throws Exception;
}
