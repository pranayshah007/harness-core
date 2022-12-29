package io.harness.batch.processing.tasklet;

import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.service.impl.GoogleCloudStorageServiceImpl;
import io.harness.batch.processing.service.intfc.AwsS3SyncService;
import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.batch.processing.tasklet.support.HarnessEntitiesService;
import io.harness.batch.processing.tasklet.support.HarnessTagService;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.service.intf.InstanceDataService;
import io.harness.ff.FeatureFlagService;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class ClusterDataToClickHouseTasklet implements Tasklet {
  public static final String CLUSTER_DATA_AVRO_FILES_BUCKET = "cluster-data-avro-files";
  public static final String S3_PREFIX = "";
  @Autowired private BatchMainConfig config;
  @Autowired private BillingDataServiceImpl billingDataService;
  @Autowired private InstanceDataService instanceDataService;
  @Autowired private GoogleCloudStorageServiceImpl googleCloudStorageService;

  @Autowired private AwsS3SyncService awsS3SyncService;
  @Autowired private HarnessTagService harnessTagService;
  @Autowired private HarnessEntitiesService harnessEntitiesService;
  @Autowired private WorkloadRepository workloadRepository;
  //  @Autowired private FeatureFlagService featureFlagService;

  private static final String defaultParentWorkingDirectory = "./avro/";
  private static final String defaultBillingDataFileNameDaily = "billing_data_%s_%s_%s.avro";
  private static final String defaultBillingDataFileNameHourly = "billing_data_hourly_%s_%s_%s_%s.avro";
  private static final String gcsObjectNameFormat = "%s/%s";
  public static final long CACHE_SIZE = 10000;

  LoadingCache<HarnessEntitiesService.CacheKey, String> entityIdToNameCache =
      Caffeine.newBuilder()
          .maximumSize(CACHE_SIZE)
          .build(key -> harnessEntitiesService.fetchEntityName(key.getEntity(), key.getEntityId()));

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    JobParameters parameters = chunkContext.getStepContext().getStepExecution().getJobParameters();
    BatchJobType batchJobType = CCMJobConstants.getBatchJobTypeFromJobParams(parameters);
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);
    String accountId = jobConstants.getAccountId();
    Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
    Instant endTime = Instant.ofEpochMilli(jobConstants.getJobEndTime());

    AWSCredentialsProvider credentials = constructStaticBasicAwsCredentials(
        config.getAwsS3SyncConfig().getAwsAccessKey(), config.getAwsS3SyncConfig().getAwsSecretKey());
    S3Objects s3Objects = getIterableS3ObjectSummaries(credentials, CLUSTER_DATA_AVRO_FILES_BUCKET, S3_PREFIX);

    for (S3ObjectSummary objectSummary : s3Objects) {
      List<String> path = Arrays.asList(objectSummary.getKey().split("/"));
      String accountIdFolderName = path.get(0);
      String fileName = path.get(1);
      String cloudProvider = "CLUSTER";

      if (objectSummary.getLastModified().compareTo(java.util.Date.from(startTime)) >= 0
          //          && objectSummary.getLastModified().compareTo(java.util.Date.from(endTime)) <= 0) {
      ) {
        if (!objectSummary.getKey().endsWith(".avro")) {
          log.info("Nothing to ingest!!");
        }
      }
    }

    return null;
  }

  public S3Objects getIterableS3ObjectSummaries(
      AWSCredentialsProvider credentialsProvider, String s3BucketName, String s3Prefix) {
    try {
      return S3Objects.withPrefix(getAmazonS3Client(credentialsProvider), s3BucketName, s3Prefix);
    } catch (Exception e) {
      System.out.println(e);
    }
    return null;
  }

  public AWSCredentialsProvider constructStaticBasicAwsCredentials(String accessKey, String secretKey) {
    return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
  }

  public AmazonS3Client getAmazonS3Client(AWSCredentialsProvider credentialsProvider) {
    return (AmazonS3Client) AmazonS3ClientBuilder.standard()
        .withRegion(config.getAwsS3SyncConfig().getRegion())
        .withForceGlobalBucketAccessEnabled(Boolean.TRUE)
        .withCredentials(credentialsProvider)
        .build();
  }
}
