/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.HarnessValue;

import com.google.protobuf.ByteString;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class DependencyMetadata {
  // Both maps contain the same metadata, the first one's value will be kryo serialized bytes while second one can have
  // values in their primitive form like strings, int, etc. and will have kryo serialized bytes for complex objects. We
  // will deprecate the first one in v1
  Map<String, ByteString> metadataMap;
  Map<String, HarnessValue> nodeMetadataMap;
}
