package io.harness.ng.chaos;

import io.harness.persistence.HPersistence;

import com.google.inject.AbstractModule;
import java.util.concurrent.atomic.AtomicReference;

public class ChaosModule extends AbstractModule {
  private static final AtomicReference<ChaosModule> instanceRef = new AtomicReference<>();

  public static ChaosModule getInstance() {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null, new ChaosModule());
    }
    return instanceRef.get();
  }

  @Override
  protected void configure() {
    bind(ChaosService.class).to(ChaosServiceImpl.class);
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
  }
}
