package io.harness.pms.sdk.core.creator;

import static io.harness.pms.sdk.PmsSdkModuleUtils.PLAN_CREATOR_SERVICE_EXECUTOR;

import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.CreationRequest;
import io.harness.pms.contracts.plan.CreationResponse;
import io.harness.pms.contracts.plan.DependencyV1;
import io.harness.pms.sdk.core.plan.creation.beans.MergeCreationResponse;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class CreatorFrameworkEngine {
  @Inject @Named(PLAN_CREATOR_SERVICE_EXECUTOR) private Executor executor;

  @Inject CreatorFactory factory;
  public CreationResponse create(CreationRequest creationRequest) {
    Map<String, DependencyV1> initialDependency = creationRequest.getDepsMap();
    Map<String, DependencyV1> dependencies = new HashMap<>(initialDependency);
    // Creating empty final response. All the creation responses will be merged in the final response.
    MergeCreationResponse finalResponse = MergeCreationResponse.parentBuilder().build();
    CreatorServiceV1 creatorService = factory.getCreatorService(creationRequest);

    CreatorContext ctx = creatorService.getContext(creationRequest);

    while (!dependencies.isEmpty()) {
      dependencies = createAndGetChildrenDependencies(creatorService, finalResponse, ctx, dependencies);
      // Remove initial dependencies. Also breaking condition if loop does not exit.
    }
    return finalResponse.toProto();
  }

  Map<String, DependencyV1> createAndGetChildrenDependencies(CreatorServiceV1 service,
      MergeCreationResponse finalResponse, CreatorContext ctx, Map<String, DependencyV1> dependencies) {
    String currentYaml = ctx.getYaml();
    YamlField fullField;
    try {
      fullField = YamlUtils.readTree(currentYaml);
    } catch (IOException ex) {
      String message = "Invalid yaml during creation";
      throw new InvalidRequestException(message);
    }
    CompletableFutures<MergeCreationResponse> completableFutures = new CompletableFutures<>(executor);

    dependencies.forEach((key1, value) -> completableFutures.supplyAsync(() -> {
      try {
        return service.resolveDependency(
            currentYaml, fullField.fromYamlPath(value.getValue()), ctx, value.getDependencyMetadataMap());
      } catch (IOException e) {
        throw new InvalidRequestException("");
      }
    }));

    List<MergeCreationResponse> creationResponses = null;
    try {
      creationResponses = completableFutures.allOf().get(5, TimeUnit.MINUTES);
    } catch (InterruptedException | TimeoutException | ExecutionException e) {
      //
    }
    Map<String, DependencyV1> newDependencies = service.getNewDependenciesFromCreationResponse(creationResponses);
    service.mergeCreationResponsesIntoFinalResponse(finalResponse, creationResponses, dependencies);
    return newDependencies;
  }
}
