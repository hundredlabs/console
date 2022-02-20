import { Card, Tag } from "antd";
import React, { FC, useEffect, useState } from "react";
import { CloudCluster, ClusterProvider, WorkspaceApiKey } from "../../../services/Workspace";

import { MdCheckCircle } from "react-icons/md";
import WorkspaceService from "../../../services/Workspace";
import { FormInstance } from "antd/lib/form";
import { IconMultinode, IconSandbox } from "../../../components/Icons/PlatformIcons";
import SparkLocalCluster from "../../../components/clusters/spark/SparkLocalCluster";
import KafkaLocalCluster from "../../../components/clusters/kafka/KafkaLocalCluster";
import HadoopLocalCluster from "../../../components/clusters/hadoop/HadoopLocalCluster";

export type addonName = "Postgres" | "MySQL" | "Kafka" | "ElasticSearch" | "none" | "Spark Standalone" | "Spark on YARN";

export interface ComposeConfig {
  image: string;
  [key: string]: any;
}
export interface AddOnServiceConfig {
  reverseDNS: string;
  name: addonName;
  versions: string[];
  selectedVersion?: string;
  resetConfig: () => void;
  getComposeConfig: (clusterId: number, ipAddr: string, network: string, extraHosts: string[]) => ComposeConfig;
}

const SandboxLocalClsFrom: FC<{ clusterForm: FormInstance; orgSlugId: string; workspaceId: number; selectedService: string }> = ({
  clusterForm,
  orgSlugId,
  workspaceId,
  selectedService,
}) => {
  let clsForm = <div></div>;
  switch (selectedService) {
    case "spark":
      clsForm = <SparkLocalCluster clusterForm={clusterForm} orgSlugId={orgSlugId} workspaceId={workspaceId} />;
      break;
    case "kafka":
      clsForm = <KafkaLocalCluster clusterForm={clusterForm} orgSlugId={orgSlugId} workspaceId={workspaceId} />;
      break;
    case "hadoop":
      clsForm = <HadoopLocalCluster clusterForm={clusterForm} orgSlugId={orgSlugId} workspaceId={workspaceId} />;
      break;
    default:
      break;
  }

  return clsForm;
};

const AddKafkaCloudCluster: FC = () => {
  return (
    <>
      <Tag className='tag' color='#f0f4ff'>
        COMING SOON - Q2 '22
      </Tag>

      <div className='feature'>
        <i>
          <MdCheckCircle />
        </i>
        <span>Launch and manage Kafka clusters in your cloud accounts - AWS, GCP, Azure, Digital Ocean and Linode</span>
      </div>
      <div className='feature'>
        <i>
          <MdCheckCircle />
        </i>
        <span>Monitor health of brokers, topics and partitions of Kafka Cluster.</span>
      </div>

      <div className='feature'>
        <i>
          <MdCheckCircle />
        </i>
        <span>Browse and explore messages stored in topics</span>
      </div>
    </>
  );
};

const AddHDFSCloudCluster: FC = () => {
  return (
    <>
      <Tag className='tag' color='#f0f4ff'>
        COMING SOON Q2 '22
      </Tag>

      <div className='feature'>
        <i>
          <MdCheckCircle />
        </i>
        <span>Connect to managed Hadoop services in your cloud accounts</span>
      </div>
      <div className='feature'>
        <i>
          <MdCheckCircle />
        </i>
        <span>Monitor the health of NameNode and Data Node in the cluster</span>
      </div>
      <div className='feature'>
        <i>
          <MdCheckCircle />
        </i>
        <span>Browse and explore Hadoop file system with easy to use interface</span>
      </div>
    </>
  );
};

const AddSparkCloudCluster: FC = () => {
  return (
    <>
      <Tag className='tag' color='#f0f4ff'>
        COMING SOON Q2 '22
      </Tag>

      <div className='feature'>
        <i>
          <MdCheckCircle />
        </i>
        <span>Connect to managed Spark clusters in your cloud accounts </span>
      </div>

      <div className='feature'>
        <i>
          <MdCheckCircle />
        </i>
        <span>Deploy and monitor Spark applications running on remote clusters.</span>
      </div>
      <div className='feature'>
        <i>
          <MdCheckCircle />
        </i>
        <span>Works with AWS EMR, Databricks and Dataproc</span>
      </div>
    </>
  );
};

const ClusterProviderTitle: FC<{ provider: ClusterProvider }> = ({ provider }) => {
  let name = "Local Sandbox";
  let text = "";
  let icon: React.ReactNode = <IconSandbox className='large-option-icon' selected={true} />;
  switch (provider) {
    case "sandbox":
      text = "Run single node cluster locally";
      break;
    case "production":
      name = "Cloud cluster";
      icon = <IconMultinode className='large-option-icon' selected={true} />;
      text = "Run distributed cluster on private or public cloud";
      break;

    default:
      break;
  }

  return (
    <div className='provider-title'>
      <div>{icon}</div>
      <div>
        <div className='name'>{name}</div>
        <div className='info'>{text}</div>
      </div>
    </div>
  );
};
export const AddClusterProvider: FC<{
  provider: ClusterProvider;
  clusterForm: FormInstance;
  onClusterSelect: (id: number, name: string, provider: ClusterProvider) => void;
  orgSlugId: string;
  selectedService?: string;
  workspaceId: number;
  onClusterCreation?: (id: number) => void;
}> = ({ provider, clusterForm, onClusterSelect, selectedService, orgSlugId, workspaceId, onClusterCreation }) => {
  const [providerForm, setProvider] = useState(false);
  const [processing, setProcessing] = useState<{
    isProcessing: boolean;
    isSaving: boolean;
    notifyBtnLoading: boolean;
    keys: WorkspaceApiKey[];
    clusters: CloudCluster[];
    errorMessage?: string;
  }>({
    isProcessing: false,
    isSaving: false,
    notifyBtnLoading: false,
    clusters: [],
    keys: [],
  });

  useEffect(() => {
    WorkspaceService.getWorkspaceKeys((keys) => {
      setProcessing({ ...processing, keys: keys });
    });
  }, []);

  const onClose = () => {
    setProvider(false);
  };

  return (
    <>
      <Card title={<ClusterProviderTitle provider={provider} />} bordered={false} className='cluster-provider-card btn-action-light'>
        <div style={{ padding: "10px 20px" }}>
          {provider === "production" && selectedService === "spark" && <AddSparkCloudCluster />}
          {provider === "production" && selectedService === "kafka" && <AddKafkaCloudCluster />}
          {provider === "production" && selectedService === "hadoop" && <AddHDFSCloudCluster />}
          {provider === "sandbox" && (
            <SandboxLocalClsFrom
              orgSlugId={orgSlugId}
              selectedService={selectedService}
              workspaceId={workspaceId}
              clusterForm={clusterForm}
            />
          )}
        </div>
      </Card>
    </>
  );
};
