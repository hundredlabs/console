import React, { useEffect, useState } from "react";
import { Skeleton, Input, Form, Radio, Drawer, Button, Space, message } from "antd";
import Workspace from "../../../services/Workspace";

import { useForm } from "antd/lib/form/Form";
import { AddConnectionProvider } from "./ConnectionProvider";
import { ConnectionConfig } from "../../../components/connections/ConnectionConfig";
const _sodium = require("libsodium-wrappers");

const ServiceConnectionBuilder: React.FC<{
  service: string;
  orgSlugId: string;
  workspaceId: number;
  isOpen: boolean;
  onClose: () => void;
}> = ({ service, orgSlugId, workspaceId, isOpen, onClose }) => {
  const [builderForm] = useForm();
  const serviceOpt = ["spark", "kafka", "hadoop"];
  const [builder, setBuilder] = useState<{
    loading: boolean;
  }>({
    loading: false,
  });

  const onFinish = (values: any) => {
    values["_type"] = "com.gigahex.services.AWSS3Connection";

    Workspace.saveConnection(values["name"], "S3", JSON.stringify(values), 1, (r) => {
      console.log(`connection id - ${r}`);
      message.success(`Connection with name ${values["name"]} has been saved`);
      onClose();
    });

    console.log(values);
  };

  useEffect(() => {
    builderForm.resetFields();
  }, [isOpen]);

  return (
    <Drawer
      title={`Connect ${service}`}
      width={500}
      visible={isOpen}
      bodyStyle={{ paddingBottom: 80 }}
      onClose={onClose}
      closable={true}
      footer={
        <Space>
          <Button type='primary' onClick={(e) => builderForm.submit()}>
            Save Connection
          </Button>
        </Space>
      }>
      <Form
        layout={"vertical"}
        name='basic'
        requiredMark={false}
        form={builderForm}
        initialValues={{ _type: "com.gigahex.services.AWSS3Connection", schemaVersion: 1 }}
        onFinish={onFinish}
        className='config-deploy-form wizard-form'>
        <AddConnectionProvider
          service={service.toLowerCase()}
          connectionForm={builderForm}
          orgSlugId={orgSlugId}
          workspaceId={workspaceId}
        />
      </Form>
    </Drawer>
  );
};

export default ServiceConnectionBuilder;
