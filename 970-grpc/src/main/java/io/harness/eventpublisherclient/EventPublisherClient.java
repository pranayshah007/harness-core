package io.harness.eventpublisherclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.event.PublishRequest;
import io.harness.event.PublishResponse;

import javax.ws.rs.Consumes;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CE)
public interface EventPublisherClient {
  @Consumes({"application/x-protobuf"})
  @POST("k8sevent/publish")
  Call<PublishResponse> publish(@Query("accountId") String accountId, @Body PublishRequest publishRequest);
}
