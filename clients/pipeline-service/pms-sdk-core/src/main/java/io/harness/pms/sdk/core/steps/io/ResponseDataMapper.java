/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.steps.io;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(CDC)
@Singleton
public class ResponseDataMapper {
  @Inject private KryoSerializer kryoSerializer;

  public Map<String, ByteString> toResponseDataProto(Map<String, ResponseData> responseDataMap) {
    Map<String, ByteString> byteStringMap = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(responseDataMap)) {
      responseDataMap.forEach((k, v) -> {
        if (v instanceof BinaryResponseData) {
          // This implies this is coming from the PMS driver module. Eventually this will be the only way
          byteStringMap.put(k, ByteString.copyFrom(((BinaryResponseData) v).getData()));
        } else {
          byteStringMap.put(k, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(v)));
        }
      });
    }
    return byteStringMap;
  }

  public Map<String, ResponseDataProto> toResponseDataProtoV2(Map<String, ResponseData> responseDataMap) {
    Map<String, ResponseDataProto> responseDataProtoMap = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(responseDataMap)) {
      responseDataMap.forEach((k, v) -> {
        if (v instanceof BinaryResponseData) {
          BinaryResponseData binaryResponseData = (BinaryResponseData) v;
          // This implies this is coming from the PMS driver module. Eventually this will be the only way
          responseDataProtoMap.put(k,
              ResponseDataProto.newBuilder()
                  .setResponse(ByteString.copyFrom(binaryResponseData.getData()))
                  .setUsingKryoWithoutReference(binaryResponseData.getUsingKryoWithoutReference())
                  .build());
        } else {
          responseDataProtoMap.put(k,
              ResponseDataProto.newBuilder()
                  .setResponse(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(v)))
                  .setUsingKryoWithoutReference(false)
                  .build());
        }
      });
    }
    return responseDataProtoMap;
  }
}
