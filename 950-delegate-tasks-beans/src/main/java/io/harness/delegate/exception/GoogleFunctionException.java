package io.harness.delegate.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DataException;
import lombok.EqualsAndHashCode;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@EqualsAndHashCode(callSuper = false)
public class GoogleFunctionException extends DataException {
    public GoogleFunctionException(Throwable cause) {
        super(cause);
    }
}
