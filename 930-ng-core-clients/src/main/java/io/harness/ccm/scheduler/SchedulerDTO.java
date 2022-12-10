/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.scheduler;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SchedulerDTO {
  String name; // This is the job id in dkron
  String schedule;
  String displayname;
  String timezone;
  String owner;
  String owner_email;
  Boolean disabled;
  int retries;
  String executor;
  SchedulerDTO.ExecutorConfig executor_config;
  Map<String, String> tags;
  Map<String, String> metadata;

  @Builder
  public static class ExecutorConfig {
    String method;
    String url;
    String headers;
    String body;
    String timeout;
    String expectCode;
    String expectBody;
    String debug;
    String tlsNoVerifyPeer;
  }
}
