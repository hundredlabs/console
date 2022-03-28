import React, { useEffect, useState } from "react";
import { Skeleton, Input, Form, Radio, Drawer, Button, Space, message } from "antd";
import Workspace from "../../../services/Workspace";

import { useForm } from "antd/lib/form/Form";
import { AddConnectionProvider } from "./ConnectionProvider";
import { ConnectionConfig } from "../../../components/connections/ConnectionConfig";
import Connections from "../../../services/Connections";

const connectionRegistry = {
  S3: "com.gigahex.services.AWSS3Connection",
  Postgres: "com.gigahex.services.PgConnection",
  MySQL: "com.gigahex.services.MySQLConnection",
  MariaDB: "com.gigahex.services.MariaDBConnection",
};

const ServiceConnectionBuilder: React.FC<{
  service: string;
  isOpen: boolean;
  onClose: () => void;
  connectionId?: number;
  initialValues?: any;
}> = ({ service, isOpen, connectionId, onClose, initialValues }) => {
  const [builderForm] = useForm();
  const serviceOpt = ["spark", "kafka", "hadoop"];
  const [builder, setBuilder] = useState<{
    loading: boolean;
  }>({
    loading: false,
  });

  const onFinish = (values: any) => {
    values["_type"] = connectionRegistry[service];
    if (connectionId) {
      Connections.updateConnection(connectionId, values["name"], service, JSON.stringify(values), 1, (r) => {
        message.success(`Connection has been saved`);
        onClose();
      });
    } else {
      Connections.saveConnection(values["name"], service, JSON.stringify(values), 1, (r) => {
        message.success(`Connection has been saved`);
        onClose();
      });
    }

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
        initialValues={initialValues}
        onFinish={onFinish}
        className='config-deploy-form wizard-form'>
        <AddConnectionProvider service={service.toLowerCase()} connectionForm={builderForm} />
      </Form>
    </Drawer>
  );
};

export default ServiceConnectionBuilder;
