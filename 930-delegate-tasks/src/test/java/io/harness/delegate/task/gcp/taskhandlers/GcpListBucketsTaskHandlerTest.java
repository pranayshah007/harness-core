/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gcp.taskhandlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.delegate.task.gcp.request.GcpListBucketsRequest;
import io.harness.delegate.task.gcp.request.GcpListClustersRequest;
import io.harness.delegate.task.gcp.response.GcpBucketDetails;
import io.harness.delegate.task.gcp.response.GcpListBucketsResponse;
import io.harness.encryption.SecretRefData;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.Buckets;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class GcpListBucketsTaskHandlerTest extends CategoryTest {
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private GcpHelperService gcpHelperService;
  @Mock private NGErrorHelper ngErrorHelper;

  @InjectMocks GcpListBucketsTaskHandler taskHandler;

  @Mock private Storage storageService;
  @Mock private Storage.Buckets bucketsService;
  @Mock private Storage.Buckets.List bucketsList;

  private final List<EncryptedDataDetail> encryptionDetails =
      Collections.singletonList(EncryptedDataDetail.builder().fieldName("serviceToken").build());
  private final char[] serviceAccountFileContent = "{\"project_id\": \"test_sa\"}".toCharArray();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn("test_sa").when(gcpHelperService).getProjectId(serviceAccountFileContent, false);
    doReturn("test_delegate").when(gcpHelperService).getProjectId(null, true);
    doReturn(storageService).when(gcpHelperService).getGcsStorageService(any(), anyBoolean());
    doReturn(bucketsService).when(storageService).buckets();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteServiceAccount() throws Exception {
    GcpManualDetailsDTO manualDetailsDTO =
        GcpManualDetailsDTO.builder()
            .secretKeyRef(SecretRefData.builder().decryptedValue(serviceAccountFileContent).build())
            .build();
    GcpListBucketsRequest request = GcpListBucketsRequest.builder()
                                        .encryptionDetails(encryptionDetails)
                                        .delegateSelectors(Collections.emptySet())
                                        .gcpManualDetailsDTO(manualDetailsDTO)
                                        .build();
    sampleTest(request, "test_sa");
    verify(secretDecryptionService, times(1)).decrypt(manualDetailsDTO, encryptionDetails);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteUseDelegate() throws Exception {
    GcpListBucketsRequest request =
        GcpListBucketsRequest.builder().delegateSelectors(Collections.singleton("test")).useDelegate(true).build();
    sampleTest(request, "test_delegate");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteMultiplePages() throws Exception {
    GcpListBucketsRequest request =
        GcpListBucketsRequest.builder().delegateSelectors(Collections.singleton("test")).useDelegate(true).build();
    Buckets bucketsPage1 = new Buckets();
    Buckets bucketsPage2 = new Buckets();
    Buckets bucketsPage3 = new Buckets();
    bucketsPage1.setItems(asList(bucket("bucket-1", "bucket-1"), bucket("bucket-2", "bucket-2")));
    bucketsPage1.setNextPageToken("second");
    bucketsPage2.setItems(asList(bucket("bucket-3", "bucket-3"), bucket("bucket-4", "bucket-4")));
    bucketsPage2.setNextPageToken("third");
    bucketsPage3.setItems(Collections.singletonList(bucket("bucket-5", "bucket-5")));

    doReturn(bucketsList).when(bucketsService).list("test_delegate");
    doCallRealMethod().when(bucketsList).setPageToken(anyString());
    doCallRealMethod().when(bucketsList).getPageToken();
    doAnswer(invocationOnMock -> {
      Storage.Buckets.List mock = (Storage.Buckets.List) invocationOnMock.getMock();
      if (isEmpty(mock.getPageToken())) {
        return bucketsPage1;
      } else if ("second".equals(mock.getPageToken())) {
        return bucketsPage2;
      } else if ("third".equals(mock.getPageToken())) {
        return bucketsPage3;
      }

      throw new IllegalStateException();
    })
        .when(bucketsList)
        .execute();

    GcpListBucketsResponse response = (GcpListBucketsResponse) taskHandler.executeRequest(request);
    assertThat(response.getBuckets()).hasSize(5);
    assertThat(response.getBuckets().stream().map(GcpBucketDetails::getId))
        .containsExactly("bucket-1", "bucket-2", "bucket-3", "bucket-4", "bucket-5");
    assertThat(response.getBuckets().stream().map(GcpBucketDetails::getName))
        .containsExactly("bucket-1", "bucket-2", "bucket-3", "bucket-4", "bucket-5");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteException() throws Exception {
    GcpListBucketsRequest request =
        GcpListBucketsRequest.builder().delegateSelectors(Collections.singleton("test")).useDelegate(true).build();
    RuntimeException thrownException = new RuntimeException("An error occurred");
    ErrorDetail errorDetail = ErrorDetail.builder().code(500).build();
    doThrow(thrownException).when(bucketsService).list("test_delegate");
    doReturn("Something went wrong").when(ngErrorHelper).getErrorSummary("An error occurred");
    doReturn(errorDetail).when(ngErrorHelper).createErrorDetail("An error occurred");

    GcpListBucketsResponse response = (GcpListBucketsResponse) taskHandler.executeRequest(request);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getErrorMessage()).isEqualTo("Something went wrong");
    assertThat(response.getErrorDetail()).isEqualTo(errorDetail);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteInvalidType() {
    assertThatThrownBy(() -> taskHandler.executeRequest(GcpListClustersRequest.builder().build()))
        .hasMessageContaining("Unsupported request type: GcpListClustersRequest, expected: GcpListBucketsRequest");
  }

  private void sampleTest(GcpListBucketsRequest request, String projectId) throws Exception {
    Buckets buckets = new Buckets();
    buckets.setItems(asList(bucket("bucket-1", "bucket-1"), bucket("bucket-2", "bucket-2")));
    doReturn(bucketsList).when(bucketsService).list(projectId);
    doReturn(buckets).when(bucketsList).execute();

    GcpListBucketsResponse response = (GcpListBucketsResponse) taskHandler.executeRequest(request);
    assertThat(response.getBuckets()).hasSize(2);
    assertThat(response.getBuckets().stream().map(GcpBucketDetails::getId)).containsExactly("bucket-1", "bucket-2");
    assertThat(response.getBuckets().stream().map(GcpBucketDetails::getName)).containsExactly("bucket-1", "bucket-2");
  }

  private Bucket bucket(String id, String name) {
    Bucket bucket = new Bucket();
    bucket.setId(id);
    bucket.setName(name);

    return bucket;
  }
}
