import React, { FC } from "react";
import { Badge, Tag, Button, Col, Divider, Popconfirm, Row, Space, Tooltip, Progress, Breadcrumb } from "antd";

import { HDFSClusterMetric, SparkClusterMetric } from "../../services/Workspace";
import { StatusBadge, ProcessesStatus } from "../StatusTag/StatusTag";
import { DownOutlined, LoadingOutlined, UpOutlined } from "@ant-design/icons";

import { IconSandbox } from "../Icons/PlatformIcons";

import { MdMemory, MdDelete, MdPowerSettingsNew, MdLoop, MdViewCarousel, MdPlayCircleOutline } from "react-icons/md";
import { ClusterStatus } from "../../services/Workspace";
import { bytesToSize, getLocalStorage, setLocalStorage } from "../../services/Utils";
import { DownloadStatus } from "../../pages/workspace/Clusters/ClusterDashboard";
import { Link } from "react-router-dom";
import { UserContext } from "../../store/User";

const BtnClusterStart: FC<{ onClick: () => void }> = ({ onClick }) => (
  <Tooltip title={`Start the cluster`}>
    <Button style={{ border: "none", padding: 0 }} onClick={onClick}>
      <i>
        <MdPlayCircleOutline />
      </i>
    </Button>
  </Tooltip>
);

const BtnClusterReStart: FC<{ onClick: () => void }> = ({ onClick }) => (
  <Tooltip title={`Restart the cluster`}>
    <Button style={{ border: "none", padding: 0 }} onClick={onClick}>
      <i>
        <MdLoop />
      </i>
    </Button>
  </Tooltip>
);

const ServiceTag: React.FC<{ status: ClusterStatus; name: string; port?: number }> = (props) => {
  switch (props.status) {
    case "unhealthy":
    case "inactive":
      return (
        <Tooltip title={props.status}>
          <Tag color='#e4e5f1'>
            <span style={{ color: "#3b4a73" }}>{props.name}</span>
          </Tag>
        </Tooltip>
      );
    case "healthy":
    case "running":
      return (
        <Tooltip title={props.status}>
          <Tag
            className='service-running'
            onClick={(e) => {
              e.preventDefault();
            }}>
            <span style={{ color: "#fff" }}>{props.name}</span>
          </Tag>
        </Tooltip>
      );
    case "bootstrapping":
    case "downloading":
    case "stopping":
    case "starting":
      return (
        <Tooltip title={props.status}>
          <Tag color='#546382'>
            <span>{props.name}</span>
          </Tag>
        </Tooltip>
      );

    default:
      return (
        <Tooltip title={props.status}>
          <span className='status-tag status-tag-gray'>{props.name}</span>
        </Tooltip>
      );
  }
};

const BtnClusterShutdown: FC<{ handleShutdown: () => void }> = ({ handleShutdown }) => (
  <Tooltip title={`Stop the cluster`}>
    <Button style={{ border: "none", padding: 0 }} onClick={(e) => handleShutdown()}>
      <i>
        <MdPowerSettingsNew />
      </i>
    </Button>
  </Tooltip>
);
const ClusterHeader: FC<{
  metric: SparkClusterMetric | HDFSClusterMetric;
  clusterId: number;
  serviceName: string;
  handleClusterDel: (id: number) => void;
  handleClusterStart: (id: number) => void;
  handleClusterStop: (id: number) => void;
  downloadStatus: DownloadStatus;
}> = ({ metric, clusterId, handleClusterDel, serviceName, downloadStatus, handleClusterStart, handleClusterStop }) => {
  const context = React.useContext(UserContext);

  const [isExpand, setExpand] = React.useState<boolean>(getLocalStorage("isHeaderExpand") ?? true);
  const [sandboxCluster, setSandboxCluster] = React.useState<{
    processing: boolean;
    downloadProgress: number;
    showDeleteConfirm: boolean;
    clusterState?: {
      appsCompleted: number;
      appsActive: number;
    };
  }>({
    processing: false,
    showDeleteConfirm: false,
    downloadProgress: 0,
  });

  const onHeaderExpand = () => {
    setExpand(!isExpand);
    setLocalStorage("isHeaderExpand", !isExpand);
  };

  return (
    <div className='card-shadow-light cluster-header-wrapper' style={{ marginBottom: 20 }}>
      <Row
        gutter={[0, 0]}
        className={`header-row ${isExpand ? "header-row-expand" : "header-row-shrink"}`}
        align='middle'
        justify='space-between'>
        {!isExpand ? (
          <Col span={20}>
            <Breadcrumb separator='>'>
              <Breadcrumb.Item>
                <Link
                  style={{ color: "#5f72f2" }}
                  to={`/${context.currentUser.profile?.orgSlugId}/workspace/${context.currentUser.profile?.workspaceId}/clusters`}>
                  Clusters
                </Link>
              </Breadcrumb.Item>
              <Breadcrumb.Item>
                {metric.name} <StatusBadge status={metric.status} />
              </Breadcrumb.Item>
              <Breadcrumb.Item></Breadcrumb.Item>
            </Breadcrumb>
          </Col>
        ) : (
          <>
            <Col span={8} className='header-col'>
              <div className='header-icon'>
                <IconSandbox selected={false} />
              </div>
              <div>
                <div style={{ display: "flex", marginBottom: 10 }}>
                  <div className='header-title' style={{ marginRight: 10 }}>
                    {metric.name} <StatusBadge status={metric.status} />
                  </div>
                </div>
                <div>
                  <div>
                    <span className='config-key'>{serviceName}</span>
                    <span className='config-val'>{metric.version}</span>
                  </div>
                </div>
              </div>
            </Col>
            <Col span={6}>
              <div className='meta-container'>
                <i>
                  <MdMemory />
                </i>
                <div className='title'>Resources</div>
                <br />
              </div>
              <div className='meta-info'>
                <Space>
                  <div>{bytesToSize(metric.metrics.totalMem)} RAM</div>
                  <Badge color='#a5aec0' text={`${metric.metrics.cpuCores} cores`} style={{ color: "#a5aec0" }} />
                </Space>
              </div>
            </Col>
            <Col span={6}>
              <div className='meta-container'>
                <i>
                  <MdViewCarousel />
                </i>
                <div className='title'>Cluster </div>
              </div>

              <div className='meta-info'>
                {metric.metrics ? (
                  metric.processes
                    .sort((a, b) => a.port - b.port)
                    .map((p, i) => {
                      return <ProcessesStatus key={i.toString()} status={p.status} processName={p.name.replace("_", " ")} />;
                    })
                ) : (
                  <div>Not applicable</div>
                )}
              </div>
            </Col>
          </>
        )}

        <Col span={4} className='btn-container'>
          <Space size='large'>
            {(metric.status === "inactive" ||
              metric.status === "new" ||
              metric.status === "terminated" ||
              metric.status === "terminated_with_errors") && <BtnClusterStart onClick={() => handleClusterStart(clusterId)} />}

            {(metric.status === "starting" ||
              metric.status === "bootstrapping" ||
              metric.status === "stopping" ||
              metric.status === "terminating") && (
              <Tooltip title={`${metric.status}`}>
                <Button className='icon-btn-disabled' style={{ border: "none", padding: 0 }} disabled>
                  <i style={{ fontSize: 26, position: "relative", top: -3 }}>
                    <LoadingOutlined />
                  </i>
                </Button>
              </Tooltip>
            )}

            {metric.status === "downloading" && (
              <Tooltip
                title={
                  <div>
                    {metric.status} : {downloadStatus.downValue}/{downloadStatus.totalValue}
                  </div>
                }>
                <Progress type='circle' width={40} percent={downloadStatus.downPer} />
              </Tooltip>
            )}

            {(metric.status === "healthy" || metric.status === "running") && (
              <BtnClusterShutdown handleShutdown={() => handleClusterStop(clusterId)} />
            )}

            <Divider type='vertical' style={{ borderLeft: "2px solid #a5aec0", height: 24 }} />
            <Popconfirm
              title='Are you sure to delete this cluster?'
              okText='Yes'
              cancelText='No'
              visible={sandboxCluster.showDeleteConfirm}
              onConfirm={(e) => {
                handleClusterDel(clusterId);
              }}
              onCancel={(e) => {
                setSandboxCluster({ ...sandboxCluster, showDeleteConfirm: false });
              }}>
              <Tooltip title='Delete cluster'>
                <Button
                  style={{ border: "none", padding: 0 }}
                  className='icon-btn-disabled'
                  onClick={() => {
                    setSandboxCluster({ ...sandboxCluster, showDeleteConfirm: true });
                  }}
                  disabled={metric.status === "downloading" || metric.status === "running" || metric.status === "starting"}>
                  <i>
                    <MdDelete />
                  </i>
                </Button>
              </Tooltip>
            </Popconfirm>
            <Tooltip title={isExpand ? "hide" : "show more"}>
              <div className='expand-icon'>
                {isExpand ? <UpOutlined onClick={onHeaderExpand} /> : <DownOutlined onClick={onHeaderExpand} />}
              </div>
            </Tooltip>
          </Space>
        </Col>
      </Row>
      {/* <Row className='cluster-footer' align='middle'>
        <Col span={3}>
          <Space>
            <StatusBadge status={metric.status} />
            <Divider type='vertical' style={{ borderLeftColor: "#0000004a", height: "1.4em" }} />
          </Space>
        </Col>
        <Col span={19}>
          <div style={{ display: "flex" }}>
            <span style={{ fontWeight: "bold", marginRight: 20 }}>{metric.status === "downloading" ? "Progress" : "Processes"}</span>
            {metric.status === "downloading" && <Progress percent={downloadStatus.downPer} />}
            <Space size='small'>
              {metric.status !== "downloading" &&
                metric.processes.map((s) => <ServiceTag key={s.name} name={s.name} status={s.status} port={s.port} />)}
            </Space>
          </div>
        </Col>
        {metric.status === "downloading" && (
          <Col span={2}>
            <div style={{ fontSize: ".8rem", marginLeft: 10, marginTop: "5px" }}>
              {downloadStatus.downValue}/{downloadStatus.totalValue}
            </div>
          </Col>
        )}
      </Row> */}
    </div>
  );
};

export default ClusterHeader;

{
  /* {Object.entries(metric.metrics.properties).map(([k, v], i) => {
                      if (i === 0) {
                        return (
                          <div key={i}>
                            {v} {k}
                          </div>
                        );
                      } else {
                        return <Badge key={i} color='#a5aec0' text={`${v} ${k}`} style={{ color: "#a5aec0" }} />;
                      }
                    })} */
}
