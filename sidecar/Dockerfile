FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod main.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /sidecar .
FROM scratch
COPY --from=build /sidecar /sidecar
USER 65532:65532
ENTRYPOINT ["/sidecar"]
