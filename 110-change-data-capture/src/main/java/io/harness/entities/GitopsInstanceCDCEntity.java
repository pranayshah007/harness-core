package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.changehandlers.GitopsInstanceChangeDataHandler;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GitopsInstanceCDCEntity implements CDCEntity<Instance> {
  @Inject private GitopsInstanceChangeDataHandler gitopsInstanceChangeDataHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    if (handlerClass.contentEquals("GitopsInstanceServiceAndCluster")) {
      return gitopsInstanceChangeDataHandler;
    }
    return null;
  }

  @Override
  public Class<Instance> getSubscriptionEntity() {
    return Instance.class;
  }
}
