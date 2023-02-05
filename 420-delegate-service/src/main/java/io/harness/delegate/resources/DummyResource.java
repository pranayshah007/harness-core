package io.harness.delegate.resources;

import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.persistence.HPersistence;
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
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api("version")
@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class DummyResource {
  @Inject private HPersistence persistence;

  @GET
  @Path("/delegate/random")
  @Timed
  @ExceptionMetered
  @DelegateAuth2
  public RestResponse<List<String>> getDelegateVersion(@QueryParam("accountId") @NotEmpty String accountId) {
    List<Delegate> groupDelegates = persistence.createQuery(Delegate.class)
                                        .filter(Delegate.DelegateKeys.accountId, "kmpySmUISimoRrJL6NL73w")
                                        .asList();

    log.info("Checking if persistence is working or not : " + groupDelegates.get(0).getIp());

    return new RestResponse<>(List.of("outputString"));
  }
}