package io.harness.cdng.environment.filters;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.BaseFilterYaml;
import io.harness.walktree.visitor.SimpleVisitorHelper;

import org.codehaus.jackson.annotate.JsonProperty;
import org.springframework.data.annotation.TypeAlias;

@SimpleVisitorHelper(helperClass = EnvironmentFilterVisitorHelper.class)
@TypeAlias("environmentsFilterYaml")
@RecasterAlias("io.harness.cdng.environment.filters.EnvironmentsFilterYaml")
@OwnedBy(HarnessTeam.CDC)
public class EnvironmentsFilterYaml extends BaseFilterYaml<EnvironmentsFilterYaml.Entity> {
  public enum Entity {
    @JsonProperty("infrastructures") infrastructures,
    @JsonProperty("gitOpsClusters") gitOpsClusters,
    @JsonProperty("environments") environments;
  }
}
