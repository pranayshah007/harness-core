# GraalVm  and Native Image

There is a continuous drive to get our services to have the smallest footprint and the fastest and most efficient
runtime.  GraalVm and it's native image tool can be instrumental in shrinking down the size of our deployable artifacts 
as well as reducing said services memory footprint, startup time and even response times in some cases.


## GraalVM as a jdk/jre
GraalVM promises to build and run any java application that may also be built using any other jdk of the same version. 
In other words, an project that may be built and run using jdk 11, may also, instead, be built and run using graalvm
version 11.  We may get some benefit from using graalvm as our jdk but that is not going to be where the big gains in 
performance come from.. for the big gains, we need to compile our java apps to native executables.

##GraalVM native image tool
Once we intall graal, we may add the native-image tool.. this tool allows us to take a java file and it's dependencies
and build them into a native executable for a given operating system.  There are challenges to doing this.

- Reflection.  
- Build time vs run time initializations
- OS compatibility

### Reflection
Reflection is allowed but must be accounted for in a file that lists all classes that will require it

### Build time vs Run time init
Ideally, all classes should be intitialized at build time.  In practice,
unless we are building the simplest of java files, we will need to initialize some things at run time.

### Os compatibility
At that time of this writing, for an M1 mac, there are fewer versions of GraalVM available than for intel versions of 
Mac.  Where this can be a problem, is not as much in the versions of graalVM available for the M1 (there is one for 
java version 11 as well as for 17 which is good), the problem though is that there may not be an exact version available
that may have been used in a white paper or demo that exists on the internet that we may want to repro and having the
exact versions are nice and sometimes required to have.  

##Building the native-image tool from source code
###Why would we want to build native-image
We if we need to debug the native-image app itself, we may do so. This is useful when the stack trace info that 
native-image give is not enough and we want to walk the code with a debugger when we run into issues that are really 
tough to find a work around for. An example of such an issue is, for instance, when we need to load some class at run
time but the native-image binary is not telling us exactly which class.. in this case, we can debug it and walk the 
stack to find the class that attempted to load at built time and move forward from there..

###How to build
native-image source lives in this repo:
https://github.com/oracle/graal

clone the repo and cd to substratevm

follow SubstrateVM.md file to build substratvm this will compile native-image 

Once built, we will want to debug it as described
