package io.harness.repositories;

import lombok.Data;

@Data
public class UpsertOptions {
  boolean sendOutboxEvent;

  public UpsertOptions() {
    this.sendOutboxEvent = true;
  }

  public UpsertOptions(boolean sendOutboxEvent) {
    this.sendOutboxEvent = sendOutboxEvent;
  }

  public static final UpsertOptions DEFAULT = new UpsertOptions();

  public UpsertOptions withNoOutbox() {
    this.sendOutboxEvent = false;
    return this;
  }

  public static UpsertOptions none() {
    return DEFAULT;
  }
}
