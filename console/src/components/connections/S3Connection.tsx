import { Button, Card, Form, Input } from "antd";
import { FormInstance } from "antd/lib/form";
import { FC, useState } from "react";
import { AWSS3ConnectionConfig } from "./ConnectionConfig";

export const S3Connection: FC = () => {
  return (
    <>
      <Form.Item name='name' label='Give the connection a name' rules={[{ required: true, message: "Please enter the connection name" }]}>
        <Input placeholder='Cluster Name' />
      </Form.Item>
      <Form.Item name='awsKey' label='Provide AWS Access Key' rules={[{ required: true, message: "Please enter the connection name" }]}>
        <Input placeholder='AWS Access Key' />
      </Form.Item>
      <Form.Item
        name='awsSecretKey'
        label='Provide AWS Access Secret Key'
        rules={[{ required: true, message: "Please enter the connection name" }]}>
        <Input.Password placeholder='AWS Access Secret Key' />
      </Form.Item>
      <Form.Item name='bucket' label='Provide bucket name' rules={[{ required: true, message: "Please enter the connection name" }]}>
        <Input placeholder='bucket-s3' />
      </Form.Item>
      <Form.Item name='region' label='Provide S3 Region' rules={[{ required: true, message: "Please enter the connection name" }]}>
        <Input placeholder='us-west-2' />
      </Form.Item>
    </>
  );
};
