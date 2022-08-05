/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;

import io.harness.event.model.EventInfo;

public interface EventProcessor<T extends EventInfo> {
  int MAX_RETRY_COUNT = 5;

  /**
   EXECUTIONID TEXT NOT NULL,
   STARTTIME TIMESTAMP NOT NULL,
   ENDTIME TIMESTAMP NOT NULL,
   ACCOUNTID TEXT NOT NULL,
   APPID TEXT NOT NULL,
   TRIGGERED_BY TEXT,
   TRIGGER_ID TEXT,
   STATUS VARCHAR(20),
   SERVICES TEXT[],
   WORKFLOWS TEXT[],
   CLOUDPROVIDERS TEXT[],
   ENVIRONMENTS TEXT[],
   PIPELINE TEXT,
   DURATION BIGINT NOT NULL,
   ARTIFACTS TEXT[],
   ROLLBACK_DURATION
   */
  String EXECUTIONID = "EXECUTIONID";
  String STARTTIME = "STARTTIME";
  String ENDTIME = "ENDTIME";
  String ACCOUNTID = "ACCOUNTID";
  String APPID = "APPID";
  String TRIGGERED_BY = "TRIGGERED_BY";
  String TRIGGER_ID = "TRIGGER_ID";
  String STATUS = "STATUS";
  String SERVICE_LIST = "SERVICE_LIST";
  String WORKFLOW_LIST = "WORKFLOW_LIST";
  String CLOUD_PROVIDER_LIST = "CLOUD_PROVIDER_LIST";
  String ENV_LIST = "ENV_LIST";
  String ENVTYPES = "ENVTYPES";
  String PARENT_EXECUTION = "PARENT_EXECUTION";
  String STAGENAME = "STAGENAME";
  String PIPELINE = "PIPELINE";
  String DURATION = "DURATION";
  String ROLLBACK_DURATION = "ROLLBACK_DURATION";
  String ARTIFACT_LIST = "ARTIFACT_LIST";
  String SERVICEID = "SERVICEID";
  String ARTIFACTID = "ARTIFACTID";
  String ENVID = "ENVID";
  String INFRAMAPPINGID = "INFRAMAPPINGID";
  String CLOUDPROVIDERID = "CLOUDPROVIDERID";
  String INSTANCECOUNT = "INSTANCECOUNT";
  String SANITYSTATUS = "SANITYSTATUS";
  String INSTANCES_DEPLOYED = "INSTANCES_DEPLOYED";
  String INSTANCETYPE = "INSTANCETYPE";
  String TAGS = "TAGS";
  String REPORTEDAT = "REPORTEDAT";
  String WORKFLOWS = "WORKFLOWS";
  String FAILURE_DETAILS = "FAILURE_DETAILS";
  String FAILED_STEP_NAMES = "FAILED_STEP_NAMES";
  String FAILED_STEP_TYPES = "FAILED_STEP_TYPES";

  void processEvent(T eventInfo) throws Exception;
}
