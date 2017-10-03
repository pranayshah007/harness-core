package software.wings.beans;

import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.IndexOptions;
import org.mongodb.morphia.annotations.Indexed;
import software.wings.utils.Misc;

import java.util.List;

@Entity("lambdaSpecifications")
@Data
@Builder
public class LambdaSpecification extends Base {
  @NotEmpty @Indexed(options = @IndexOptions(unique = true)) private String serviceId;
  private DefaultSpecification defaults;
  private List<FunctionSpecification> functions;

  @Data
  @Builder
  public static class DefaultSpecification {
    @NotEmpty private String runtime;
    private Integer memorySize = 128;
    private Integer timeout = 3;
    public String getRuntime() {
      return Misc.trim(runtime);
    }
  }

  @Data
  @Builder
  public static class FunctionSpecification {
    @NotEmpty private String runtime;
    private Integer memorySize = 128;
    private Integer timeout = 3;
    @NotEmpty private String functionName;
    @NotEmpty private String handler;

    public String getRuntime() {
      return Misc.trim(runtime);
    }
    public String getFunctionName() {
      return Misc.trim(functionName);
    }
    public String getHandler() {
      return Misc.trim(handler);
    }
  }
}
