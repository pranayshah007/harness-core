#build scripts for building various native-images can be stored in this directory

In this directory, you will find scripts and supporting files for building two different harness modules.

1) CG Manager

    build script: build-cg-manager.native.sh
    status: not building successfully


3) Platform Service

   build script: build-platform-manager.native.sh
   status: builds successfully and runs but errors out on Dropwizard complaining about defining classes at runtime
    
   In oder to build succesfully, we needed to run with native-image-agent like so:
   
   `java -agentlib:native-image-agent=config-output-dir=./tools/graalvm/scripts/agentout -Xlog:class+load=info:classloaded.txt -jar $P_JAR server $HOME/workspace/harness-core/platform-service/config/config.yml`

