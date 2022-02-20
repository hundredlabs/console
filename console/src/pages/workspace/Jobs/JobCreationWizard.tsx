import React, { FC } from "react";

import { IconHadoop, IconKafka, IconMultinode, IconSandbox, SparkMiniIcon } from "../../../components/Icons/PlatformIcons";

import { ClusterProvider, ClusterMiniView } from "../../../services/Workspace";

type ClusterType = "new" | "existing";
export interface SparkJobConfig {
  jobName: string;
  className: string;
  artifactPath: string;
  applicationParams: string;
  driverMemory: number;
  executorMemory: number;
  executorCores: number;
  numExecutors: number;
  extraConf: string[];
  _type: string;
}
export interface DeploymentConfig {
  jobType?: string;
  depId?: string;
  clusterId?: number;
  clusterName?: string;
  region?: string;
  isExisting?: boolean;
  jobConfig?: SparkJobConfig;
  clusterProvider?: ClusterProvider;
}

interface IConfigInfo {
  clusterType: ClusterType;
  provider?: ClusterProvider;
  nextDisabled: boolean;
  validataAwsAcc: boolean;
  clusters: ClusterMiniView[];
  selectedCluster?: ClusterMiniView;
  loading: boolean;
}
export const ClusterOption: FC<{ logo: React.ReactNode; text: string }> = ({ logo, text }) => (
  <div>
    {logo}
    <span>{text}</span>
  </div>
);

export const ClusterTypes = [
  {
    label: <ClusterOption logo={<IconSandbox className='option-icon' selected={false} />} text='Local Sandbox' />,
    value: "sandbox",
  },
  {
    label: <ClusterOption logo={<IconMultinode className='option-icon' selected={false} />} text='Cloud Cluster' />,
    value: "production",
  },
];

export const ServiceTypes = [
  {
    label: <ClusterOption logo={<SparkMiniIcon className='option-icon' />} text='Apache Spark™' />,
    value: "spark",
  },
  {
    label: <ClusterOption logo={<IconHadoop className='option-icon' />} text='Hadoop' />,
    value: "hadoop",
  },
  {
    label: <ClusterOption logo={<IconKafka className='option-icon' />} text='Kafka' />,
    value: "kafka",
  },
];
