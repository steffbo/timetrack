package api

//go:generate env XDG_CACHE_HOME=/tmp/timetrack-go-cache go tool oapi-codegen --config codegen.yaml ../../../openapi.yaml
//go:generate go run ../../scripts/jsoncompat.go
