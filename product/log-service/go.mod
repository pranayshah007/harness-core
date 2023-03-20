module github.com/harness/harness-core/product/log-service

go 1.14

require (
	cloud.google.com/go/secretmanager v1.0.0
	github.com/alecthomas/template v0.0.0-20190718012654-fb15b899a751 // indirect
	github.com/alecthomas/units v0.0.0-20190924025748-f65c72e2690d // indirect
	github.com/alicebob/miniredis/v2 v2.13.3
	github.com/aws/aws-sdk-go v1.34.29
	github.com/bazelbuild/bazel-gazelle v0.22.2
	github.com/cenkalti/backoff/v4 v4.1.0
	github.com/dchest/authcookie v0.0.0-20190824115100-f900d2294c8e
	github.com/elliotchance/redismock v1.5.3
	github.com/elliotchance/redismock/v7 v7.0.1
	github.com/go-chi/chi v4.1.2+incompatible
	github.com/go-co-op/gocron v1.7.0
	github.com/go-redis/redis v6.15.9+incompatible // indirect
	github.com/go-redis/redis/v7 v7.4.0
	github.com/gofrs/uuid v3.3.0+incompatible
	github.com/golang/mock v1.6.0
	github.com/google/go-cmp v0.5.6
	github.com/google/uuid v1.1.2
	github.com/hashicorp/go-multierror v1.1.0
	github.com/jasonlvhit/gocron v0.0.1
	github.com/joho/godotenv v1.3.0
	github.com/kelseyhightower/envconfig v1.4.0
	github.com/onsi/ginkgo v1.14.1 // indirect
	github.com/onsi/gomega v1.10.2 // indirect
	github.com/pkg/errors v0.9.1
	github.com/sirupsen/logrus v1.6.0
	github.com/stretchr/testify v1.7.1
	go.etcd.io/bbolt v1.3.5
	go.uber.org/automaxprocs v1.5.1 // indirect
	go.uber.org/goleak v1.1.10
	golang.org/x/crypto v0.0.0-20200820211705-5c72a883971a
	golang.org/x/sync v0.0.0-20210220032951-036812b2e83c
	gopkg.in/alecthomas/kingpin.v2 v2.2.6
)
