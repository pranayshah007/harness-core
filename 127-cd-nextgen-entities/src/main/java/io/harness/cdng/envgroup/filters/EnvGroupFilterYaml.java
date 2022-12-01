package io.harness.cdng.envgroup.filters;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.BaseFilterYaml;
import io.harness.walktree.visitor.SimpleVisitorHelper;

import org.codehaus.jackson.annotate.JsonProperty;
import org.springframework.data.annotation.TypeAlias;

@SimpleVisitorHelper(helperClass = EnvGroupFilterVisitorHelper.class)
@TypeAlias("envGroupFilterYaml")
@RecasterAlias("io.harness.cdng.envgroup.filters.EnvGroupFilterYaml")
@OwnedBy(HarnessTeam.CDC)
public class EnvGroupFilterYaml extends BaseFilterYaml<EnvGroupFilterYaml.Entity> {
  public enum Entity {
    @JsonProperty("infrastructures") infrastructures,
    @JsonProperty("gitOpsClusters") gitOpsClusters,
    @JsonProperty("environments") environments;
  }
}