declare namespace WorkspaceDashboardScssNamespace {
  export interface IWorkspaceDashboardScss {
    "ant-btn": string;
    "ant-table-tbody": string;
    "ant-tabs-nav": string;
    "ant-tabs-tab": string;
    "ant-tabs-tab-btn": string;
    "app-desc": string;
    "app-icon": string;
    "app-name": string;
    "btn-all": string;
    "btn-container": string;
    colorWaiting: string;
    "dashboard-container": string;
    "header-col": string;
    "header-desc": string;
    "header-icon": string;
    "header-row": string;
    "header-title": string;
    "icon-btn-disabled": string;
    "jobs-tabs": string;
    "meta-container": string;
    "meta-info": string;
    "run-history-col": string;
    "stat-item": string;
    "stat-name": string;
    "stat-value": string;
    "stats-info": string;
    "table-cell-light": string;
    "tbl-applications": string;
    title: string;
    xterm: string;
  }
}

declare const WorkspaceDashboardScssModule: WorkspaceDashboardScssNamespace.IWorkspaceDashboardScss & {
  /** WARNING: Only available when `css-loader` is used without `style-loader` or `mini-css-extract-plugin` */
  locals: WorkspaceDashboardScssNamespace.IWorkspaceDashboardScss;
};

export = WorkspaceDashboardScssModule;
