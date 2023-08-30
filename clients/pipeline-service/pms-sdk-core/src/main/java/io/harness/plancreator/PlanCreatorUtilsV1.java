/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.KryoSerializer;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PlanCreatorUtilsV1 {
  // TODO:use https://github.com/protocolbuffers/protobuf/blob/main/src/google/protobuf/struct.proto#L51 for metadataMap
  public List<AdviserObtainment> getAdviserObtainmentsForStage(KryoSerializer kryoSerializer, Dependency dependency) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    AdviserObtainment nextStepAdviser = getNextStepAdviser(kryoSerializer, dependency);
    if (nextStepAdviser != null) {
      adviserObtainments.add(nextStepAdviser);
    }
    return adviserObtainments;
  }

  public String getNextNodeUuid(KryoSerializer kryoSerializer, Dependency dependency) {
    Optional<Object> nextNodeIdOptional =
        getDeserializedObjectFromDependency(dependency, kryoSerializer, YAMLFieldNameConstants.NEXT_ID, false);
    if (nextNodeIdOptional.isPresent() && nextNodeIdOptional.get() instanceof String) {
      return (String) nextNodeIdOptional.get();
    }
    return null;
  }

  public AdviserObtainment getNextStepAdviser(KryoSerializer kryoSerializer, Dependency dependency) {
    if (dependency == null) {
      return null;
    }
    String nextId = getNextNodeUuid(kryoSerializer, dependency);
    if (nextId != null) {
      return AdviserObtainment.newBuilder()
          .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
          .setParameters(ByteString.copyFrom(
              kryoSerializer.asBytes(NextStepAdviserParameters.builder().nextNodeId(nextId).build())))
          .build();
    }
    return null;
  }

  public HarnessValue getNodeMetadataValueFromDependency(Dependency dependency, String key) {
    if (dependency.getNodeMetadata() != null && isNotEmpty(dependency.getNodeMetadata().getDataMap())
        && dependency.getNodeMetadata().getDataMap().containsKey(key)) {
      return dependency.getNodeMetadata().getDataMap().get(key);
    }
    return null;
  }

  public ByteString getMetadataValueFromDependency(Dependency dependency, String key) {
    if (isNotEmpty(dependency.getMetadataMap()) && dependency.getMetadataMap().containsKey(key)) {
      return dependency.getMetadataMap().get(key);
    }
    return null;
  }

  public Optional<Object> getDeserializedObjectFromDependency(
      Dependency dependency, KryoSerializer kryoSerializer, String key, boolean asInflatedObject) {
    if (dependency == null) {
      return Optional.empty();
    }
    HarnessValue harnessValue = getNodeMetadataValueFromDependency(dependency, key);
    if (harnessValue != null) {
      if (harnessValue.hasStringValue()) {
        return Optional.of(harnessValue.getStringValue());
      }
      if (harnessValue.hasBytesValue()) {
        if (asInflatedObject) {
          return Optional.of(kryoSerializer.asInflatedObject(harnessValue.getBytesValue().toByteArray()));
        }
        return Optional.of(kryoSerializer.asObject(harnessValue.getBytesValue().toByteArray()));
      }
    }
    ByteString bytes = getMetadataValueFromDependency(dependency, key);
    if (bytes != null) {
      if (asInflatedObject) {
        return Optional.of(kryoSerializer.asInflatedObject(bytes.toByteArray()));
      }
      return Optional.of(kryoSerializer.asObject(bytes.toByteArray()));
    }
    return Optional.empty();
  }

  // TODO: Get isStepInsideRollback from dependency metadata map
  public boolean isStepInsideRollback(Dependency dependency) {
    return false;
  }
}
