import { Button, Card, Form, Input } from "antd";
import { FC, useState } from "react";

export const PgConnection: FC = () => {
  return (
    <>
      <Form.Item name='name' label='Give the connection a name' rules={[{ required: true, message: "Please enter the connection name" }]}>
        <Input placeholder='Cluster Name' />
      </Form.Item>
      <Form.Item
        name='username'
        label='Provide database username'
        rules={[{ required: true, message: "Please enter the database username" }]}>
        <Input placeholder='postgres' />
      </Form.Item>
      <Form.Item
        name='password'
        label='Provide database password'
        rules={[{ required: true, message: "Please enter the connection name" }]}>
        <Input.Password placeholder='s3cret' />
      </Form.Item>
    </>
  );
};
