import { Button, Form, Select } from "antd";
import React, { FC, useEffect, useState } from "react";
import Workspace, { ServiceOption } from "../../../services/Workspace";
import { history } from "../../../configureStore";
import { FormInstance } from "antd/lib/form";
const { Option } = Select;

const HadoopLocalCluster: FC<{ clusterForm: FormInstance; orgSlugId: string; workspaceId: number }> = ({
  clusterForm,
  orgSlugId,
  workspaceId,
}) => {
  const [builder, setBuilder] = useState<{
    versions: string[];
    clusterManagers: string[];
    loading: boolean;
    platform: "sandbox-kubernetes" | "sandbox-docker";
    selectedSandboxId?: number;
    selectedPackageVersion?: string;
    selectedHdfsVersion?: string;
    serviceOptionId?: string;
    serviceOptions: ServiceOption[];
  }>({
    versions: [],
    clusterManagers: ["Standalone", "Kubernetes"],
    loading: true,
    platform: "sandbox-docker",
    serviceOptions: [],
  });

  const [coreStie, setCoreSite] = React.useState<string>("");
  const [hdfsSite, setHdfsSite] = React.useState<string>("");

  const isValidConf = (lineCmd: string) => {
    return (
      lineCmd
        .trim()
        .split("=")
        .filter((x) => x.length > 0).length === 2
    );
  };

  const saveSandboxCluster = () => {
    let coreSitePrams: Array<string> = [];
    let hdfsSitePrams: Array<string> = [];

    clusterForm.validateFields().then((r) => {
      if (coreStie !== "") {
        let coreSiteCmdObj: Array<string> = coreStie.split("\n");
        let filteredLines = coreSiteCmdObj.map((line) => line.trim()).filter((v) => v !== "");
        let finalArray = filteredLines.filter((line) => isValidConf(line));
        if (filteredLines.length != finalArray.length) {
          clusterForm.setFields([
            {
              name: "coreSite",
              errors: ["Invalid core site. Eg. key = value"],
            },
          ]);
          return false;
        }
        coreSitePrams = finalArray;
      }
      if (hdfsSite !== "") {
        let hdfSiteCmdObj: Array<string> = hdfsSite.split("\n");
        let filteredLines = hdfSiteCmdObj.map((line) => line.trim()).filter((v) => v !== "");
        let finalArray = filteredLines.filter((line) => isValidConf(line));
        if (filteredLines.length != finalArray.length) {
          clusterForm.setFields([
            {
              name: "hdfsSite",
              errors: ["Invalid HDFS site. Eg. key = value"],
            },
          ]);
          return false;
        }
        hdfsSitePrams = finalArray;
      }

      setBuilder({ ...builder, loading: true });
      Workspace.saveLocalHadoopSandbox(
        clusterForm.getFieldsValue()["name"],
        builder.selectedHdfsVersion,
        coreSitePrams,
        hdfsSitePrams,
        (r) => {
          setBuilder({ ...builder, loading: false });
          history.push(`/${orgSlugId}/workspace/${workspaceId}/clusters/${r.clusterId}/hadoop`);
        },
        () => {
          setBuilder({ ...builder, loading: false });
        }
      );
    });
  };

  useEffect(() => {
    if (builder.platform === "sandbox-docker") {
      Workspace.listPackageVersions("hadoop", (list) => setBuilder({ ...builder, loading: false, versions: list }));
    }
  }, [builder.platform]);

  return (
    <>
      <Form.Item label='Choose HDFS version' name='hdfsVersion' rules={[{ required: true, message: "You must choose a version" }]}>
        <Select
          style={{ width: "100%" }}
          placeholder='Choose a version'
          className='ant-select-selector-light'
          onSelect={(v: any) => {
            clusterForm.setFieldsValue({ selectedHdfsVersion: v.toString() });
            setBuilder({ ...builder, selectedHdfsVersion: v.toString() });
          }}
          optionLabelProp='label'>
          {builder.versions.map((s, i) => (
            <Option key={i} value={s} label={s}>
              <div className='existing-cls-item'>{s}</div>
            </Option>
          ))}
        </Select>
      </Form.Item>

      {/* <Form.Item label='Core site' name='coreSite'>
        <Input.TextArea rows={4} value={coreStie} className='ant-select-selector-light' placeholder={`key1 = value1 \nkey2 = value2`} onChange={(e) => setCoreSite(e.target.value)} />
      </Form.Item>
      <Form.Item label='HDFS site' name='hdfsSite'>
        <Input.TextArea rows={4} value={hdfsSite} className='ant-select-selector-light' placeholder={`key3 = value3  \nkey4 = value 4`} onChange={(e) => setHdfsSite(e.target.value)} />
      </Form.Item> */}

      <Button
        type='primary'
        loading={builder.loading}
        onClick={(e) => saveSandboxCluster()}
        className='btn-action btn-next btn-action-light'>
        Save
      </Button>
    </>
  );
};

export default HadoopLocalCluster;
