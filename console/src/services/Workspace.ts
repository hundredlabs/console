import WebService from "./WebService";
import { IErrorHandler } from "./IErrorHander";
import { BadRequest, InternalServerError } from "./SparkService";
import { HDFSStrogeFiles } from "./LocalHDFSFileService";

export interface IllegalParam {
  path: string;
  memberId: number;
  message: string;
}
export type FailedResponse = {
  message: string;
};
export type UnAuthorized = {
  requestedResource: string;
  message: string;
};

export type Status = "failed" | "running" | "waiting" | "passed" | "completed" | "succeeded" | "exceeded" | "not started" | "skipped" | "starting";

export type ClusterStatus =
  | "new"
  | "unknown"
  | "starting"
  | "bootstrapping"
  | "running"
  | "waiting"
  | "terminating"
  | "terminated"
  | "healthy"
  | "unhealthy"
  | "stopping"
  | "downloading"
  | "inactive"
  | "not started"
  | "stopped"
  | "terminated_with_errors";
export interface clusterImgInfo {
  image: string;
  environment: Array<string>;
  services: Array<{
    name: string;
    internalPort: number;
    externalPort?: number;
  }>;
}
export function getEnvKeys() {
  return {
    apiKey: "GHX_API_KEY_ID",
    secretKeyId: "GHX_API_KEY_SECRET",
  };
}
export interface ServiceOption {
  id: string;
  image: string;
  services: Array<{ name: string; version: string }>;
}
export interface SandboxInfo {
  id: number;
  cluster: {
    version: string;
    serviceOptions: Array<ServiceOption>;
  };
}

export interface WorkspaceCreated {
  workspaceId: number;
  orgSlugId: string;
}
export type ClusterProvider = "sandbox" | "production";
export interface ClusterNode {
  id: string;
  host: string;
  cpuCores: number;
  memory: string;
  port?: number;
}
export interface CloudCluster {
  name: string;
  id: number;
  provider: string;
}

export interface OrgDetail {
  name: string;
  slugId: string;
  thumbnailImg?: string;
}
export interface ClusterDeploymentHistory {
  deploymentName: string;
  jobName: string;
  internalJobRunId?: string;
  jobId: number;
  depId: number;
  deploymentRunId: number;
  triggerMethod: string;
  status: Status;
  started: string;
  runtime: string;
  links: Array<{ name: string; link: string }>;
}

export interface ClusterAttempts {
  startTime: string;
  endTime: string;
  lastUpdated: string;
  duration: number;
  sparkUser: string;
  completed: Boolean;
  appSparkVersion: string;
  startTimeEpoch: number;
  endTimeEpoch: number;
  lastUpdatedEpoch: number;
}
export interface SparkClusterHistory {
  id: string;
  name: string;
  attempts: Array<ClusterAttempts>;
}
export type ServicePort = {
  port: number;
  name: string;
  isWebPort: boolean;
  externalPort?: number;
  hostIp?: string;
};
export interface ClusterMetric {
  info: {
    name: string;
    provider: ClusterProvider;
    region: string;
    providerClusterId: string;
    status: ClusterStatus;
    imageInfo: clusterImgInfo;
  };
  sandboxContainer?: {
    image: string;
    apps: Array<{ name: string; version: string; ports: ServicePort[] }>;
    addOns: string[];
  };
  state?: {
    clusterId: number;
    activeApps: number;
    completedApps: number;
    failedApps: number;
    nodes: ClusterNode[];
  };
  hdfsFiles?: HDFSStrogeFiles;
}

export interface ClusterProcess {
  name: string;
  host: string;
  port: number;
  status: ClusterStatus;
}

export interface SparkClusterMetric {
  name: string;
  clusterManager: string;
  metrics: {
    totalMem: number;
    cpuCores: number;
    properties: { [key: string]: string };
    masterCount: number;
    workerCount: number;
  };
  processes: Array<ClusterProcess>;
  scalaVersion: string;
  status: ClusterStatus;
  version: string;
  statusDetail: string;
}

export interface HDFSClusterMetric {
  id: number;
  name: string;
  metrics: {
    totalMem: number;
    cpuCores: number;
  };
  processes: Array<ClusterProcess>;
  hdfsSite: string[];
  coreSite: string[];
  status: ClusterStatus;
  version: string;
  statusDetail: string;
}

export interface WorkspaceMiniView {
  id: number;
  name: string;
  created: string;
}

export interface ClusterMiniView {
  id: number;
  name: string;
  provider: ClusterProvider;
}

export interface ClusterView {
  cid: number;
  name: string;
  clusterId: string;
  status: ClusterStatus;
  provider: ClusterProvider;
  created: string;
  serviceName: string;
  serviceVersion: string;
}
export interface ClustersUsage {
  sandboxCreated: number;
  clustersConnected: number;
  maxSandboxAllowed: number;
  maxClustersConnections: number;
}

export interface WorkspaceApiKey {
  name: string;
  apiKey: string;
  apiSecretKey: string;
}

export interface SandboxConf {
  name: string;
  version: string;
  scalaVersion: string;
  clusterManager: string;
  isLocal: boolean;
  hosts: Array<string>;
  configParams: Array<string>;
  username: string;
}

export interface HostList {
  id: number;
  name: string;
  provider: string;
  components: Array<{ name: string; version: string }>;
  dtAddedEpoch: number;
  status: ClusterStatus;
}

export interface ClusterActionResponse {
  status: ClusterStatus;
}

export interface KafkaPartitions {
  partitions: number;
  leader: number;
  replicas: [
    {
      broker: number;
      leader: boolean;
      InSyne: boolean;
    }
  ];
}

export interface KafkaClusterTopic {
  id: string;
  name: string;
  partitions: number[];
  replications: number[];
  messages: number;
  size: number;
}
export interface KafkaClusterBrokers {
  id: number;
  host: string;
  port: number;
  rack: string;
}
export interface PartitionDetails {
  id: number;
  startingOffset: number;
  endingOffset: number;
  messages: number;
  replicas: number[];
}
export interface TopicConfigDetail {
  config: string;
  value: string;
  type: string;
  source: string;
}

export interface TopicMessage {
  offset: number;
  partition: number;
  key: string;
  message: string;
  timestamp: number;
}
export interface ConsumerMember {
  assignedMember: string;
  partition: number;
  topicPartitionOffset: number;
  consumedOffset: number;
}
export interface ConsumerGroupInfo {
  id: string;
  coordinator: number;
  lag: number;
  state: string;
  members: ConsumerMember[];
}

export interface CreateTopicResponse {}

type OnboardMemberResponse = WorkspaceCreated | IllegalParam | UnAuthorized;
class WorkspaceService extends IErrorHandler {
  private webAPI: WebService = new WebService();

  listWorkspaces = async (onSuccess: (workspaces: WorkspaceMiniView[]) => void) => {
    try {
      const response = this.webAPI.get<WorkspaceMiniView[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/workspaces`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as WorkspaceMiniView[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Workspace list");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  listPackageVersions = async (name: string, onSuccess: (workspaces: string[]) => void) => {
    try {
      const response = this.webAPI.get<string[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/packages/${name}`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as string[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Package versions");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getHostsList = async (onSuccess: (hosts: HostList[]) => void) => {
    try {
      const response = this.webAPI.get<HostList[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/workspace/hosts`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as HostList[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Host list");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  deleteCluster = async (id: number, onSuccess: (workspaces: { clusterRemoved: boolean }) => void) => {
    try {
      const response = this.webAPI.delete<{ clusterRemoved: boolean } | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/clusters/${id}`, {});

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as { clusterRemoved: boolean };
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Workspace keys");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  notifyWhenAvailable = async (onSuccess: (result: { saved: boolean }) => void, onFailure: () => void) => {
    try {
      const response = this.webAPI.post<{ saved: boolean } | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/notify-feature`, {});

      const r = await response;

      if (r.status === 200 && r.parsedBody) {
        const result = r.parsedBody as { saved: boolean };
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.notifyError(body.message);
        onFailure();
      } else {
        const err = this.getDefaultError("Notify when available");
        this.notifyError(err.message);
        onFailure();
      }
    } catch (e) {
      const err = this.getDefaultError("Notify when available");
      this.notifyError(err.message);
      onFailure();
    }
  };

  saveLocalSparkSandbox = async (
    name: string,
    packageVersion: string,
    clusterManager: string,
    confiParams: Array<string>,
    onSuccess: (workspaces: { clusterId: number }) => void,
    onFailure: () => void
  ) => {
    const scalaVersion = packageVersion.startsWith("3") ? "2.12" : "2.11";
    try {
      const response = this.webAPI.put<{ clusterId: number } | BadRequest | UnAuthorized | InternalServerError>(`/web/v1/spark-cluster`, {
        name: name,
        version: packageVersion,
        clusterManager: clusterManager,
        scalaVersion: scalaVersion,
        isLocal: true,
        hosts: [],
        configParams: confiParams,
        username: "",
      });

      const r = await response;

      if (r.status === 201 && r.parsedBody) {
        const result = r.parsedBody as { clusterId: number };
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as BadRequest;
        this.notifyError(body.error);
        onFailure();
      } else {
        const err = this.getDefaultError("Save Sandbox cluster");
        this.notifyError(err.message);
        onFailure();
      }
    } catch (e: any) {
      const err = this.getDefaultError("Failed Sandbox cluster creation");
      this.notifyError(err.message);
      onFailure();
    }
  };

  saveLocalKafkaSandbox = async (
    name: string,
    serviceName: string,
    kafkaVersion: string,
    scalaVersion: string,
    configParams: string[],
    onSuccess: (workspaces: { clusterId: number }) => void,
    onFailure: () => void
  ) => {
    console.log({
      name: name,
      serviceName: serviceName,
      kafkaVersion: kafkaVersion,
      scalaVersion: scalaVersion,
      isLocal: true,
      hosts: [],
      username: "",
    });
    try {
      const response = this.webAPI.put<{ clusterId: number } | BadRequest | UnAuthorized | InternalServerError>(`/web/v1/kafka-cluster`, {
        name: name,
        version: kafkaVersion,
        scalaVersion: scalaVersion,
        isLocal: true,
        hosts: [],
        username: "",
        configParams: configParams,
      });

      const r = await response;

      if (r.status === 201 && r.parsedBody) {
        const result = r.parsedBody as { clusterId: number };
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as BadRequest;

        this.notifyError(body.error);
        onFailure();
      } else {
        const err = this.getDefaultError("Save Sandbox cluster");
        this.notifyError(err.message);
        onFailure();
      }
    } catch (e) {
      const err = this.getDefaultError("Failed Sandbox cluster creation");
      this.notifyError(err.message);
      onFailure();
    }
  };

  saveLocalHadoopSandbox = async (
    name: string,
    packageVersion: string,
    coreSitePrams: Array<string>,
    hdfsSitePrams: Array<string>,
    onSuccess: (workspaces: { clusterId: number }) => void,
    onFailure: () => void
  ) => {
    try {
      const response = this.webAPI.put<{ clusterId: number } | BadRequest | UnAuthorized | InternalServerError>(`/web/v1/hdfs-cluster`, {
        name: name,
        version: packageVersion,
        coreSite: coreSitePrams,
        hdfsSite: hdfsSitePrams,
        isLocal: true,
        hosts: [],
        username: "",
      });

      const r = await response;

      if (r.status === 201 && r.parsedBody) {
        const result = r.parsedBody as { clusterId: number };
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as BadRequest;
        this.notifyError(body.error);
        onFailure();
      } else {
        const err = this.getDefaultError("Save Sandbox cluster");
        this.notifyError(err.message);
        onFailure();
      }
    } catch (e: any) {
      const err = this.getDefaultError("Failed Sandbox cluster creation");
      this.notifyError(err.message);
      onFailure();
    }
  };

  getOrgDetail = async (onSuccess: (detail: OrgDetail) => void) => {
    try {
      const response = this.webAPI.get<OrgDetail | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/org`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as OrgDetail;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Org");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  updateOrgDetail = async (org: OrgDetail, onSuccess: (detail: OrgDetail) => void) => {
    try {
      const response = this.webAPI.post<OrgDetail | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/org`, org);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as OrgDetail;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Org");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getSparkClustertHistory = async (cId: number, onSuccess: (history: SparkClusterHistory[]) => void) => {
    try {
      const response = this.webAPI.get<SparkClusterHistory[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/spark/${cId}/history/api/v1/applications`);
      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as SparkClusterHistory[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Spark Cluster History");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  startCluster = async (cId: number, service: "spark" | "kafka" | "hadoop", onSuccess: (r: ClusterActionResponse) => void) => {
    try {
      const response = this.webAPI.post<{} | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/clusters/${cId}/services/${service}/start`, {});
      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as { status: ClusterStatus };
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Start the cluster");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  stopCluster = async (cId: number, onSuccess: (r: ClusterActionResponse) => void) => {
    try {
      const response = this.webAPI.post<{} | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/clusters/${cId}/stop`, {});
      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as { status: ClusterStatus };
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Stop the cluster");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getSparkClusterMetric = async (cId: number, onSuccess: (metric: SparkClusterMetric) => void) => {
    try {
      const response = this.webAPI.get<SparkClusterMetric | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/spark/${cId}`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as SparkClusterMetric;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Spark Cluster metric");
        this.showError(err.message);
      }
    } catch (e) {}
  };
  getKafkaClusterMetric = async (cId: number, onSuccess: (metric: SparkClusterMetric) => void) => {
    try {
      const response = this.webAPI.get<SparkClusterMetric | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/kafka/${cId}`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as SparkClusterMetric;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Spark Cluster metric");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getHDFSClusterMetric = async (cId: number, onSuccess: (metric: HDFSClusterMetric) => void) => {
    try {
      const response = this.webAPI.get<HDFSClusterMetric | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/hadoop/${cId}`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as HDFSClusterMetric;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Spark Cluster metric");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getKafkaConsumerGroups = async (cId: number, onSuccess: (topic: ConsumerGroupInfo[]) => void) => {
    try {
      const response = this.webAPI.get<ConsumerGroupInfo[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/kafka/${cId}/consumer-groups`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as ConsumerGroupInfo[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Kafka Cluster ");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getKafkaClusterBrokers = async (cId: number, onSuccess: (topic: KafkaClusterBrokers[]) => void) => {
    try {
      const response = this.webAPI.get<KafkaClusterBrokers[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/kafka/${cId}/nodes`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as KafkaClusterBrokers[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Kafka Cluster ");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getKafkaTopicMessages = async (cId: number, topic: string, maxResults: number, startingFrom: string, onSuccess: (topic: TopicMessage[]) => void) => {
    try {
      const response = this.webAPI.post<TopicMessage[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/kafka/${cId}/topics/${topic}/messages`, {
        maxResults: maxResults,
        startingFrom: startingFrom,
      });

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as TopicMessage[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Kafka Cluster ");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getKafkaTopicPartitions = async (cId: number, topic: string, onSuccess: (topic: PartitionDetails[]) => void) => {
    try {
      const response = this.webAPI.get<PartitionDetails[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/kafka/${cId}/topics/${topic}/partitions`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as PartitionDetails[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Kafka Cluster ");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getKafkaTopicConfigs = async (cId: number, topic: string, onSuccess: (topic: TopicConfigDetail[]) => void) => {
    try {
      const response = this.webAPI.get<TopicConfigDetail[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/kafka/${cId}/topics/${topic}/configs`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as TopicConfigDetail[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Kafka Cluster ");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getKafkaClusterTopic = async (cId: number, onSuccess: (topic: KafkaClusterTopic[]) => void) => {
    try {
      const response = this.webAPI.get<KafkaClusterTopic[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/kafka/${cId}/topics`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as KafkaClusterTopic[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the Kafka Cluster Topic");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  createKafkaTopic = async (cId: number, name: string, partitions: number, replicas: number, onSuccess: (r: CreateTopicResponse) => void) => {
    try {
      const response = this.webAPI.put<{ uuid: string } | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/kafka/${cId}/topics`, {
        name: name,
        partitions: partitions,
        replicationFactor: replicas,
      });
      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as CreateTopicResponse;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Create a");
        this.showError(err.message);
      }
    } catch (e) {
      onSuccess("created");
    }
  };

  getWorkspaceKeys = async (onSuccess: (clusters: WorkspaceApiKey[]) => void) => {
    try {
      const response = this.webAPI.get<WorkspaceApiKey[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/workspaces/keys`);
      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as WorkspaceApiKey[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the clusters list");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  listAllClusters = async (onSuccess: (clusters: ClusterView[]) => void) => {
    try {
      const response = this.webAPI.get<ClusterView[] | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/clusters`);
      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as ClusterView[];
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the clusters list");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  getClustersUsage = async (onSuccess: (clusters: ClustersUsage) => void) => {
    try {
      const response = this.webAPI.get<ClustersUsage | IllegalParam | UnAuthorized | InternalServerError>(`/web/v1/clusters/usage`);

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as ClustersUsage;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        this.showError(body.message);
      } else {
        const err = this.getDefaultError("Fetching the clusters list");
        this.showError(err.message);
      }
    } catch (e) {}
  };

  onboardMember = async (
    name: string,
    isPersonalAccount: boolean,
    orgName: string | undefined,
    orgSlugId: string | undefined,
    onSuccess: (r: WorkspaceCreated) => void,
    onFailure: (message: string) => void
  ) => {
    try {
      const response = this.webAPI.post<OnboardMemberResponse>(`/web/v1/onboard`, {
        name: name,
        isPersonalAccount: isPersonalAccount,
        orgName: orgName,
        orgSlugId: orgSlugId,
      });

      const r = await response;
      if (r.parsedBody) {
        const result = r.parsedBody as WorkspaceCreated;
        onSuccess(result);
      } else if (r.status === 400 && r.parsedBody) {
        const body = r.parsedBody as IllegalParam;
        onFailure(body.message);
      } else {
        const err = this.getDefaultError("Fetch provisioning the organisation");
        this.showError(err.message);
      }
    } catch (e) {}
  };
}

export default new WorkspaceService();
