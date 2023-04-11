# Step 1: Installation

Installation and deployment should take about 10 minutes.

The Docker Compose installer is described below, but Harness also supports a [Helm installer](https://github.com/harness/harness-cd-community/blob/main/helm/README.md).

Make sure your system meets the **Requirements** above.

Ensure Docker Desktop is running and Docker Desktop Kubernetes is running with it.

Clone the Harness Git repo into your local directory:

```git
git clone https://github.com/harness/harness-cd-community
```

The output will look something like this:

```
Cloning into 'harness-cd-community'...  
remote: Enumerating objects: 793, done.  
remote: Counting objects: 100% (793/793), done.  
remote: Compressing objects: 100% (371/371), done.  
remote: Total 793 (delta 497), reused 592 (delta 339), pack-reused 0  
Receiving objects: 100% (793/793), 116.39 KiB | 2.42 MiB/s, done.  
Resolving deltas: 100% (497/497), done.
```
Change directory to the **harness** folder:


```
cd harness-cd-community/docker-compose/harness
```
Build and run Harness using Docker Compose:


```
docker-compose up -d
```
Do not try to bring up containers one by one. Harness CD Community Edition is a distributed system with dependencies. The only way to bring it up is using `docker-compose up -d`.

The first download can take 3–12 mins (downloading images and starting all containers) depending on the speed of your Internet connection. You won't be able to sign up until all the required containers are up and running.The output will look something like this:

```
[+] Running 13/13  
 ⠿ Network harness_harness-network       Created                                                                                                                                                  0.1s  
 ⠿ Container harness_log-service_1       Started                                                                                                                                                  2.9s  
 ⠿ Container harness_redis_1             Started                                                                                                                                                  2.7s  
...
```

Run the following command to ensure all services are running:

```
docker-compose ps
```

All services should show `running (healthy)`. If any show `running (starting)`, wait a minute, and run `docker-compose ps` again until they are all `running (healthy)`.

Run the following command to start the Harness Manager (it will wait until all services are healthy):

```
docker-compose run --rm proxy wait-for-it.sh ng-manager:7090 -t 180
```

The output will look like this:

```
[+] Running 6/0  
 ⠿ Container harness_platform-service_1  Running                                                                                                                                                  0.0s  
 ⠿ Container harness_pipeline-service_1  Running                                                                                                                                                  0.0s  
 ⠿ Container harness_manager_1           Running                                                                                                                                                  0.0s  
 ⠿ Container harness_delegate-proxy_1    Running                                                                                                                                                  0.0s  
 ⠿ Container harness_ng-manager_1        Running                                                                                                                                                  0.0s  
 ⠿ Container harness_ng-ui_1             Running                                                                                                                                                  0.0s  
wait-for-it.sh: waiting 180 seconds for ng-manager:7090  
wait-for-it.sh: ng-manager:7090 is available after 0 seconds
```

Wait until you see that `ng-manager` is available:

```
wait-for-it.sh: ng-manager:7090 is available after 0 seconds
```

In your browser, go to the URL `http://localhost/#/signup`.

If you see a 403 error, that just means the Harness Manager service isn't up and running yet. Make sure you ran the wait-for-it.sh script earlier and wait a few minutes: `docker-compose run --rm proxy wait-for-it.sh ng-manager:7090 -t 180`.

Enter an email address and password and click **Sign up**.

You'll see the CD page:

![](./static/harness-community-edition-quickstart-133.png)

You're now using Harness!

The next section walks you through setting up and running a simple CD Pipeline using a public manifest and Docker image.
