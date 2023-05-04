/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.delegate.runner.execinfra.ExecutionInfrastructure;
import io.harness.delegate.task.TaskParameters;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import software.wings.beans.SerializationFormat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class DelegateInitiateInfrastructureRequest {
    @Singular Map<String, String> taskSetupAbstractions;
    List<String> eligibleToExecuteDelegateIds;
    @Singular List<String> taskSelectors;
    String runnerType;

    // Scheduling behaviors
    Duration executionTimeout;
    boolean forceExecute;
    boolean parked;

    // decryption. TODO: remove below two after moving evaluation out of manager
    long expressionFunctorToken;
    @Builder.Default
    software.wings.beans.SerializationFormat serializationFormat = SerializationFormat.KRYO;

    // Other behaviors
    // Used for harness hosted delegates
    boolean executeOnHarnessHostedDelegates;
    boolean emitEvent;
    boolean selectionLogEnabled;

    String stageId;
    String accountId;

    String taskDescription;

    // Standard runners' API interface of initializing an execution ifnra. VM and Docker runners can easily adopt this
    ExecutionInfrastructure infraConfig;
    // Kryo parameter. It will be serialized to bytes and pass through to runner. This introduces backward compatibility
    // with existing runners working with kryo. eg. k8s runner.
    TaskParameters kryoExecutionInfraData;
    // private boolean shouldSkipOpenStream; -- not used by any tasks

    // LinkedHashMap<String, String> logStreamingAbstractions; -- defined in runner's config
    // private String baseLogKey;
}
