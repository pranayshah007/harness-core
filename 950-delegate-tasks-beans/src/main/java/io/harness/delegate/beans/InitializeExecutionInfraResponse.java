/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

<<<<<<<< HEAD:950-delegate-tasks-beans/src/main/java/io/harness/delegate/beans/scheduler/InitializeExecutionInfraResponse.java
package io.harness.delegate.beans.scheduler;
========
package io.harness.delegate.beans;
>>>>>>>> 94fed9e9570 (feat: [CDS-79217]: Fixed ExecuteTask task):950-delegate-tasks-beans/src/main/java/io/harness/delegate/beans/InitializeExecutionInfraResponse.java

import io.harness.delegate.beans.DelegateResponseData;

import lombok.Builder;
import lombok.Data;

// TODO: should be proto
@Data
@Builder
public class InitializeExecutionInfraResponse implements DelegateResponseData {
  private final String executionInfraReferenceId;
}
