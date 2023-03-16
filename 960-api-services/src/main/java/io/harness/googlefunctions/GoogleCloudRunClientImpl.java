/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.googlefunctions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.run.v2.DeleteRevisionRequest;
import com.google.cloud.run.v2.GetRevisionRequest;
import com.google.cloud.run.v2.GetServiceRequest;
import com.google.cloud.run.v2.ListRevisionsRequest;
import com.google.cloud.run.v2.ListRevisionsResponse;
import com.google.cloud.run.v2.Revision;
import com.google.cloud.run.v2.RevisionsClient;
import com.google.cloud.run.v2.Service;
import com.google.cloud.run.v2.ServicesClient;
import com.google.cloud.run.v2.UpdateServiceRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.longrunning.Operation;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class GoogleCloudRunClientImpl implements GoogleCloudRunClient {
  @Inject private GoogleCloudClientHelper googleCloudClientHelper;

  private static final String CLIENT_NAME = "Google Cloud Run";
  @Override
  public ListRevisionsResponse listRevisions(
      ListRevisionsRequest listRevisionsRequest, GcpInternalConfig gcpInternalConfig) {
    try (RevisionsClient client = googleCloudClientHelper.getRevisionsClient(gcpInternalConfig)) {
      googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return client.listRevisions(listRevisionsRequest).getPage().getResponse();
    } catch (Exception e) {
      googleCloudClientHelper.logError(
          CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      googleCloudClientHelper.handleException(e);
    }
    return ListRevisionsResponse.getDefaultInstance();
  }

  @Override
  public Service getService(GetServiceRequest getServiceRequest, GcpInternalConfig gcpInternalConfig) {
    try (ServicesClient client = googleCloudClientHelper.getServicesClient(gcpInternalConfig)) {
      googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return client.getService(getServiceRequest);
    } catch (Exception e) {
      googleCloudClientHelper.logError(
          CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      googleCloudClientHelper.handleException(e);
    }
    return Service.getDefaultInstance();
  }

  @Override
  public OperationFuture<Service, Service> updateService(
      UpdateServiceRequest updateServiceRequest, GcpInternalConfig gcpInternalConfig) {
    try (ServicesClient client = googleCloudClientHelper.getServicesClient(gcpInternalConfig)) {
      googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return client.updateServiceAsync(updateServiceRequest);
    } catch (Exception e) {
      googleCloudClientHelper.logError(
          CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      googleCloudClientHelper.handleException(e);
    }
    return null;
  }

  @Override
  public OperationFuture<Revision, Revision> deleteRevision(
      DeleteRevisionRequest deleteRevisionRequest, GcpInternalConfig gcpInternalConfig) {
    try (RevisionsClient client = googleCloudClientHelper.getRevisionsClient(gcpInternalConfig)) {
      googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return client.deleteRevisionAsync(deleteRevisionRequest);
    } catch (Exception e) {
      googleCloudClientHelper.logError(
          CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      googleCloudClientHelper.handleException(e);
    }
    return null;
  }

  @Override
  public Revision getRevision(GetRevisionRequest getRevisionRequest, GcpInternalConfig gcpInternalConfig) {
    try (RevisionsClient client = googleCloudClientHelper.getRevisionsClient(gcpInternalConfig)) {
      googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return client.getRevision(getRevisionRequest);
    } catch (Exception e) {
      googleCloudClientHelper.logError(
          CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      googleCloudClientHelper.handleException(e);
    }
    return Revision.getDefaultInstance();
  }

  @Override
  public Operation getOperation(String operationName, GcpInternalConfig gcpInternalConfig) {
    try (ServicesClient client = googleCloudClientHelper.getServicesClient(gcpInternalConfig)) {
      googleCloudClientHelper.logCall(CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName());
      return client.getOperationsClient().getOperation(operationName);
    } catch (Exception e) {
      googleCloudClientHelper.logError(
          CLIENT_NAME, Thread.currentThread().getStackTrace()[1].getMethodName(), e.getMessage());
      googleCloudClientHelper.handleException(e);
    }
    return Operation.getDefaultInstance();
  }
}
