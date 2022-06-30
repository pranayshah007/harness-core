package software.wings.jersey;


import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

public class ProtoBuffFeature implements Feature {


    @Override
    public boolean configure(FeatureContext featureContext) {
        return false;
    }
}
