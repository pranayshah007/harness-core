/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.kryo.ProtobufKryoSerializer;
import io.harness.polling.contracts.service.PollingDocument;

@OwnedBy(PIPELINE)
public class PollingDocumentKryoSerializer extends ProtobufKryoSerializer<PollingDocument> {
  private static PollingDocumentKryoSerializer instance;

  public PollingDocumentKryoSerializer() {}

  public static synchronized PollingDocumentKryoSerializer getInstance() {
    if (instance == null) {
      instance = new PollingDocumentKryoSerializer();
    }
    return instance;
  }
}
