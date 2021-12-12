package io.harness.pms.sdk;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.lock.PersistentLocker;
import io.harness.pms.contracts.plan.InitializeSdkRequest;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.repositories.sdk.PmsSdkInstanceRepository;
import io.harness.rule.Owner;
import io.harness.springdata.TransactionHelper;

import io.vavr.collection.Iterator;
import java.util.Map;
import javax.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSdkInstanceTest extends CategoryTest {
  @Mock PmsSdkInstanceRepository pmsSdkInstanceRepository;
  @Mock MongoTemplate mongoTemplate;
  @Mock PersistentLocker persistentLocker;
  @Mock SchemaFetcher schemaFetcher;
  @Mock Cache<String, PmsSdkInstance> sdkInstanceCache;
  @Mock TransactionHelper transactionHelper;
  PmsSdkInstanceService pmsSdkInstanceService;

  @Before
  public void SetUp() {
    MockitoAnnotations.initMocks(this);
    pmsSdkInstanceService = new PmsSdkInstanceService(pmsSdkInstanceRepository, mongoTemplate, persistentLocker,
        schemaFetcher, sdkInstanceCache, true, transactionHelper);
    when(sdkInstanceCache.iterator()).thenReturn(Iterator.empty());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSaveSdkInstance() {
    InitializeSdkRequest request = InitializeSdkRequest.newBuilder().putStaticAliases("alias", "value").build();
    assertThatCode(() -> pmsSdkInstanceService.saveSdkInstance(request)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetSdkInstanceCacheValueShouldUseCache() {
    when(sdkInstanceCache.containsKey("cd")).thenReturn(true);
    when(sdkInstanceCache.get("cd")).thenReturn(PmsSdkInstance.builder().name("cd").build());
    Map<String, PmsSdkInstance> sdkInstanceMap = pmsSdkInstanceService.getSdkInstanceCacheValue();
    assertThat(sdkInstanceMap.size()).isEqualTo(0);
  }
}
