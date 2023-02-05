package io.harness.delegate.resources;

import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

@Api("version")
@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DummyResource {
  @GET
  @Path("/delegate")
  @Timed
  @ExceptionMetered
  @DelegateAuth2
  public RestResponse<List<String>> getDelegateVersion(@QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(List.of("outputString"));
  }
}