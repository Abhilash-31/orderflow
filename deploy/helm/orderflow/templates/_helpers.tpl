{{/*
Common labels applied to every resource in this chart.
*/}}
{{- define "orderflow.labels" -}}
app.kubernetes.io/part-of: orderflow
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}
