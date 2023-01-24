package io.harness.googlefunctions;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.OperationMetadata;
import com.google.cloud.run.v2.DeleteRevisionRequest;
import com.google.cloud.run.v2.GetRevisionRequest;
import com.google.cloud.run.v2.GetServiceRequest;
import com.google.cloud.run.v2.ListRevisionsRequest;
import com.google.cloud.run.v2.ListRevisionsResponse;
import com.google.cloud.run.v2.Revision;
import com.google.cloud.run.v2.RevisionsClient;
import com.google.cloud.run.v2.Service;
import com.google.cloud.run.v2.UpdateServiceRequest;

public interface GoogleCloudRunClient {
  ListRevisionsResponse listRevisions(ListRevisionsRequest listRevisionsRequest, GcpInternalConfig gcpInternalConfig);

  Service getService(GetServiceRequest getServiceRequest, GcpInternalConfig gcpInternalConfig);

  OperationFuture<Service, Service> updateService(
      UpdateServiceRequest updateServiceRequest, GcpInternalConfig gcpInternalConfig);

  OperationFuture<Revision, Revision> deleteRevision(
      DeleteRevisionRequest deleteRevisionRequest, GcpInternalConfig gcpInternalConfig);

  Revision getRevision(GetRevisionRequest getRevisionRequest, GcpInternalConfig gcpInternalConfig);
}
