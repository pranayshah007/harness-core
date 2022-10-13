package io.harness.freeze.beans;

import io.harness.notification.FreezeEventType;

import java.util.List;

public class FreezeEvent {
  FreezeEventType type;
  List<String> forStages;
}
