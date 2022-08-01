/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.executionmetadata;

import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.serializer.spring.ProtoReadConverter;

public class TriggerPayloadReadConverter extends ProtoReadConverter<TriggerPayload> {
  public TriggerPayloadReadConverter() {
    super(TriggerPayload.class);
  }
}
