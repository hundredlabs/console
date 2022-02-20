declare namespace CreateOrgWorkspaceScssNamespace {
  export interface ICreateOrgWorkspaceScss {
    "btn-all": string;
    colorWaiting: string;
    "create-org-workspace": string;
    "create-wrapper": string;
    desc: string;
    "form-container": string;
    header: string;
    title: string;
    "user-name": string;
  }
}

declare const CreateOrgWorkspaceScssModule: CreateOrgWorkspaceScssNamespace.ICreateOrgWorkspaceScss & {
  /** WARNING: Only available when `css-loader` is used without `style-loader` or `mini-css-extract-plugin` */
  locals: CreateOrgWorkspaceScssNamespace.ICreateOrgWorkspaceScss;
};

export = CreateOrgWorkspaceScssModule;
