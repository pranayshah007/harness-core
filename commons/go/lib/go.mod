module github.com/harness/harness-core/commons/go/lib

go 1.14

require (
	cloud.google.com/go/secretmanager v1.11.1
	cloud.google.com/go/storage v1.33.0
	github.com/aws/aws-sdk-go v1.34.29
	github.com/blendle/zapdriver v1.3.1
	github.com/cenkalti/backoff/v4 v4.2.1
	github.com/go-sql-driver/mysql v1.5.0
	github.com/gofrs/uuid v4.4.0+incompatible
	github.com/golang/mock v1.6.0
	github.com/google/go-containerregistry v0.3.0
	github.com/grpc-ecosystem/go-grpc-middleware v1.2.1
	github.com/harness/harness-core v0.0.0-20231030111038-62145d0092e3
	github.com/harness/harness-core/product/log-service v0.0.0-20231030114849-cac3c1783f0b // indirect
	github.com/harness/ti-client v0.0.0-20231018000842-d164bcf4f802
	github.com/hashicorp/go-multierror v1.1.0
	github.com/lib/pq v1.10.9
	github.com/mattn/go-zglob v0.0.4
	github.com/minio/minio-go/v6 v6.0.57
	github.com/opentracing/opentracing-go v1.2.0
	github.com/pkg/errors v0.9.1
	github.com/satori/go.uuid v1.2.0
	github.com/shirou/gopsutil/v3 v3.21.1
	github.com/sirupsen/logrus v1.9.2
	github.com/stretchr/testify v1.8.3
	github.com/vdemeester/k8s-pkg-credentialprovider v1.18.1-0.20201019120933-f1d16962a4db
	go.uber.org/zap v1.15.0
	google.golang.org/api v0.145.0
	google.golang.org/grpc v1.58.3
	gopkg.in/DATA-DOG/go-sqlmock.v1 v1.3.0
	k8s.io/api v0.20.1
	mvdan.cc/sh/v3 v3.7.0
)
