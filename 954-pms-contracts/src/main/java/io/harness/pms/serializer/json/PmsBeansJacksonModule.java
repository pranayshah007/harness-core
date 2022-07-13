/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.serializer.json;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.UnitProgress;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionErrorInfo;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.contracts.governance.PolicyMetadata;
import io.harness.pms.contracts.governance.PolicySetMetadata;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptEffectProto;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.GraphLayoutInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.serializer.json.serializers.ExecutableResponseSerializer;
import io.harness.pms.serializer.json.serializers.ExecutionErrorInfoSerializer;
import io.harness.pms.serializer.json.serializers.ExecutionMetadataSerializer;
import io.harness.pms.serializer.json.serializers.ExecutionPrincipalInfoSerializer;
import io.harness.pms.serializer.json.serializers.ExecutionTriggerInfoSerializer;
import io.harness.pms.serializer.json.serializers.FailureInfoSerializer;
import io.harness.pms.serializer.json.serializers.GovernanceMetadataSerializer;
import io.harness.pms.serializer.json.serializers.InterruptConfigSerializer;
import io.harness.pms.serializer.json.serializers.InterruptEffectJsonSerializer;
import io.harness.pms.serializer.json.serializers.LayoutNodeInfoSerializer;
import io.harness.pms.serializer.json.serializers.NodeRunInfoSerializer;
import io.harness.pms.serializer.json.serializers.PolicyMetadataSerializer;
import io.harness.pms.serializer.json.serializers.PolicySetMetadataSerializer;
import io.harness.pms.serializer.json.serializers.SkipInfoSerializer;
import io.harness.pms.serializer.json.serializers.StepTypeSerializer;
import io.harness.pms.serializer.json.serializers.TriggeredBySerializer;
import io.harness.pms.serializer.json.serializers.UnitProgressSerializer;
import io.harness.pms.serializer.json.serializers.YamlOutputPropertiesSerializer;
import io.harness.pms.serializer.json.serializers.YamlPropertiesSerializer;

import com.fasterxml.jackson.databind.module.SimpleModule;

@OwnedBy(PIPELINE)
public class PmsBeansJacksonModule extends SimpleModule {
  public PmsBeansJacksonModule() {
    addSerializer(StepType.class, new StepTypeSerializer());
    addSerializer(FailureInfo.class, new FailureInfoSerializer());
    addSerializer(ExecutionErrorInfo.class, new ExecutionErrorInfoSerializer());
    addSerializer(GraphLayoutInfo.class, new LayoutNodeInfoSerializer());
    addSerializer(ExecutionTriggerInfo.class, new ExecutionTriggerInfoSerializer());
    addSerializer(TriggeredBy.class, new TriggeredBySerializer());
    addSerializer(ExecutionMetadata.class, new ExecutionMetadataSerializer());
    addSerializer(YamlProperties.class, new YamlPropertiesSerializer());
    addSerializer(YamlOutputProperties.class, new YamlOutputPropertiesSerializer());
    addSerializer(ExecutableResponse.class, new ExecutableResponseSerializer());
    addSerializer(SkipInfo.class, new SkipInfoSerializer());
    addSerializer(UnitProgress.class, new UnitProgressSerializer());
    addSerializer(InterruptConfig.class, new InterruptConfigSerializer());
    addSerializer(NodeRunInfo.class, new NodeRunInfoSerializer());
    addSerializer(ExecutionPrincipalInfo.class, new ExecutionPrincipalInfoSerializer());
    addSerializer(InterruptEffectProto.class, new InterruptEffectJsonSerializer());
    addSerializer(PolicyMetadata.class, new PolicyMetadataSerializer());
    addSerializer(PolicySetMetadata.class, new PolicySetMetadataSerializer());
    addSerializer(GovernanceMetadata.class, new GovernanceMetadataSerializer());
  }
}
