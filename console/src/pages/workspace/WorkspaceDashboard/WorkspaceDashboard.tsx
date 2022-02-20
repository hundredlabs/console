import { Col, Row, Table, Tabs } from "antd";
import Column from "antd/lib/table/Column";
import React, { FC } from "react";
import { Hexagon } from "../../../components/Icons/NavIcons";
import "./WorkspaceDashboard.scss";
import "../Workspace.scss";
import { ErrorBoundary, useErrorHandler } from "react-error-boundary";
import { StatusTag, WorkspaceStatusTag } from "../../../components/StatusTag/StatusTag";
import SparkService from "../../../services/SparkService";
import ErrorFallback from "../../../commons/ErrorFallBack";

const { TabPane } = Tabs;

export interface IAppCluster {
  id: string;
  name: string;
  user: string;
  state: any;
  finalStatus: any;
  runtime: string;
  containers: string;
  vCpu: string;
}

const applications: IAppCluster[] = [
  {
    id: "1",
    name: "customer",
    user: "Shadab",
    state: "ERROR",
    finalStatus: "succeeded",
    runtime: "5 hrs",
    containers: "8",
    vCpu: "4",
  },
  {
    id: "2",
    name: "customer",
    user: "Shadab",
    state: "ERROR",
    finalStatus: "succeeded",
    runtime: "5 hrs",
    containers: "8",
    vCpu: "4",
  },
];

const JobsTabPane: FC<{}> = () => {
  const handleError = useErrorHandler();
  const [state, setState] = React.useState<{ allCluster: IAppCluster[] }>({
    allCluster: [],
  });
  const renderName = (t: any, r: IAppCluster, i: number) => {
    return (
      <div style={{ display: "flex", alignItems: "center" }}>
        <div className='app-icon'>
          <Hexagon size={28} />
        </div>
        <div>
          <div className='app-name'>{r.name}</div>
          <div className='app-desc'>WORKPACE</div>
        </div>
      </div>
    );
  };
  const renderCurrentState = (t: any, r: IAppCluster, i: number) => {
    return (
      <div style={{ display: "flex", alignItems: "center" }}>
        <WorkspaceStatusTag status={r.state} />
      </div>
    );
  };
  const renderFinalStatus = (t: any, r: IAppCluster, i: number) => {
    return (
      <div style={{ display: "flex", alignItems: "center" }}>
        <StatusTag status={r.finalStatus.toLowerCase()} />
      </div>
    );
  };

  React.useEffect(() => {
    SparkService.getAllCluster(
      (r) => {
        setState({ ...state, allCluster: r });
      },
      (err) => {
        if (err) {
          handleError({ name: "something", message: "Failed calling jobs in the cluster" });
        }
      }
    );
  }, []);

  return (
    <Table dataSource={state.allCluster} rowKey={(r: IAppCluster) => r.id} pagination={false} className='tbl-applications' style={{ minHeight: 200 }}>
      <Column title='NAME' dataIndex='name' key='id' className='table-cell-light' render={renderName} />
      <Column title='USER' dataIndex='user' key='id' className='table-cell-light' />
      <Column title='STATE' dataIndex='state' key='id' className='table-cell-light' render={renderCurrentState} />
      <Column title='FINAL STATUS' dataIndex='finalStatus' key='id' className='table-cell-light' render={renderFinalStatus} />
      <Column title='RUNTIME' dataIndex='runtime' key='id' className='table-cell-light' />
      <Column title='CONTAINERS' dataIndex='containers' key='id' className='table-cell-light' />
      <Column title='V CPU' dataIndex='vCpu' key='id' className='table-cell-light' />
    </Table>
  );
};

const WorkspaceDashboard: FC = () => {
  const onTabsChange = (v: any) => {};

  return (
    <div className='workspace-wrapper dashboard-container'>
      <Row gutter={[0, 0]} className='header-row card-shadow-light' align='middle' justify='space-between'>
        <Col span={12} className='header-col'>
          <div className='header-icon'>
            <Hexagon size={48} />
          </div>
          <div>
            <div className='header-title'>Gigahex.com</div>
            <div className='header-desc'>WORKPACE DASHBOARD</div>
          </div>
        </Col>
        <Col className='header-col stats-info'>
          <div className='stat-item'>
            <div className='stat-value'>
              18 <span>%</span>
            </div>
            <div className='stat-name'>CPU USED</div>
          </div>
          <div className='stat-item'>
            <div className='stat-value'>
              50 <span>%</span>
            </div>
            <div className='stat-name'>MEMORY USED</div>
          </div>
          <div className='stat-item'>
            <div className='stat-value'>20</div>
            <div className='stat-name'>APPS ACITVE</div>
          </div>
        </Col>
      </Row>

      <div className='tabs-section card-shadow-light'>
        <Tabs defaultActiveKey='jobs' onChange={onTabsChange} className='jobs-tabs'>
          <TabPane tab='Jobs' key='jobs' className='jobs-tab-pane' style={{ minHeight: 200 }}>
            <ErrorBoundary FallbackComponent={ErrorFallback}>
              <JobsTabPane />
            </ErrorBoundary>
          </TabPane>
          <TabPane tab='Nodes' style={{ minHeight: 200 }}></TabPane>
        </Tabs>
      </div>
    </div>
  );
};
export default WorkspaceDashboard;
