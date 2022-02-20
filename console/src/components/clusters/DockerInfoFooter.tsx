import { Space, Tag, Tooltip } from "antd";
import React, { FC } from "react";
import packageJson from "../../../package.json";
import { ConsoleLogo } from "../Icons/ConsoleLogo";

const DockerInfoFooter: FC<{ contId: string; isCollapsed: Boolean }> = ({ contId, isCollapsed }) => {
  return (
    <div className='docker-info-footer'>
      {isCollapsed ? (
        <>
          <Tooltip title={`V : ${packageJson?.version}`} placement='right'>
            <div className='logo-center'>
              <ConsoleLogo />
            </div>
          </Tooltip>
        </>
      ) : (
        <>
          <Space size='small' align='center'>
            <ConsoleLogo />
            <div className={`product-name`}>{packageJson.name}</div>
            <Tag color='geekblue'>{packageJson?.version}</Tag>
          </Space>
        </>
      )}
    </div>
  );
};

export default DockerInfoFooter;
