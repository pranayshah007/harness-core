package io.harness.steps.Email;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDC)
public class EmailOutcome implements Outcome {}
