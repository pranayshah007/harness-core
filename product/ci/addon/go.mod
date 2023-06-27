module github.com/harness/harness-core/product/ci/addon

go 1.14

replace github.com/harness/harness-core/commons/go/lib => ../../../commons/go/lib

replace github.com/harness/harness-core/product/log-service => ../../../product/log-service

require (
	github.com/Azure/azure-pipeline-go v0.2.2 // indirect
	github.com/Azure/go-autorest/autorest/azure/auth v0.4.2 // indirect
	github.com/Djarvur/go-err113 v0.1.0 // indirect
	github.com/alexflint/go-arg v1.3.0
	github.com/apex/log v1.3.0 // indirect
	github.com/bombsimon/wsl/v2 v2.2.0 // indirect
	github.com/bombsimon/wsl/v3 v3.1.0 // indirect
	github.com/ghodss/yaml v1.0.0
	github.com/go-critic/go-critic v0.4.3 // indirect
	github.com/go-toolsmith/typep v1.0.2 // indirect
	github.com/golang/mock v1.6.0
	github.com/golangci/gocyclo v0.0.0-20180528144436-0a533e8fa43d // indirect
	github.com/golangci/misspell v0.3.5 // indirect
	github.com/golangci/revgrep v0.0.0-20180812185044-276a5c0a1039 // indirect
	github.com/google/go-containerregistry v0.3.0
	github.com/google/wire v0.4.0 // indirect
	github.com/goreleaser/goreleaser v0.136.0 // indirect
	github.com/goreleaser/nfpm v1.3.0 // indirect
	github.com/gostaticanalysis/analysisutil v0.0.3 // indirect
	github.com/grpc-ecosystem/go-grpc-middleware v1.2.1
	github.com/harness/harness-core v0.0.0-20220210161509-69d6cb167b7a
	github.com/harness/harness-core/commons/go/lib v0.0.0-20220222141117-7659b7eca599
	github.com/harness/harness-core/product/ci/engine v0.0.0-20220526003445-374c61227e45
	github.com/harness/ti-client v0.0.0-20230625210213-de916cd21d22
	github.com/hashicorp/go-retryablehttp v0.6.6 // indirect
	github.com/jirfag/go-printf-func-name v0.0.0-20200119135958-7558a9eaa5af // indirect
	github.com/joho/godotenv v1.4.0
	github.com/mattn/go-colorable v0.1.6 // indirect
	github.com/mattn/go-ieproxy v0.0.1 // indirect
	github.com/mattn/go-zglob v0.0.3
	github.com/mitchellh/mapstructure v1.3.1 // indirect
	github.com/pelletier/go-toml v1.8.0 // indirect
	github.com/pkg/errors v0.9.1
	github.com/ryancurrah/gomodguard v1.1.0 // indirect
	github.com/securego/gosec v0.0.0-20200401082031-e946c8c39989 // indirect
	github.com/sourcegraph/go-diff v0.5.3 // indirect
	github.com/spf13/cast v1.3.1 // indirect
	github.com/spf13/jwalterweatherman v1.1.0 // indirect
	github.com/spf13/viper v1.7.0 // indirect
	github.com/stretchr/testify v1.7.1
	github.com/tdakkota/asciicheck v0.0.0-20200416200610-e657995f937b // indirect
	github.com/tetafro/godot v0.4.2 // indirect
	github.com/timakin/bodyclose v0.0.0-20200424151742-cb6215831a94 // indirect
	github.com/tj/assert v0.0.0-20171129193455-018094318fb0
	github.com/vdemeester/k8s-pkg-credentialprovider v1.18.1-0.20201019120933-f1d16962a4db
	github.com/xanzy/go-gitlab v0.32.0 // indirect
	go.uber.org/zap v1.16.0
	golang.org/x/net v0.0.0-20220114011407-0dd24b26b47d
	google.golang.org/grpc v1.43.0
	gopkg.in/ini.v1 v1.56.0 // indirect
	honnef.co/go/tools v0.0.1-2020.1.5 // indirect
	k8s.io/api v0.20.1
	mvdan.cc/sh v2.6.4+incompatible
	mvdan.cc/sh/v3 v3.2.4
	mvdan.cc/unparam v0.0.0-20200501210554-b37ab49443f7 // indirect
	sourcegraph.com/sqs/pbtypes v1.0.0 // indirect
)
