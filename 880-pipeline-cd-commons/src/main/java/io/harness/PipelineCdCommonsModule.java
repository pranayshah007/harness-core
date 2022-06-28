package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.AbstractModule;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineCdCommonsModule extends AbstractModule {
  private static PipelineCdCommonsModule instance;

  public static PipelineCdCommonsModule getInstance() {
    if (instance == null) {
      instance = new PipelineCdCommonsModule();
    }
    return instance;
  }

  @Override
  protected void configure() {}
}
