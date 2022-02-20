declare namespace WorkspaceScssNamespace {
  export interface IWorkspaceScss {
    CodeMirror: string;
    "ant-card-body": string;
    "ant-drawer-body": string;
    "ant-form-item-control-input-content": string;
    "ant-form-item-label": string;
    "ant-page-header": string;
    "ant-radio-button-wrapper": string;
    "ant-radio-button-wrapper-checked": string;
    "ant-radio-button-wrapper-disabled": string;
    "ant-radio-wrapper": string;
    "ant-radio-wrapper-checked": string;
    "ant-select-selector": string;
    "ant-select-selector-light": string;
    "ant-typography": string;
    "aws-config-code": string;
    "brand-feature": string;
    "brand-info": string;
    "brand-logo": string;
    "btn-action": string;
    "btn-action-light": string;
    "btn-all": string;
    "card-shadow-light": string;
    "cm-s-material": string;
    colorWaiting: string;
    "config-key": string;
    "config-val": string;
    "deployment-details": string;
    "desktop-footer": string;
    "details-header": string;
    "docker-icon": string;
    "docker-info-footer": string;
    "docker-stats-info": string;
    "drawer-light": string;
    "empty-msg": string;
    "footer-light": string;
    header: string;
    "hex-sider-light": string;
    key: string;
    "label-light": string;
    "main-app-wrapper": string;
    "pills-radio-group": string;
    "product-name": string;
    "service-opt": string;
    "service-options": string;
    "site-layout-background": string;
    "sub-header": string;
    "system-cores": string;
    "system-icon": string;
    "system-info": string;
    "system-mem": string;
    "system-name": string;
    "tags-kv": string;
    value: string;
    "wizard-form": string;
    "wks-header": string;
    "wks-name": string;
    "wks-name-ctr": string;
    "wks-name-ctr-light": string;
    "wks-name-light": string;
    "workspace-side-nav": string;
    "workspace-wrapper": string;
    "wrk-prop-key": string;
    "wrk-prop-val": string;
    "wrk-props": string;
  }
}

declare const WorkspaceScssModule: WorkspaceScssNamespace.IWorkspaceScss & {
  /** WARNING: Only available when `css-loader` is used without `style-loader` or `mini-css-extract-plugin` */
  locals: WorkspaceScssNamespace.IWorkspaceScss;
};

export = WorkspaceScssModule;
