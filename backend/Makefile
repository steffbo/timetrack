GO ?= go
.PHONY: generate test vet build
generate:
	$(GO) generate ./...
test:
	$(GO) test ./...
vet:
	$(GO) vet ./...
build:
	CGO_ENABLED=0 $(GO) build -trimpath -ldflags="-s -w" ./cmd/timetrack
