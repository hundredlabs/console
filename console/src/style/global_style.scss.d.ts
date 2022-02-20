declare namespace GlobalStyleScssNamespace {
  export interface IGlobalStyleScss {
    App: string;
    "App-header": string;
    "App-link": string;
    "App-logo": string;
    "btn-all": string;
    colorWaiting: string;
    title: string;
    "title-centerd": string;
  }
}

declare const GlobalStyleScssModule: GlobalStyleScssNamespace.IGlobalStyleScss & {
  /** WARNING: Only available when `css-loader` is used without `style-loader` or `mini-css-extract-plugin` */
  locals: GlobalStyleScssNamespace.IGlobalStyleScss;
};

export = GlobalStyleScssModule;
