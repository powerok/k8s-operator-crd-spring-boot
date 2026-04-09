/* global Vue, hljs */
const API = "/api/dashboard";

const SAMPLES = {
  ghostdatabases: `apiVersion: operator.example.com/v1alpha1
kind: GhostDatabase
metadata:
  name: ui-demo-db
  namespace: default
spec:
  databaseName: appdb
  storageSize: 1Gi
  initSql: |
    CREATE TABLE IF NOT EXISTS hello (id serial PRIMARY KEY, msg text);
`,
  staticsitedeployers: `apiVersion: operator.example.com/v1alpha1
kind: StaticSiteDeployer
metadata:
  name: ui-demo-site
  namespace: default
spec:
  gitRepoUrl: https://github.com/octocat/Hello-World.git
  branch: master
`,
  resourcequotaguards: `apiVersion: operator.example.com/v1alpha1
kind: ResourceQuotaGuard
metadata:
  name: ui-demo-quota
  namespace: default
spec:
  targetNamespace: default
  thresholdPercent: 80
  workloadLabelKey: app
  workloadLabelValue: scale-me
`,
  maintenancewindows: `apiVersion: operator.example.com/v1alpha1
kind: MaintenanceWindow
metadata:
  name: ui-demo-window
  namespace: default
spec:
  targetNamespace: default
  deploymentName: your-deployment
  timezone: Asia/Seoul
  windowStart: "09:00"
  windowEnd: "18:00"
  replicasDuringWindow: 2
  replicasOutsideWindow: 0
`,
  apigatewayroutes: `apiVersion: operator.example.com/v1alpha1
kind: ApiGatewayRoute
metadata:
  name: ui-demo-route
  namespace: default
spec:
  host: api.example.test
  pathPrefix: /api
  backendServiceName: backend-svc
  backendPort: 8080
  ingressClassName: nginx
  requireAuth: false
`,
};

const { createApp } = Vue;

createApp({
  data() {
    return {
      theme: localStorage.getItem("dash-theme") || "dark",
      health: null,
      kinds: [],
      namespaces: [],
      namespace: "default",
      selectedPlural: "ghostdatabases",
      rows: [],
      listWarning: "",
      filterText: "",
      loading: false,
      modal: null,
      applyNamespace: "default",
      applyYamlText: "",
      applyBusy: false,
      viewTitle: "",
      viewYamlText: "",
      activityLog: [],
      toasts: [],
      toastId: 0,
      testTab: "ping",
      testPingBody: '{\n  "ping": "dashboard"\n}',
      testPingResult: "",
      healthJson: "",
    };
  },
  computed: {
    filteredRows() {
      const q = this.filterText.toLowerCase();
      if (!q) return this.rows;
      return this.rows.filter((r) => r.name.toLowerCase().includes(q));
    },
    highlightedViewYaml() {
      const t = this.viewYamlText;
      if (!t || typeof hljs === "undefined") return "";
      try {
        return hljs.highlight(t, { language: "yaml" }).value;
      } catch {
        return hljs.highlightAuto(t).value;
      }
    },
  },
  watch: {
    theme(v) {
      document.documentElement.setAttribute("data-theme", v === "light" ? "light" : "dark");
      localStorage.setItem("dash-theme", v);
    },
  },
  mounted() {
    document.documentElement.setAttribute("data-theme", this.theme === "light" ? "light" : "dark");
    this.refreshAll();
  },
  methods: {
    log(line) {
      const ts = new Date().toISOString().slice(11, 19);
      this.activityLog.unshift(`[${ts}] ${line}`);
      if (this.activityLog.length > 14) this.activityLog.pop();
    },
    toast(text, type = "ok") {
      const id = ++this.toastId;
      this.toasts.push({ id, text, type });
      setTimeout(() => {
        this.toasts = this.toasts.filter((t) => t.id !== id);
      }, 4200);
    },
    toggleTheme() {
      this.theme = this.theme === "dark" ? "light" : "dark";
    },
    kindIcon(kind) {
      const m = {
        GhostDatabase: "🐘",
        StaticSiteDeployer: "🌐",
        ResourceQuotaGuard: "🛡️",
        MaintenanceWindow: "⏰",
        ApiGatewayRoute: "🔀",
      };
      return m[kind] || "📦";
    },
    phaseClass(phase) {
      if (!phase || phase === "—") return "badge-muted";
      const p = String(phase).toLowerCase();
      if (p === "ready" || p === "ok") return "badge-ready";
      if (p === "failed") return "badge-failed";
      if (p === "alerting") return "badge-alerting";
      return "badge-muted";
    },
    async apiGet(path) {
      const res = await fetch(API + path);
      const text = await res.text();
      let data;
      try {
        data = JSON.parse(text);
      } catch {
        data = text;
      }
      if (!res.ok) throw new Error(typeof data === "string" ? data : JSON.stringify(data));
      return data;
    },
    async refreshHealth() {
      try {
        this.health = await this.apiGet("/health");
        this.log(`health: ${this.health.ok ? "OK" : "FAIL"}`);
      } catch (e) {
        this.health = { ok: false, error: e.message };
        this.log(`health error: ${e.message}`);
      }
    },
    async loadNamespaces() {
      try {
        this.namespaces = await this.apiGet("/namespaces");
        if (!this.namespaces.includes(this.namespace)) {
          this.namespace = this.namespaces[0] || "default";
        }
        this.applyNamespace = this.namespace;
        this.log(`namespaces: ${this.namespaces.length}`);
      } catch (e) {
        this.toast("네임스페이스 목록 실패: " + e.message, "err");
      }
    },
    async loadKinds() {
      try {
        this.kinds = await this.apiGet("/kinds");
      } catch (e) {
        this.kinds = Object.keys(SAMPLES).map((plural) => ({
          plural,
          kind: plural,
          description: "",
        }));
        this.toast("kinds API 실패, 로컬 목록 사용", "err");
      }
    },
    selectKind(plural) {
      this.selectedPlural = plural;
      this.loadRows();
    },
    async loadRows() {
      this.loading = true;
      this.listWarning = "";
      try {
        const q = new URLSearchParams({ namespace: this.namespace });
        const data = await this.apiGet(`/resources/${this.selectedPlural}?${q}`);
        this.rows = data.items || [];
        if (data.warning) this.listWarning = data.warning;
        this.log(`list ${this.selectedPlural} @ ${this.namespace}: ${this.rows.length}`);
      } catch (e) {
        this.rows = [];
        this.listWarning = e.message;
        this.toast("목록 조회 실패: " + e.message, "err");
      } finally {
        this.loading = false;
      }
    },
    async refreshAll() {
      await this.refreshHealth();
      await this.loadKinds();
      await this.loadNamespaces();
      await this.loadRows();
    },
    openApplyModal() {
      this.applyNamespace = this.namespace;
      this.applyYamlText = SAMPLES[this.selectedPlural] || "";
      this.modal = "apply";
    },
    closeModal() {
      this.modal = null;
    },
    loadSampleToApply() {
      this.applyYamlText = SAMPLES[this.selectedPlural] || "";
      this.toast("샘플 YAML을 불러왔습니다. metadata.name·namespace를 확인하세요.");
    },
    async submitApply() {
      if (!this.applyYamlText.trim()) {
        this.toast("YAML이 비어 있습니다.", "err");
        return;
      }
      this.applyBusy = true;
      try {
        const res = await fetch(API + "/apply", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            namespace: this.applyNamespace,
            yaml: this.applyYamlText,
          }),
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.error || res.statusText);
        this.toast(`적용 완료 (${data.applied || 0}개 리소스)`);
        this.log(`apply OK: ${data.applied}`);
        this.closeModal();
        await this.loadRows();
      } catch (e) {
        this.toast("적용 실패: " + e.message, "err");
        this.log(`apply FAIL: ${e.message}`);
      } finally {
        this.applyBusy = false;
      }
    },
    async viewYaml(row) {
      this.viewTitle = `${row.name} · ${this.selectedPlural}`;
      this.viewYamlText = "로딩 중…";
      this.modal = "view";
      try {
        const q = new URLSearchParams({ namespace: this.namespace });
        const res = await fetch(`${API}/resources/${this.selectedPlural}/${encodeURIComponent(row.name)}?${q}`);
        this.viewYamlText = await res.text();
        if (!res.ok) throw new Error(this.viewYamlText);
        this.log(`get yaml ${row.name}`);
      } catch (e) {
        this.viewYamlText = "# 오류\n" + e.message;
        this.toast("YAML 조회 실패", "err");
      }
    },
    copyYaml() {
      navigator.clipboard.writeText(this.viewYamlText).then(() => this.toast("YAML 복사됨"));
    },
    copyName(name) {
      navigator.clipboard.writeText(name).then(() => this.toast("이름 복사됨"));
    },
    confirmDelete(row) {
      if (!confirm(`삭제할까요?\n${row.name} (${this.namespace})`)) return;
      this.deleteRow(row);
    },
    async deleteRow(row) {
      try {
        const q = new URLSearchParams({ namespace: this.namespace });
        const res = await fetch(
          `${API}/resources/${this.selectedPlural}/${encodeURIComponent(row.name)}?${q}`,
          { method: "DELETE" }
        );
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.error || res.statusText);
        this.toast("삭제 요청 완료");
        this.log(`delete ${row.name}`);
        await this.loadRows();
      } catch (e) {
        this.toast("삭제 실패: " + e.message, "err");
      }
    },
    async runPing() {
      try {
        let body = {};
        try {
          body = JSON.parse(this.testPingBody || "{}");
        } catch {
          throw new Error("JSON 형식이 올바르지 않습니다");
        }
        const res = await fetch(API + "/test/ping", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        });
        const data = await res.json();
        this.testPingResult = JSON.stringify(data, null, 2);
        this.log("POST /test/ping");
      } catch (e) {
        this.testPingResult = e.message;
        this.toast(e.message, "err");
      }
    },
    async runHealthJson() {
      try {
        const h = await this.apiGet("/health");
        this.healthJson = JSON.stringify(h, null, 2);
        this.log("health json dump");
      } catch (e) {
        this.healthJson = e.message;
      }
    },
  },
}).mount("#app");
