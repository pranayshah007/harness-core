package io.harness.ng.chaos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CE)
@Api(value = "chaos", hidden = true)
@Path("chaos")
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class ChaosK8sResource {
  ChaosService chaosService;

  @POST
  @InternalApi
  @ApiOperation(value = "Apply K8s manifest for chaos", nickname = "chaosK8sApply", hidden = true)
  public void applyChaosK8sManifest(ChaosK8sRequest request) {
    chaosService.applyK8sManifest(request);
  }
}
