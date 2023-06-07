package io.harness.util;

import lombok.Builder;
import lombok.Data;

import java.util.concurrent.Future;

@Data
@Builder
public class DelegateTaskExecutionData {
    private long executionStartTime;
    private Future<?> taskFuture;
}