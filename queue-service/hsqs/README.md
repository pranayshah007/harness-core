# hsqs
Harness Queueing and Scheduling library

## Setup

Requirements:

`go>=1.19`\
`redis`

This library uses [echo](https://echo.labstack.com/]echo) framework

Default run configuration (for Goland) is already present in the repo in  

Use below command to run using bazel

1. `bazel build //queue-service/hsqs/...` to create bazel build

2. `swag init -g cmd/server.go` from hsqs folder to generate swagger               
