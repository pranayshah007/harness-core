/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.gcs;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.UNKNOWN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GcpConfig;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.api.client.util.DateTime;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.Buckets;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

public class GcsServiceTest extends WingsBaseTest {
  @Mock private GcpHelperService gcpHelperService;
  @Mock private Storage gcsStorageService;
  @Mock private Storage.Buckets bucketsObj;
  @Mock private Storage.Buckets.List listRequest;
  @Mock private Storage.Buckets.Get unversionedBucketReq;
  @Mock private Storage.Buckets.Get versionedBucketReq;
  @Inject private GcsService gcsService;
  private static final String TEST_PROJECT_ID = "test";
  private static String serviceAccountFileContent = "{\"project_id\":\"test\"}";
  @Mock Storage.Objects objects;
  @Mock Storage storage;
  @Mock Storage.Objects.List lists;

  private static final GcpConfig gcpConfig = GcpConfig.builder()
                                                 .accountId("accountId")
                                                 .serviceAccountKeyFileContent(serviceAccountFileContent.toCharArray())
                                                 .build();

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    FieldUtils.writeField(gcsService, "gcpHelperService", gcpHelperService, true);
    when(gcpHelperService.getGcsStorageService(
             gcpConfig.getServiceAccountKeyFileContent(), gcpConfig.isUseDelegateSelectors()))
        .thenReturn(storage)
        .thenReturn(storage);
    when(storage.objects()).thenReturn(objects);
    when(objects.list(anyString())).thenReturn(lists);
    when(storage.buckets()).thenReturn(bucketsObj);
    when(bucketsObj.get("bucket")).thenReturn(unversionedBucketReq);
    when(bucketsObj.get("versionedBucket")).thenReturn(versionedBucketReq);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldGetProject() {
    assertThat(gcsService.getProject(gcpConfig, null)).isNotNull().isEqualTo(TEST_PROJECT_ID);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldListBuckets() throws IOException {
    HashMap<String, String> bucketList = new HashMap<>();
    bucketList.put("bucket", "bucketId");
    Buckets buckets = new Buckets();
    Bucket bucket = new Bucket();
    bucket.setName("bucket");
    bucket.setId("bucketId");
    buckets.setItems(Arrays.asList(bucket));
    when(gcpHelperService.getGcsStorageService(
             gcpConfig.getServiceAccountKeyFileContent(), gcpConfig.isUseDelegateSelectors()))
        .thenReturn(gcsStorageService);
    when(gcsStorageService.buckets()).thenReturn(bucketsObj);
    when(bucketsObj.list(TEST_PROJECT_ID)).thenReturn(listRequest);
    when(listRequest.execute()).thenReturn(buckets);
    assertThat(gcsService.listBuckets(gcpConfig, TEST_PROJECT_ID, null)).isNotNull().hasSize(1).containsKeys("bucket");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetArtifactPaths() throws IOException {
    Objects objects1 = new Objects();
    String[] items = new String[] {"obj1", "randomObj", "obj2", "obj3"};
    objects1.setItems(IntStream.rangeClosed(0, 3)
                          .mapToObj(i -> {
                            StorageObject storageObject = new StorageObject();
                            storageObject.setName(items[i]);
                            storageObject.setUpdated(new DateTime(new Date().getTime() + i));
                            return storageObject;
                          })
                          .unordered()
                          .collect(Collectors.toList()));
    when(lists.execute()).thenReturn(objects1);

    assertThat(gcsService.getArtifactPaths(gcpConfig, null, "bucketName"))
        .hasSize(4)
        .isEqualTo(Lists.newArrayList("obj3", "obj2", "randomObj", "obj1"));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetArtifactsBuildDetails() throws IOException {
    // Versioned Bucket
    Bucket versionedBucket = new Bucket();
    Bucket.Versioning versioning = new Bucket.Versioning();
    versioning.setEnabled(true);
    versionedBucket.setVersioning(versioning);
    when(versionedBucketReq.execute()).thenReturn(versionedBucket);

    Storage.Objects.Get versionedReq = Mockito.mock(Storage.Objects.Get.class);
    when(objects.get("versionedBucket", "versionedObj")).thenReturn(versionedReq);
    StorageObject versionedObj = new StorageObject();
    versionedObj.setUpdated(new DateTime(1608176560));
    versionedObj.setGeneration(1L);
    when(versionedReq.execute()).thenReturn(versionedObj);

    // Unversioned Bucket
    when(unversionedBucketReq.execute()).thenReturn(new Bucket());

    Storage.Objects.Get unversionedReq = Mockito.mock(Storage.Objects.Get.class);
    when(objects.get("bucket", "unversionedObj")).thenReturn(unversionedReq);
    when(objects.get("bucket", "path/p1/abc")).thenReturn(unversionedReq);
    StorageObject unversionedObj = new StorageObject();
    unversionedObj.setUpdated(new DateTime(1608176690));
    unversionedObj.setGeneration(1L);
    when(unversionedReq.execute()).thenReturn(unversionedObj);

    BuildDetails actual = gcsService
                              .getArtifactsBuildDetails(gcpConfig, null,
                                  ArtifactStreamAttributes.builder().jobName("versionedBucket").build(),
                                  Lists.newArrayList("versionedObj"), false, 100)
                              .get(0);
    assertThat(actual.getNumber()).isEqualTo("versionedObj:1");
    assertThat(actual.getRevision()).isEqualTo("versionedObj:1");
    assertThat(actual.getArtifactPath()).isEqualTo("versionedObj");
    assertThat(actual.getUiDisplayName()).isEqualTo("Build# versionedObj:1");

    actual =
        gcsService
            .getArtifactsBuildDetails(gcpConfig, null, ArtifactStreamAttributes.builder().jobName("bucket").build(),
                Lists.newArrayList("unversionedObj"), false, 100)
            .get(0);
    assertThat(actual.getNumber()).isEqualTo("unversionedObj");
    assertThat(actual.getRevision()).isEqualTo("unversionedObj");
    assertThat(actual.getArtifactPath()).isEqualTo("unversionedObj");
    assertThat(actual.getUiDisplayName()).isEqualTo("Build# unversionedObj");

    Objects objects1 = new Objects();
    objects1.setItems(Lists.newArrayList("path/p1/", "path/p1/abc")
                          .stream()
                          .map(item -> {
                            StorageObject storageObject = new StorageObject();
                            storageObject.setName(item);
                            storageObject.setUpdated(new DateTime(new Date(), TimeZone.getTimeZone("GMT")));
                            return storageObject;
                          })
                          .unordered()
                          .collect(Collectors.toList()));
    when(lists.execute()).thenReturn(objects1);
    List<BuildDetails> actualBuildDetails = gcsService.getArtifactsBuildDetails(gcpConfig, null,
        ArtifactStreamAttributes.builder().jobName("bucket").build(), Lists.newArrayList("path/*"), true, 100);
    assertThat(actualBuildDetails).hasSize(1);
    actual = actualBuildDetails.get(0);
    assertThat(actual.getNumber()).isEqualTo("path/p1/abc");
    assertThat(actual.getRevision()).isEqualTo("path/p1/abc");
    assertThat(actual.getArtifactPath()).isEqualTo("path/p1/abc");
    assertThat(actual.getUiDisplayName()).isEqualTo("Build# path/p1/abc");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldHandleThrownExceptionListBuckets() throws IOException {
    when(gcpHelperService.getGcsStorageService(
             gcpConfig.getServiceAccountKeyFileContent(), gcpConfig.isUseDelegateSelectors()))
        .thenReturn(gcsStorageService);
    when(gcsStorageService.buckets()).thenReturn(bucketsObj);
    when(bucketsObj.list(TEST_PROJECT_ID)).thenReturn(listRequest);
    when(listRequest.execute()).thenThrow(new RuntimeException("some error"));
    assertThatThrownBy(() -> gcsService.listBuckets(gcpConfig, TEST_PROJECT_ID, null))
        .isInstanceOf(InvalidArtifactServerException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldThrowHandleExceptionWhenGetArtifactBuildDetails() {
    when(gcpHelperService.getGcsStorageService(
             gcpConfig.getServiceAccountKeyFileContent(), gcpConfig.isUseDelegateSelectors()))
        .thenThrow(new RuntimeException("some error"));
    assertThatThrownBy(() -> gcsService.getArtifactBuildDetails(gcpConfig, null, "somebucket", "someObj", true))
        .isInstanceOf(InvalidArtifactServerException.class);
  }
}
