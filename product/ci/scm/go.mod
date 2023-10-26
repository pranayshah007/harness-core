module github.com/harness/harness-core/product/ci/scm

go 1.20

replace github.com/harness/harness-core/commons/go/lib => ../../../commons/go/lib

replace github.com/harness/harness-core/product/log-service => ../../../product/log-service

require (
	github.com/alexflint/go-arg v1.3.0
	github.com/drone/go-scm v1.32.3
	github.com/drone/go-scm-codecommit v0.0.0-20210315104920-2d8b9dc5ed8a
	github.com/go-git/go-git/v5 v5.4.2
	github.com/golang/mock v1.6.0
	github.com/golang/protobuf v1.5.2
	github.com/grpc-ecosystem/go-grpc-middleware v1.2.1
	github.com/harness/harness-core/commons/go/lib v0.0.0-20230704115211-8b21b2758330
	github.com/stretchr/testify v1.8.1
	github.com/wings-software/autogen-go v1.0.0
	go.uber.org/zap v1.15.0
	golang.org/x/net v0.5.0
	google.golang.org/grpc v1.53.0
	google.golang.org/protobuf v1.28.1
)

require (
	github.com/Microsoft/go-winio v0.4.16 // indirect
	github.com/ProtonMail/go-crypto v0.0.0-20210428141323-04723f9f07d7 // indirect
	github.com/acomagu/bufpipe v1.0.3 // indirect
	github.com/alexflint/go-scalar v1.0.0 // indirect
	github.com/blendle/zapdriver v1.3.1 // indirect
	github.com/cenkalti/backoff v2.2.1+incompatible // indirect
	github.com/cenkalti/backoff/v4 v4.2.1 // indirect
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/docker/go-units v0.5.0 // indirect
	github.com/drone/go-convert v0.0.0-20230329091825-a238380a6b0e // indirect
	github.com/drone/spec v0.0.0-20230328162846-c2bc4a6d62ab // indirect
	github.com/emirpasic/gods v1.12.0 // indirect
	github.com/ghodss/yaml v1.0.0 // indirect
	github.com/go-git/gcfg v1.5.0 // indirect
	github.com/go-git/go-billy/v5 v5.3.1 // indirect
	github.com/gofrs/uuid v4.4.0+incompatible // indirect
	github.com/harness/harness-core v0.0.0-20230630234258-c5cacffec07e // indirect
	github.com/harness/harness-core/product/log-service v0.0.0-00010101000000-000000000000 // indirect
	github.com/harness/ti-client v0.0.0-20230923024915-d3bca98c7660 // indirect
	github.com/imdario/mergo v0.3.12 // indirect
	github.com/jbenet/go-context v0.0.0-20150711004518-d14ea06fba99 // indirect
	github.com/kevinburke/ssh_config v0.0.0-20201106050909-4977a11b4351 // indirect
	github.com/mattn/go-zglob v0.0.4 // indirect
	github.com/mitchellh/go-homedir v1.1.0 // indirect
	github.com/pkg/errors v0.9.1 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	github.com/robbiet480/go.sns v0.0.0-20181124163742-ca087b49e1da // indirect
	github.com/sergi/go-diff v1.1.0 // indirect
	github.com/sirupsen/logrus v1.9.2 // indirect
	github.com/xanzy/ssh-agent v0.3.0 // indirect
	go.uber.org/atomic v1.6.0 // indirect
	go.uber.org/multierr v1.5.0 // indirect
	golang.org/x/crypto v0.0.0-20210421170649-83a5a9bb288b // indirect
	golang.org/x/sys v0.8.0 // indirect
	golang.org/x/text v0.7.0 // indirect
	google.golang.org/genproto v0.0.0-20230110181048-76db0878b65f // indirect
	gopkg.in/warnings.v0 v0.1.2 // indirect
	gopkg.in/yaml.v2 v2.4.0 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
	mvdan.cc/sh/v3 v3.2.4 // indirect
)
