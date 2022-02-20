import { Button, Form, Input, Select, Radio } from "antd";
import React, { FC, useEffect, useState } from "react";
import Workspace, { ServiceOption } from "../../../services/Workspace";
import { history } from "../../../configureStore";
import { FormInstance } from "antd/lib/form";
const { Option } = Select;

const SparkLocalCluster: FC<{ clusterForm: FormInstance; orgSlugId: string; workspaceId: number }> = ({ clusterForm, orgSlugId, workspaceId }) => {
  const [builder, setBuilder] = useState<{
    versions: string[];
    clusterManagers: string[];
    loading: boolean;
    platform: "sandbox-kubernetes" | "sandbox-docker";
    selectedSandboxId?: number;
    selectedPackageVersion?: string;
    selectedClusterManager?: string;
    serviceOptionId?: string;
    serviceOptions: ServiceOption[];
  }>({
    versions: [],
    clusterManagers: ["Standalone", "Kubernetes"],
    loading: true,
    platform: "sandbox-docker",
    serviceOptions: [],
  });

  const [sparkConf, setSparkConf] = React.useState<string>("");

  const isValidConf = (lineCmd: string) => {
    return (
      lineCmd
        .trim()
        .split(" ")
        .filter((x) => x.length > 0).length === 2
    );
  };

  const saveSandboxCluster = () => {
    let confiParams: Array<string> = [];

    clusterForm.validateFields().then((r) => {
      if (sparkConf !== "") {
        let sparkCmdObj: Array<string> = sparkConf.split("\n");
        let filteredLines = sparkCmdObj.map((line) => line.trim()).filter((v) => v !== "");
        let finalArray = filteredLines.filter((line) => isValidConf(line));
        if (filteredLines.length != finalArray.length) {
          clusterForm.setFields([
            {
              name: "configurations",
              errors: ["Invalid configuration. Eg. spark.executor.memory 2g"],
            },
          ]);
          return false;
        }
        confiParams = finalArray;
      }

      setBuilder({ ...builder, loading: true });
      Workspace.saveLocalSparkSandbox(
        clusterForm.getFieldsValue()["name"],
        builder.selectedPackageVersion,
        builder.selectedClusterManager,
        confiParams,
        (r) => {
          setBuilder({ ...builder, loading: false });
          history.push(`/${orgSlugId}/workspace/${workspaceId}/clusters/${r.clusterId}/spark`);
        },
        () => {
          setBuilder({ ...builder, loading: false });
        }
      );
    });
  };

  useEffect(() => {
    console.log("fetching the packages");
    if (builder.platform === "sandbox-docker") {
      Workspace.listPackageVersions("spark", (list) => setBuilder({ ...builder, loading: false, versions: list }));
    }
  }, [builder.platform]);

  return (
    <>
      <Form.Item label='Choose spark version' name='sandboxId' rules={[{ required: true, message: "You must choose a version" }]}>
        <Select
          style={{ width: "100%" }}
          placeholder='Choose a version'
          className='ant-select-selector-light'
          onSelect={(v) => {
            clusterForm.setFieldsValue({ selectedPackageVersion: v.toString() });
            setBuilder({ ...builder, selectedPackageVersion: v.toString() });
          }}
          optionLabelProp='label'>
          {builder.versions.map((s, i) => (
            <Option key={i} value={s} label={s}>
              <div className='existing-cls-item'>{s}</div>
            </Option>
          ))}
        </Select>
      </Form.Item>

      {builder.selectedPackageVersion && (
        <>
          <Form.Item label='Choose Cluster Manager' name='clusterManager' rules={[{ required: true, message: "Choose atleast one service" }]}>
            <Radio.Group
              value='Spark Standalone'
              className='service-options'
              onChange={(e) => {
                clusterForm.setFieldsValue({ clusterManager: e.target.value });
                setBuilder({ ...builder, selectedClusterManager: e.target.value });
              }}>
              {builder.clusterManagers.map((s) => (
                <Radio value={s} key={s} disabled={s === "Kubernetes"}>
                  <div className='service-opt'>
                    <div>{`${s}${s === "Kubernetes" ? " (Coming Soon)" : ""}`}</div>
                  </div>
                </Radio>
              ))}
            </Radio.Group>
          </Form.Item>
          <Form.Item label='Configurations' name='configurations'>
            <Input.TextArea
              rows={7}
              value={sparkConf}
              className='ant-select-selector-light'
              placeholder={`spark.executor.memory   4g\nspark.eventLog.enabled  true`}
              onChange={(e) => setSparkConf(e.target.value)}
            />
          </Form.Item>
        </>
      )}
      <Button type='primary' loading={builder.loading} onClick={(e) => saveSandboxCluster()} className='btn-action btn-next btn-action-light'>
        Save
      </Button>
    </>
  );
};

export default SparkLocalCluster;
