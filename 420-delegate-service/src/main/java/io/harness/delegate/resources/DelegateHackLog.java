package io.harness.delegate.resources;

import io.harness.service.impl.stackdriver.ErrorHack;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DelegateHackLog {
  String delegateName;
  String delegateId;
  String accountId;
  String exceptionType;
}
