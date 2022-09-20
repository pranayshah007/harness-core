package io.harness.ng.core;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class DecryptableEntityDTO {
  String type;
  NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer;
}
