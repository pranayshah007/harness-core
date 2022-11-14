package io.harness.ccm.commons.constants;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import java.time.Duration;
import lombok.experimental.UtilityClass;

@OwnedBy(CE)
@UtilityClass
public final class RecommendationConstants {
  public static final Double SAVINGS_THRESHOLD = 0.0;
  public static final Duration RECOMMENDATION_TTL = Duration.ofDays(15);
}
