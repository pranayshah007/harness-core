/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.serializer.kryo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dto.PollingResponseDTO;
import io.harness.kryo.KryoRegistrar;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.service.PollingDocument;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(HarnessTeam.CDC)
public class PollingKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(PollingItem.class, PollingItemKryoSerializer.getInstance(), 410001);
    kryo.register(PollingDocument.class, PollingDocumentKryoSerializer.getInstance(), 410002);
    kryo.register(PollingResponseDTO.class, 410003);
  }
}
