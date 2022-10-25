/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import com.google.protobuf.Timestamp;
import java.time.Duration;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProtoUtils {
  public Long timestampToUnixMillis(Timestamp ts) {
    return ts == null ? null : ts.getSeconds() * 1000 + ts.getNanos() / 1000000;
  }

  public Timestamp unixMillisToTimestamp(Long millis) {
    return millis == null
        ? null
        : Timestamp.newBuilder().setSeconds(millis / 1000).setNanos((int) (millis % 1000) * 1000000).build();
  }

  public Duration durationToJavaDuration(com.google.protobuf.Duration d) {
    return d == null ? null : Duration.ofNanos(d.getSeconds() * 1000000000 + d.getNanos());
  }

  public com.google.protobuf.Duration javaDurationToDuration(Duration d) {
    return d == null
        ? null
        : com.google.protobuf.Duration.newBuilder().setSeconds(d.getSeconds()).setNanos(d.getNano()).build();
  }
}
