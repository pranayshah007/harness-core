module github.com/harness/harness-core/product/ci/engine

go 1.14

replace github.com/harness/harness-core/commons/go/lib => ../../../commons/go/lib

replace github.com/harness/harness-core/product/log-service => ../../../product/log-service

replace github.com/harness/harness-core/product/ci/addon => ../../../product/ci/addon

require (
	github.com/alexflint/go-arg v1.3.0
	github.com/cenkalti/backoff/v4 v4.1.3
	github.com/cespare/xxhash v1.1.0
	github.com/gofrs/uuid v4.2.0+incompatible
	github.com/gogo/protobuf v1.3.1
	github.com/golang/mock v1.6.0
	github.com/golang/protobuf v1.5.2
	github.com/grpc-ecosystem/go-grpc-middleware v1.2.1
	github.com/harness/harness-core/commons/go/lib v0.0.0-20220222141117-7659b7eca599
	github.com/harness/harness-core/product/log-service v0.0.0-20220222141117-7659b7eca599
	github.com/minio/minio-go/v6 v6.0.57
	github.com/pkg/errors v0.9.1
	github.com/stretchr/testify v1.7.5
	github.com/wings-software/dlite v1.0.0-rc.5 // indirect
	go.uber.org/zap v1.15.0
	golang.org/x/net v0.0.0-20220114011407-0dd24b26b47d
	google.golang.org/grpc v1.43.0
	google.golang.org/protobuf v1.27.1
	mvdan.cc/sh/v3 v3.2.4 // indirect
	sigs.k8s.io/structured-merge-diff v1.0.1-0.20191108220359-b1b620dd3f06 // indirect
)
