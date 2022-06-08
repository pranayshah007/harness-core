package io.harness.ci.integrationstage;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;

import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.ff.CIFeatureFlagService;
import io.harness.logserviceclient.CILogServiceUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.stateutils.buildstate.SecretUtils;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.tiserviceclient.TIServiceUtils;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class K8InitializeTaskUtilsTest extends CIExecutionTestBase {
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock CILogServiceUtils logServiceUtils;
  @Mock TIServiceUtils tiServiceUtils;
  @Mock STOServiceUtils stoServiceUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private SecretUtils secretUtils;
  @Inject private K8InitializeTaskUtils k8InitializeTaskUtils;

  @Before
  public void setUp() {
    on(k8InitializeTaskUtils).set("connectorUtils", connectorUtils);
    on(k8InitializeTaskUtils).set("secretUtils", secretUtils);
    on(k8InitializeTaskUtils).set("executionSweepingOutputResolver", executionSweepingOutputResolver);
    on(k8InitializeTaskUtils).set("logServiceUtils", logServiceUtils);
    on(k8InitializeTaskUtils).set("featureFlagService", featureFlagService);
    on(k8InitializeTaskUtils).set("tiServiceUtils", tiServiceUtils);
    on(k8InitializeTaskUtils).set("stoServiceUtils", stoServiceUtils);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void convertDirectK8Volumes() {
    K8sDirectInfraYaml k8sDirectInfraYaml = K8InitializeTaskUtilsHelper.getDirectK8Infrastructure();

    List<PodVolume> expected = K8InitializeTaskUtilsHelper.getConvertedVolumes();
    List<PodVolume> actual = k8InitializeTaskUtils.convertDirectK8Volumes(k8sDirectInfraYaml);

    assertThat(actual).isEqualTo(expected);
  }
}