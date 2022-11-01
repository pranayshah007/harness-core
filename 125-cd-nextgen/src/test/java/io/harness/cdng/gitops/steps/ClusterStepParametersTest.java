package io.harness.cdng.gitops.steps;

import static io.harness.rule.OwnerRule.MANAVJOT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import de.bwaldvogel.mongo.backend.Assert;
import junitparams.JUnitParamsRunner;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@OwnedBy(HarnessTeam.GITOPS)
@RunWith(JUnitParamsRunner.class)
public class ClusterStepParametersTest {
  @Test
  @Owner(developers = MANAVJOT)
  @Category(UnitTests.class)
  public void testWithEnvGroup() {
    String envGroupName = "name";
    String envGroupId = "id";
    ClusterStepParameters parameters =
        ClusterStepParameters.WithEnvGroup(Metadata.builder().identifier(envGroupId).name(envGroupName).build());
    Assert.equals(envGroupName, parameters.getEnvGroupName());
    Assert.equals(envGroupId, parameters.getEnvGroupRef());
    Assert.equals(Boolean.TRUE, parameters.isDeployToAllEnvs());
  }

  @Test(expected = NullPointerException.class)
  @Category(UnitTests.class)
  public void testWithEnvGroup_exception() {
    ClusterStepParameters.WithEnvGroup(null);
  }
}
