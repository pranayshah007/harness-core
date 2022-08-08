/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.infrastructure.instance;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "InvocationCountFields")
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class InvocationCount {
  private InvocationCountKey key;
  private long count;
  private Instant from;
  private Instant to;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InvocationCount that = (InvocationCount) o;
    return count == that.count && key == that.key;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, count);
  }

  public enum InvocationCountKey {
    LAST_30_DAYS,
    SINCE_LAST_DEPLOYED;
    public static final List<InvocationCountKey> INVOCATION_COUNT_KEY_LIST =
        ImmutableList.of(LAST_30_DAYS, SINCE_LAST_DEPLOYED);
  }
}
