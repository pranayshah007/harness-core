package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.Builder;
import lombok.Data;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RMap;

@OwnedBy(HarnessTeam.DEL)
@Data
@Builder
public class DelegateTaskCache {
  private String accountId;
  private AtomicInteger noOfDelegateTasksCurrentlyAssigned;
  private AtomicInteger noOfPerpetualTasksCurrentlyAssigned;
  private RAtomicLong v;

}
