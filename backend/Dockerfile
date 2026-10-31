FROM node:20-alpine AS frontend-build
WORKDIR /frontend
RUN npm config set registry https://registry.npmjs.org/
COPY frontend/package*.json ./
RUN npm ci
COPY openapi.yaml /openapi.yaml
COPY frontend/ ./
RUN npm run generate-api && npm run build

FROM golang:1.25-alpine AS backend-build
WORKDIR /src/backend
COPY backend/go.mod backend/go.sum ./
RUN go mod download
COPY backend/ ./
COPY --from=frontend-build /frontend/dist ./web/dist
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /timetrack ./cmd/timetrack

FROM alpine:3.22
RUN apk add --no-cache ca-certificates tzdata && addgroup -S timetrack && adduser -S timetrack -G timetrack
WORKDIR /app
COPY --from=backend-build /timetrack ./timetrack
USER timetrack:timetrack
EXPOSE 8811 7080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
 CMD wget --spider "http://localhost:${MANAGEMENT_PORT:-7080}/actuator/health" || exit 1
ENTRYPOINT ["/app/timetrack"]
