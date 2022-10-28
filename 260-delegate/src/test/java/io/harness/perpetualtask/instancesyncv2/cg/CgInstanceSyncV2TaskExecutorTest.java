package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.DelegateTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncTrackedDeploymentDetails;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import java.time.Instant;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class CgInstanceSyncV2TaskExecutorTest extends DelegateTestBase {
  @Inject KryoSerializer kryoSerializer;
  @InjectMocks private CgInstanceSyncV2TaskExecutor executor;

  @Mock private Call<RestResponse<Boolean>> call;
  @Mock private Call<InstanceSyncTrackedDeploymentDetails> callFetch;

  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Before
  public void setup() {
    on(executor).set("kryoSerializer", kryoSerializer);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void runOnceWithK8sCallSuccess() throws Exception {
    InstanceSyncTrackedDeploymentDetails instanceSyncTrackedDeploymentDetails =
        InstanceSyncTrackedDeploymentDetails.newBuilder()
            .setPerpetualTaskId("perpetualTaskId")
            .setAccountId("AccountId")
            .addAllDeploymentDetails(Collections.singleton(
                CgDeploymentReleaseDetails.newBuilder()
                    .setTaskDetailsId("taskDetailsId")
                    .setInfraMappingType("K8s")
                    .setInfraMappingId("infraMappingId")
                    .setReleaseDetails(Any.pack(
                        DirectK8sInstanceSyncTaskDetails.newBuilder()
                            .setReleaseName("releaseName")
                            .setNamespace("namespace")
                            /*                    .setK8SClusterConfig(ByteString.copyFrom(kryoSerializer.asBytes(
                                                    K8sClusterConfig.builder().clusterName("clusterName").namespace("namespace").build())))*/
                            .setIsHelm(false)
                            .setContainerServiceName("")
                            .build()))
                    .build()))
            .build();
    doReturn(call)
        .when(delegateAgentManagerClient)
        .publishInstanceSyncResult(anyString(), anyString(), any(DelegateResponseData.class));
    doReturn(retrofit2.Response.success("success")).when(call).execute();

    doReturn(callFetch).when(delegateAgentManagerClient).fetchTrackedReleaseDetails(anyString(), anyString());
    doReturn(retrofit2.Response.success(instanceSyncTrackedDeploymentDetails)).when(callFetch).execute();

    PerpetualTaskResponse perpetualTaskResponse;
    perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId("id").build(), getK8sPerpetualTaskParams(), Instant.now());
  }

  private PerpetualTaskExecutionParams getK8sPerpetualTaskParams() {
    /*    ByteString configBytes =
            ByteString.copyFrom(kryoSerializer.asBytes(K8sClusterConfig.builder().namespace("namespace").build()));*/

    CgInstanceSyncTaskParams params =
        CgInstanceSyncTaskParams.newBuilder().setAccountId("AccountId").setCloudProviderType("K8s").build();
    return PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(params)).build();
  }
}