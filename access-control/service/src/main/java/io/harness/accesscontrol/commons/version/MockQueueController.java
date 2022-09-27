package io.harness.accesscontrol.commons.version;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.queue.QueueController;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class MockQueueController implements QueueController {
  @Override
  public boolean isPrimary() {
    return true;
  }

  @Override
  public boolean isNotPrimary() {
    return false;
  }
}
