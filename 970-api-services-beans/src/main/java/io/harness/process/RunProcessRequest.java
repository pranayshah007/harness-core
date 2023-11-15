package io.harness.process;

import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RunProcessRequest {
  String command;
  String pwd;
  Map<String, String> environment;
  OutputStream outputStream;
  OutputStream errorStream;
  long timeout;
  TimeUnit timeoutTimeUnit;
  boolean readOutput;

  public String getProcessKey() {
    return command;
  }
}
