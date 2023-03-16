/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.googlefunctions;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.run.v2.DeleteRevisionRequest;
import com.google.cloud.run.v2.GetRevisionRequest;
import com.google.cloud.run.v2.GetServiceRequest;
import com.google.cloud.run.v2.ListRevisionsRequest;
import com.google.cloud.run.v2.ListRevisionsResponse;
import com.google.cloud.run.v2.Revision;
import com.google.cloud.run.v2.Service;
import com.google.cloud.run.v2.UpdateServiceRequest;
import com.google.longrunning.Operation;

public interface GoogleCloudRunClient {
  ListRevisionsResponse listRevisions(ListRevisionsRequest listRevisionsRequest, GcpInternalConfig gcpInternalConfig);

  Service getService(GetServiceRequest getServiceRequest, GcpInternalConfig gcpInternalConfig);

  OperationFuture<Service, Service> updateService(
      UpdateServiceRequest updateServiceRequest, GcpInternalConfig gcpInternalConfig);

  OperationFuture<Revision, Revision> deleteRevision(
      DeleteRevisionRequest deleteRevisionRequest, GcpInternalConfig gcpInternalConfig);

  Revision getRevision(GetRevisionRequest getRevisionRequest, GcpInternalConfig gcpInternalConfig);

  Operation getOperation(String operationName, GcpInternalConfig gcpInternalConfig);
}
