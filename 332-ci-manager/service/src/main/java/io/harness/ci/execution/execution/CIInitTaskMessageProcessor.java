package io.harness.ci.execution;

import io.harness.hsqs.client.model.DequeueResponse;

public interface CIInitTaskMessageProcessor {
    public Boolean processMessage(DequeueResponse dequeueResponse);
}
