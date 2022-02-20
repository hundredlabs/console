import React, { useState } from "react";
import { Skeleton, Input, Form, Radio, message, Select } from "antd";
import { ClusterOption, ClusterTypes, ServiceTypes } from "../../pages/workspace/Jobs/JobCreationWizard";
import K8sImg from "../../static/img/k8s.png";
import DockerImg from "../../static/img/docker.png";
import { RadioChangeEvent } from "antd/lib/radio";
import Workspace, { ClusterProvider } from "../../services/Workspace";
import { useForm } from "antd/lib/form/Form";

import { AddClusterProvider } from "../../pages/workspace/Jobs/ClusterProviders";
const { Option } = Select;
const clusterPlatforms = [
  { label: <ClusterOption logo={DockerImg} text='Docker' />, value: "sandbox-docker" },
  { label: <ClusterOption logo={K8sImg} text='Kubernetes' />, value: "sandbox-kubernetes" },
];

const RemoteClusterBuilder: React.FC<{ orgSlugId: string; workspaceId: number }> = ({ orgSlugId, workspaceId }) => {
  const [builderForm] = useForm();
  const serviceOpt = ["spark", "kafka", "hadoop"];
  const [builder, setBuilder] = useState<{
    loading: boolean;
    provider: ClusterProvider;
    serviceName: string;
    isClusterVerified: boolean;
    verifiedClusterId?: number;
  }>({
    loading: false,
    serviceName: "spark",
    isClusterVerified: false,
    provider: "sandbox",
  });

  const onFinish = (values: any) => {};

  const onClusterProviderChange = (e: RadioChangeEvent) => {
    setBuilder({ ...builder, provider: e.target.value, loading: false, isClusterVerified: false });
  };

  return (
    <div className='form-section clusters-container'>
      <Skeleton loading={builder.loading} paragraph={{ rows: 5 }}>
        <Form
          layout={"vertical"}
          name='basic'
          requiredMark={false}
          initialValues={{ clusterProvider: builder.provider, serviceName: builder.serviceName }}
          form={builderForm}
          onFinish={onFinish}
          className='config-deploy-form wizard-form'>
          <div className='header'>Create cluster</div>
          <p className='sub-header'>Follow the below steps to create a local sandbox cluster for development and testing.</p>
          <Form.Item name='name' label='Give the cluster a name' rules={[{ required: true, message: "Please enter the cluster name" }]}>
            <Input placeholder='Cluster Name' />
          </Form.Item>
          <Form.Item label='Choose a service' name='serviceName' rules={[{ required: true, message: "You must choose a service" }]}>
            <Radio.Group
              name='clusterProvider'
              className='pills-radio-group'
              value={builder.serviceName}
              options={ServiceTypes}
              onChange={(e: RadioChangeEvent) => {
                setBuilder({ ...builder, serviceName: e.target.value });
              }}
              optionType='button'
              buttonStyle='solid'
            />
          </Form.Item>

          {builder.serviceName && (
            <>
              <AddClusterProvider
                provider={builder.provider}
                selectedService={builder.serviceName}
                clusterForm={builderForm}
                onClusterSelect={(id, name, provder) => {}}
                orgSlugId={orgSlugId}
                workspaceId={workspaceId}
              />
            </>
          )}
        </Form>
      </Skeleton>
    </div>
  );
};

export default RemoteClusterBuilder;
