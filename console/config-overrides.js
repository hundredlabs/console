const { override, fixBabelImports, addLessLoader } = require("customize-cra");

module.exports = override(
  fixBabelImports("import", {
    libraryName: "antd",
    libraryDirectory: "es",
    style: true
  }),
  addLessLoader({
    javascriptEnabled: true,
    noIeCompat: true,
    exclude: "./node_modules/",
    localIdentName: "[local]--[hash:base64:5]",
    modifyVars: { "@primary-color": "#5f72f2" }
  })
);
