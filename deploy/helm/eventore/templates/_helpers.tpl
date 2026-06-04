{{- define "eventore.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "eventore.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "eventore.labels" -}}
helm.sh/chart: {{ include "eventore.name" . }}
app.kubernetes.io/name: {{ include "eventore.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "eventore.selectorLabels" -}}
app.kubernetes.io/name: {{ include "eventore.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "eventore.providerSlug" -}}
{{- $protocol := . -}}
{{- if eq $protocol "GCP_PUBSUB" -}}gcp-pubsub
{{- else if eq $protocol "AZURE_SERVICE_BUS" -}}azure-servicebus
{{- else -}}{{ $protocol | lower }}
{{- end -}}
{{- end }}

{{- define "eventore.backendImageTag" -}}
{{- if not .Values.eventore.streamProviders }}
{{- fail "eventore.streamProviders is required (e.g. [KAFKA] or [KAFKA, KINESIS]). Backend image tag is derived from this list." }}
{{- end }}
{{- if .Values.image.backend.tag -}}
{{- .Values.image.backend.tag -}}
{{- else if eq (len .Values.eventore.streamProviders) 8 -}}all
{{- else -}}
{{- $slugs := list -}}
{{- range $p := sortAlpha .Values.eventore.streamProviders -}}
{{- $slugs = append $slugs (include "eventore.providerSlug" $p) -}}
{{- end -}}
{{- join "-" $slugs -}}
{{- end -}}
{{- end }}

{{- define "eventore.enabledProtocolsCsv" -}}
{{- join "," .Values.eventore.streamProviders -}}
{{- end }}
