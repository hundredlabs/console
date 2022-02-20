import * as React from "react";
import { ClusterStatus, Status } from "../../services/Workspace";
import { Badge, Tag, Tooltip } from "antd";
import "./StatusTag.scss";
import { SparkProcessStatus } from "../../services/SparkService/SparkService";
export type WorkspaceStatus = "INITIALISING" | "ACTIVE" | "TERMINATING" | "TERMINATED" | "ERROR";
export const StatusTag: React.FC<{ status: Status }> = (props) => {
  switch (props.status) {
    case "succeeded":
      return <span className='status-tag status-tag-green'>{props.status}</span>;
    case "running":
      return <span className='status-tag status-tag-blue'>{props.status}</span>;
    case "failed":
      return <span className='status-tag status-tag-red'>{props.status}</span>;
    case "exceeded":
      return <span className='status-tag status-tag-yellow'>time limit {props.status}</span>;
    default:
      return <span className='status-tag status-tag-gray'>{props.status}</span>;
  }
};

export const StatusBadge: React.FC<{ status: ClusterStatus }> = (props) => {
  switch (props.status) {
    case "unhealthy":
      return (
        <Tag color='transparent'>
          <Badge status='error' style={{ color: "#cc7f30" }} text='requires restart' />
        </Tag>
      );
    case "new":
    case "inactive":
      return (
        <Tag color='transparent'>
          <Badge status='default' style={{ color: "#a5aec0", fontWeight: "bold" }} text={props.status} />
        </Tag>
      );
    case "healthy":
    case "running":
      return (
        <Tag color='transparent'>
          <Badge status='success' style={{ color: "#4bbb4b", fontWeight: "bold" }} text='running' />
        </Tag>
      );
    case "terminated_with_errors":
      return (
        <Tag color='transparent'>
          <Badge status='error' style={{ fontWeight: "bold" }} text='terminated with errors' />
        </Tag>
      );
    case "bootstrapping":
    case "downloading":
    case "stopping":
    case "starting":
      return (
        <Tag color='transparent'>
          <Badge status='processing' style={{ color: "#5b91ff", fontWeight: "bold" }} text={props.status} />
        </Tag>
      );
    case "terminating":
      return (
        <Tag color='transparent'>
          <Badge status='processing' color='#ffbb02' style={{ color: "#ffbb02", fontWeight: "bold" }} text={props.status} />
        </Tag>
      );
    case "terminated":
      return (
        <Tag color='transparent'>
          <Badge status='default' style={{ color: "#a5aec0", fontWeight: "bold" }} text={props.status} />
        </Tag>
      );
    default:
      return <span className='status-tag status-tag-yellow'>{props.status}</span>;
  }
};

export const WorkspaceStatusTag: React.FC<{ status: WorkspaceStatus }> = ({ status }) => {
  switch (status) {
    case "INITIALISING":
      return <span className='status-tag status-tag-green'>{status.toLowerCase()}</span>;
    case "ACTIVE":
      return <span className='status-tag status-tag-blue'>{status.toLowerCase()}</span>;
    case "ERROR":
      return <span className='status-tag status-tag-red'>{status.toLowerCase()}</span>;
    case "TERMINATING":
      return <span className='status-tag status-tag-yellow'>{status.toLowerCase()}</span>;
    default:
      return <span className='status-tag status-tag-gray'>{status.toLowerCase()}</span>;
  }
};

export const RunBadge: React.FC<{ status: Status }> = ({ status }) => {
  return <span className={`run-circle run-status-${status}`}></span>;
};

export const ClusterStatusTag: React.FC<{ status: ClusterStatus }> = (props) => {
  switch (props.status) {
    case "running":
      return <span className='status-tag status-tag-green'>{props.status}</span>;
    case "starting":
      return <span className='status-tag status-tag-blue'>{props.status}</span>;
    case "terminated_with_errors":
      return <span className='status-tag status-tag-red'>{props.status}</span>;
    case "bootstrapping":
      return <span className='status-tag status-tag-yellow'>{props.status}</span>;
    default:
      return <span className='status-tag status-tag-gray'>{props.status}</span>;
  }
};

export const ProcessesStatus: React.FC<{ status: ClusterStatus; processName: string }> = (props) => {
  switch (props.status) {
    case "unhealthy":
      return (
        <Tag color='transparent'>
          <Badge status='error' style={{ color: "#3b4a73" }} text={props.processName} />
        </Tag>
      );

    case "not started":
    case "new":
    case "inactive":
      return (
        <Tooltip title={props.status}>
          <Tag color='transparent'>
            <Badge status='default' style={{ color: "#3b4a73" }} text={props.processName} />
          </Tag>
        </Tooltip>
      );
    case "healthy":
    case "running":
      return (
        <Tooltip title={props.status}>
          <Tag color='transparent'>
            <Badge color='#4bbb4b' style={{ color: "#3b4a73" }} text={props.processName} />
          </Tag>
        </Tooltip>
      );
    case "bootstrapping":
    case "downloading":
    case "stopping":
    case "starting":
      return (
        <Tooltip title={props.status}>
          <Tag color='transparent'>
            <Badge status='processing' style={{ color: "#3b4a73" }} text={props.processName} />
          </Tag>
        </Tooltip>
      );
    case "terminating":
      return (
        <Tag color='transparent'>
          <Badge status='processing' color='#ffbb02' style={{ color: "#3b4a73" }} text={props.processName} />
        </Tag>
      );
    case "stopped":
    case "terminated":
      return (
        <Tooltip title={props.status}>
          <Tag color='transparent'>
            <Badge status='default' style={{ color: "#3b4a73" }} text={props.processName} />
          </Tag>
        </Tooltip>
      );
    default:
      return <span className='status-tag status-tag-yellow'>{props.processName}</span>;
  }
};

export const SparkProcessStatusTag: React.FC<{ status: SparkProcessStatus }> = (props) => {
  switch (props.status) {
    case "ALIVE":
      return (
        <Tooltip title={props.status}>
          <Tag color='transparent'>
            <Badge color='#4bbb4b' style={{ color: "#3b4a73" }} text={false} />
          </Tag>
        </Tooltip>
      );
    case "STANDBY":
      return (
        <Tooltip title={props.status}>
          <Tag color='transparent'>
            <Badge status='default' style={{ color: "#3b4a73" }} text={false} />
          </Tag>
        </Tooltip>
      );
    default:
      return (
        <Tooltip title={props.status}>
          <Tag color='transparent'>
            <Badge status='default' style={{ color: "#3b4a73" }} text={false} />
          </Tag>
        </Tooltip>
      );
  }
};
