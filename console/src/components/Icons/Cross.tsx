import React, { PureComponent } from "react";
import { Tag } from "antd";
import "./Icon.scss";
import { statusColor, Color } from "./Colors";

export default class Cross extends PureComponent<{ color: Color; border?: boolean }> {
  render() {
    const color = statusColor(this.props.color);
    let cls = "";
    if (this.props.border) {
      cls = `icon-status-${this.props.color}`;
    }
    return (
      //<Tag color='red'>#7</Tag>
      <div className={`icon-status`}>
        <svg
          xmlns='http://www.w3.org/2000/svg'
          x='0'
          y='0'
          enableBackground='new 0 0 47.971 47.971'
          version='1.1'
          viewBox='0 0 47.971 47.971'
          xmlSpace='preserve'>
          <path
            stroke={color}
            fill={color}
            d='M28.228 23.986L47.092 5.122a2.998 2.998 0 000-4.242 2.998 2.998 0 00-4.242 0L23.986 19.744 5.121.88a2.998 2.998 0 00-4.242 0 2.998 2.998 0 000 4.242l18.865 18.864L.879 42.85a2.998 2.998 0 104.242 4.241l18.865-18.864L42.85 47.091c.586.586 1.354.879 2.121.879s1.535-.293 2.121-.879a2.998 2.998 0 000-4.242L28.228 23.986z'></path>
        </svg>
      </div>
    );
  }
}
