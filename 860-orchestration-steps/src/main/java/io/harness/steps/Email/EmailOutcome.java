package io.harness.steps.Email;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDC)
@RecasterAlias("io.harness.steps.Email.EmailOutcome")
public class EmailOutcome implements Outcome {}
