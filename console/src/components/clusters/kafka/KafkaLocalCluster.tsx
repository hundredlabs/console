import { Button, Form, Select } from "antd";
import React, { FC, useEffect, useState } from "react";
import Workspace from "../../../services/Workspace";
import { history } from "../../../configureStore";
import { FormInstance } from "antd/lib/form";
const { Option } = Select;

const KafkaLocalCluster: FC<{ clusterForm: FormInstance; orgSlugId: string; workspaceId: number }> = ({ clusterForm, orgSlugId, workspaceId }) => {
  const [builder, setBuilder] = useState<{
    kafkaVerison: string[];
    scalaVerison: string[];
    selectedKafkaVersion: string;
    selectedScalaVersion: string;
    loading: boolean;
    platform: "sandbox-kubernetes" | "sandbox-docker";
  }>({
    kafkaVerison: ["3.2.3", "1.2.3"],
    scalaVerison: ["2.12", "2.13"],
    loading: true,
    platform: "sandbox-docker",
    selectedKafkaVersion: "",
    selectedScalaVersion: "",
  });

  const saveSandboxCluster = () => {
    const clsName = clusterForm.getFieldsValue()["name"];
    clusterForm
      .validateFields()
      .then((r) => {
        if (!clsName) return false;
        setBuilder({ ...builder, loading: true });
        Workspace.saveLocalKafkaSandbox(
          clsName,
          clusterForm.getFieldsValue()["serviceName"],
          builder.selectedKafkaVersion,
          builder.selectedScalaVersion,
          [""],
          (r) => {
            setBuilder({ ...builder, loading: false });
            history.push(`/${orgSlugId}/workspace/${workspaceId}/clusters/${r.clusterId}/kafka`);
          },
          () => {
            setBuilder({ ...builder, loading: false });
          }
        );
      })
      .catch((e) => {});
  };

  useEffect(() => {
    console.log("fetching the versions");
    if (builder.platform === "sandbox-docker") {
      Workspace.listPackageVersions("kafka", (list) => setBuilder({ ...builder, loading: false, kafkaVerison: list }));
    }
  }, [builder.platform]);

  return (
    <>
      <Form.Item label='Choose kafka version' name='kafkaVersion' rules={[{ required: true, message: "You must choose a version" }]}>
        <Select
          style={{ width: "100%" }}
          placeholder='Choose a version'
          className='ant-select-selector-light'
          onSelect={(v) => {
            clusterForm.setFieldsValue({ kafkaVersion: v.toString() });
            setBuilder({ ...builder, selectedKafkaVersion: v.toString() });
          }}
          optionLabelProp='label'>
          {builder.kafkaVerison.map((s, i) => (
            <Option key={i} value={s} label={s}>
              <div className='existing-cls-item'>{s}</div>
            </Option>
          ))}
        </Select>
      </Form.Item>
      <Form.Item label='Choose scala version' name='scalaVersion' rules={[{ required: true, message: "You must choose a version" }]}>
        <Select
          style={{ width: "100%" }}
          placeholder='Choose a version'
          className='ant-select-selector-light'
          onSelect={(v) => {
            clusterForm.setFieldsValue({ scalaVersion: v.toString() });
            setBuilder({ ...builder, selectedScalaVersion: v.toString() });
          }}
          optionLabelProp='label'>
          {builder.scalaVerison.map((s, i) => (
            <Option key={i} value={s} label={s}>
              <div className='existing-cls-item'>{s}</div>
            </Option>
          ))}
        </Select>
      </Form.Item>

      <Button type='primary' loading={builder.loading} onClick={(e) => saveSandboxCluster()} className='btn-action btn-next btn-action-light'>
        Save
      </Button>
    </>
  );
};

export default KafkaLocalCluster;
