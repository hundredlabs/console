import React, { FC, useContext, useEffect } from "react";
import { useLocation } from "react-router-dom";
import { Card, Skeleton, Space, Table, Tag } from "antd";
import "../Workspace.scss";
import { history } from "../../../configureStore";
import { UserContext } from "../../../store/User";

import WorkspaceService, { HostList } from "../../../services/Workspace";
import { ClusterStatusTag } from "../../../components/StatusTag/StatusTag";
import moment from "moment";

const { Column } = Table;

const HostsList: FC = () => {
  const context = useContext(UserContext);
  const location = useLocation();
  const handleMenuClick = (menuKey: any) => {
    history.push(`/${context.currentUser.profile?.orgSlugId}/workspace/${context.currentUser.profile?.workspaceId}/new-cluster}`);
  };

  const [hostList, setHostList] = React.useState<{ loading: boolean; hosts: HostList[]; error?: string }>({
    loading: false,
    hosts: [],
  });

  useEffect(() => {
    WorkspaceService.getHostsList((r) => {
      setHostList({ ...hostList, hosts: r, loading: false });
    });
  }, []);

  return (
    <div className='workspace-wrapper clusters-container'>
      <Skeleton loading={hostList.loading} paragraph={{ rows: 4 }} active>
        <Card title='Hosts' className='card-shadow-light' bordered={false}>
          <Table dataSource={hostList.hosts} pagination={false} bordered={false} className='tbl-cluster' rowKey={(record: HostList) => record.id}>
            <Column title='NAME' className='project-name table-cell-light' dataIndex='name' />
            <Column
              title='PROVIDER'
              dataIndex=''
              className='run-history-col table-cell-light'
              render={(v: HostList) => {
                return <Tag color='geekblue'>{v.provider}</Tag>;
              }}
            />
            <Column
              title='COMPONENTS'
              dataIndex=''
              className='run-history-col table-cell-light'
              render={(h: HostList) => (
                <Space size='small'>
                  {h.components.map((c) => (
                    <div>
                      <span className='config-key'>{c.name}</span>
                      <span className='config-val'>{c.version}</span>
                    </div>
                  ))}
                </Space>
              )}
            />
            <Column title='STATUS' dataIndex='' className='run-history-col table-cell-light' render={(h: HostList) => <ClusterStatusTag status={h.status} />} />
            <Column title='ADDED' dataIndex='' className='run-history-col table-cell-light' render={(h: HostList) => moment(h.dtAddedEpoch * 1000).fromNow()} />{" "}
          </Table>
        </Card>
      </Skeleton>
    </div>
  );
};

export default HostsList;
