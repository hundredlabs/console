import React, { FC, useContext, useEffect } from "react";
import { useLocation } from "react-router-dom";
import { Alert, Button, Card, Dropdown, Menu, message, Skeleton, Space, Table, Tag, Tooltip } from "antd";
import "../Workspace.scss";
import "./Clusters.scss";
import { history } from "../../../configureStore";
import { UserContext } from "../../../store/User";
import { AppstoreOutlined, DeleteOutlined, EllipsisOutlined } from "@ant-design/icons";
import { Link } from "react-router-dom";
import confirm from "antd/lib/modal/confirm";
import WorkspaceService, { ClusterProvider, ClusterView } from "../../../services/Workspace";
import { ClusterStatusTag } from "../../../components/StatusTag/StatusTag";
import { ClusterStatus } from "../../../services/Workspace";

const { Column } = Table;

const deleteCluster = (cId: number, name: string, onClusterDelete: (id: number) => void) => {
  confirm({
    title: `Do you want to delete the cluster - ${name}`,
    content: "Once deleted, you won't be able to deploy jobs on this cluster through Gigahex console.",
    onOk() {
      onClusterDelete(cId);
    },
    onCancel() {},
  });
};
const renderActionBtn = (
  cid: number,
  name: string,
  provider: ClusterProvider,
  status: ClusterStatus,
  currentPath: string,
  onClusterDelete: (id: number) => void
) => {
  const actions = (
    <Menu key={cid.toString()}>
      <Menu.Item key='1' onClick={(e) => history.push(`${currentPath}/${cid}`)} style={{ display: "flex", alignItems: "center" }}>
        <AppstoreOutlined />
        Dashboard
      </Menu.Item>
      <Menu.Item key='2' onClick={(e) => deleteCluster(cid, name, onClusterDelete)} style={{ display: "flex", alignItems: "center" }}>
        <DeleteOutlined />
        Delete
      </Menu.Item>
    </Menu>
  );

  return (
    <Space>
      <Tooltip title='Manage Clusters'>
        <Dropdown overlay={actions} trigger={["click"]}>
          <Button type='default' style={{ fontSize: "18px" }} size='small'>
            <EllipsisOutlined />
          </Button>
        </Dropdown>
      </Tooltip>
    </Space>
  );
};

const EmptyClusterSection: FC<{ slugId: string; workspaceId: number }> = ({ slugId, workspaceId }) => {
  return (
    <div className='clusters-deploy'>
      <h2 className='header-title'>Create your first cluster</h2>
      <p className='header-desc'>Create a cluster locally personal development, testing or learning.</p>
      <Button
        type='primary'
        className='jobs-deploy-btn btn-action-light'
        onClick={(e) => history.push(`/${slugId}/workspace/${workspaceId}/new-cluster`)}>
        Create Cluster
      </Button>
    </div>
  );
};

const Clusters: FC = () => {
  const context = useContext(UserContext);
  const location = useLocation();
  const handleMenuClick = (menuKey: any) => {
    history.push(`/${context.currentUser.profile?.orgSlugId}/workspace/${context.currentUser.profile?.workspaceId}/new-cluster}`);
  };
  const menu = (
    <Menu onClick={handleMenuClick}>
      <Menu.Item key='sandbox'>Local Sandbox</Menu.Item>
      <Menu.Item key='remote'>Remote Cluster</Menu.Item>
    </Menu>
  );
  const [clustersState, setClustersState] = React.useState<{ loading: boolean; clusters: ClusterView[]; error?: string }>({
    loading: true,
    clusters: [],
  });

  useEffect(() => {
    context.currentUser.profile &&
      WorkspaceService.listAllClusters((clusters) => {
        setClustersState({ ...clustersState, clusters: clusters, loading: false });
      });
  }, []);

  const onClusterDelete = (id: number) => {
    setClustersState({ ...clustersState, loading: true });
    WorkspaceService.deleteCluster(id, (result) => {
      if (result.clusterRemoved) {
        message.info("Cluster has been deleted.");
        WorkspaceService.listAllClusters((clusters) => {
          setClustersState({ ...clustersState, clusters: clusters, loading: false });
        });
      } else {
        message.info("Failed to delete the cluster.");
        setClustersState({ ...clustersState, loading: false });
      }
    });
  };
  return (
    <div className='workspace-wrapper clusters-container'>
      <Skeleton loading={clustersState.loading} paragraph={{ rows: 4 }} active>
        {clustersState.clusters.length === 0 && (
          <div>
            {context.currentUser.profile && (
              <EmptyClusterSection slugId={context.currentUser.profile.orgSlugId} workspaceId={context.currentUser.profile.workspaceId} />
            )}
          </div>
        )}
        {clustersState.clusters.length > 0 && (
          <>
            <Card
              title='Clusters'
              className='card-shadow-light'
              bordered={false}
              extra={
                <Button
                  type='primary'
                  onClick={() =>
                    history.push(
                      `/${context.currentUser.profile?.orgSlugId}/workspace/${context.currentUser.profile?.workspaceId}/new-cluster`
                    )
                  }>
                  Add Cluster
                </Button>
              }>
              <Table
                dataSource={clustersState.clusters}
                pagination={false}
                bordered={false}
                className='tbl-cluster'
                rowKey={(record: ClusterView) => record.clusterId}>
                <Column
                  title='NAME'
                  className='project-name table-cell-light'
                  dataIndex=''
                  render={(v: ClusterView) => (
                    <div>
                      {context.currentUser.profile && (
                        <Link
                          to={`/${context.currentUser.profile.orgSlugId}/workspace/${context.currentUser.profile.workspaceId}/clusters/${v.cid}/${v.serviceName}`}>
                          {v.name}
                        </Link>
                      )}
                    </div>
                  )}
                />

                <Column
                  title='PROVIDER'
                  dataIndex=''
                  className='run-history-col table-cell-light'
                  render={(v: ClusterView) => {
                    return <Tag color='geekblue'>{v.provider}</Tag>;
                  }}
                />
                <Column
                  title='SERVICE'
                  dataIndex=''
                  className='run-history-col table-cell-light'
                  render={(c: ClusterView) => (
                    <div>
                      <span className='config-key'>{c.serviceName}</span>
                      <span className='config-val'>{c.serviceVersion}</span>
                    </div>
                  )}
                />
                <Column
                  title='STATUS'
                  dataIndex=''
                  className='run-history-col table-cell-light'
                  render={(v: ClusterView) => {
                    return <ClusterStatusTag status={v.status} />;
                  }}
                />
                <Column title='CREATED' dataIndex='created' className='run-history-col table-cell-light' />
              </Table>
            </Card>
          </>
        )}
        {clustersState.error && <Alert showIcon style={{ marginTop: 20 }} type='warning' message={clustersState.error} />}
      </Skeleton>
    </div>
  );
};

export default Clusters;
