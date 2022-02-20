declare namespace HdfsStorageScssNamespace {
  export interface IHdfsStorageScss {
    "ant-cascader-input": string;
    "ant-cascader-menu": string;
    "ant-cascader-menu-item": string;
    "ant-cascader-menus": string;
    "ant-cascader-picker-arrow": string;
    "ant-cascader-picker-label": string;
    "ant-dropdown-trigger": string;
    "ant-menu": string;
    "btn-all": string;
    "cascader-area": string;
    colorWaiting: string;
    "empty-container": string;
    "hdfs-storage-location": string;
    "left-menu-opts": string;
    "location-container": string;
    "menu-header": string;
    "menu-items": string;
    "right-menu-items": string;
  }
}

declare const HdfsStorageScssModule: HdfsStorageScssNamespace.IHdfsStorageScss & {
  /** WARNING: Only available when `css-loader` is used without `style-loader` or `mini-css-extract-plugin` */
  locals: HdfsStorageScssNamespace.IHdfsStorageScss;
};

export = HdfsStorageScssModule;
