package software.wings.jersey;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

@Singleton
public class ProtobufFeature implements Feature {
  @Inject ProtoBuffMessageBodyProvider protoBuffMessageBodyProvider;


  @Override
  public boolean configure(FeatureContext featureContext) {
    Configuration config = featureContext.getConfiguration();
    if (protoBuffMessageBodyProvider != null && !config.isRegistered(protoBuffMessageBodyProvider)) {
      //featureContext.register(protoBuffMessageBodyProvider);
      //featureContext.register(new ProtocolBufferMessageBodyProvider());
    }

    return true;
  }
}
